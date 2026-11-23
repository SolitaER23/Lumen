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
}
