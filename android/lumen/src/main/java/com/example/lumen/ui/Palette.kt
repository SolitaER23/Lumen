package com.example.lumen.ui

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
    surface = Color(0xFF0E0F12),
    surfaceContainer = Color(0xFF1A1B1F),
    surfaceContainerHigh = Color(0xFF222327),
    onSurface = Color(0xFFE8E9ED),
    onSurfaceVariant = Color(0xFFA9ABB3),
    outlineVariant = Color(0xFF3A3B40),
)

private val http = OkHttpClient()

/**
 * 从封面取主色，生成一套 M3 深色配色（对应旧版的 `Roles::from_tint`）。
 *
 * 取 `dominantSwatch` 的 HSL：主色提亮到可读的明度，容器色压暗，背景带一点色相。
 */
suspend fun schemeFromCover(url: String): ColorScheme? = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext null
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
    val surfaceHigh = Color.hsl(h, 0.09f, 0.14f)
    FallbackScheme.copy(
        primary = primary,
        onPrimary = if (primary.luminance() > 0.5f) Color(0xFF140F10) else Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        surface = surface,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceHigh,
    )
}
