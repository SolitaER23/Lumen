package app.lumen.player.data

/**
 * 首页「专辑 / 艺人」排序的纯逻辑。
 *
 * 抽到 data 层的原因：
 * - 排序方式要落盘并在页面切换 / 冷启动后恢复，存档的合法范围必须与这里的
 *   分支一一对应，UI 不能各自再写一份「有几项」；
 * - 排序规则能单测：换排序方式后列表到底按哪个字段排、非法存档是否回退默认，
 *   都可以在 JVM 测试里钉死，不需要起模拟器。
 *
 * 下标含义（按 tab 分开）：
 * - 专辑：0 名称、1 发行年份、2 歌曲数量、3 艺人；
 * - 艺人：0 名称、1 专辑数量。
 */
object HomeSort {
    const val TAB_ALBUMS = 0
    const val TAB_ARTISTS = 1

    /** 默认排序（名称）。存档缺失或越界时回退到它。 */
    const val DEFAULT = 0

    /** 排序菜单的项数：专辑 4 项、艺人 2 项。 */
    fun optionCount(tab: Int): Int = when (tab) {
        TAB_ARTISTS -> 2
        else -> 4
    }

    /**
     * 把存档 / 调用方给的排序下标收敛到当前 tab 的合法范围。
     *
     * 越界值（旧版本遗留、手改 prefs、以后缩减选项）一律回退默认，而不是 clamp 到
     * 最后一档：clamp 会让「备注里没选过的排序」看起来像用户选过的。
     */
    fun sanitize(tab: Int, mode: Int): Int =
        if (mode in 0 until optionCount(tab)) mode else DEFAULT

    fun albums(list: List<Album>, mode: Int): List<Album> = when (sanitize(TAB_ALBUMS, mode)) {
        1 -> list.sortedWith(compareByDescending<Album> { it.year }.thenBy { it.name.lowercase() })
        2 -> list.sortedByDescending { it.songCount }
        3 -> list.sortedBy { it.artist.lowercase() + "|" + it.name.lowercase() }
        else -> list.sortedBy { it.name.lowercase() }
    }

    fun artists(list: List<Artist>, mode: Int): List<Artist> =
        when (sanitize(TAB_ARTISTS, mode)) {
            1 -> list.sortedByDescending { it.albumCount }
            else -> list.sortedBy { it.name.lowercase() }
        }
}
