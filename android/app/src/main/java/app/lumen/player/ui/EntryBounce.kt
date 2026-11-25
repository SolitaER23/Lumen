package app.lumen.player.ui

import androidx.compose.runtime.mutableStateMapOf

/**
 * 页面缩回入口后，让入口按钮轻微"回弹"一下（1.00 → 1.04 → 1.00，120ms），
 * 强化"落回按钮"的观感。只做视觉反馈，不参与导航。
 */
object EntryBounce {
    private val hits = mutableStateMapOf<String, Int>()

    fun trigger(key: String) {
        hits[key] = (hits[key] ?: 0) + 1
    }

    fun count(key: String): Int = hits[key] ?: 0
}
