package app.lumen.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import app.lumen.player.data.MoodResult
import blend.Blend
import hct.Hct
import quantize.QuantizerCelebi
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

/** 取色不再自己下载：直接向 Coil 要 200px 位图，命中列表已加载的缓存，零额外流量。 */
private suspend fun coverBitmap(ctx: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val req = coil3.request.ImageRequest.Builder(ctx)
            .data(url)
            .size(200, 200)
            .build()
        val result = coil3.SingletonImageLoader.get(ctx).execute(req)
        val bmp = (result.image as? coil3.BitmapImage)?.bitmap ?: return@runCatching null
        // 硬件位图不能 getPixel：只有这种情况下才复制一份软件位图
        if (bmp.config == android.graphics.Bitmap.Config.HARDWARE) {
            bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        } else {
            bmp
        }
    }.getOrNull()
}

/** 取色结果缓存（P2-1：64 条，按 albumId + 选项 + 情绪档）。 */
private val schemeCache = object : android.util.LruCache<String, ColorScheme>(64) {}

/** 情绪锚点色：按标签给 HCT 锚点，再按坐标做小幅修正。 */
private fun moodAnchorArgb(m: MoodResult): Int {
    val (hue, chroma, tone) = when (m.label) {
        "Happy" -> Triple(52.0, 70.0, 62.0)
        "Excited" -> Triple(28.0, 75.0, 64.0)
        "Tender" -> Triple(338.0, 45.0, 70.0)
        "Calm" -> Triple(168.0, 32.0, 66.0)
        "Sad" -> Triple(232.0, 30.0, 55.0)
        "Angry" -> Triple(8.0, 75.0, 55.0)
        else -> Triple(286.0, 45.0, 52.0)
    }
    val hueShift = (m.valence - 0.5f) * 18.0 - (m.arousal - 0.5f) * 10.0
    return Hct.from((hue + hueShift + 360.0) % 360.0, chroma, tone).toInt()
}

/**
 * 封面主色为主、情绪色只做「协调偏移」：
 * 色相按情绪强度旋转 25%–75%，色度混合 15%–45%，明度保持封面。
 */
fun moodTintedSeed(coverSeed: Int, mood: MoodResult): Int {
    val moodColor = moodAnchorArgb(mood)
    val strength = (
        (kotlin.math.abs(mood.valence - 0.5f) + kotlin.math.abs(mood.arousal - 0.5f)) * 2f
        ).coerceIn(0f, 1f)
    val cover = Hct.fromInt(coverSeed)
    val moodHct = Hct.fromInt(moodColor)
    val amount = (0.25f + 0.50f * strength).toDouble()
    val rotated = Hct.fromInt(Blend.hctHue(coverSeed, moodColor, amount))
    val chromaK = (0.15 + 0.30 * strength.toDouble()).coerceIn(0.0, 0.45)
    val chroma = cover.chroma * (1.0 - chromaK) + moodHct.chroma * chromaK
    return Hct.from(rotated.hue, chroma, cover.tone).toInt()
}

/** 从封面图算出 1~3 个种子（已约束）。失败返回 null。 */
private fun seedsOf(source: Bitmap, albumId: String): List<Int>? {
    val bitmap = source
    // 注意：位图来自 Coil 的缓存，不能在这里 recycle()
    val small = if (bitmap.width > 96 || bitmap.height > 96) {
        Bitmap.createScaledBitmap(bitmap, 96, 96, true)
    } else bitmap
    // 同一次解码顺带采样四条边的代表色（不额外下载）
    runCatching { app.lumen.player.data.CoverEdgeStore.put(albumId, app.lumen.player.data.CoverEdgeStore.sample(small)) }
    val pixels = IntArray(small.width * small.height)
    small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
    val quantized = QuantizerCelebi.quantize(pixels, 128)
    val scored = Score.score(quantized)
    if (scored.isEmpty()) return null
    return scored.take(3).map { constrainSeed(it) }
}

/** 下载并解析封面种子；网络失败返回 null（由调用方显式降级）。 */
private suspend fun fetchSeeds(ctx: Context, url: String, albumId: String): List<Int>? {
    val bmp = coverBitmap(ctx, url) ?: return null
    return runCatching { seedsOf(bmp, albumId) }.getOrNull()
}

/**
 * 取色主入口：先查持久化种子（P2-2），没有再下载；结果进 64 条 LRU。
 * [albumId] 作为缓存主键（URL 里的 token 不进 key，P0-2）。
 */
suspend fun schemeFromCover(
    ctx: Context,
    albumId: String,
    url: String,
    mood: MoodResult?,
    opts: ThemeOptions,
): ColorScheme? = withContext(Dispatchers.IO) {
    val moodBucket = if (opts.moodTint && mood != null) {
        "${(mood.valence * 10).toInt()}${(mood.arousal * 10).toInt()}"
    } else {
        "-"
    }
    val key = "$albumId|${opts.contrast}|${opts.style.key}|${opts.isDark}|${opts.amoled}|${opts.gradient}|$moodBucket"
    schemeCache.get(key)?.let { return@withContext it }

    val stored = PaletteStore.seeds(ctx, albumId)
    val seeds = stored ?: fetchSeeds(ctx, url, albumId)?.also { PaletteStore.put(ctx, albumId, it) }
    if (seeds.isNullOrEmpty()) return@withContext null

    val tinted = if (opts.moodTint && mood != null) {
        listOf(moodTintedSeed(seeds.first(), mood)) + seeds.drop(1)
    } else {
        seeds
    }
    val scheme = buildScheme(tinted, opts)
    schemeCache.put(key, scheme)
    scheme
}

/** 同步命中内存缓存（页面首帧直接用，避免"先错色再改色"的闪烁）。 */
fun schemeFromCache(albumId: String, mood: MoodResult?, opts: ThemeOptions): ColorScheme? {
    val moodBucket = if (opts.moodTint && mood != null) {
        "${(mood.valence * 10).toInt()}${(mood.arousal * 10).toInt()}"
    } else {
        "-"
    }
    val key = "$albumId|${opts.contrast}|${opts.style.key}|${opts.isDark}|${opts.amoled}|${opts.gradient}|$moodBucket"
    return schemeCache.get(key)
}

/** 清空取色缓存（设置里「重新取色」用）。 */
fun clearPaletteCache(ctx: Context) {
    schemeCache.evictAll()
    PaletteStore.clear(ctx)
}
