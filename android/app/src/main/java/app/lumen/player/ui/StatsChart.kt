package app.lumen.player.ui

import app.lumen.player.data.StatsDay
import kotlin.math.ceil

/**
 * 每日柱状图的分桶结果。
 *
 * 选择「1 年 / 全部」时原始天数可达 365/3650，一天一根柱子会细到看不见；
 * 超过 [maxBars] 就按连续的若干天合并成一根柱子，总量不变，趋势仍可读。
 *
 * 抽成纯函数是为了单测：合并不能丢播放量，边界（0/1/整除/有余数）都要钉住。
 */
internal data class DailyBarSeries(
    val counts: List<Int>,
    /** 每根柱子合并了多少天；1 表示一天一根 */
    val daysPerBar: Int,
)

internal fun dailyBarSeries(days: List<StatsDay>, maxBars: Int = 56): DailyBarSeries {
    if (days.isEmpty()) return DailyBarSeries(emptyList(), 1)
    val perBar = ceil(days.size.toFloat() / maxBars.coerceAtLeast(1).toFloat())
        .toInt()
        .coerceAtLeast(1)
    return DailyBarSeries(
        counts = days.chunked(perBar) { chunk -> chunk.sumOf { it.count } },
        daysPerBar = perBar,
    )
}
