package app.lumen.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import blend.Blend
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive 风格的动态背景：
 * - 5 团超大径向渐变缓慢漂移（主/次/第三色 + 两个派生色），颜色更丰富；
 * - 叠加柔和高光呼吸；
 * - 只做渐变绘制，保证滚动/转场流畅。
 */
class BackdropMotion(
    val drift: State<Float>,
    val breathe: State<Float>,
    val sheen: State<Float>,
)

/**
 * 背景相位时钟：自己逐帧推进，可随时停下。
 *
 * 之前用 rememberInfiniteTransition，动画一旦创建就一直跑，
 * 「暂停 / 省电 / 关闭氛围动效」都没法让它真正停下。改成自有时钟后：
 * 停下即不再请求下一帧；恢复时按累计时间接着走，画面不会跳。
 */
private class PhaseClock {
    private var elapsedMs = 0.0
    private var lastFrameNs = 0L

    var drift = 0f
        private set
    var breathe = BREATHE_MIN
        private set
    var sheen = SHEEN_MIN
        private set

    /** 暂停后恢复：丢掉停顿期间的时间差，从当前相位继续。 */
    fun resume() {
        lastFrameNs = 0L
    }

    fun advance(frameNs: Long, breathePeriodMs: Int) {
        if (lastFrameNs != 0L) {
            // 掉帧或系统卡顿时夹住步长，避免相位猛跳
            elapsedMs += ((frameNs - lastFrameNs) / 1_000_000.0).coerceIn(0.0, MAX_STEP_MS)
        }
        lastFrameNs = frameNs
        drift = ((elapsedMs % DRIFT_PERIOD_MS) / DRIFT_PERIOD_MS * TWO_PI).toFloat()
        breathe = triangle(elapsedMs, breathePeriodMs.toDouble(), BREATHE_MIN, BREATHE_MAX)
        sheen = triangle(elapsedMs, SHEEN_PERIOD_MS, SHEEN_MIN, SHEEN_MAX)
    }

    /** 三角波 0→1→0，等价于 RepeatMode.Reverse 的往返。 */
    private fun triangle(t: Double, periodMs: Double, from: Float, to: Float): Float {
        val half = ((t % (periodMs * 2)) / periodMs).toFloat()
        return if (half <= 1f) from + (to - from) * half else to - (to - from) * (half - 1f)
    }

    companion object {
        const val DRIFT_PERIOD_MS = 36_000.0
        const val SHEEN_PERIOD_MS = 7_500.0
        const val BREATHE_MIN = 0.82f
        const val BREATHE_MAX = 1.20f
        const val SHEEN_MIN = 0.45f
        const val SHEEN_MAX = 1f
        val TWO_PI = 2 * Math.PI
        private const val MAX_STEP_MS = 64.0
    }
}

/**
 * 背景相位。
 *
 * @param active 是否推进。false 时连帧都不再申请（暂停 / 省电 / 不可见），
 *   画面停在最后一帧的相位，恢复时从同一相位接着走。
 * @param periodMs 光团呼吸周期。
 */
@Composable
fun rememberBackdropMotion(active: Boolean = true, periodMs: Int = 11_000): BackdropMotion {
    val drift = remember { mutableFloatStateOf(0f) }
    val breathe = remember { mutableFloatStateOf(PhaseClock.BREATHE_MIN) }
    val sheen = remember { mutableFloatStateOf(PhaseClock.SHEEN_MIN) }
    val clock = remember { PhaseClock() }

    // 关闭氛围动效同样算「不推进」：这是「关闭后完全停止渲染」的落点
    val running = active && AmbientFx.enabled

    LaunchedEffect(running, periodMs) {
        if (!running) return@LaunchedEffect
        clock.resume()
        while (true) {
            withFrameNanos { frameNs ->
                clock.advance(frameNs, periodMs)
                drift.floatValue = clock.drift
                breathe.floatValue = clock.breathe
                sheen.floatValue = clock.sheen
            }
        }
    }

    return BackdropMotion(drift, breathe, sheen)
}

@Composable
fun ExpressiveBackdrop(
    modifier: Modifier = Modifier,
    /** F3：由上层共享的相位（主界面所有页面共用一个时钟）；不传则用自带的一份 */
    motion: BackdropMotion = rememberBackdropMotion(),
) {
    val scheme = MaterialTheme.colorScheme

    val base = scheme.surface
    // P2-4：光斑与底色亮度差不足时自动提亮，保证在极端配色下也看得见
    val c1 = androidx.compose.runtime.remember(scheme) { ensureLuminous(scheme.primary, base) }
    val c2 = androidx.compose.runtime.remember(scheme) { ensureLuminous(scheme.secondary, base) }
    val c3 = androidx.compose.runtime.remember(scheme) { ensureLuminous(scheme.tertiary, base) }
    val c4 = androidx.compose.runtime.remember(scheme) {
        ensureLuminous(Color(Blend.hctHue(scheme.primary.toArgb(), scheme.tertiary.toArgb(), 0.55)), base)
    }
    val c5 = androidx.compose.runtime.remember(scheme) {
        ensureLuminous(Color(Blend.hctHue(scheme.secondary.toArgb(), scheme.primary.toArgb(), 0.50)), base)
    }

    Canvas(modifier.fillMaxSize()) {
        val drift = motion.drift.value
        val breatheRaw = motion.breathe.value
        val breathe = breatheRaw.coerceIn(0.70f, 1.32f)
        val sheen = motion.sheen.value
        val w = size.width
        val h = size.height
        drawRect(base)

        fun blob(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }

        val r1 = w * 1.10f * breathe
        blob(
            cx = w * (0.26f + 0.22f * cos(drift).toFloat()),
            cy = h * (0.28f + 0.16f * sin(drift * 0.8f).toFloat()),
            radius = r1, color = c1, alpha = 0.26f,
        )
        val r2 = w * 1.00f * (1.6f - breathe * 0.6f)
        blob(
            cx = w * (0.76f + 0.20f * cos(drift * 0.7f + 2.1f).toFloat()),
            cy = h * (0.56f + 0.18f * sin(drift * 0.9f + 1.2f).toFloat()),
            radius = r2, color = c2, alpha = 0.22f,
        )
        val r3 = w * 0.92f * breathe
        blob(
            cx = w * (0.48f + 0.28f * sin(drift * 0.55f + 3.3f).toFloat()),
            cy = h * (0.84f + 0.14f * cos(drift * 0.75f + 0.6f).toFloat()),
            radius = r3, color = c3, alpha = 0.20f,
        )
        val r4 = w * 0.80f * (1.2f - breathe * 0.2f)
        blob(
            cx = w * (0.82f + 0.16f * sin(drift * 1.1f + 1.7f).toFloat()),
            cy = h * (0.12f + 0.10f * cos(drift * 0.6f + 2.4f).toFloat()),
            radius = r4, color = c4, alpha = 0.18f,
        )
        val r5 = w * 0.75f * (1.2f - breathe * 0.2f)
        blob(
            cx = w * (0.16f + 0.18f * cos(drift * 0.9f + 4.2f).toFloat()),
            cy = h * (0.70f + 0.16f * sin(drift * 0.65f + 3.1f).toFloat()),
            radius = r5, color = c5, alpha = 0.16f,
        )

        // 高光呼吸
        blob(
            cx = w * (0.62f + 0.10f * cos(drift * 1.3f).toFloat()),
            cy = h * (0.22f + 0.06f * sin(drift * 1.1f).toFloat()),
            radius = w * 0.42f * (0.9f + 0.2f * sheen),
            color = Color.White,
            alpha = 0.045f * sheen,
        )
    }
}
