package com.example.app.playback

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.app.data.Track
import java.io.File

/**
 * 播放控制：ExoPlayer + 512MB 磁盘缓存 + 解码失败自动转码兜底。
 *
 * - 队列是「整张专辑按碟/轨排序后的全部曲目」，多碟分页只影响展示，索引始终是绝对下标；
 * - 缓存走 [SimpleCache]（边下边播，二次播放直接命中）；
 * - 有些格式 ExoPlayer 解不了（旧版是 ALAC 触发），此时把当前曲目换成 Subsonic 的
 *   `stream?format=mp3&maxBitRate=320` 转码流重试一次。
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class Playback(private val ctx: Context, private val transcoded: (Track) -> String) {
    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(ctx.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024),
            StandaloneDatabaseProvider(ctx),
        )
    }

    val player: ExoPlayer = run {
        val upstream = DefaultHttpDataSource.Factory().setConnectTimeoutMs(10_000).setReadTimeoutMs(20_000)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx, upstream))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .build()
    }

    var queue: List<Track> = emptyList()
        private set
    var albumId: String? = null
        private set
    var index by mutableIntStateOf(-1)
        private set
    var playing by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    /** 已经为哪一首做过转码兜底（按曲目 id 记，换曲才重置，避免 setMediaItem 触发 transition 后无限重试） */
    private var fallbackTrackId: String? = null

    val current: Track? get() = queue.getOrNull(index)

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = player.currentMediaItemIndex
            }

            override fun onPlayerError(error: PlaybackException) {
                val t = current ?: return
                if (fallbackTrackId == t.id) {
                    android.util.Log.w("Lumen", "playback error again for ${t.id}: ${error.errorCodeName}")
                    return
                }
                fallbackTrackId = t.id
                android.util.Log.w(
                    "Lumen",
                    "decode failed (${error.errorCodeName}) for ${t.title} [${t.suffix}] -> transcode fallback",
                )
                val pos = player.currentPosition.coerceAtLeast(0L)
                player.setMediaItem(MediaItem.fromUri(transcoded(t)), pos)
                player.prepare()
                player.play()
            }
        })
    }

    fun play(tracks: List<Track>, startIndex: Int, album: String, urlFor: (Track) -> String) {
        if (tracks.isEmpty()) return
        queue = tracks
        albumId = album
        val items = tracks.map { MediaItem.fromUri(urlFor(it)) }
        fallbackTrackId = null
        player.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        player.prepare()
        player.play()
        index = startIndex
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        val target = nextIndex(queue.size, player.currentMediaItemIndex)
        if (target != player.currentMediaItemIndex) {
            player.seekTo(target, 0L)
            player.play()
            index = target
        }
    }

    fun prev() {
        val target = prevIndex(queue.size, player.currentMediaItemIndex, player.currentPosition)
        player.seekTo(target, 0L)
        player.play()
        index = target
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms.coerceAtLeast(0L))
        positionMs = ms.coerceAtLeast(0L)
    }

    fun tick() {
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.coerceAtLeast(0L)
        index = player.currentMediaItemIndex
        android.util.Log.d(
            "Lumen",
            "tick state=${player.playbackState} playing=${player.isPlaying} pos=$positionMs dur=$durationMs " +
                "buf=${player.bufferedPercentage} err=${player.playerError?.errorCodeName}",
        )
    }

    fun release() {
        player.release()
        runCatching { cache.release() }
    }

}
