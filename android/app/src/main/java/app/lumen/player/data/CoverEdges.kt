package app.lumen.player.data

import android.graphics.Bitmap
import android.util.LruCache

/** 封面四条边的采样色（ARGB）。 */
data class CoverEdges(val top: Int, val bottom: Int, val left: Int, val right: Int)

/**
 * 封面边色采样缓存：与取色共用同一张位图，不额外请求。
 * 供首页 / 播放页的 Lumoo 融合使用——边色随封面变化，背景色随主题变化。
 */
object CoverEdgeStore {
    private val cache = object : LruCache<String, CoverEdges>(96) {}

    fun get(albumId: String): CoverEdges? = cache.get(albumId)

    fun put(albumId: String, edges: CoverEdges) {
        cache.put(albumId, edges)
    }

    /** 取最外圈 1/14 短边的平均色作为该边的代表色。 */
    fun sample(bitmap: Bitmap): CoverEdges {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return CoverEdges(0xFF808080.toInt(), 0xFF808080.toInt(), 0xFF808080.toInt(), 0xFF808080.toInt())
        val band = maxOf(1, minOf(w, h) / 14)

        fun avg(x0: Int, y0: Int, x1: Int, y1: Int): Int {
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0L
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    val p = bitmap.getPixel(x, y)
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                    n++
                }
            }
            if (n == 0L) return 0xFF808080.toInt()
            return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }

        return CoverEdges(
            top = avg(0, 0, w, band),
            bottom = avg(0, h - band, w, h),
            left = avg(0, 0, band, h),
            right = avg(w - band, 0, w, h),
        )
    }
}
