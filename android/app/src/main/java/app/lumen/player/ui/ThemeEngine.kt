package app.lumen.player.ui

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dynamiccolor.ColorSpec.SpecVersion
import dynamiccolor.DynamicColor
import dynamiccolor.DynamicScheme
import dynamiccolor.DynamicScheme.Platform
import dynamiccolor.MaterialDynamicColors
import hct.Hct
import org.json.JSONArray
import scheme.SchemeExpressive
import scheme.SchemeNeutral
import scheme.SchemeTonalSpot
import scheme.SchemeVibrant

/**
 * 配色风格档：与 Material Color Utilities 的官方 Variant 一一对应。
 *
 * 只暴露产品真正会用到的四档；其余 Variant（fidelity / content / monochrome /
 * fruit_salad / rainbow）没有入口，避免设置页出现"看起来差不多"的选项。
 */
enum class PaletteStyle(val key: String, val label: String, val labelEn: String) {
    TONAL("tonal", "色调", "Tonal"),
    VIBRANT("vibrant", "活力", "Vibrant"),
    EXPRESSIVE("expressive", "表现", "Expressive"),
    NEUTRAL("neutral", "中性", "Neutral"),
    ;

    companion object {
        fun of(key: String): PaletteStyle = entries.firstOrNull { it.key == key } ?: TONAL
    }
}

/** 深浅模式。 */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light"),
    ;

    companion object {
        fun of(key: String): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * 对比度档位：沿用 Google 的 MCU 对比度刻度（0 = 设计标准，1 = 最高）。
 *
 * 平台侧（Android 12+ 的「对比度」设置、Material Theme Builder）暴露的也就是这三档，
 * 这里保持同一组数字，避免出现 0.45 / 0.7 这种既不是标准档、也无法和系统对齐的取值。
 */
enum class ContrastLevel(val level: Float, val label: String, val labelEn: String) {
    STANDARD(0f, "标准", "Standard"),
    MEDIUM(0.5f, "中等", "Medium"),
    HIGH(1f, "最高", "High"),
    ;

    companion object {
        /** 旧版本存过任意浮点（0.45、0.62…）：按最近档位归一，避免出现"三档全不选中"。 */
        fun nearest(value: Float): ContrastLevel =
            entries.minByOrNull { kotlin.math.abs(it.level - value) } ?: STANDARD
    }
}

/** 一次配色构建需要的全部开关。 */
data class ThemeOptions(
    val contrast: Float = 0f,
    val isDark: Boolean = true,
    val style: PaletteStyle = PaletteStyle.TONAL,
    val amoled: Boolean = false,
    val gradient: Boolean = false,
)

/**
 * 生成配色使用的规范版本。
 *
 * material3 1.5（M3 Expressive）以及本仓库内置的 MaterialDynamicColors 都按 2025 规范的
 * 角色/色调映射取值，因此调色板也必须用同一版规范生成；混用 2021 调色板会得到
 * "色调来自 2025、色度来自 2021"的半套配色。
 */
private val SPEC = SpecVersion.SPEC_2025
private val PLATFORM = Platform.PHONE

private const val SEED_MIN_CHROMA = 18.0
private const val SEED_MAX_CHROMA = 92.0
private const val SEED_MIN_TONE = 42.0
private const val SEED_MAX_TONE = 86.0

/**
 * 种子色约束（P1-4）：灰白封面把色度抬到可用下限，过饱和封面压回舒适区，
 * 极暗/极亮的种子把明度拉回中间带，避免前景对比度塌陷。
 */
fun constrainSeed(argb: Int): Int {
    val hct = Hct.fromInt(argb)
    val chroma = hct.chroma.coerceIn(SEED_MIN_CHROMA, SEED_MAX_CHROMA)
    val tone = hct.tone.coerceIn(SEED_MIN_TONE, SEED_MAX_TONE)
    return Hct.from(hct.hue, chroma, tone).toInt()
}

private fun schemeOf(style: PaletteStyle, seedHct: Hct, isDark: Boolean, contrast: Double): DynamicScheme =
    when (style) {
        PaletteStyle.TONAL -> SchemeTonalSpot(seedHct, isDark, contrast, SPEC, PLATFORM)
        PaletteStyle.VIBRANT -> SchemeVibrant(seedHct, isDark, contrast, SPEC, PLATFORM)
        PaletteStyle.EXPRESSIVE -> SchemeExpressive(seedHct, isDark, contrast, SPEC, PLATFORM)
        PaletteStyle.NEUTRAL -> SchemeNeutral(seedHct, isDark, contrast, SPEC, PLATFORM)
    }

/**
 * 把 MCU 的 49 个色彩角色**一个不落地**映射到 Compose 的 ColorScheme。
 *
 * 早先只映射了二十多个角色，其余（品牌固定色、scrim、surfaceTint、surfaceContainerLow
 * 之类）回退到 `darkColorScheme()/lightColorScheme()` 的默认值——那不是"没设"，而是
 * 一套紫色的基线配色，任何用到它们的组件都会突然冒出和封面无关的紫色。
 */
internal fun colorSchemeFrom(scheme: DynamicScheme): ColorScheme {
    val mdc = MaterialDynamicColors()
    fun c(role: DynamicColor): Color = Color(role.getArgb(scheme))
    val base = if (scheme.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        // 主色
        primary = c(mdc.primary()),
        onPrimary = c(mdc.onPrimary()),
        primaryContainer = c(mdc.primaryContainer()),
        onPrimaryContainer = c(mdc.onPrimaryContainer()),
        inversePrimary = c(mdc.inversePrimary()),
        primaryFixed = c(mdc.primaryFixed()),
        primaryFixedDim = c(mdc.primaryFixedDim()),
        onPrimaryFixed = c(mdc.onPrimaryFixed()),
        onPrimaryFixedVariant = c(mdc.onPrimaryFixedVariant()),
        // 辅助色
        secondary = c(mdc.secondary()),
        onSecondary = c(mdc.onSecondary()),
        secondaryContainer = c(mdc.secondaryContainer()),
        onSecondaryContainer = c(mdc.onSecondaryContainer()),
        secondaryFixed = c(mdc.secondaryFixed()),
        secondaryFixedDim = c(mdc.secondaryFixedDim()),
        onSecondaryFixed = c(mdc.onSecondaryFixed()),
        onSecondaryFixedVariant = c(mdc.onSecondaryFixedVariant()),
        // 第三色
        tertiary = c(mdc.tertiary()),
        onTertiary = c(mdc.onTertiary()),
        tertiaryContainer = c(mdc.tertiaryContainer()),
        onTertiaryContainer = c(mdc.onTertiaryContainer()),
        tertiaryFixed = c(mdc.tertiaryFixed()),
        tertiaryFixedDim = c(mdc.tertiaryFixedDim()),
        onTertiaryFixed = c(mdc.onTertiaryFixed()),
        onTertiaryFixedVariant = c(mdc.onTertiaryFixedVariant()),
        // 错误
        error = c(mdc.error()),
        onError = c(mdc.onError()),
        errorContainer = c(mdc.errorContainer()),
        onErrorContainer = c(mdc.onErrorContainer()),
        // 表面：surface 是画布，surfaceContainer* 是叠在画布上的层级（越大越"高"）
        background = c(mdc.surface()),
        onBackground = c(mdc.onSurface()),
        surface = c(mdc.surface()),
        onSurface = c(mdc.onSurface()),
        surfaceDim = c(mdc.surfaceDim()),
        surfaceBright = c(mdc.surfaceBright()),
        surfaceContainerLowest = c(mdc.surfaceContainerLowest()),
        surfaceContainerLow = c(mdc.surfaceContainerLow()),
        surfaceContainer = c(mdc.surfaceContainer()),
        surfaceContainerHigh = c(mdc.surfaceContainerHigh()),
        surfaceContainerHighest = c(mdc.surfaceContainerHighest()),
        // surfaceVariant 是 M2 遗留语义（M3 里只做次级表面），保留以兼容存量组件
        surfaceVariant = c(mdc.surfaceVariant()),
        onSurfaceVariant = c(mdc.onSurfaceVariant()),
        surfaceTint = c(mdc.surfaceTint()),
        inverseSurface = c(mdc.inverseSurface()),
        inverseOnSurface = c(mdc.inverseOnSurface()),
        outline = c(mdc.outline()),
        outlineVariant = c(mdc.outlineVariant()),
        scrim = c(mdc.scrim()),
    )
}

private fun mdcScheme(style: PaletteStyle, seed: Int, opts: ThemeOptions): ColorScheme {
    val scheme = schemeOf(style, Hct.fromInt(constrainSeed(seed)), opts.isDark, opts.contrast.toDouble())
    val cs = colorSchemeFrom(scheme)
    // AMOLED 只在深色模式生效：浅色模式下即使开关仍为开，也不能把底色压黑
    return if (opts.amoled && opts.isDark) amoledOf(cs) else cs
}

/**
 * 封面渐变主题：主色取种子 1，副色/第三色取评分第 2、3 名。
 *
 * 在**完整**的基底配色上只替换 secondary / tertiary 两族角色（含它们的固定色），
 * 其余角色仍来自同一次 MCU 计算，不引入第二套基线值。
 */
private fun gradientScheme(seeds: List<Int>, opts: ThemeOptions): ColorScheme {
    val base = mdcScheme(opts.style, seeds.firstOrNull() ?: BRAND_SEEDS[0], opts)
    if (seeds.size < 2) return base
    val s2 = mdcScheme(opts.style, seeds[1], opts)
    val s3 = if (seeds.size > 2) mdcScheme(opts.style, seeds[2], opts) else s2
    return base.copy(
        secondary = s2.primary,
        onSecondary = s2.onPrimary,
        secondaryContainer = s2.primaryContainer,
        onSecondaryContainer = s2.onPrimaryContainer,
        secondaryFixed = s2.primaryFixed,
        secondaryFixedDim = s2.primaryFixedDim,
        onSecondaryFixed = s2.onPrimaryFixed,
        onSecondaryFixedVariant = s2.onPrimaryFixedVariant,
        tertiary = s3.primary,
        onTertiary = s3.onPrimary,
        tertiaryContainer = s3.primaryContainer,
        onTertiaryContainer = s3.onPrimaryContainer,
        tertiaryFixed = s3.primaryFixed,
        tertiaryFixedDim = s3.primaryFixedDim,
        onTertiaryFixed = s3.onPrimaryFixed,
        onTertiaryFixedVariant = s3.onPrimaryFixedVariant,
    )
}

/** 构建一套配色（种子可 1~3 个）。 */
fun buildScheme(seeds: List<Int>, opts: ThemeOptions): ColorScheme {
    if (seeds.isEmpty()) return fallbackScheme(opts.isDark, opts.amoled, opts.contrast)
    return if (opts.gradient && seeds.size > 1) gradientScheme(seeds, opts) else mdcScheme(opts.style, seeds.first(), opts)
}

/**
 * AMOLED：只把**画布**压到纯黑（surface / background / surfaceDim / lowest），
 * 卡片等容器保持规范里的色调层级。
 *
 * 早先把所有容器一起压成同一个近黑色，等于把"用色调表达层级"这件事抹平了——
 * 打开 AMOLED 后卡片和背景分不开，层级只能靠描边硬撑。
 */
fun amoledOf(src: ColorScheme): ColorScheme {
    val black = Color(0xFF000000)
    return src.copy(
        background = black,
        surface = black,
        surfaceDim = black,
        surfaceContainerLowest = black,
    )
}

/**
 * 品牌兜底配色：没有封面、取色失败、登录页与引导页都用它。
 *
 * 由 MCU 从三枚品牌色生成（红 + 青 + 金），角色齐全、配对与对比度由算法保证；
 * 早先这里是手写的十几个色值，剩下的角色回落到 M3 基线紫。
 */
private val BRAND_SEEDS = listOf(0xFFE04A5F.toInt(), 0xFF6FD3C7.toInt(), 0xFFF2C36B.toInt())

fun brandScheme(isDark: Boolean, amoled: Boolean = false, contrast: Float = 0f): ColorScheme =
    gradientScheme(
        BRAND_SEEDS,
        ThemeOptions(contrast = contrast, isDark = isDark, amoled = false, gradient = true),
    ).let { if (amoled && isDark) amoledOf(it) else it }

/** 无封面/取色失败时的兜底（P1-3），浅色与深色各一套。 */
fun fallbackScheme(isDark: Boolean, amoled: Boolean = false, contrast: Float = 0f): ColorScheme =
    brandScheme(isDark, amoled, contrast)

val FallbackScheme: ColorScheme by lazy { brandScheme(isDark = true) }
val FallbackLight: ColorScheme by lazy { brandScheme(isDark = false) }

/**
 * 封面种子持久化（P2-2）：albumId → 1~3 个种子 ARGB。
 * 冷启动直接出正确主题，不必等网络与量化。
 */
object PaletteStore {
    private const val FILE = "lumen_palette"
    private const val LIMIT = 600

    fun seeds(ctx: Context, albumId: String): List<Int>? = runCatching {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("s_$albumId", null)
            ?: return null
        val arr = JSONArray(raw)
        List(arr.length()) { arr.getInt(it) }
    }.getOrNull()

    fun put(ctx: Context, albumId: String, seeds: List<Int>) {
        runCatching {
            val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val arr = JSONArray()
            seeds.take(3).forEach { arr.put(it) }
            val e = p.edit().putString("s_$albumId", arr.toString())
            // 简单容量控制：条目过多时清空重建，色值是纯缓存，丢了只是重算
            if (p.all.size > LIMIT) e.clear()
            e.putString("s_$albumId", arr.toString()).apply()
        }
    }

    fun clear(ctx: Context) {
        runCatching { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply() }
    }
}
