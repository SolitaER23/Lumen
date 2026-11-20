package com.example.app.playback

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.app.data.Track

/**
 * 播放控制：ExoPlayer + 512MB 磁盘缓存 + 解码失败自动转码兜底。
 *
 * - 队列是「整张专辑按碟/轨排序后的全部曲目」，多碟分页只影响展示，索引始终是绝对下标；
 * - 缓存走 [SimpleCache]（边下边播，二次播放直接命中）；
 * - 有些格式 ExoPlayer 解不了（旧版是 ALAC 触发），此时把当前曲目换成 Subsonic 的
 *   `stream?format=mp3&maxBitRate=320` 转码流重试一次。
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class Playback(private val ctx: Context) {

    private val debug = com.example.app.BuildConfig.DEBUG

    private fun logd(msg: String) { if (debug) android.util.Log.d("Lumen", msg) }
    private fun logi(msg: String) { if (debug) android.util.Log.i("Lumen", msg) }
    private fun logw(msg: String) { if (debug) android.util.Log.w("Lumen", msg) }
    /** 播放地址提供者：Activity 登录后注入（要区分 m4a 转码） */
    var urlFor: (Track) -> String = { "" }

    /** 强制转码地址（解码失败兜底用），默认退回 urlFor */
    var transcodeUrl: (Track) -> String = { urlFor(it) }


    val player: ExoPlayer = run {
        // 与离线下载共用同一个 SimpleCache（MediaCache），下载完的内容播放直接命中
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(MediaCache.cacheDataSourceFactory(ctx)))
            .build()
            .also { p ->
                // 媒体用途 + 自动处理音频焦点（来电/其他应用播放时让路）
                p.setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                // 拔耳机 / 断蓝牙自动暂停
                p.setHandleAudioBecomingNoisy(true)
            }
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
                    logw("playback error again for ${t.id}: ${error.errorCodeName}")
                    return
                }
                fallbackTrackId = t.id
                logw("decode failed (${error.errorCodeName}) for ${t.title} [${t.suffix}] -> transcode fallback")
                val pos = player.currentPosition.coerceAtLeast(0L)
                player.setMediaItem(
                    MediaItem.Builder()
                        .setUri(transcodeUrl(t))
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(t.title)
                                .setArtist(t.artist)
                                .build(),
                        )
                        .build(),
                    pos,
                )
                player.prepare()
                player.play()
            }
        })
    }

    fun play(tracks: List<Track>, startIndex: Int, album: String) {
        if (tracks.isEmpty()) return
        queue = tracks
        albumId = album
        val items = tracks.map { tr ->
            MediaItem.Builder()
                .setUri(urlFor(tr))
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(tr.title)
                        .setArtist(tr.artist)
                        .build(),
                )
                .build()
        }
        fallbackTrackId = null
        logi(
            "play album=$album start=$startIndex track=${tracks[startIndex].title} " +
                "suffix=${tracks[startIndex].suffix} uri=${items[startIndex].localConfiguration?.uri}",
        )
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
        logd(
            "tick state=${player.playbackState} playing=${player.isPlaying} pos=$positionMs dur=$durationMs " +
                "buf=${player.bufferedPercentage} err=${player.playerError?.errorCodeName}",
        )
    }

    fun release() {
        player.release()
    }

}
