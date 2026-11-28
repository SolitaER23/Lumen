package app.lumen.player.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import hct.Hct
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3 主题一致性（Google 官方规范）回归测试。
 *
 * 三件事一旦被打回原形，端上就会以"某个角落莫名发紫 / 深色主题看不清"的形式复现：
 * 1. 49 个色彩角色必须全部由 MCU 生成，不能有几个偷偷回落到 M3 基线配色；
 * 2. 前景/背景配对必须满足 WCAG（正文 4.5:1，图形与描边 3:1）；
 * 3. surface 层级必须单调（深色越叠越亮、浅色越叠越暗），AMOLED 只压底色不抹层级。
 */
class ThemeConformanceTest {

    private val seeds = listOf(
        0xFFE04A5F.toInt(), // 品牌红
        0xFF00695C.toInt(), // 青绿
        0xFF4F6BFF.toInt(), // 蓝
        0xFF8D6E63.toInt(), // 棕（低色度）
        0xFF9E9E9E.toInt(), // 灰白封面
    )

    private fun modeNames(isDark: Boolean) = if (isDark) "dark" else "light"

    private fun ratio(a: Color, b: Color): Double {
        val l1 = maxOf(a.luminance(), b.luminance()).toDouble()
        val l2 = minOf(a.luminance(), b.luminance()).toDouble()
        return (l1 + 0.05) / (l2 + 0.05)
    }

    private fun tone(c: Color): Double = Hct.fromInt(c.toArgb()).tone

    private fun roleMap(cs: ColorScheme): List<Pair<String, Color>> = listOf(
        "primary" to cs.primary,
        "onPrimary" to cs.onPrimary,
        "primaryContainer" to cs.primaryContainer,
        "onPrimaryContainer" to cs.onPrimaryContainer,
        "inversePrimary" to cs.inversePrimary,
        "primaryFixed" to cs.primaryFixed,
        "primaryFixedDim" to cs.primaryFixedDim,
        "onPrimaryFixed" to cs.onPrimaryFixed,
        "onPrimaryFixedVariant" to cs.onPrimaryFixedVariant,
        "secondary" to cs.secondary,
        "onSecondary" to cs.onSecondary,
        "secondaryContainer" to cs.secondaryContainer,
        "onSecondaryContainer" to cs.onSecondaryContainer,
        "secondaryFixed" to cs.secondaryFixed,
        "secondaryFixedDim" to cs.secondaryFixedDim,
        "onSecondaryFixed" to cs.onSecondaryFixed,
        "onSecondaryFixedVariant" to cs.onSecondaryFixedVariant,
        "tertiary" to cs.tertiary,
        "onTertiary" to cs.onTertiary,
        "tertiaryContainer" to cs.tertiaryContainer,
        "onTertiaryContainer" to cs.onTertiaryContainer,
        "tertiaryFixed" to cs.tertiaryFixed,
        "tertiaryFixedDim" to cs.tertiaryFixedDim,
        "onTertiaryFixed" to cs.onTertiaryFixed,
        "onTertiaryFixedVariant" to cs.onTertiaryFixedVariant,
        "background" to cs.background,
        "onBackground" to cs.onBackground,
        "surface" to cs.surface,
        "onSurface" to cs.onSurface,
        "surfaceVariant" to cs.surfaceVariant,
        "onSurfaceVariant" to cs.onSurfaceVariant,
        "surfaceTint" to cs.surfaceTint,
        "inverseSurface" to cs.inverseSurface,
        "inverseOnSurface" to cs.inverseOnSurface,
        "outline" to cs.outline,
        "outlineVariant" to cs.outlineVariant,
        "surfaceBright" to cs.surfaceBright,
        "surfaceDim" to cs.surfaceDim,
        "surfaceContainer" to cs.surfaceContainer,
        "surfaceContainerHigh" to cs.surfaceContainerHigh,
        "surfaceContainerHighest" to cs.surfaceContainerHighest,
        "surfaceContainerLow" to cs.surfaceContainerLow,
        "surfaceContainerLowest" to cs.surfaceContainerLowest,
    )

    /** 1) 每一个角色都必须来自 MCU；回落到基线就等于漏映射。error 族与 scrim 与种子无关，单独排除。 */
    @Test
    fun everyRoleComesFromMcU() {
        val failures = mutableListOf<String>()
        for (seed in seeds) {
            for (isDark in listOf(false, true)) {
                for (style in PaletteStyle.entries) {
                    val cs = buildScheme(listOf(seed), ThemeOptions(contrast = 0f, isDark = isDark, style = style))
                    val baseline = if (isDark) darkColorScheme() else lightColorScheme()
                    val baseMap = roleMap(baseline).toMap()
                    for ((name, color) in roleMap(cs)) {
                        // 纯白与种子无关（浅色的 surfaceContainerLowest 就是纯白），不算漏映射
                        if (color.toArgb() == 0xFFFFFFFF.toInt()) continue
                        if (color == baseMap[name]) {
                            failures += "seed=%08X %s %s 角色 %s 回落到 M3 基线配色".format(seed, modeNames(isDark), style.key, name)
                        }
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** 2) WCAG：正文 4.5:1、图形/描边 3:1，三档对比度都要过。 */
    @Test
    fun rolePairsMeetWcag() {
        val failures = mutableListOf<String>()
        fun check(tag: String, fg: Color, bg: Color, min: Double) {
            val r = ratio(fg, bg)
            if (r < min) failures += "%s 对比度 %.2f < %.1f".format(tag, r, min)
        }
        for (seed in seeds) {
            for (isDark in listOf(false, true)) {
                for (level in ContrastLevel.entries) {
                    for (style in PaletteStyle.entries) {
                        val tag = "seed=%08X %s %s %s".format(seed, modeNames(isDark), style.key, level.name)
                        val cs = buildScheme(listOf(seed), ThemeOptions(contrast = level.level, isDark = isDark, style = style))
                        // 渐变会把辅色/第三色换成另外两枚种子的主色族，配对关系必须仍然成立
                        val grad = buildScheme(
                            listOf(seed, seeds[2], seeds[3]),
                            ThemeOptions(contrast = level.level, isDark = isDark, style = style, gradient = true),
                        )
                        check("$tag(gradient) onSecondary/secondary", grad.onSecondary, grad.secondary, 4.5)
                        check("$tag(gradient) onSecondaryContainer/secondaryContainer", grad.onSecondaryContainer, grad.secondaryContainer, 4.5)
                        check("$tag(gradient) onTertiary/tertiary", grad.onTertiary, grad.tertiary, 4.5)
                        check("$tag(gradient) onTertiaryContainer/tertiaryContainer", grad.onTertiaryContainer, grad.tertiaryContainer, 4.5)
                        check("$tag(gradient) onTertiaryFixed/tertiaryFixed", grad.onTertiaryFixed, grad.tertiaryFixed, 4.5)
                        check("$tag(gradient) onSecondaryFixedVariant/secondaryFixed", grad.onSecondaryFixedVariant, grad.secondaryFixed, 4.5)
                        check("$tag onPrimary/primary", cs.onPrimary, cs.primary, 4.5)
                        check("$tag onSecondary/secondary", cs.onSecondary, cs.secondary, 4.5)
                        check("$tag onTertiary/tertiary", cs.onTertiary, cs.tertiary, 4.5)
                        check("$tag onPrimaryContainer/primaryContainer", cs.onPrimaryContainer, cs.primaryContainer, 4.5)
                        check("$tag onSecondaryContainer/secondaryContainer", cs.onSecondaryContainer, cs.secondaryContainer, 4.5)
                        check("$tag onTertiaryContainer/tertiaryContainer", cs.onTertiaryContainer, cs.tertiaryContainer, 4.5)
                        check("$tag onSurface/surface", cs.onSurface, cs.surface, 4.5)
                        check("$tag onSurfaceVariant/surface", cs.onSurfaceVariant, cs.surface, 4.5)
                        check("$tag onSurfaceContainer/surfaceContainer", cs.onSurface, cs.surfaceContainer, 4.5)
                        check("$tag onSurfaceHighest/surfaceContainerHighest", cs.onSurface, cs.surfaceContainerHighest, 4.5)
                        check("$tag inverseOnSurface/inverseSurface", cs.inverseOnSurface, cs.inverseSurface, 4.5)
                        check("$tag onError/error", cs.onError, cs.error, 4.5)
                        check("$tag onErrorContainer/errorContainer", cs.onErrorContainer, cs.errorContainer, 4.5)
                        check("$tag onPrimaryFixed/primaryFixed", cs.onPrimaryFixed, cs.primaryFixed, 4.5)
                        check("$tag onPrimaryFixedVariant/primaryFixed", cs.onPrimaryFixedVariant, cs.primaryFixed, 4.5)
                        // 图形/描边：3:1
                        check("$tag outline/surface", cs.outline, cs.surface, 3.0)
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n").take(4000), failures.isEmpty())
    }

    /** 3) surface 层级单调：深色越叠越亮，浅色越叠越暗；surface 落在 lowest 与 low 之间。 */
    @Test
    fun surfaceHierarchyIsMonotonic() {
        val failures = mutableListOf<String>()
        for (seed in seeds) {
            for (isDark in listOf(false, true)) {
                val cs = buildScheme(listOf(seed), ThemeOptions(isDark = isDark))
                val chain = listOf(
                    "surfaceContainerLowest" to cs.surfaceContainerLowest,
                    "surface" to cs.surface,
                    "surfaceContainerLow" to cs.surfaceContainerLow,
                    "surfaceContainer" to cs.surfaceContainer,
                    "surfaceContainerHigh" to cs.surfaceContainerHigh,
                    "surfaceContainerHighest" to cs.surfaceContainerHighest,
                )
                val tones = chain.map { (n, c) -> n to tone(c) }
                for (i in 1 until tones.size) {
                    val (prevName, prev) = tones[i - 1]
                    val (name, cur) = tones[i]
                    val ok = if (isDark) cur >= prev - 0.01 else cur <= prev + 0.01
                    if (!ok) failures += "seed=%08X %s: %s(tone=%.1f) 与 %s(tone=%.1f) 顺序反了".format(seed, modeNames(isDark), prevName, prev, name, cur)
                }
                // surfaceBright 必须比 surface 亮，surfaceDim 必须比 surface 暗
                if (cs.surfaceBright.luminance() < cs.surface.luminance()) failures += "seed=%08X %s: surfaceBright 比 surface 暗".format(seed, modeNames(isDark))
                if (cs.surfaceDim.luminance() > cs.surface.luminance()) failures += "seed=%08X %s: surfaceDim 比 surface 亮".format(seed, modeNames(isDark))
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** 4) AMOLED 只压画布：surface 纯黑，容器层级仍然可分辨。 */
    @Test
    fun amoledKeepsHierarchy() {
        for (seed in seeds) {
            val plain = buildScheme(listOf(seed), ThemeOptions(isDark = true))
            val amoled = buildScheme(listOf(seed), ThemeOptions(isDark = true, amoled = true))
            assertTrue("AMOLED surface 不是纯黑", amoled.surface.toArgb() == 0xFF000000.toInt())
            assertTrue("AMOLED 改动了容器色，层级被抹平", plain.surfaceContainer == amoled.surfaceContainer)
            assertTrue(
                "AMOLED 下卡片与背景分不开",
                tone(amoled.surfaceContainer) - tone(amoled.surface) >= 5.0,
            )
            assertTrue("AMOLED 影响到了浅色模式", buildScheme(listOf(seed), ThemeOptions(isDark = false, amoled = true)).surface == buildScheme(listOf(seed), ThemeOptions(isDark = false)).surface)
        }
    }

    /** 5) 对比度档位确实抬升了正文对比度（否则设置项就是假的）。 */
    @Test
    fun contrastLevelsRaiseContrast() {
        val failures = mutableListOf<String>()
        for (seed in seeds) {
            for (isDark in listOf(false, true)) {
                val standard = buildScheme(listOf(seed), ThemeOptions(contrast = 0f, isDark = isDark))
                val medium = buildScheme(listOf(seed), ThemeOptions(contrast = 0.5f, isDark = isDark))
                val high = buildScheme(listOf(seed), ThemeOptions(contrast = 1f, isDark = isDark))
                fun bodyR(cs: ColorScheme) = ratio(cs.onSurface, cs.surface)
                if (bodyR(medium) < bodyR(standard) - 0.01) failures += "seed=%08X %s: 中等对比度没有高于标准".format(seed, modeNames(isDark))
                if (bodyR(high) < bodyR(medium) - 0.01) failures += "seed=%08X %s: 最高对比度没有高于中等".format(seed, modeNames(isDark))
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** 6) 品牌兜底 / 渐变 / 锁定配色都必须角色齐全（同样不能有基线残留）。 */
    @Test
    fun brandAndGradientSchemesAreComplete() {
        val baselineDark = roleMap(darkColorScheme()).toMap()
        val baselineLight = roleMap(lightColorScheme()).toMap()
        val cases = listOf(
            "brandDark" to brandScheme(isDark = true),
            "brandLight" to brandScheme(isDark = false),
            "fallbackDark" to fallbackScheme(isDark = true),
            "fallbackLight" to fallbackScheme(isDark = false),
            "gradient" to buildScheme(seeds, ThemeOptions(isDark = true, gradient = true)),
        )
        val failures = mutableListOf<String>()
        for ((tag, cs) in cases) {
            val isDark = cs.surface.luminance() < 0.5f
            val baseMap = if (isDark) baselineDark else baselineLight
            for ((name, color) in roleMap(cs)) {
                if (color.toArgb() == 0xFFFFFFFF.toInt()) continue
                if (color == baseMap[name]) failures += "$tag 角色 $name 回落到 M3 基线配色"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** 7) 风格档位真的改变色度：活力 > 色调 > 中性。 */
    @Test
    fun paletteStylesDifferInChroma() {
        fun chroma(style: PaletteStyle): Double =
            Hct.fromInt(buildScheme(seeds, ThemeOptions(style = style, isDark = true)).primary.toArgb()).chroma
        val vibrant = chroma(PaletteStyle.VIBRANT)
        val tonal = chroma(PaletteStyle.TONAL)
        val neutral = chroma(PaletteStyle.NEUTRAL)
        assertTrue("活力档不比色调档更饱和：$vibrant vs $tonal", vibrant > tonal)
        assertTrue("中性档不比色调档更低饱和：$neutral vs $tonal", neutral < tonal)
    }
}
