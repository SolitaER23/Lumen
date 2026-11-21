package com.example.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import blend.Blend
import com.example.app.data.MoodResult
import dynamiccolor.DynamicColor
import hct.Hct
import dynamiccolor.MaterialDynamicColors
import quantize.QuantizerCelebi
import scheme.SchemeTonalSpot
import score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 默认主题（没有封面取色时）。 */
val FallbackScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE04A5F),
    onPrimary = Color(0xFF1C0A0D),
    primaryContainer = Color(0xFF5A1E27),
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = Color(0xFF6FD3C7),
    onSecondary = Color(0xFF06302C),
    secondaryContainer = Color(0xFF16413C),
    onSecondaryContainer = Color(0xFFB9F2EA),
    tertiary = Color(0xFFF2C36B),
    onTertiary = Color(0xFF3A2A05),
    tertiaryContainer = Color(0xFF4A3712),
    onTertiaryContainer = Color(0xFFFFE7B8),
    inversePrimary = Color(0xFF8FB6FF),
    surface = Color(0xFF202126),
    surfaceContainer = Color(0xFF26272D),
    surfaceContainerHigh = Color(0xFF2E2F36),
    onSurface = Color(0xFFE8E9ED),
    onSurfaceVariant = Color(0xFFA9ABB3),
    outlineVariant = Color(0xFF3A3B40),
)

private val http = OkHttpClient.Builder()
    .cache(okhttp3.Cache(java.io.File(System.getProperty("java.io.tmpdir"), "lumen_palette"), 8L * 1024 * 1024))
    .build()

/** 按「封面 URL + 对比度」缓存取色结果，避免同一张封面重复下载 + 量化。 */
private val schemeCache = object : android.util.LruCache<String, ColorScheme>(8) {}

private fun Int.asColor(): Color = Color(this)

/**
 * 用 Material Color Utilities 从封面生成一套 M3 深色配色。
 *
 * 流程：封面解码 → 缩到 96×96 → Celebi 量化 → Score 选种子色
 * → SchemeTonalSpot(seed, dark=true, contrastLevel) → 映射成 Compose ColorScheme。
 *
 * @param contrast 0f = 标准，0.5f = 中，1f = 高
 */
/** 情绪锚点色：按标签给 HCT 锚点，再按坐标做小幅修正。 */
private fun moodAnchorArgb(m: MoodResult): Int {
    val (hue, chroma, tone) = when (m.label) {
        "Happy" -> Triple(52.0, 70.0, 62.0)
        "Excited" -> Triple(28.0, 75.0, 64.0)
        "Tender" -> Triple(338.0, 45.0, 70.0)
        "Calm" -> Triple(168.0, 32.0, 66.0)
        "Sad" -> Triple(232.0, 30.0, 55.0)
        "Angry" -> Triple(8.0, 75.0, 55.0)
        else -> Triple(286.0, 45.0, 52.0) // Tense
    }
    val hueShift = (m.valence - 0.5f) * 18.0 - (m.arousal - 0.5f) * 10.0
    return Hct.from((hue + hueShift + 360.0) % 360.0, chroma, tone).toInt()
}

/**
 * 封面主色为主、情绪色只做「协调偏移」：
 * 1) Blend.harmonize(封面seed, 情绪色)：把封面色相柔和拉向情绪色（≤15°）；
 * 2) 再按情绪强度做部分应用（18%–60%），保证封面视觉身份不丢。
 */
fun moodTintedSeed(coverSeed: Int, mood: MoodResult): Int {
    val moodColor = moodAnchorArgb(mood)
    val strength = (
        (kotlin.math.abs(mood.valence - 0.5f) + kotlin.math.abs(mood.arousal - 0.5f)) * 2f
        ).coerceIn(0f, 1f)
    // harmonize 只做 ≤15° 的“协调”，幅度太弱；这里在 HCT 色相上按情绪强度做 15%–60% 的旋转，
    // chroma/tone 仍取封面 seed，视觉身份保留、情绪倾向可见。
    val cover = Hct.fromInt(coverSeed)
    val moodHct = Hct.fromInt(moodColor)
    val amount = (0.25f + 0.50f * strength).toDouble()
    val harmonized = Blend.harmonize(coverSeed, moodColor)
    val rotated = Hct.fromInt(Blend.hctHue(coverSeed, moodColor, amount))
    // 色度也往情绪色靠一点（15%–45%），明度仍保持封面 seed
    val chromaK = (0.15 + 0.30 * strength.toDouble()).coerceIn(0.0, 0.45)
    val chroma = cover.chroma * (1.0 - chromaK) + moodHct.chroma * chromaK
    val seed = Hct.from(rotated.hue, chroma, cover.tone).toInt()
    android.util.Log.i(
        "LumenMood",
        "tint coverHue=%.0f moodHue=%.0f harm=%.0f outHue=%.0f chroma %.1f→%.1f (k=%.2f) amount=%.2f".format(
            cover.hue,
            moodHct.hue,
            Hct.fromInt(harmonized).hue,
            rotated.hue,
            cover.chroma,
            chroma,
            chromaK,
            amount,
        ),
    )
    return seed
}

suspend fun schemeFromCover(
    url: String,
    contrast: Float = 0f,
    mood: MoodResult? = null,
): ColorScheme? = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext null
    val moodKey = mood?.let { "${it.label}|%.2f/%.2f".format(it.valence, it.arousal) } ?: "-"
    val key = "$url|$contrast|$moodKey"
    schemeCache.get(key)?.let { return@withContext it }

    val bitmap: Bitmap = try {
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { r ->
            val bytes = r.body?.bytes() ?: return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        }
    } catch (e: Exception) {
        return@withContext null
    }

    val small = if (bitmap.width > 96 || bitmap.height > 96) {
        Bitmap.createScaledBitmap(bitmap, 96, 96, true)
    } else bitmap
    val pixels = IntArray(small.width * small.height)
    small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)

    val quantized = QuantizerCelebi.quantize(pixels, 128)
    var seed = Score.score(quantized).firstOrNull() ?: return@withContext null
    if (mood != null) seed = moodTintedSeed(seed, mood)
    val scheme = SchemeTonalSpot(Hct.fromInt(seed), true, contrast.toDouble())
    // material 1.12 内置的 MCU 版本用 MaterialDynamicColors 计算各角色
    val mdc = MaterialDynamicColors()
    fun argb(role: DynamicColor): Color = role.getArgb(scheme).asColor()

    val cs = darkColorScheme(
        primary = argb(mdc.primary()),
        onPrimary = argb(mdc.onPrimary()),
        primaryContainer = argb(mdc.primaryContainer()),
        onPrimaryContainer = argb(mdc.onPrimaryContainer()),
        inversePrimary = argb(mdc.inversePrimary()),
        secondary = argb(mdc.secondary()),
        onSecondary = argb(mdc.onSecondary()),
        secondaryContainer = argb(mdc.secondaryContainer()),
        onSecondaryContainer = argb(mdc.onSecondaryContainer()),
        tertiary = argb(mdc.tertiary()),
        onTertiary = argb(mdc.onTertiary()),
        tertiaryContainer = argb(mdc.tertiaryContainer()),
        onTertiaryContainer = argb(mdc.onTertiaryContainer()),
        error = argb(mdc.error()),
        onError = argb(mdc.onError()),
        errorContainer = argb(mdc.errorContainer()),
        onErrorContainer = argb(mdc.onErrorContainer()),
        // 底色抬到 MCU 的 surfaceContainer（约 tone 12-14），不再用接近纯黑的 surface
        background = argb(mdc.surfaceContainer()),
        surface = argb(mdc.surfaceContainer()),
        onSurface = argb(mdc.onSurface()),
        surfaceVariant = argb(mdc.surfaceVariant()),
        onSurfaceVariant = argb(mdc.onSurfaceVariant()),
        surfaceContainerLowest = argb(mdc.surface()),
        surfaceContainerLow = argb(mdc.surfaceContainerLow()),
        surfaceContainer = argb(mdc.surfaceContainer()),
        surfaceContainerHigh = argb(mdc.surfaceContainerHigh()),
        surfaceContainerHighest = argb(mdc.surfaceContainerHighest()),
        outline = argb(mdc.outline()),
        outlineVariant = argb(mdc.outlineVariant()),
        inverseSurface = argb(mdc.inverseSurface()),
        inverseOnSurface = argb(mdc.inverseOnSurface()),
    )
    schemeCache.put(key, cs)
    cs
}
