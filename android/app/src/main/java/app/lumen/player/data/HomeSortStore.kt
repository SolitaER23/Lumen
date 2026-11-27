package app.lumen.player.data

import android.content.Context

/**
 * 首页排序方式的持久化：按「专辑 / 艺人」两个 tab 分别记忆。
 *
 * 旧实现把排序放在 HomeScreen 的 `remember` 里。首页被专辑页 / 设置页盖住时
 * HomeScreen 会退出组合，状态跟着销毁，返回时排序就退回「字母顺序」。
 * 现在 UI 状态由 MainActivity 的 App 持有（页面切换不丢），每次改动经这里落盘，
 * 冷启动 / 进程被杀后也能读回上次的选择。
 *
 * 读写都会经过 [HomeSort.sanitize]：存档里是脏数据时按默认排序显示，
 * 不会出现「菜单高亮某一项、列表却按另一套规则排列」。
 */
object HomeSortStore {
    const val PREFS = "lumen_home_sort"
    private const val KEY_ALBUM = "album"
    private const val KEY_ARTIST = "artist"

    fun album(ctx: Context): Int =
        HomeSort.sanitize(HomeSort.TAB_ALBUMS, prefs(ctx).getInt(KEY_ALBUM, HomeSort.DEFAULT))

    fun artist(ctx: Context): Int =
        HomeSort.sanitize(HomeSort.TAB_ARTISTS, prefs(ctx).getInt(KEY_ARTIST, HomeSort.DEFAULT))

    /** 按 tab 取排序方式（0 = 专辑，1 = 艺人）。 */
    fun sort(ctx: Context, tab: Int): Int =
        if (tab == HomeSort.TAB_ARTISTS) artist(ctx) else album(ctx)

    /** 落盘前先收敛合法范围，返回真正生效的值，调用方拿它同步 UI 状态。 */
    fun setSort(ctx: Context, tab: Int, mode: Int): Int {
        val safe = HomeSort.sanitize(tab, mode)
        prefs(ctx).edit()
            .putInt(if (tab == HomeSort.TAB_ARTISTS) KEY_ARTIST else KEY_ALBUM, safe)
            .apply()
        return safe
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
