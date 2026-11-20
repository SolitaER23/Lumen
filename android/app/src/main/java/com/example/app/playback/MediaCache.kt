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
 * 播放与离线下载必须用**同一个** [SimpleCache] 实例（同一目录二次打开会抛
 * "SimpleCache is locked"），因此这里统一持有。下载完的内容会被播放直接命中。
 */
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
