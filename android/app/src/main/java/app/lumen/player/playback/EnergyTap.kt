package app.lumen.player.playback

import app.lumen.player.data.MoodResult

/**
 * 背景的节拍/强弱驱动：
 * - 分析阶段已存下 beatsMs（拍点）与 env（100ms 能量包络）；
 * - 播放时用 position 平滑推进，拍点给柔和冲量，包络驱动明暗呼吸；
 * - 没有分析数据时退回 tempo 相位呼吸，保证不会死板。
 */
object MusicPulse {
    @Volatile var playing: Boolean = false
        private set
    @Volatile var level: Float = 0f
        private set
    @Volatile var beat: Float = 0f
        private set
    @Volatile var tempoBpm: Float = 0f
        private set

    private var beats = IntArray(0)
    private var env = FloatArray(0)
    private var envStep = 100
    private var posMs = 0.0
    private var beatIdx = 0
    private var winBeats = ArrayList<IntArray>()
    private var winStarts = IntArray(0)
    private var winIdx = -1
    private var winArousal = FloatArray(0)
    private var winValence = FloatArray(0)
    private var winTempo = FloatArray(0)
    private var trackId: String? = null
    private var beatLogs = 0
    private var lastPos = 0.0

    fun sync(id: String?, playingNow: Boolean, mood: MoodResult?) {
        if (id != trackId) {
            trackId = id
            posMs = 0.0
            beatIdx = 0
            beat = 0f
            beatLogs = 0
        }
        playing = playingNow
        val t = mood?.tempo ?: 0f
        tempoBpm = t.coerceIn(0f, 200f)
        beats = mood?.beatsMs ?: IntArray(0)
        winBeats = ArrayList(mood?.windowsBeats ?: emptyList())
        winStarts = mood?.windowsStartMs ?: IntArray(0)
        winArousal = mood?.windowsArousal ?: FloatArray(0)
        winValence = mood?.windowsValence ?: FloatArray(0)
        winTempo = mood?.windowsTempo ?: FloatArray(0)
        winIdx = -1
        env = mood?.env ?: FloatArray(0)
        envStep = mood?.envStepMs ?: 100
        if (beats.isNotEmpty()) {
            app.lumen.player.util.Logx.i(
                "LumenPulse",
                "sync $id beats=${beats.size} env=${env.size} step=$envStep tempo=${"%.0f".format(tempoBpm)}",
            )
        }
    }

    /** 播放器上报的绝对位置（毫秒）；跳变视为 seek。 */
    fun setPosition(ms: Long) {
        val p = ms.toDouble()
        // 按窗节拍：播放位置落在某窗内时，改用该窗的拍点（解决长曲节拍漂移）
        if (winStarts.isNotEmpty()) {
            val idx = winStarts.indexOfLast { p >= it }.coerceIn(0, winStarts.size - 1)
            if (idx != winIdx) {
                winIdx = idx
                val wb = winBeats.getOrNull(idx) ?: IntArray(0)
                val base = winStarts[idx]
                if (wb.isNotEmpty()) {
                    beats = IntArray(wb.size) { wb[it] + base }
                    beatIdx = 0
                }
                // 段落级情绪：驱动配色与动效强度
                SegmentMood.update(
                    winValence.getOrElse(idx) { -1f },
                    winArousal.getOrElse(idx) { -1f },
                )
                SegmentMood.updateTempo(winTempo, idx)
            }
        }
        if (beats.isEmpty()) {
            posMs = p
            return
        }
        if (kotlin.math.abs(p - posMs) > 1500.0) {
            posMs = p
            val idx = beats.indexOfFirst { it >= ms }
            beatIdx = if (idx < 0) beats.size else idx
        } else {
            posMs = p
        }
    }

    /** UI 每帧调用，dt 秒。 */
    fun advance(dt: Float) {
        if (!playing) {
            level *= 0.95f
            beat *= 0.88f
            return
        }
        posMs += dt * 1000.0

        // 能量包络（0..1）
        val target = if (env.isNotEmpty()) {
            val i = (posMs / envStep).toInt()
            if (i in env.indices) env[i] else 0.2f
        } else 0.2f
        level += (target - level) * 0.10f

        if (beats.isNotEmpty()) {
            while (beatIdx < beats.size && posMs >= beats[beatIdx]) {
                beat = 1f
                beatIdx++
                if (beatLogs < 5) {
                    beatLogs++
                    app.lumen.player.util.Logx.i("LumenPulse", "beat @${beats[beatIdx - 1]}ms level=${"%.2f".format(level)}")
                }
            }
            beat *= 0.86f
        } else if (tempoBpm > 0f) {
            // 无拍点数据时：按 tempo 相位呼吸
            val phase = ((posMs / 1000.0) * (tempoBpm / 60.0)) % 1.0
            if (phase < 0.08) beat = 1f else beat *= 0.86f
        } else {
            beat *= 0.90f
        }
    }
}
