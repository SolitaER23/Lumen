package app.lumen.player.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import hct.Hct

/** 用 HCT 色相旋转生成段落地色，保持明度与彩度不变。 */
object HctTint {
    fun rotate(color: Color, degrees: Float, weight: Float): Color {
        if (degrees == 0f) return color
        return runCatching {
            val c = Hct.fromInt(color.toArgb())
            val hue = ((c.hue + degrees) % 360f + 360f) % 360f
            val tinted = Color(Hct.from(hue, c.chroma, c.tone).toInt())
            lerp(color, tinted, weight.coerceIn(0f, 1f))
        }.getOrDefault(color)
    }

    private fun lerp(a: Color, b: Color, t: Float): Color = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )
}
