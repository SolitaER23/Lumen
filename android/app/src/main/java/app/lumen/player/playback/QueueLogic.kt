package app.lumen.player.playback

/** 队列索引的纯逻辑（不依赖 ExoPlayer，方便单测）。 */
fun nextIndex(size: Int, index: Int): Int {
    if (size <= 0) return 0
    if (index < 0) return 0
    return (index + 1).coerceAtMost(size - 1)
}

/**
 * 上一首：播放超过 3 秒时先回到本曲开头（和主流播放器一致），
 * 否则回上一首；已经在第一首就回到 0。
 */
fun prevIndex(size: Int, index: Int, positionMs: Long, restartAfterMs: Long = 3000L): Int {
    if (index <= 0) return 0
    return if (positionMs > restartAfterMs) index else index - 1
}