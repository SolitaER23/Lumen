package app.lumen.player.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * 当前播放段落（采样窗）的情绪，用于段落级配色与动效强度。
 * 由 [MusicPulse] 在播放位置跨窗时更新。
 */
object SegmentMood {
    /** 当前窗的 arousal（0–1）；无窗级数据时为 -1 */
    var arousal by mutableFloatStateOf(-1f)
        internal set

    /** 当前窗的 valence（0–1）；无数据时为 -1 */
    var valence by mutableFloatStateOf(-1f)
        internal set

    val available: Boolean get() = arousal >= 0f

    /** 动效增益：0.80（安静）– 1.25（高唤醒）；无段落数据时为 1.0。 */
    /** 段落的色相偏移（度）：valence 高偏暖、低偏冷。 */
    var hueShift by mutableFloatStateOf(0f)
        internal set

    fun update(v: Float, a: Float) {
        valence = v
        arousal = a
        hueShift = if (v < 0f && a < 0f) 0f else ((v.coerceAtLeast(0f) - 0.5f) * 55f + (a.coerceAtLeast(0f) - 0.5f) * 25f)
    }

    /** 当前窗 BPM（0 表示未知） */
    var tempoBpm by mutableFloatStateOf(0f)
        internal set

    /** 节奏置信度：5 窗 BPM 越一致越高（0–1）。低置信时动效退回固定周期。 */
    var tempoConfidence by mutableFloatStateOf(0f)
        internal set

    fun updateTempo(tempos: FloatArray, idx: Int) {
        tempoBpm = tempos.getOrElse(idx) { 0f }
        val valid = tempos.filter { it > 0f }
        tempoConfidence = if (valid.size < 2) 0f else {
            val mean = valid.average().toFloat()
            val variance = valid.map { (it - mean) * (it - mean) }.average().toFloat()
            val cv = kotlin.math.sqrt(variance) / mean.coerceAtLeast(1f)
            (1f - (cv / 0.12f)).coerceIn(0f, 1f)
        }
    }

    fun gain(): Float = if (arousal < 0f) 1f else (0.80f + 0.45f * arousal)

}
