package app.lumen.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

/**
 * 首页排序的回归测试。
 *
 * 重点不是「排序算法能跑」，而是：
 * - 每种下标确实按对应字段排列（存档恢复后列表不能和菜单文案对不上）；
 * - 非法存档回退默认，而不是越界崩溃或悄悄落到别的档位；
 * - 排序不修改传入列表（首页数据是共享缓存，原地排序会把艺人页/下次进入的顺序带偏）。
 */
class HomeSortTest {

    private fun album(
        id: String,
        name: String,
        artist: String = "A",
        songCount: Int = 1,
        year: Int = 2000,
    ) = Album(
        id = id,
        name = name,
        artist = artist,
        coverId = "c$id",
        songCount = songCount,
        year = year,
    )

    private fun artist(id: String, name: String, albumCount: Int = 1) =
        Artist(id = id, name = name, albumCount = albumCount, coverId = "c$id")

    private val albums = listOf(
        album("1", "beta", artist = "Zed", songCount = 5, year = 2001),
        album("2", "Alpha", artist = "mike", songCount = 9, year = 2020),
        album("3", "gamma", artist = "Alice", songCount = 1, year = 2001),
    )

    private fun names(list: List<Album>) = list.map { it.id }

    @Test
    fun defaultSortsAlbumsByNameCaseInsensitive() {
        assertEquals(listOf("2", "1", "3"), names(HomeSort.albums(albums, HomeSort.DEFAULT)))
        // 越界的默认值同样走名称排序
        assertEquals(listOf("2", "1", "3"), names(HomeSort.albums(albums, 99)))
    }

    @Test
    fun yearSortPutsNewestFirstWithNameTieBreak() {
        val list = listOf(
            album("x", "Zulu", year = 2020),
            album("y", "beta", year = 2001),
            album("z", "Alpha", year = 2001),
        )
        // 年份优先：2020 在最前（即使名称排序时它在最后）；
        // 同年按名称升序：Alpha < beta
        assertEquals(listOf("x", "z", "y"), names(HomeSort.albums(list, 1)))
    }

    @Test
    fun songCountSortIsDescending() {
        assertEquals(listOf("2", "1", "3"), names(HomeSort.albums(albums, 2)))
    }

    @Test
    fun artistSortGroupsByArtistThenAlbum() {
        // Alice < mike < Zed
        assertEquals(listOf("3", "2", "1"), names(HomeSort.albums(albums, 3)))
    }

    @Test
    fun artistsSortByNameOrAlbumCount() {
        val list = listOf(
            artist("a", "Zoe", albumCount = 7),
            artist("b", "adele", albumCount = 2),
            artist("c", "Mika", albumCount = 4),
        )
        // 名称：adele < Mika < Zoe
        assertEquals(listOf("b", "c", "a"), HomeSort.artists(list, 0).map { it.id })
        // 专辑数量：Zoe(7) > Mika(4) > adele(2)
        assertEquals(listOf("a", "c", "b"), HomeSort.artists(list, 1).map { it.id })
        // 艺人页只有两项，2 越界 -> 回退名称排序
        assertEquals(listOf("b", "c", "a"), HomeSort.artists(list, 2).map { it.id })
    }

    @Test
    fun sanitizeFallsBackToDefaultForCorruptValues() {
        // 专辑：合法 0..3
        assertEquals(0, HomeSort.sanitize(HomeSort.TAB_ALBUMS, -1))
        assertEquals(0, HomeSort.sanitize(HomeSort.TAB_ALBUMS, 4))
        assertEquals(3, HomeSort.sanitize(HomeSort.TAB_ALBUMS, 3))
        // 艺人：合法 0..1
        assertEquals(1, HomeSort.sanitize(HomeSort.TAB_ARTISTS, 1))
        assertEquals(0, HomeSort.sanitize(HomeSort.TAB_ARTISTS, 2))
    }

    @Test
    fun optionCountMatchesSortMenus() {
        assertEquals(4, HomeSort.optionCount(HomeSort.TAB_ALBUMS))
        assertEquals(2, HomeSort.optionCount(HomeSort.TAB_ARTISTS))
        // 未知 tab 与首页默认 tab 一致，避免调用方传错时下标范围缩水
        assertEquals(4, HomeSort.optionCount(7))
    }

    @Test
    fun sortingDoesNotMutateInputOrReturnSameList() {
        val original = albums.toList()
        val sorted = HomeSort.albums(albums, 2)
        assertEquals(original, albums)
        assertNotSame(albums, sorted)
    }
}
