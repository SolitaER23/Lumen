package app.lumen.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin

/**
 * 背景相位（全局唯一）：缓慢漂移 + 呼吸。
 *
 * 关键点：**整个应用只有这一层背景**。页面自己不再画背景，所以页面切换时
 * 背景不会重新淡入、不会出现"两层不同亮度的底"这种视觉切割。
 */
class BackdropMotion(
    val drift: State<Float>,
    val breathe: State<Float>,
)

private class PhaseClock {
    private var elapsedMs = 0.0
    private var lastFrameNs = 0L

    var drift = 0f
        private set
    var breathe = BREATHE_MIN
        private set

    fun resume() {
        lastFrameNs = 0L
    }

    fun advance(frameNs: Long) {
        if (lastFrameNs != 0L) {
            elapsedMs += ((frameNs - lastFrameNs) / 1_000_000.0).coerceIn(0.0, MAX_STEP_MS)
        }
        lastFrameNs = frameNs
        drift = ((elapsedMs % DRIFT_PERIOD_MS) / DRIFT_PERIOD_MS * TWO_PI).toFloat()
        breathe = triangle(elapsedMs, BREATHE_PERIOD_MS, BREATHE_MIN, BREATHE_MAX)
    }

    private fun triangle(t: Double, periodMs: Double, from: Float, to: Float): Float {
        val half = ((t % (periodMs * 2)) / periodMs).toFloat()
        return if (half <= 1f) from + (to - from) * half else to - (to - from) * (half - 1f)
    }

    companion object {
        const val DRIFT_PERIOD_MS = 42_000.0
        const val BREATHE_PERIOD_MS = 13_000.0
        const val BREATHE_MIN = 0.85f
        const val BREATHE_MAX = 1.15f
        const val TWO_PI = 2 * Math.PI
        private const val MAX_STEP_MS = 64.0
    }
}

/**
 * 背景相位。
 *
 * @param active 是否推进。false 时连帧都不再申请（暂停 / 省电 / 关闭氛围动效），
 *   画面停在最后一帧，恢复时接着走，不会跳。
 */
@Composable
fun rememberBackdropMotion(active: Boolean = true): BackdropMotion {
    val drift = remember { mutableFloatStateOf(0f) }
    val breathe = remember { mutableFloatStateOf(PhaseClock.BREATHE_MIN) }
    val clock = remember { PhaseClock() }
    val running = active && AmbientFx.enabled

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        clock.resume()
        while (true) {
            withFrameNanos { frameNs ->
                clock.advance(frameNs)
                drift.floatValue = clock.drift
                breathe.floatValue = clock.breathe
            }
        }
    }
    return BackdropMotion(drift, breathe)
}

/**
 * M3 Expressive 背景：以 `surface` 为底，叠一层封面主色的柔和环境光。
 *
 * 比旧版更克制（旧版 5 团光斑 + 高光呼吸，过曝且和内容抢注意力）：
 * 1 条竖向渐变（顶部来自封面色、向下淡出到 surface）+ 2 团极淡的径向光。
 * 文字对比度仍由 surface 保证，光斑只做"氛围"。
 *
 * 性能：这是一层**每一帧都在动**的全屏绘制，所以
 * - 用 [drawWithCache] 而不是 Canvas：尺寸/配色不变时，渐变与单位空间径向 Brush 只建一次；
 * - 两团光的中心/半径逐帧变化，用「单位空间 Brush + canvas 变换」表达，
 *   与原来的 `Brush.radialGradient(center=…, radius=…)` 视觉一致，但每帧零分配；
 * - 相位只在绘制阶段读取（onDrawBehind 里），不会引起任何重组。
 */
@Composable
fun ExpressiveBackdrop(
    modifier: Modifier = Modifier,
    motion: BackdropMotion = rememberBackdropMotion(),
) {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surface
    val tint = scheme.primary
    val tint2 = scheme.tertiary

    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val topGradient = Brush.verticalGradient(
                    0f to tint.copy(alpha = 0.20f),
                    0.34f to tint.copy(alpha = 0.06f),
                    0.62f to Color.Transparent,
                    1f to Color.Transparent,
                )
                // 单位空间（半径 = 1、圆心 = 原点）的径向渐变；绘制时再按目标半径缩放画布
                val unitTint = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.10f), tint.copy(alpha = 0f)),
                    center = Offset.Zero,
                    radius = 1f,
                )
                val unitTint2 = Brush.radialGradient(
                    colors = listOf(tint2.copy(alpha = 0.07f), tint2.copy(alpha = 0f)),
                    center = Offset.Zero,
                    radius = 1f,
                )
                onDrawBehind {
                    val w = size.width
                    val h = size.height
                    if (w <= 0f || h <= 0f) return@onDrawBehind
                    drawRect(base)
                    drawRect(topGradient)

                    val drift = motion.drift.value
                    val breathe = motion.breathe.value

                    fun blob(cx: Float, cy: Float, radius: Float, brush: Brush) {
                        if (radius <= 0f) return
                        withTransform({
                            translate(cx, cy)
                            scale(radius, radius, pivot = Offset.Zero)
                        }) {
                            drawCircle(brush = brush, radius = 1f, center = Offset.Zero)
                        }
                    }

                    blob(
                        cx = w * (0.16f + 0.06f * cos(drift).toFloat()),
                        cy = h * (0.10f + 0.03f * sin(drift).toFloat()),
                        radius = w * 1.15f * breathe,
                        brush = unitTint,
                    )
                    blob(
                        cx = w * (0.92f + 0.05f * sin(drift * 0.8f).toFloat()),
                        cy = h * (0.86f + 0.04f * cos(drift * 0.7f).toFloat()),
                        radius = w * 1.05f * (1.6f - breathe * 0.6f),
                        brush = unitTint2,
                    )
                }
            },
    )
}
