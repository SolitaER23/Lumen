package app.lumen.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val drift: androidx.compose.runtime.State<Float>,
    val breathe: androidx.compose.runtime.State<Float>,
    val sheen: androidx.compose.runtime.State<Float>,
)

@Composable
fun rememberBackdropMotion(): BackdropMotion = rememberBackdropMotionFor(11_000)

@Composable
private fun rememberBackdropMotionFor(periodMs: Int): BackdropMotion {
    androidx.compose.runtime.key(periodMs) {
        return rememberBackdropMotionInner(periodMs)
    }
}

@Composable
private fun rememberBackdropMotionInner(periodMs: Int): BackdropMotion {
    if (!AmbientFx.enabled) {
        // 关闭动效：返回恒定相位，Canvas 仍然只绘制一帧静态背景
        return BackdropMotion(
            androidx.compose.runtime.rememberUpdatedState(0f),
            androidx.compose.runtime.rememberUpdatedState(1f),
            androidx.compose.runtime.rememberUpdatedState(1f),
        )
    }
    val transition = rememberInfiniteTransition(label = "expressive-bg")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing)),
        label = "drift",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val sheen by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7_500, easing = LinearEasing), RepeatMode.Reverse),
        label = "sheen",
    )
    return BackdropMotion(
        androidx.compose.runtime.rememberUpdatedState(drift),
        androidx.compose.runtime.rememberUpdatedState(breathe),
        androidx.compose.runtime.rememberUpdatedState(sheen),
    )
}

@Composable
fun ExpressiveBackdrop(
    modifier: Modifier = Modifier,
    /** P0-1：不播放/不可见/省电时传 false，彻底停止无限动画（不再每帧重绘） */
    animate: Boolean = true,
    /** F3：由上层共享的相位；传了就与其它页面同一相位，切换时不会跳位 */
    sharedMotion: BackdropMotion? = null,
) {
    val scheme = MaterialTheme.colorScheme

    // 相位用 State 承载：只在绘制阶段读取，避免整树每帧重组
    val anims = sharedMotion ?: if (animate) rememberBackdropMotion() else null

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
        val drift = anims?.drift?.value ?: 0.6f
        val breatheRaw = anims?.breathe?.value ?: 1f
        val breathe = breatheRaw.coerceIn(0.70f, 1.32f)
        val sheen = anims?.sheen?.value ?: 0.8f
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
