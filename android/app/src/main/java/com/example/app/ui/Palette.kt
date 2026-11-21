package com.example.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
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
suspend fun schemeFromCover(url: String, contrast: Float = 0f): ColorScheme? = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext null
    val key = "$url|$contrast"
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
    val seed = Score.score(quantized).firstOrNull() ?: return@withContext null
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
