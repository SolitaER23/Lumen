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
    /** 播放模式：列表循环 / 单曲循环 / 随机播放 */
    var playMode by mutableStateOf(PlayMode.LIST)
        private set
    /** 切歌方向：1=下一首（封面从左上展开），-1=上一首（从右下展开） */
    var coverDirection by mutableIntStateOf(1)
        private set
    /** 已经为哪一首做过转码兜底（按曲目 id 记，换曲才重置，避免 setMediaItem 触发 transition 后无限重试） */
    private var fallbackTrackId: String? = null

    val current: Track? get() = queue.getOrNull(index)

    init {
        applyPlayMode(PlayMode.LIST)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                android.util.Log.i("LumenPlay", "isPlaying=$isPlaying state=${player.playbackState} pwr=${player.playWhenReady}")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = player.currentMediaItemIndex
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) coverDirection = 1
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
        // 新队列：先把对外暴露的进度清零，避免播放页用上一首的 position/duration 算比例
        positionMs = 0L
        durationMs = 0L
        player.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        player.prepare()
        player.play()
        index = startIndex
    }

    /** 随机播放整张专辑：洗牌后从头播，队列顺序即随机顺序 */
    fun playShuffled(tracks: List<Track>, album: String) {
        if (tracks.isEmpty()) return
        play(tracks.shuffled(), 0, album)
    }

    fun toggle() {
        if (player.isPlaying) {
            player.pause()
            saveState()
        } else {
            if (player.mediaItemCount == 0 && queue.isNotEmpty()) {
                // 冷启动后第一次播放：按上次的曲目与进度装载
                val target = index.coerceIn(0, queue.lastIndex)
                play(queue, target, albumId ?: "")
                player.seekTo(resumePosition)
                return
            }
            if (player.playbackState == Player.STATE_IDLE && queue.isNotEmpty()) {
                player.prepare()
                player.seekTo(index.coerceAtLeast(0), resumePosition)
            }
            // 播放到末尾后再次点播放：从当前曲目开头重新开始
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(index.coerceAtLeast(0), 0L)
                positionMs = 0L
            }
            player.play()
        }
    }

    fun cyclePlayMode(): PlayMode {
        val next = when (playMode) {
            PlayMode.LIST -> PlayMode.ONE
            PlayMode.ONE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.LIST
        }
        applyPlayMode(next)
        return next
    }

    private fun applyPlayMode(mode: PlayMode) {
        playMode = mode
        player.repeatMode = when (mode) {
            PlayMode.ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_ALL
        }
        player.shuffleModeEnabled = mode == PlayMode.SHUFFLE
    }

    fun saveState() {
        runCatching { PlaybackStateStore.save(ctx, queue, index, albumId, positionMs) }
    }

    fun enforcePaused() {
        runCatching {
            player.pause()
            player.playWhenReady = false
        }
    }

    /** 冷启动恢复：载入队列与进度，保持暂停。 */
    fun restore(tracks: List<Track>, startIndex: Int, progressMs: Long, album: String?) {
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
        val idx = startIndex.coerceIn(0, items.lastIndex)
        // 不往播放器装载：保持完全静默，UI 用 queue/index/position 显示“上次播放”，点播放时才真正装载
        index = idx
        positionMs = progressMs.coerceAtLeast(0L)
        resumePosition = progressMs.coerceAtLeast(0L)
        android.util.Log.i("LumenMood", "restore hint idx=$idx pos=$progressMs state=${player.playbackState}")
    }

    fun next() {
        coverDirection = 1
        if (player.shuffleModeEnabled) {
            player.seekToNextMediaItem()
            player.play()
            return
        }
        val target = nextIndex(queue.size, player.currentMediaItemIndex)
        if (target != player.currentMediaItemIndex) {
            player.seekTo(target, 0L)
            player.play()
            index = target
        }
    }

    fun prev() {
        coverDirection = -1
        if (player.shuffleModeEnabled) {
            player.seekToPreviousMediaItem()
            player.play()
            return
        }
        val target = prevIndex(queue.size, player.currentMediaItemIndex, player.currentPosition)
        player.seekTo(target, 0L)
        player.play()
        index = target
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms.coerceAtLeast(0L))
        positionMs = ms.coerceAtLeast(0L)
    }

    private var lastSaveAt = 0L
    private var resumePosition = 0L

    fun tick() {
        if (player.playbackState != Player.STATE_IDLE) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            index = player.currentMediaItemIndex
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastSaveAt > 3000L) {
            lastSaveAt = now
            saveState()
        }
        logd(
            "tick state=${player.playbackState} playing=${player.isPlaying} pos=$positionMs dur=$durationMs " +
                "buf=${player.bufferedPercentage} err=${player.playerError?.errorCodeName}",
        )
    }

    fun release() {
        player.release()
    }

}

/** 播放模式：列表循环、单曲循环、随机播放。 */
enum class PlayMode { LIST, ONE, SHUFFLE }