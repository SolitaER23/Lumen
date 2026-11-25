package app.lumen.player.ui

import androidx.compose.ui.geometry.Rect

/**
 * 记录可点击卡片在根坐标系里的位置，作为"容器变换"的起点/终点。
 * 进入详情时页面从这张卡片的位置长出来，返回时收缩回同一位置。
 */
object NavOriginStore {
    private val map = HashMap<String, Rect>()

    fun put(key: String, rect: Rect) {
        if (rect.width > 0f && rect.height > 0f) map[key] = rect
    }

    fun get(key: String?): Rect? = key?.let { map[it] }

    /** 以记录到的卡片中心作为缩放原点（分数坐标，0..1）。 */
    fun transformOrigin(key: String?, widthPx: Float, heightPx: Float): androidx.compose.ui.graphics.TransformOrigin? {
        val r = get(key) ?: return null
        if (widthPx <= 0f || heightPx <= 0f) return null
        return androidx.compose.ui.graphics.TransformOrigin(
            (r.center.x / widthPx).coerceIn(0f, 1f),
            (r.center.y / heightPx).coerceIn(0f, 1f),
        )
    }

    /** 展开起点比例：小按钮 ≈ 屏幕宽度的 6%~12%。 */
    fun initialScale(key: String?, widthPx: Float): Float {
        val r = get(key) ?: return 0.9f
        if (widthPx <= 0f) return 0.9f
        return (r.width / widthPx * 1.6f).coerceIn(0.06f, 0.45f)
    }
}
