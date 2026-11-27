package app.lumen.player.playback

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
import app.lumen.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    private val debug = app.lumen.player.BuildConfig.DEBUG

    private fun logd(msg: String) { if (debug) android.util.Log.d("Lumen", msg) }
    private fun logi(msg: String) { if (debug) app.lumen.player.util.Logx.i("Lumen", msg) }
    private fun logw(msg: String) { if (debug) android.util.Log.w("Lumen", msg) }
    /** 播放地址提供者：Activity 登录后注入（要区分 m4a 转码） */
    var urlFor: (Track) -> String = { "" }

    /** 切歌音量渐变时长（毫秒，0 = 关闭） */
    var fadeMs: Long = 260L

    private val fadeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var fadeJob: Job? = null

    /** 强制转码地址（解码失败兜底用），默认退回 urlFor */
    var transcodeUrl: (Track) -> String = { urlFor(it) }


    val player: ExoPlayer = run {
        // 在线播放共用同一个 SimpleCache（MediaCache），听过的段落再次播放直接命中
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

    // 队列也做成 Compose 状态：UI 的「当前曲目」由 queue + index 推导，
    // 只让 index 可观察时，换队列但下标不变（如从专辑 A 第 1 首切到专辑 B 第 1 首）
    // 不会触发重组，专辑页的按钮状态就会停在上一张专辑。队列只在开始播放时整体替换，开销可忽略。
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var albumId: String? = null
        private set
    var index by mutableIntStateOf(-1)
        private set
    var playing by mutableStateOf(false)
        private set
    /**
     * 播放意图：从「点了播放」到「真正出声」之间的缓冲窗口也算在播。
     *
     * [playing] 只在 ExoPlayer 真正出声时为 true，缓冲期间是 false；如果 UI 只看它，
     * 刚点完播放到开声前的一小段时间按钮会退回「播放/继续」，此时再点也无法暂停
     * （toggle 会把它当成"还没播"又 play 一次）。按钮图标、迷你条与专辑页主按钮
     * 用这个状态，scrobble / 背景律动等"确实在出声"的逻辑仍用 [playing]。
     */
    var playIntent by mutableStateOf(false)
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
    /**
     * 已知需要走"转码兜底"的曲目 id 集合。
     *
     * 一条直接流解码失败后就记住它：下次装载同样走转码地址，
     * 避免每次播放都先失败一次（旧实现只记一首，换到同专辑另一首坏文件时会重复抖一下）。
     */
    private val transcodeNeeded = HashSet<String>()

    val current: Track? get() = queue.getOrNull(index)

    init {
        applyPlayMode(PlayMode.LIST)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                refreshPlayIntent()
                app.lumen.player.util.Logx.i("LumenPlay", "isPlaying=$isPlaying state=${player.playbackState} pwr=${player.playWhenReady}")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                refreshPlayIntent()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                refreshPlayIntent()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = player.currentMediaItemIndex
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) coverDirection = 1
            }

            override fun onPlayerError(error: PlaybackException) {
                val t = current ?: return
                if (t.id in transcodeNeeded) {
                    logw("playback error again for ${t.id} after transcode: ${error.errorCodeName}")
                    return
                }
                transcodeNeeded += t.id
                logw("decode failed (${error.errorCodeName}) for ${t.title} [${t.suffix}] -> transcode fallback")
                val pos = player.currentPosition.coerceAtLeast(0L)
                val itemIndex = player.currentMediaItemIndex
                val transcodeItem = mediaItemOf(t)
                // 必须"原地替换当前条目"而不是 setMediaItem：
                // 后者会把整条队列换成单曲，currentMediaItemIndex 归 0，
                // 于是标题/迷你条跳回第一首（音频其实还是当前这首）。
                if (itemIndex in 0 until player.mediaItemCount) {
                    player.replaceMediaItem(itemIndex, transcodeItem)
                    player.seekTo(itemIndex, pos)
                    player.prepare()
                    player.play()
                } else {
                    // 队列尚未装载（极端情况）才退回单曲播放
                    player.setMediaItem(transcodeItem, pos)
                    player.prepare()
                    player.play()
                }
            }
        })
    }

    /** 统一的 MediaItem 构造：需要转码的曲目直接用转码地址。 */
    private fun mediaItemOf(track: Track): MediaItem {
        val uri = if (track.id in transcodeNeeded) transcodeUrl(track) else urlFor(track)
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build(),
            )
            .build()
    }

    /** 是否处于「请求播放且尚未结束」的状态（含缓冲）。 */
    private fun hasPlayIntent(): Boolean =
        player.playWhenReady &&
            (player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY)

    private fun refreshPlayIntent() {
        playIntent = hasPlayIntent()
    }

    private fun cancelFade(restore: Boolean = true) {
        fadeJob?.cancel()
        fadeJob = null
        if (restore) player.volume = 1f
    }

    private suspend fun rampVolume(target: Float, ms: Long) {
        val from = player.volume.coerceIn(0f, 1f)
        val to = target.coerceIn(0f, 1f)
        if (ms <= 0L) {
            player.volume = to
            return
        }
        val frames = (ms / 16L).coerceAtLeast(1L)
        for (i in 1..frames) {
            player.volume = (from + (to - from) * (i.toFloat() / frames)).coerceIn(0f, 1f)
            delay(16L)
        }
    }

    private suspend fun awaitPlaying(maxMs: Long = 400L) {
        var waited = 0L
        while (!player.isPlaying && waited < maxMs) {
            delay(16L)
            waited += 16L
        }
    }

    private fun startFadeIn(duration: Long = fadeMs) {
        fadeJob?.cancel()
        // 关闭淡入淡出（0ms）时不要先把音量压到 0：
        // 否则 awaitPlaying 期间（最长 400ms）首帧是静音的，用户会觉得"开头被吃掉"
        if (duration <= 0L) {
            fadeJob = null
            player.volume = 1f
            return
        }
        player.volume = 0f
        fadeJob = fadeScope.launch {
            awaitPlaying()
            rampVolume(1f, duration)
            player.volume = 1f
            fadeJob = null
        }
    }

    /** 切歌：先淡出、切换、再淡入；快速连切时跳过淡出、切换后快速淡入 */
    private fun switchWithFade(doSwitch: () -> Unit) {
        if (fadeMs <= 0L || !player.isPlaying) {
            cancelFade(true)
            doSwitch()
            return
        }
        val duration = fadeMs
        if (fadeJob?.isActive == true) {
            cancelFade(true)
            doSwitch()
            startFadeIn(minOf(duration, 140L))
            return
        }
        fadeJob = fadeScope.launch {
            rampVolume(0f, duration / 2)
            doSwitch()
            player.volume = 0f
            awaitPlaying()
            rampVolume(1f, duration - duration / 2)
            player.volume = 1f
            fadeJob = null
        }
    }

    fun play(tracks: List<Track>, startIndex: Int, album: String) {
        if (tracks.isEmpty()) return
        queue = tracks
        albumId = album
        // 已知需要转码的曲目直接给转码地址，避免"先失败再兜底"的那一下
        val items = tracks.map(::mediaItemOf)
        // 注意：流 URL 带 Subsonic 的 u/t/s（可重放凭据），日志必须脱敏
        logi(
            "play album=$album start=$startIndex track=${tracks[startIndex].title} " +
                "suffix=${tracks[startIndex].suffix} " +
                "uri=${app.lumen.player.util.Redact.url(items[startIndex].localConfiguration?.uri?.toString())}",
        )
        // 新队列：先把对外暴露的进度清零，避免播放页用上一首的 position/duration 算比例
        positionMs = 0L
        durationMs = 0L
        player.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        player.prepare()
        player.play()
        startFadeIn()
        index = startIndex
        refreshPlayIntent()
    }

    /** 随机播放整张专辑：洗牌后从头播，队列顺序即随机顺序 */
    fun playShuffled(tracks: List<Track>, album: String) {
        if (tracks.isEmpty()) return
        play(tracks.shuffled(), 0, album)
    }

    fun toggle() {
        // 用 hasPlayIntent 而不是 player.isPlaying：缓冲中再点也要暂停，
        // 否则会重复调用 play()，用户看到的是「暂停键点不动」。
        if (hasPlayIntent()) {
            cancelFade(true)
            player.pause()
            // 暂停时 tick 不再跑，必须显式落一次进度，否则冷启动会回到 0:00
            savePositionOnly()
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

    /** 切换账号等场景：停止播放并清空队列，避免旧账号的曲目/进度串到新账号。 */
    fun stopAndClear() {
        cancelFade(true)
        runCatching {
            player.stop()
            player.clearMediaItems()
        }
        queue = emptyList()
        index = -1
        albumId = null
        // 曲目 id 属于某个服务器账号：换账号/清数据后旧的"需转码"结论不再可信
        transcodeNeeded.clear()
        positionMs = 0L
        durationMs = 0L
        playing = false
        playIntent = false
        savedQueueSignature = null
        runCatching { PlaybackStateStore.clear(ctx) }
    }

    /** 队列签名：队列或索引变化才重写 JSON，避免每 3 秒序列化上千首曲目。 */
    private var savedQueueSignature: Int? = null

    private fun queueSignature(): Int =
        queue.size * 31 + (albumId?.hashCode() ?: 0) + index

    fun saveState() {
        val sig = queueSignature()
        if (sig == savedQueueSignature) return
        savedQueueSignature = sig
        runCatching { PlaybackStateStore.saveQueue(ctx, queue, index, albumId) }
        runCatching { PlaybackStateStore.savePosition(ctx, positionMs) }
    }

    /** 只写进度（播放中每 3 秒一次）。 */
    private fun savePositionOnly() {
        runCatching { PlaybackStateStore.savePosition(ctx, positionMs) }
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
        val items = tracks.map(::mediaItemOf)
        val idx = startIndex.coerceIn(0, items.lastIndex)
        // 不往播放器装载：保持完全静默，UI 用 queue/index/position 显示“上次播放”，点播放时才真正装载
        index = idx
        positionMs = progressMs.coerceAtLeast(0L)
        resumePosition = progressMs.coerceAtLeast(0L)
        app.lumen.player.util.Logx.i("LumenPlay", "restore hint idx=$idx pos=$progressMs state=${player.playbackState}")
    }

    fun next() {
        switchWithFade {
            coverDirection = 1
            if (player.shuffleModeEnabled) {
                player.seekToNextMediaItem()
                player.play()
                return@switchWithFade
            }
            val target = nextIndex(queue.size, player.currentMediaItemIndex)
            if (target != player.currentMediaItemIndex) {
                player.seekTo(target, 0L)
                player.play()
                index = target
            }
        }
    }

    fun prev() {
        switchWithFade {
            coverDirection = -1
            if (player.shuffleModeEnabled) {
                player.seekToPreviousMediaItem()
                player.play()
                return@switchWithFade
            }
            val target = prevIndex(queue.size, player.currentMediaItemIndex, player.currentPosition)
            player.seekTo(target, 0L)
            player.play()
            index = target
        }
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
            // 队列变了就写队列，否则只写一个小 Long
            if (queueSignature() != savedQueueSignature) saveState() else savePositionOnly()
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