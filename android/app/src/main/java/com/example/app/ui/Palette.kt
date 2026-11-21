package com.example.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
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
    surface = Color(0xFF0E0F12),
    surfaceContainer = Color(0xFF1A1B1F),
    surfaceContainerHigh = Color(0xFF222327),
    onSurface = Color(0xFFE8E9ED),
    onSurfaceVariant = Color(0xFFA9ABB3),
    outlineVariant = Color(0xFF3A3B40),
)

private val http = OkHttpClient.Builder()
    .cache(okhttp3.Cache(java.io.File(System.getProperty("java.io.tmpdir"), "lumen_palette"), 8L * 1024 * 1024))
    .build()

/** 按封面 URL 缓存取色结果，避免同一张封面重复下载 + 解码。 */
private val schemeCache = object : android.util.LruCache<String, ColorScheme>(8) {}

/**
 * 从封面取主色，生成一套 M3 深色配色（对应旧版的 `Roles::from_tint`）。
 *
 * 取 `dominantSwatch` 的 HSL：主色提亮到可读的明度，容器色压暗，背景带一点色相。
 */
suspend fun schemeFromCover(url: String): ColorScheme? = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext null
    schemeCache.get(url)?.let { return@withContext it }
    val bmp: Bitmap = try {
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { r ->
            val bytes = r.body?.bytes() ?: return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        }
    } catch (e: Exception) {
        return@withContext null
    }
    val palette = Palette.from(bmp).generate()
    val swatch = palette.dominantSwatch
        ?: palette.vibrantSwatch
        ?: palette.mutedSwatch
        ?: return@withContext null
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(swatch.rgb, hsl)
    val h = hsl[0]
    val primary = Color.hsl(h, 0.62f, 0.58f)
    val primaryContainer = Color.hsl(h, 0.45f, 0.18f)
    val onPrimaryContainer = Color.hsl(h, 0.35f, 0.90f)
    val surface = Color.hsl(h, 0.10f, 0.055f)
    val surfaceContainer = Color.hsl(h, 0.10f, 0.095f)

    // 由封面主色再转出两个相邻但明显不同的色相，供「随机 / 下载」等按钮使用
    val h2 = (h + 40f) % 360f
    val h3 = (h + 320f) % 360f
    val scheme = darkColorScheme(
        primary = primary,
        secondary = Color.hsl(h2, 0.55f, 0.66f),
        onSecondary = Color.hsl(h2, 0.60f, 0.12f),
        secondaryContainer = Color.hsl(h2, 0.45f, 0.22f),
        onSecondaryContainer = Color.hsl(h2, 0.40f, 0.90f),
        tertiary = Color.hsl(h3, 0.60f, 0.62f),
        onTertiary = Color.hsl(h3, 0.60f, 0.12f),
        tertiaryContainer = Color.hsl(h3, 0.50f, 0.20f),
        onTertiaryContainer = Color.hsl(h3, 0.40f, 0.90f),
        onPrimary = Color.hsl(h, 0.6f, 0.10f),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        surface = surface,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = Color.hsl(h, 0.10f, 0.14f),
        onSurface = Color.hsl(h, 0.06f, 0.93f),
        onSurfaceVariant = Color.hsl(h, 0.06f, 0.68f),
        outlineVariant = Color.hsl(h, 0.08f, 0.24f),
    )
    schemeCache.put(url, scheme)
    scheme
}
