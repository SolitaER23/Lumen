package app.lumen.player.data

/**
 * 多碟分页的纯逻辑：分组、取某碟曲目、算某碟在整张专辑里的**绝对起始下标**。
 *
 * 抽出来是为了能单测——旧版这块出过"切碟后播放错曲"的问题，
 * 绝对下标必须由这里统一算。
 */
fun discsOf(tracks: List<Track>): List<Int> =
    tracks.map { it.disc }.distinct().sorted()

fun tracksOfDisc(tracks: List<Track>, disc: Int): List<Track> =
    tracks.filter { it.disc == disc }

fun absoluteStart(tracks: List<Track>, disc: Int): Int =
    tracks.indexOfFirst { it.disc == disc }.coerceAtLeast(0)

/** 当前曲目所在碟的下标（0-based）；找不到返回 0 */
fun discIndexOfTrack(tracks: List<Track>, trackId: String?): Int {
    val disc = tracks.firstOrNull { it.id == trackId }?.disc ?: return 0
    return discsOf(tracks).indexOf(disc).coerceAtLeast(0)
}
