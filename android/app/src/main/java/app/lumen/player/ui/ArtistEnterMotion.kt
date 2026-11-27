package app.lumen.player.ui

/**
 * 艺人页专辑卡片的入场节奏。
 *
 * 首屏卡片按网格阅读顺序（从左到右、再下一行）依次起步；错峰总预算 300ms，
 * 而不是给每张卡固定加一个延时——卡片再多，最后一张的起步也不会被推到很久以后。
 * 超过 [ALBUM_ENTER_STAGGER_MAX_ITEMS] 的卡片不参与首屏入场（它们本来就在首屏之外，
 * 滚动进来时再播放会变成"每次滚动都入场"，反而打扰阅读）。
 */
internal const val ALBUM_ENTER_STAGGER_BUDGET_MS = 300
internal const val ALBUM_ENTER_STAGGER_MAX_ITEMS = 12

/**
 * 第 [index] 张卡片的起步延时；返回 null 表示这张卡不播放入场动画。
 *
 * index 必须是 LazyVerticalGrid 的 item 下标：两列网格里 0/1 是第一行，
 * 2/3 是第二行，依次递增，天然就是"先左后右、再下一行"的阅读顺序。
 */
internal fun albumEnterDelayMs(index: Int, albumCount: Int): Int? {
    if (index < 0 || index >= albumCount || index >= ALBUM_ENTER_STAGGER_MAX_ITEMS) return null
    val animated = minOf(albumCount, ALBUM_ENTER_STAGGER_MAX_ITEMS)
    if (animated <= 1) return 0
    val gap = ALBUM_ENTER_STAGGER_BUDGET_MS / (animated - 1)
    return index * gap
}
