package app.lumen.player.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

/**
 * Material 3 Expressive 字体：
 * 把默认 Typography 的**每一档（含 Emphasized 强调档）**都换成 Google Sans Flex。
 *
 * Emphasized 档是 M3 Expressive 的排版语言核心：同一字号用更重的字重表达"主要层级"，
 * 用于大标题、正在播放的曲名、分区标题；普通档用于正文与辅助信息。
 */
internal fun lumenTypography(): Typography {
    val base = Typography()
    fun TextStyle.gsf() = copy(fontFamily = LumenFontFamily)
    return base.copy(
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
        displayLargeEmphasized = base.displayLargeEmphasized.gsf(),
        displayMediumEmphasized = base.displayMediumEmphasized.gsf(),
        displaySmallEmphasized = base.displaySmallEmphasized.gsf(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.gsf(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.gsf(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.gsf(),
        titleLargeEmphasized = base.titleLargeEmphasized.gsf(),
        titleMediumEmphasized = base.titleMediumEmphasized.gsf(),
        titleSmallEmphasized = base.titleSmallEmphasized.gsf(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.gsf(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.gsf(),
        bodySmallEmphasized = base.bodySmallEmphasized.gsf(),
        labelLargeEmphasized = base.labelLargeEmphasized.gsf(),
        labelMediumEmphasized = base.labelMediumEmphasized.gsf(),
        labelSmallEmphasized = base.labelSmallEmphasized.gsf(),
    )
}

/**
 * 全局主题：Material 3 Expressive。
 *
 * - 配色：封面取色的 MCU 12 角色方案（含 surfaceContainer 全套容器色）；
 * - 动效：`MotionScheme.expressive()` —— 空间动效（位置/尺寸）用 spring，
 *   效果动效（颜色/透明度）用短促的 emphasized 曲线；
 * - 字体：Google Sans Flex 的完整 Expressive 字阶；
 * - 形状：沿用 M3 1.5 的官方形状刻度（XS 4 / S 8 / M 12 / L 16 / XL 28 / Full），
 *   组件一律从 `MaterialTheme.shapes` / `ShapeDefaults` 取值，不写临时圆角。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LumenTheme(
    colorScheme: ColorScheme,
    /** 应用级触感总开关：在这里下发 CompositionLocal，所有页面共用同一份状态。 */
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalHapticsEnabled provides hapticsEnabled) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = lumenTypography(),
            content = content,
        )
    }
}
