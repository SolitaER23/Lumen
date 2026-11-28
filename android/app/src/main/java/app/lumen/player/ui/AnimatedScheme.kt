package app.lumen.player.ui

import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
    // 配色属于"效果"类动效：用 M3 effects spring，不要自造 420ms 曲线
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    LaunchedEffect(target, effectsSpec) {
        if (from.value != target) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = effectsSpec)
            from.value = target
        }
    }

    val p = progress.value.coerceIn(0f, 1f)
    if (p >= 1f || from.value == target) return target
    val step = (p * STEPS).toInt().coerceIn(0, STEPS)
    return interpolate(from.value, target, step)
}

/**
 * 只插值「会随封面大幅变化」的角色，其余取目标值。
 *
 * 表面层级要**整层**一起插值：只动 surface、不动 surfaceContainerLow/High 的话，
 * 卡片会在过渡中短暂"陷进"背景里，看起来像闪了一下。
 */
private fun interpolate(a: ColorScheme, b: ColorScheme, step: Int): ColorScheme {
    val key = a.hashCode().toString() + "|" + b.hashCode() + "|" + step
    lerpCache.get(key)?.let { return it }
    val t = step / STEPS.toFloat()
    fun m(x: Color, y: Color): Color = if (t >= 1f) y else lerp(x, y, t)
    val cs = b.copy(
        primary = m(a.primary, b.primary),
        primaryContainer = m(a.primaryContainer, b.primaryContainer),
        onPrimary = m(a.onPrimary, b.onPrimary),
        onPrimaryContainer = m(a.onPrimaryContainer, b.onPrimaryContainer),
        secondary = m(a.secondary, b.secondary),
        secondaryContainer = m(a.secondaryContainer, b.secondaryContainer),
        tertiary = m(a.tertiary, b.tertiary),
        tertiaryContainer = m(a.tertiaryContainer, b.tertiaryContainer),
        surface = m(a.surface, b.surface),
        surfaceDim = m(a.surfaceDim, b.surfaceDim),
        surfaceBright = m(a.surfaceBright, b.surfaceBright),
        surfaceContainerLowest = m(a.surfaceContainerLowest, b.surfaceContainerLowest),
        surfaceContainerLow = m(a.surfaceContainerLow, b.surfaceContainerLow),
        surfaceContainer = m(a.surfaceContainer, b.surfaceContainer),
        surfaceContainerHigh = m(a.surfaceContainerHigh, b.surfaceContainerHigh),
        surfaceContainerHighest = m(a.surfaceContainerHighest, b.surfaceContainerHighest),
        onSurface = m(a.onSurface, b.onSurface),
        onSurfaceVariant = m(a.onSurfaceVariant, b.onSurfaceVariant),
        outline = m(a.outline, b.outline),
    )
    lerpCache.put(key, cs)
    return cs
}
