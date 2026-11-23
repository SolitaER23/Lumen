package app.lumen.player.ui

import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * P0-4：只插值「关键角色」，其余角色直接切到目标值；
 * 进度量化成 24 档并对结果做 LRU 缓存，避免每帧新建整套配色。
 */
private const val STEPS = 24

private val lerpCache = object : LruCache<String, ColorScheme>(48) {}

@Composable
fun rememberAnimatedColorScheme(target: ColorScheme): ColorScheme {
    val from = remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (from.value != target) {
            from.value = from.value
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            )
            from.value = target
        }
    }

    val p = progress.value
    if (p >= 1f || from.value == target) return target
    val step = (p * STEPS).toInt().coerceIn(0, STEPS)
    return interpolate(from.value, target, step)
}

/** 只对 6 个关键角色做插值，其余取目标值。 */
private fun interpolate(a: ColorScheme, b: ColorScheme, step: Int): ColorScheme {
    val key = a.hashCode().toString() + "|" + b.hashCode() + "|" + step
    lerpCache.get(key)?.let { return it }
    val t = step / STEPS.toFloat()
    fun m(x: Color, y: Color): Color = if (t >= 1f) y else lerp(x, y, t)
    val cs = b.copy(
        primary = m(a.primary, b.primary),
        secondary = m(a.secondary, b.secondary),
        tertiary = m(a.tertiary, b.tertiary),
        surface = m(a.surface, b.surface),
        surfaceContainer = m(a.surfaceContainer, b.surfaceContainer),
        onSurface = m(a.onSurface, b.onSurface),
    )
    lerpCache.put(key, cs)
    return cs
}
