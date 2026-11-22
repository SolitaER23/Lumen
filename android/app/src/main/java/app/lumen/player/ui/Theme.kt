package app.lumen.player.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.lumen.player.R

/** Google Sans Flex（OFL 1.1）：拉丁与数字用自定义字体，中文自动回退系统思源黑体。 */
internal val LumenFontFamily = FontFamily(
    Font(R.font.google_sans_flex_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_semibold, FontWeight.SemiBold),
)

/** 把默认 Typography 的每个层级都换成 Google Sans Flex。 */
internal fun lumenTypography(): Typography {
    val base = Typography()
    fun TextStyle.gsf() = copy(fontFamily = LumenFontFamily)
    return Typography(
        displayLarge = base.displayLarge.gsf(),
        displayMedium = base.displayMedium.gsf(),
        displaySmall = base.displaySmall.gsf(),
        headlineLarge = base.headlineLarge.gsf(),
        headlineMedium = base.headlineMedium.gsf(),
        headlineSmall = base.headlineSmall.gsf(),
        titleLarge = base.titleLarge.gsf(),
        titleMedium = base.titleMedium.gsf(),
        titleSmall = base.titleSmall.gsf(),
        bodyLarge = base.bodyLarge.gsf(),
        bodyMedium = base.bodyMedium.gsf(),
        bodySmall = base.bodySmall.gsf(),
        labelLarge = base.labelLarge.gsf(),
        labelMedium = base.labelMedium.gsf(),
        labelSmall = base.labelSmall.gsf(),
    )
}
