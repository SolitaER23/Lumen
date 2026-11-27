package app.lumen.player.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.lumen.player.data.SettingsStore
import java.io.File

/**
 * 全进程共享的在线播放缓存。
 *
 * 只做一件事：把「边听边缓冲」的音频 span 落在应用私有目录，二次播放直接命中。
 * 缓存可以随时被系统或用户清理，因此不承载任何「已保存内容」语义。
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object MediaCache {
    /** 播放缓存上限；只服务在线缓冲，不参与任何离线内容保留。 */
    private const val STREAM_MAX_BYTES = 512L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    @Volatile
    private var database: StandaloneDatabaseProvider? = null

    /** 媒体缓存目录：应用私有 files 目录，避免播放缓存被系统按 cacheDir 策略提前回收。 */
    internal fun mediaDir(ctx: Context): File =
        File(ctx.applicationContext.filesDir, "media")

    fun cache(ctx: Context): SimpleCache = synchronized(this) {
        cache ?: SimpleCache(
            mediaDir(ctx),
            LeastRecentlyUsedCacheEvictor(STREAM_MAX_BYTES),
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
     * 只用 "track:<id>" 会让 A 服务器的音频命中 B 服务器的缓存（错歌 + 跨账号串数据）。
     * 这里只读 base/user（不解密密码），并对结果做一次短哈希，避免把服务器地址写进键名。
     */
    internal fun accountScope(ctx: Context): String = scopeOf(SettingsStore.identity(ctx))

    /** 纯函数形式，便于单测。 */
    internal fun scopeOf(identity: String): String = sha256Hex("lumen-cache|" + identity).take(8)

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
                    cacheKeyFor(ctx, spec.uri)
            })
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * 播放缓存的键：**不能**直接用 URI——Subsonic 的 u/t/s 参数是可重放凭据，
     * 写进 SimpleCache 索引库等于把账号访问权留在磁盘上。
     *
     * 有 id（音频流）时用 "acct:<scope>|track:<id>#<format>[@bitrate]"
     * （同一首歌的原始流与转码流字节不同，必须区分）。没有 id 的罕见请求退回 URI 摘要。
     */
    internal fun cacheKeyFor(ctx: Context, uri: android.net.Uri): String {
        val id = trackIdOf(uri) ?: return uriKey(uri.toString())
        return streamKey(
            accountScope(ctx),
            id,
            uri.getQueryParameter("format"),
            uri.getQueryParameter("maxBitRate"),
        )
    }

    /** 纯函数：流缓存键（可单测）。 */
    internal fun streamKey(scope: String, trackId: String, format: String?, bitrate: String?): String {
        val f = format?.takeIf { it.isNotBlank() } ?: "raw"
        val b = bitrate?.takeIf { it.isNotBlank() }
        return "acct:" + scope + "|track:" + trackId + "#" + f + if (b != null) "@" + b else ""
    }

    /** 纯函数：无曲目 id 时的回落键（对 URI 取摘要，避免把凭据写进索引库）。 */
    internal fun uriKey(rawUri: String): String = "uri:" + sha256Hex(rawUri)

    private fun sha256Hex(raw: String): String = runCatching {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        d.joinToString("") { b -> "%02x".format(java.util.Locale.ROOT, b) }.take(32)
    }.getOrDefault(Integer.toHexString(raw.hashCode()))

    /** 清空在线播放缓存（设置页「歌曲缓存」与「清除本机数据」共用）。 */
    fun clearAllCache(ctx: Context) {
        val c = cache(ctx)
        val keys = runCatching { c.keys.toList() }.getOrDefault(emptyList())
        keys.forEach { key -> runCatching { c.removeResource(key) } }
    }

    fun clearImageCache(ctx: Context) {
        runCatching { File(ctx.applicationContext.cacheDir, "image_cache").deleteRecursively() }
    }

    /** 歌曲流缓存占用。 */
    fun streamingBytes(ctx: Context): Long =
        runCatching { cache(ctx).cacheSpace }.getOrDefault(0L)

    /** 封面缓存占用。 */
    fun imageBytes(ctx: Context): Long = runCatching {
        File(ctx.applicationContext.cacheDir, "image_cache")
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }.getOrDefault(0L)
}
