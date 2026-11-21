package com.example.app.ui

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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive 风格的动态背景：
 * - 3 团超大径向渐变缓慢漂移（主/次/第三色，来自封面/情绪染色后的 scheme）；
 * - 叠加一层柔和高光呼吸，随曲目 tempo 的拍点轻微闪烁；
 * - 只做渐变绘制，保证滚动/转场流畅。
 */
@Composable
fun ExpressiveBackdrop(modifier: Modifier = Modifier) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                com.example.app.playback.MusicPulse.advance(dt)
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
        targetValue = 1.10f,
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
    // 节拍相位（tempo 驱动）只做极轻的调制，保证柔和
    val lvl = com.example.app.playback.MusicPulse.level.coerceIn(0f, 1f)
    val beat = com.example.app.playback.MusicPulse.beat.coerceIn(0f, 1f)
    val energy = (lvl * 0.55f + beat * 0.45f).coerceIn(0f, 1f)
    val c1 = scheme.primary
    val c2 = scheme.secondary
    val c3 = scheme.tertiary
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

        val r1 = w * 1.05f * breathe * (1f + 0.020f * energy)
        blob(
            cx = w * (0.28f + 0.20f * cos(drift).toFloat()),
            cy = h * (0.30f + 0.14f * sin(drift * 0.8f).toFloat()),
            radius = r1, color = c1, alpha = 0.20f + 0.035f * energy,
        )
        val r2 = w * 0.95f * (1.6f - breathe * 0.6f) * (1f + 0.026f * energy)
        blob(
            cx = w * (0.74f + 0.18f * cos(drift * 0.7f + 2.1f).toFloat()),
            cy = h * (0.58f + 0.16f * sin(drift * 0.9f + 1.2f).toFloat()),
            radius = r2, color = c2, alpha = 0.15f + 0.030f * energy,
        )
        val r3 = w * 0.85f * breathe * (1f + 0.018f * energy)
        blob(
            cx = w * (0.50f + 0.26f * sin(drift * 0.55f + 3.3f).toFloat()),
            cy = h * (0.82f + 0.12f * cos(drift * 0.75f + 0.6f).toFloat()),
            radius = r3, color = c3, alpha = 0.13f + 0.025f * energy,
        )

        // 高光呼吸：用一团低透明度白斑模拟水面反光，避免出现横向色带接缝
        blob(
            cx = w * (0.62f + 0.10f * cos(drift * 1.3f).toFloat()),
            cy = h * (0.22f + 0.06f * sin(drift * 1.1f).toFloat()),
            radius = w * 0.42f * (0.9f + 0.2f * sheen),
            color = Color.White,
            alpha = 0.035f * sheen + 0.025f * energy,
        )
    }
}