package app.lumen.player.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import app.lumen.player.data.SettingsStore
import java.io.File
import java.util.concurrent.Executors

/**
 * 全进程共享的媒体缓存 + 下载管理器。
 *
 * 播放与离线下载必须用同一个 [SimpleCache] 实例（同一目录二次打开会抛
 * "SimpleCache is locked"）。因此「清理缓存」不能删目录，而是按 key 清理——
 * 下载内容以 `DownloadRequest.uri` 作为 cache key，把这些 key 保留即可。
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object MediaCache {
    @Volatile
    private var cache: SimpleCache? = null

    @Volatile
    private var database: StandaloneDatabaseProvider? = null

    @Volatile
    private var manager: DownloadManager? = null

    private const val MAX_BYTES = 1024L * 1024 * 1024 // 1 GB

    fun cache(ctx: Context): SimpleCache = synchronized(this) {
        cache ?: SimpleCache(
            File(ctx.applicationContext.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            database(ctx),
        ).also { cache = it }
    }

    fun database(ctx: Context): StandaloneDatabaseProvider = synchronized(this) {
        database ?: StandaloneDatabaseProvider(ctx.applicationContext).also { database = it }
    }

    /**
     * 账号作用域指纹。
     *
     * 缓存键里**必须**带账号身份：不同 Navidrome 上的曲目 id 完全可能相同，
     * 只用 `track:<id>` 会让 A 服务器的音频命中 B 服务器的缓存（错歌 + 跨账号串数据）。
     * 这里只读 base/user（不解密密码），并对结果做一次短哈希，避免把服务器地址写进键名。
     */
    internal fun accountScope(ctx: Context): String = scopeOf(SettingsStore.identity(ctx))

    /** 纯函数形式，便于单测。 */
    internal fun scopeOf(identity: String): String = sha256Hex("lumen-cache|" + identity).take(8)

    /** 已下载内容的独立 cache key（账号隔离），避免与在线播放的同一 URI 并发读写互相卡住。 */
    fun downloadKey(ctx: Context, trackId: String): String =
        "acct:" + accountScope(ctx) + "|" + trackId + "#lumen-dl"

    /** 0.74~0.90 的下载键（无账号前缀），仅用于兼容升级前已下载的内容。 */
    internal fun legacyDownloadKey(trackId: String): String = "$trackId#lumen-dl"

    private fun trackIdOf(uri: android.net.Uri): String? =
        runCatching { uri.getQueryParameter("id") }.getOrNull()?.takeIf { it.isNotBlank() }

    fun cacheDataSourceFactory(ctx: Context): CacheDataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
        return CacheDataSource.Factory()
            .setCache(cache(ctx))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx.applicationContext, http))
            .setCacheKeyFactory(object : CacheKeyFactory {
                override fun buildCacheKey(spec: androidx.media3.datasource.DataSpec): String =
                    cacheKeyFor(
                        ctx,
                        spec.uri,
                    ) { k -> runCatching { cache(ctx).getCachedSpans(k).isNotEmpty() }.getOrDefault(false) }
            })
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * 流缓存的键：**不能**直接用 URI——Subsonic 的 `u/t/s` 参数是可重放凭据，
     * 写进 SimpleCache 索引库（cacheDir/media）等于把账号访问权留在磁盘上。
     *
     * 有 `id`（音频流）时用 `track:<id>#<format|raw>[@bitrate]`：
     * 同一首歌的原始流与转码流字节不同，必须区分 format/bitRate，否则会串味。
     * 没有 `id` 的罕见请求退回 URI 的 SHA-256 摘要。
     */
    internal fun cacheKeyFor(
        ctx: Context,
        uri: android.net.Uri,
        hasDownloadSpans: (String) -> Boolean,
    ): String {
        val id = trackIdOf(uri)
        if (id != null) {
            val scope = accountScope(ctx)
            // 已下载内容优先（新键 > 旧键，保证升级前的离线内容仍能命中）
            val dl = downloadKey(ctx, id)
            if (hasDownloadSpans(dl)) return dl
            val legacyDl = legacyDownloadKey(id)
            if (hasDownloadSpans(legacyDl)) return legacyDl
            val format = uri.getQueryParameter("format")?.takeIf { it.isNotBlank() } ?: "raw"
            val bitrate = uri.getQueryParameter("maxBitRate")?.takeIf { it.isNotBlank() }
            return "acct:" + scope + "|track:" + id + "#" + format +
                if (bitrate != null) "@" + bitrate else ""
        }
        return "uri:" + sha256Hex(uri.toString())
    }

    private fun sha256Hex(raw: String): String = runCatching {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        d.joinToString("") { b -> "%02x".format(java.util.Locale.ROOT, b) }.take(32)
    }.getOrDefault(Integer.toHexString(raw.hashCode()))

    /** 只清歌曲流缓存，保留 [keepKeys]（已下载曲目的 cache key = 下载 URI）。封面/歌词/分析结果不受影响。 */
    fun clearSongCache(ctx: Context, keepKeys: Set<String>) {
        val c = cache(ctx)
        runCatching {
            c.keys.toList().forEach { key ->
                if (key !in keepKeys) runCatching { c.removeResource(key) }
            }
        }
    }

    /** 旧接口：清空全部本地数据时使用（歌曲 + 封面）。 */
    fun clearStreamingCache(ctx: Context, keepKeys: Set<String>) {
        clearSongCache(ctx, keepKeys)
        clearImageCache(ctx)
    }

    fun clearImageCache(ctx: Context) {
        runCatching { File(ctx.applicationContext.cacheDir, "image_cache").deleteRecursively() }
    }

    /** 歌曲流缓存占用（不含封面/歌词/下载）。 */
    fun streamingBytes(ctx: Context): Long = runCatching {
        File(ctx.applicationContext.cacheDir, "media")
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }.getOrDefault(0L)

    /** 封面缓存占用。 */
    fun imageBytes(ctx: Context): Long = runCatching {
        File(ctx.applicationContext.cacheDir, "image_cache")
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }.getOrDefault(0L)

    /** 缓存目录（流 + 下载共用）占用字节。 */
    fun cacheBytes(ctx: Context): Long = streamingBytes(ctx)

    fun downloadManager(ctx: Context): DownloadManager = synchronized(this) {
        manager ?: DownloadManager(
            ctx.applicationContext,
            database(ctx),
            cache(ctx),
            DefaultDataSource.Factory(ctx.applicationContext, DefaultHttpDataSource.Factory()),
            Executors.newFixedThreadPool(2),
        ).also { manager = it }
    }
}
