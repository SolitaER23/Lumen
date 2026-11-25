package app.lumen.player.ui

import android.content.Context

/** 首页专辑网格的滚动位置。单栏布局下用于「返回首页 / 冷启动」续接。 */
object HomeScrollStore {
    var albumIndex: Int = 0
    /** 同一条目的像素偏移，返回时能精确回到原来的位置 */
    var albumOffset: Int = 0

    private const val PREFS = "lumen_home_scroll"
    private var loaded = false
    private var lastPersist = 0L

    /** 冷启动时把上次的滚动位置/头部高度读回来，首帧就是正确布局（否则会先按兜底值渲染再跳）。 */
    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        albumIndex = p.getInt("index", 0)
        albumOffset = p.getInt("offset", 0)
    }

    /** 滚动过程中节流写入，避免频繁落盘。 */
    fun persist(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersist < 400) return
        lastPersist = now
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("index", albumIndex)
            .putInt("offset", albumOffset)
            .apply()
    }
}
