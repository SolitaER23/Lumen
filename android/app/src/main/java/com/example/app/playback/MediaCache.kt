package com.example.app.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
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

    fun cacheDataSourceFactory(ctx: Context): CacheDataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
        return CacheDataSource.Factory()
            .setCache(cache(ctx))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx.applicationContext, http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * 只清理「流媒体缓存与封面缓存」，保留 [keepKeys]（已下载曲目的 cache key = 下载 URI）。
     */
    fun clearStreamingCache(ctx: Context, keepKeys: Set<String>) {
        val c = cache(ctx)
        runCatching {
            c.keys.toList().forEach { key ->
                if (key !in keepKeys) runCatching { c.removeResource(key) }
            }
        }
        runCatching { File(ctx.applicationContext.cacheDir, "image_cache").deleteRecursively() }
    }

    /** 缓存目录（流 + 下载共用）占用字节。 */
    fun cacheBytes(ctx: Context): Long = runCatching {
        File(ctx.applicationContext.cacheDir, "media")
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }.getOrDefault(0L)

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
