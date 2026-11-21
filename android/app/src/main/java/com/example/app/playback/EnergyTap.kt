package com.example.app.playback

/**
 * 节拍相位：由播放状态 + 曲目 tempo 推导，用于背景的轻微“闪烁/呼吸”。
 *
 * 不做音频侵入（不改播放链路），相位在 [ExpressiveBackdrop] 的帧循环里推进：
 * 每拍走 0..1，脉冲在拍点达到峰值后平滑衰减，避免突兀闪烁。
 */
object MusicPulse {
    @Volatile var playing: Boolean = false
    @Volatile var tempoBpm: Float = 0f
    @Volatile var phase: Float = 0f     // 0..1，一拍内相位
    @Volatile var level: Float = 0f     // 0..1，随相位平滑起伏的“强弱”
    @Volatile var beat: Float = 0f      // 0..1，拍点冲击（柔和衰减）

    private var trackId: String? = null
    private var lastNanos = 0L

    fun setTrack(id: String?, playingNow: Boolean) {
        if (id != trackId) {
            trackId = id
            phase = 0f
            beat = 0f
        }
        playing = playingNow
    }

    fun setTempo(bpm: Float) {
        tempoBpm = bpm.coerceIn(50f, 180f)
    }

    /** 由 UI 每帧调用；dt 秒。 */
    fun advance(dt: Float) {
        if (!playing || tempoBpm <= 0f) {
            // 没有节拍信息时：极慢呼吸，不会死气沉沉
            phase = (phase + dt * 0.12f) % 1f
            level += (0.18f - level) * 0.02f
            beat *= 0.90f
            return
        }
        val beatsPerSecond = tempoBpm / 60f
        phase += dt * beatsPerSecond
        if (phase >= 1f) {
            phase -= 1f
            beat = 1f  // 拍点：只给一个柔和冲量
        }
        // 拍内包络：拍点附近亮一点，随后平滑回落
        val env = (0.35f + 0.65f * (1f - phase)).coerceIn(0f, 1f)
        level += (env - level) * 0.15f
        beat *= 0.82f
    }
}