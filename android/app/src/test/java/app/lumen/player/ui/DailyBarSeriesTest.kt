package app.lumen.player.ui

import app.lumen.player.data.StatsDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长区间柱状图分桶的回归测试。
 *
 * 核心约束：**合并前后播放总量必须一致**（不能为了好看丢数据），
 * 并且小范围（7/30 天）保持一天一根。
 */
class DailyBarSeriesTest {

    private fun days(n: Int): List<StatsDay> =
        (0 until n).map { StatsDay(date = "2026-01-%02d".format(it % 28 + 1), count = it + 1) }

    @Test
    fun shortRangesKeepOneBarPerDay() {
        for (n in listOf(1, 7, 30)) {
            val series = dailyBarSeries(days(n))
            assertEquals(n, series.counts.size)
            assertEquals(1, series.daysPerBar)
        }
    }

    @Test
    fun emptyRangeIsEmpty() {
        val series = dailyBarSeries(emptyList())
        assertTrue(series.counts.isEmpty())
        assertEquals(1, series.daysPerBar)
    }

    @Test
    fun ninetyDaysMergePairs() {
        val input = days(90)
        val series = dailyBarSeries(input)
        assertEquals(2, series.daysPerBar)
        assertEquals(45, series.counts.size)
        assertEquals(input.sumOf { it.count }, series.counts.sum())
    }

    @Test
    fun yearMergesToAboutFiftySixBarsWithoutLosingPlays() {
        val input = days(365)
        val series = dailyBarSeries(input)
        assertTrue("bars should stay readable: " + series.counts.size, series.counts.size <= 56)
        assertEquals(input.sumOf { it.count }, series.counts.sum())
    }

    @Test
    fun maxBarsOneCollapsesWholeRange() {
        val input = days(12)
        val series = dailyBarSeries(input, maxBars = 1)
        assertEquals(1, series.counts.size)
        assertEquals(input.size, series.daysPerBar)
        assertEquals(input.sumOf { it.count }, series.counts[0])
    }

    @Test
    fun partialLastBucketStillCounts() {
        // 100 天 / 56 根 -> 每根 2 天，最后剩 0 天（整除）；换成 113 天会余 1 天
        val input = days(113)
        val series = dailyBarSeries(input, maxBars = 56)
        assertEquals(3, series.daysPerBar)
        assertEquals(input.sumOf { it.count }, series.counts.sum())
        assertEquals(38, series.counts.size)
    }
}
