package app.lumen.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import app.lumen.player.data.AudioEvent
import app.lumen.player.data.EventGlyph
import app.lumen.player.data.EventRole
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
class BackdropMotion(
    val drift: androidx.compose.runtime.State<Float>,
    val breathe: androidx.compose.runtime.State<Float>,
    val sheen: androidx.compose.runtime.State<Float>,
)

@Composable
fun rememberBackdropMotion(): BackdropMotion {
    // 呼吸周期跟随当前窗 BPM：每两拍一次；置信度低或未知时退回固定 11 s
    val bpm = app.lumen.player.playback.SegmentMood.tempoBpm
    val conf = app.lumen.player.playback.SegmentMood.tempoConfidence
    val periodMs = if (bpm > 0f && conf > 0.5f) ((60_000f / bpm) * 2f).toInt().coerceIn(900, 4_000) else 11_000
    return rememberBackdropMotionFor(periodMs)
}

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
    // 呼吸：幅度加大（0.82–1.20）、周期放慢到 11s，观感更明显但仍平缓
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
    drivePulse: Boolean = true,
    event: AudioEvent? = null,
    /** P0-1：不播放/不可见/省电时传 false，彻底停止无限动画（不再每帧重绘） */
    animate: Boolean = true,
    /** F3：由上层共享的相位；传了就与其它页面同一相位，切换时不会跳位 */
    sharedMotion: BackdropMotion? = null,
) {
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
    // 段落级情绪：高唤醒段落呼吸更明显，安静段落更收敛
    val segArousal = app.lumen.player.playback.SegmentMood.arousal
    val segGain = if (segArousal >= 0f) 0.80f + 0.45f * segArousal else 1f
    // 段落配色：按当前窗的 valence 做色相偏移，长曲副歌会「变色」
    val segHue by androidx.compose.animation.core.animateFloatAsState(
        targetValue = app.lumen.player.playback.SegmentMood.hueShift,
        animationSpec = androidx.compose.animation.core.tween(2600),
        label = "seg-hue",
    )
    val scheme = MaterialTheme.colorScheme
    val roleA = event?.role ?: EventRole.PRIMARY
    val roleB = when (roleA) {
        EventRole.PRIMARY -> EventRole.SECONDARY
        EventRole.SECONDARY -> EventRole.TERTIARY
        EventRole.TERTIARY -> EventRole.PRIMARY
    }
    fun roleColor(role: EventRole): Color = when (role) {
        EventRole.PRIMARY -> scheme.primary
        EventRole.SECONDARY -> scheme.secondary
        EventRole.TERTIARY -> scheme.tertiary
    }
    val eventColorA by animateColorAsState(roleColor(roleA), tween(900), label = "event-a")
    val eventColorB by animateColorAsState(roleColor(roleB), tween(900), label = "event-b")
    val eventAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (event == null) 0f else 0.16f + 0.16f * event.intensity,
        tween(900),
        label = "event-alpha",
    )
    // 不同声音类型把光晕放到不同区域，并改变半径；位置也做插值，切换自然
    val targetX = when (event?.glyph) {
        app.lumen.player.data.EventGlyph.RINGS -> 0.26f
        app.lumen.player.data.EventGlyph.BARS -> 0.76f
        app.lumen.player.data.EventGlyph.ARCS -> 0.50f
        app.lumen.player.data.EventGlyph.WAVE -> 0.28f
        app.lumen.player.data.EventGlyph.DOTS -> 0.78f
        null -> 0.55f
    }
    val targetY = when (event?.glyph) {
        app.lumen.player.data.EventGlyph.RINGS -> 0.34f
        app.lumen.player.data.EventGlyph.BARS -> 0.66f
        app.lumen.player.data.EventGlyph.ARCS -> 0.42f
        app.lumen.player.data.EventGlyph.WAVE -> 0.70f
        app.lumen.player.data.EventGlyph.DOTS -> 0.28f
        null -> 0.44f
    }
    val eventX by androidx.compose.animation.core.animateFloatAsState(targetX, tween(900), label = "event-x")
    val eventY by androidx.compose.animation.core.animateFloatAsState(targetY, tween(900), label = "event-y")
    val eventRadius by androidx.compose.animation.core.animateFloatAsState(
        if (event == null) 0.72f else 0.60f + 0.22f * event.intensity,
        tween(900),
        label = "event-radius",
    )

    // 相位用 State 承载：只在绘制阶段读取，避免整树每帧重组
    val anims = sharedMotion ?: if (animate) rememberBackdropMotion() else null

    val base = scheme.surface
    // 节拍相位（分析阶段预计算）只做很轻的调制
    val lvl = app.lumen.player.playback.MusicPulse.level.coerceIn(0f, 1f)
    val beat = app.lumen.player.playback.MusicPulse.beat.coerceIn(0f, 1f)
    val energy = (lvl * 0.55f + beat * 0.45f).coerceIn(0f, 1f)

    // P2-4：光斑与底色亮度差不足时自动提亮，保证在极端配色下也看得见
    val c1 = remember(scheme) { ensureLuminous(scheme.primary, base) }
    val c2 = remember(scheme) { ensureLuminous(scheme.secondary, base) }
    val c3 = remember(scheme) { ensureLuminous(scheme.tertiary, base) }
    val c4 = remember(scheme) {
        ensureLuminous(Color(Blend.hctHue(scheme.primary.toArgb(), scheme.tertiary.toArgb(), 0.55)), base)
    }
    val c5 = remember(scheme) {
        ensureLuminous(Color(Blend.hctHue(scheme.secondary.toArgb(), scheme.primary.toArgb(), 0.50)), base)
    }

    // 段落地色：以主色为基准旋转色相（HCT），权重 0.55 保证仍与主题协调
    val segTint = remember(segHue, scheme) {
        runCatching {
            val base = app.lumen.player.util.HctTint.rotate(scheme.primary, segHue, 0.55f)
            base
        }.getOrDefault(scheme.primary)
    }
    Canvas(modifier.fillMaxSize()) {
        val drift = anims?.drift?.value ?: 0.6f
        // 段落调制：高唤醒段落呼吸更明显，安静段落更收敛（segGain 由 SegmentMood 计算）
        val breatheRaw = anims?.breathe?.value ?: 1f
        val breathe = (1f + (breatheRaw - 1f) * app.lumen.player.playback.SegmentMood.gain()).coerceIn(0.70f, 1.32f)
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

        val r1 = w * 1.10f * breathe * (1f + 0.022f * energy)
        blob(
            cx = w * (0.26f + 0.22f * cos(drift).toFloat()),
            cy = h * (0.28f + 0.16f * sin(drift * 0.8f).toFloat()),
            // 段落地色：副歌等段落的色相偏移在这里体现
            radius = r1, color = segTint, alpha = 0.26f + 0.045f * energy,
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

        // 当前声音类型：只叠加一层同色系光晕 + 轻量图形，颜色来自现有 scheme token
        if (eventAlpha > 0.001f) {
            val ex = w * (eventX + 0.02f * cos(drift * 0.8f).toFloat())
            val ey = h * (eventY + 0.02f * sin(drift * 0.9f).toFloat())
            blob(ex, ey, w * eventRadius, eventColorA, eventAlpha)

            // AMBIENT_PARTICLES：随心情/事件变化的轻量装饰粒子（18 个，纯数学摆动，无额外动画状态）
            if (AmbientFx.enabled && animate) {
                val particleCount = 18
                val phase = drift
                val breatheNow = breathe
                for (i in 0 until particleCount) {
                    val t = (i / particleCount.toFloat() + phase / (2f * Math.PI.toFloat())) % 1f
                    val sway = kotlin.math.sin((t * 6.283f) + i * 1.7f)
                    val px = w * (0.10f + 0.80f * ((i * 37 % 100) / 100f)) + sway * w * 0.035f
                    val py = h * (1.05f - t * 1.10f)
                    val radius = (2.2f + 2.0f * ((i * 13 % 7) / 7f)) * breatheNow
                    val alphaBase = if (event != null) 0.10f + 0.14f * event.intensity else 0.08f
                    val a = (alphaBase * (0.55f + 0.45f * kotlin.math.sin(t * 6.283f + i))).coerceIn(0f, 0.32f)
                    val c = if (i % 3 == 0) eventColorA else if (i % 3 == 1) eventColorB else scheme.primary
                    drawCircle(color = c.copy(alpha = a), radius = radius, center = androidx.compose.ui.geometry.Offset(px, py))
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEventGlyph(
    glyph: EventGlyph,
    color: Color,
    alpha: Float,
    cx: Float,
    cy: Float,
    width: Float,
) {
    if (alpha <= 0.001f) return
    val c = color.copy(alpha = alpha.coerceIn(0f, 1f))
    val unit = width * 0.20f
    val stroke = 2.dp.toPx()
    when (glyph) {
        EventGlyph.RINGS -> {
            repeat(3) { i ->
                drawCircle(
                    c,
                    radius = unit * (1.6f - i * 0.35f),
                    center = Offset(cx, cy),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }

        EventGlyph.BARS -> {
            repeat(7) { i ->
                val x = cx + (i - 3) * unit * 0.55f
                val h = unit * (0.7f + (i % 3) * 0.55f)
                drawLine(c, Offset(x, cy - h), Offset(x, cy + h), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }

        EventGlyph.ARCS -> {
            repeat(3) { i ->
                drawArc(
                    color = c,
                    startAngle = -60f + i * 18f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(cx - unit * (2f - i * 0.3f), cy - unit * (2f - i * 0.3f)),
                    size = androidx.compose.ui.geometry.Size(unit * (4f - i * 0.6f), unit * (4f - i * 0.6f)),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }

        EventGlyph.DOTS -> {
            val offsets = listOf(-1.2f to -0.7f, 0.2f to -1.1f, 1.3f to -0.2f, -0.6f to 0.9f, 0.8f to 1.0f)
            offsets.forEach { (dx, dy) ->
                drawCircle(c, radius = unit * 0.22f, center = Offset(cx + dx * unit, cy + dy * unit))
            }
        }

        EventGlyph.WAVE -> {
            val path = Path()
            for (i in 0..32) {
                val t = i / 32f
                val x = cx - unit * 2.4f + t * unit * 4.8f
                val y = cy + sin(t * 6.28f + 0.6f) * unit * 0.55f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, c, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}
