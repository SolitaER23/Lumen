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
 * - 叠加柔和高光呼吸，随曲目 tempo/节拍轻微闪动；
 * - 只做渐变绘制，保证滚动/转场流畅。
 */
@Composable
fun ExpressiveBackdrop(modifier: Modifier = Modifier, drivePulse: Boolean = true) {
    androidx.compose.runtime.LaunchedEffect(drivePulse) {
        if (!drivePulse) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                app.lumen.player.playback.MusicPulse.advance(dt)
            }
        }
    }
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "expressive-bg")

    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing)),
        label = "drift",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val sheen by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_500, easing = LinearEasing), RepeatMode.Reverse),
        label = "sheen",
    )

    val base = scheme.surface
    // 节拍相位（分析阶段预计算）只做很轻的调制
    val lvl = app.lumen.player.playback.MusicPulse.level.coerceIn(0f, 1f)
    val beat = app.lumen.player.playback.MusicPulse.beat.coerceIn(0f, 1f)
    val energy = (lvl * 0.55f + beat * 0.45f).coerceIn(0f, 1f)

    val c1 = scheme.primary
    val c2 = scheme.secondary
    val c3 = scheme.tertiary
    // 两个派生色：在 HCT 里把主/次色相轻轻推向第三色，增加层次但仍是同一套配色
    val c4 = Color(Blend.hctHue(scheme.primary.toArgb(), scheme.tertiary.toArgb(), 0.55))
    val c5 = Color(Blend.hctHue(scheme.secondary.toArgb(), scheme.primary.toArgb(), 0.50))

    Canvas(modifier.fillMaxSize()) {
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

        val r1 = w * 1.10f * breathe * (1f + 0.022f * energy)
        blob(
            cx = w * (0.26f + 0.22f * cos(drift).toFloat()),
            cy = h * (0.28f + 0.16f * sin(drift * 0.8f).toFloat()),
            radius = r1, color = c1, alpha = 0.26f + 0.045f * energy,
        )
        val r2 = w * 1.00f * (1.6f - breathe * 0.6f) * (1f + 0.028f * energy)
        blob(
            cx = w * (0.76f + 0.20f * cos(drift * 0.7f + 2.1f).toFloat()),
            cy = h * (0.56f + 0.18f * sin(drift * 0.9f + 1.2f).toFloat()),
            radius = r2, color = c2, alpha = 0.22f + 0.040f * energy,
        )
        val r3 = w * 0.92f * breathe * (1f + 0.020f * energy)
        blob(
            cx = w * (0.48f + 0.28f * sin(drift * 0.55f + 3.3f).toFloat()),
            cy = h * (0.84f + 0.14f * cos(drift * 0.75f + 0.6f).toFloat()),
            radius = r3, color = c3, alpha = 0.20f + 0.035f * energy,
        )
        val r4 = w * 0.80f * (1.2f - breathe * 0.2f) * (1f + 0.024f * energy)
        blob(
            cx = w * (0.82f + 0.16f * sin(drift * 1.1f + 1.7f).toFloat()),
            cy = h * (0.12f + 0.10f * cos(drift * 0.6f + 2.4f).toFloat()),
            radius = r4, color = c4, alpha = 0.18f + 0.030f * energy,
        )
        val r5 = w * 0.75f * (1.2f - breathe * 0.2f) * (1f + 0.022f * energy)
        blob(
            cx = w * (0.16f + 0.18f * cos(drift * 0.9f + 4.2f).toFloat()),
            cy = h * (0.70f + 0.16f * sin(drift * 0.65f + 3.1f).toFloat()),
            radius = r5, color = c5, alpha = 0.16f + 0.028f * energy,
        )

        // 高光呼吸
        blob(
            cx = w * (0.62f + 0.10f * cos(drift * 1.3f).toFloat()),
            cy = h * (0.22f + 0.06f * sin(drift * 1.1f).toFloat()),
            radius = w * 0.42f * (0.9f + 0.2f * sheen),
            color = Color.White,
            alpha = 0.045f * sheen + 0.030f * energy,
        )
    }
}
