package app.lumen.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumLogicTest {

    private fun t(id: String, disc: Int, track: Int) = Track(
        id = id, title = "T$id", artist = "A", albumId = "al",
        disc = disc, track = track, seconds = 60, suffix = "flac",
    )

    private val multi = listOf(
        t("a", 1, 1), t("b", 1, 2), t("c", 2, 1), t("d", 2, 2), t("e", 3, 1),
    )

    @Test
    fun discsAreSortedAndUnique() {
        assertEquals(listOf(1, 2, 3), discsOf(multi))
    }

    @Test
    fun singleDiscHasOneEntry() {
        assertEquals(listOf(1), discsOf(multi.take(2)))
    }

    @Test
    fun tracksOfDiscFilters() {
        assertEquals(listOf("a", "b"), tracksOfDisc(multi, 1).map { it.id })
        assertEquals(listOf("c", "d"), tracksOfDisc(multi, 2).map { it.id })
    }

    @Test
    fun absoluteStartIsOffsetIntoFullQueue() {
        assertEquals(0, absoluteStart(multi, 1))
        assertEquals(2, absoluteStart(multi, 2))
        assertEquals(4, absoluteStart(multi, 3))
    }

    @Test
    fun absoluteStartFallsBackToZero() {
        assertEquals(0, absoluteStart(multi, 9))
        assertEquals(0, absoluteStart(emptyList(), 1))
    }

    @Test
    fun discIndexOfCurrentTrack() {
        assertEquals(1, discIndexOfTrack(multi, "c"))
        assertEquals(2, discIndexOfTrack(multi, "e"))
        assertEquals(0, discIndexOfTrack(multi, "missing"))
        assertEquals(0, discIndexOfTrack(multi, null))
    }

    @Test
    fun playingEveryDiscKeepsContiguousIndices() {
        // 每碟的 (起始下标, 曲目) 必须首尾相接，跨碟播放才不会跳曲
        var expected = 0
        for (d in discsOf(multi)) {
            assertEquals(expected, absoluteStart(multi, d))
            expected += tracksOfDisc(multi, d).size
        }
        assertEquals(multi.size, expected)
        assertTrue(expected > 0)
    }
}
