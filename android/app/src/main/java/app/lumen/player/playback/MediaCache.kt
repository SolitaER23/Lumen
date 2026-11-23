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

    /** 已下载内容的独立 cache key，避免与在线播放的同一 URI 并发读写互相卡住。 */
    fun downloadKey(trackId: String): String = "$trackId#lumen-dl"

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
                override fun buildCacheKey(spec: androidx.media3.datasource.DataSpec): String {
                    // 已下载的曲目直接读下载 key；否则用 URI（流缓存/网络）
                    val id = trackIdOf(spec.uri)
                    val key = id?.let { downloadKey(it) }
                    return if (key != null && runCatching { cache(ctx).getCachedSpans(key).isNotEmpty() }.getOrDefault(false)) key
                    else spec.uri.toString()
                }
            })
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

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
