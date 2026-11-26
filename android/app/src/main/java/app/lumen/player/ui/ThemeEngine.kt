package app.lumen.player.ui

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dynamiccolor.MaterialDynamicColors
import hct.Hct
import scheme.SchemeExpressive
import scheme.SchemeNeutral
import scheme.SchemeTonalSpot
import scheme.SchemeVibrant
import org.json.JSONArray

/** 主题风格档：决定用 MCU 的哪套 Scheme。 */
enum class PaletteStyle(val key: String, val label: String) {
    TONAL("tonal", "色相"),
    VIBRANT("vibrant", "活力"),
    EXPRESSIVE("expressive", "表现"),
    NEUTRAL("neutral", "中性"),
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

/** 一次配色构建需要的全部开关。 */
data class ThemeOptions(
    val contrast: Float = 0.45f,
    val isDark: Boolean = true,
    val style: PaletteStyle = PaletteStyle.TONAL,
    val amoled: Boolean = false,
    val gradient: Boolean = false,
)

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

private fun schemeOf(
    style: PaletteStyle,
    seedHct: Hct,
    isDark: Boolean,
    contrast: Double,
): dynamiccolor.DynamicScheme = when (style) {
    PaletteStyle.TONAL -> SchemeTonalSpot(seedHct, isDark, contrast)
    PaletteStyle.VIBRANT -> SchemeVibrant(seedHct, isDark, contrast)
    PaletteStyle.EXPRESSIVE -> SchemeExpressive(seedHct, isDark, contrast)
    PaletteStyle.NEUTRAL -> SchemeNeutral(seedHct, isDark, contrast)
}

private fun mdcScheme(style: PaletteStyle, seed: Int, opts: ThemeOptions): ColorScheme {
    val scheme = schemeOf(style, Hct.fromInt(constrainSeed(seed)), opts.isDark, opts.contrast.toDouble())
    val mdc = MaterialDynamicColors()
    fun c(role: dynamiccolor.DynamicColor): Color = Color(role.getArgb(scheme))
    val builder = if (opts.isDark) darkColorScheme() else lightColorScheme()
    val cs = builder.copy(
        primary = c(mdc.primary()),
        onPrimary = c(mdc.onPrimary()),
        primaryContainer = c(mdc.primaryContainer()),
        onPrimaryContainer = c(mdc.onPrimaryContainer()),
        inversePrimary = c(mdc.inversePrimary()),
        secondary = c(mdc.secondary()),
        onSecondary = c(mdc.onSecondary()),
        secondaryContainer = c(mdc.secondaryContainer()),
        onSecondaryContainer = c(mdc.onSecondaryContainer()),
        tertiary = c(mdc.tertiary()),
        onTertiary = c(mdc.onTertiary()),
        tertiaryContainer = c(mdc.tertiaryContainer()),
        onTertiaryContainer = c(mdc.onTertiaryContainer()),
        error = c(mdc.error()),
        onError = c(mdc.onError()),
        errorContainer = c(mdc.errorContainer()),
        onErrorContainer = c(mdc.onErrorContainer()),
        background = c(mdc.surfaceContainer()),
        surface = c(mdc.surfaceContainer()),
        onBackground = c(mdc.onSurface()),
        onSurface = c(mdc.onSurface()),
        surfaceVariant = c(mdc.surfaceVariant()),
        onSurfaceVariant = c(mdc.onSurfaceVariant()),
        surfaceContainerLowest = c(mdc.surface()),
        surfaceContainerLow = c(mdc.surfaceContainerLow()),
        surfaceContainer = c(mdc.surfaceContainer()),
        surfaceContainerHigh = c(mdc.surfaceContainerHigh()),
        surfaceContainerHighest = c(mdc.surfaceContainerHighest()),
        outline = c(mdc.outline()),
        outlineVariant = c(mdc.outlineVariant()),
        inverseSurface = c(mdc.inverseSurface()),
        inverseOnSurface = c(mdc.inverseOnSurface()),
        surfaceBright = c(mdc.surfaceBright()),
        surfaceDim = c(mdc.surfaceDim()),
    )
    // AMOLED 只在深色模式生效：浅色模式下即使开关仍为开，也不能把底色压黑
    return if (opts.amoled && opts.isDark) amoledOf(cs) else cs
}

/**
 * 封面渐变主题：主色取种子 1，副色/第三色取评分第 2、3 名，容器与前景仍来自各自方案，
 * 得到一个多色但不打架的配色。
 */
private fun gradientScheme(seeds: List<Int>, opts: ThemeOptions): ColorScheme {
    val base = mdcScheme(opts.style, seeds.firstOrNull() ?: 0xFF4F6BFF.toInt(), opts)
    if (seeds.size < 2) return base
    val s2 = mdcScheme(opts.style, seeds[1], opts)
    val s3 = if (seeds.size > 2) mdcScheme(opts.style, seeds[2], opts) else s2
    return base.copy(
        secondary = s2.primary,
        onSecondary = s2.onPrimary,
        secondaryContainer = s2.primaryContainer,
        onSecondaryContainer = s2.onPrimaryContainer,
        tertiary = s3.primary,
        onTertiary = s3.onPrimary,
        tertiaryContainer = s3.primaryContainer,
        onTertiaryContainer = s3.onPrimaryContainer,
    )
}

/** 构建一套配色（种子可 1~3 个）。 */
fun buildScheme(seeds: List<Int>, opts: ThemeOptions): ColorScheme {
    if (seeds.isEmpty()) return fallbackScheme(opts.isDark, opts.amoled)
    return if (opts.gradient && seeds.size > 1) gradientScheme(seeds, opts) else mdcScheme(opts.style, seeds.first(), opts)
}

/** AMOLED：把底色压到纯黑，容器压到近黑，保留前景对比。 */
fun amoledOf(src: ColorScheme): ColorScheme {
    val black = Color(0xFF000000)
    val near = Color(0xFF070708)
    return src.copy(
        background = black,
        surface = black,
        surfaceDim = black,
        surfaceContainerLowest = black,
        surfaceContainerLow = near,
        surfaceContainer = near,
        surfaceContainerHigh = near,
        surfaceContainerHighest = near,
        surfaceBright = near,
    )
}

/** 首页低频变体（P1-1）：淡化主色并让容器向封面色轻微靠拢，保留封面的"存在感"。 */
fun mutedForHome(src: ColorScheme, isDark: Boolean): ColorScheme {
    fun desat(c: Color, k: Float): Color {
        val h = Hct.fromInt(c.toArgb())
        return Color(Hct.from(h.hue, h.chroma * k, h.tone).toInt())
    }
    if (!isDark) {
        val bg = lerp(Color(0xFFFAF9FC), src.surfaceContainer, 0.25f)
        val card = lerp(Color(0xFFF0EEF3), src.surfaceContainerHigh, 0.30f)
        return lightColorScheme(
            primary = desat(src.primary, 0.60f),
            onPrimary = Color.White,
            primaryContainer = lerp(Color(0xFFE6E1EA), src.primaryContainer, 0.45f),
            onPrimaryContainer = Color(0xFF1B1B1F),
            secondary = desat(src.secondary, 0.55f),
            onSecondary = Color.White,
            tertiary = desat(src.tertiary, 0.55f),
            background = bg,
            onBackground = Color(0xFF1B1B1F),
            surface = bg,
            onSurface = Color(0xFF1B1B1F),
            surfaceVariant = card,
            onSurfaceVariant = Color(0xFF47464F),
            surfaceContainer = card,
            surfaceContainerHigh = card,
            surfaceContainerHighest = card,
            surfaceContainerLow = card,
            outline = Color(0xFF74737B),
            outlineVariant = Color(0xFFC9C7CF),
        )
    }
    val bg = lerp(HomeBg, src.surfaceContainer, 0.22f)
    val card = lerp(HomeCard, src.surfaceContainerHigh, 0.28f)
    return darkColorScheme(
        primary = desat(src.primary, 0.55f),
        onPrimary = src.onPrimary,
        primaryContainer = lerp(HomeCard, src.primaryContainer, 0.35f),
        onPrimaryContainer = HomeText,
        secondary = desat(src.secondary, 0.5f),
        onSecondary = src.onBackground,
        tertiary = desat(src.tertiary, 0.5f),
        background = bg,
        onBackground = HomeText,
        surface = bg,
        onSurface = HomeText,
        surfaceVariant = card,
        onSurfaceVariant = HomeSub,
        surfaceContainer = card,
        surfaceContainerHigh = lerp(card, src.surfaceContainerHigh, 0.4f),
        surfaceContainerHighest = lerp(card, src.surfaceContainerHighest, 0.5f),
        surfaceContainerLow = card,
        outline = HomeSub,
        outlineVariant = Color(0xFF2A2A30),
    )
}

/** 无封面/取色失败时的兜底（P1-3），浅色模式单独一套。 */
fun fallbackScheme(isDark: Boolean, amoled: Boolean = false): ColorScheme =
    if (isDark) (if (amoled) amoledOf(FallbackScheme) else FallbackScheme) else FallbackLight

val FallbackLight: ColorScheme = lightColorScheme(
    primary = Color(0xFFB3253A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9DD),
    onPrimaryContainer = Color(0xFF40000D),
    secondary = Color(0xFF00695F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB9F2EA),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE7B8),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFFAF9FC),
    surface = Color(0xFFFAF9FC),
    onSurface = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceContainer = Color(0xFFEFEDF2),
    surfaceContainerHigh = Color(0xFFE9E7EC),
    outlineVariant = Color(0xFFC9C7CF),
)

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

/** 事件光斑对比度校验（P2-4）：与底色亮度差不足时提亮。 */
fun ensureLuminous(color: Color, base: Color, minDelta: Float = 0.22f): Color {
    val lc = color.luminance()
    val lb = base.luminance()
    if (kotlin.math.abs(lc - lb) >= minDelta) return color
    val target = if (lb > 0.5f) (lb - minDelta).coerceAtLeast(0f) else (lb + minDelta).coerceAtMost(1f)
    val h = Hct.fromInt(color.toArgb())
    return Color(Hct.from(h.hue, h.chroma, (target * 100f).coerceIn(0f, 100f).toDouble()).toInt())
}
