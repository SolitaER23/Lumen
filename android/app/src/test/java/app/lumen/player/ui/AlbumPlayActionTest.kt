package app.lumen.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 专辑页主按钮的回归测试。
 *
 * 这里钉的是「按钮该做什么」而不是 Compose 渲染：
 * - 本专辑在播 → 暂停；
 * - 本专辑暂停 → 继续（不能退化成从头播放）；
 * - 放的是别的专辑 / 什么都没放 → 播放本专辑。
 */
class AlbumPlayActionTest {

    @Test
    fun currentAlbumPlayingPausesOnTap() {
        assertEquals(
            AlbumPlayAction.PAUSE,
            albumPlayAction(isCurrentAlbum = true, isPlaying = true),
        )
    }

    @Test
    fun currentAlbumPausedResumesInsteadOfRestarting() {
        assertEquals(
            AlbumPlayAction.RESUME,
            albumPlayAction(isCurrentAlbum = true, isPlaying = false),
        )
    }

    @Test
    fun otherAlbumAlwaysStartsPlayback() {
        // 别的专辑正在播：本页按钮仍是「播放」，点了要切到本专辑，而不是暂停别人的播放
        assertEquals(
            AlbumPlayAction.START_ALBUM,
            albumPlayAction(isCurrentAlbum = false, isPlaying = true),
        )
        assertEquals(
            AlbumPlayAction.START_ALBUM,
            albumPlayAction(isCurrentAlbum = false, isPlaying = false),
        )
    }

    @Test
    fun everyCombinationIsMapped() {
        // 四种组合都必须有明确语义，避免以后新增状态时静默落到默认分支
        for (current in listOf(true, false)) {
            for (playing in listOf(true, false)) {
                val action = albumPlayAction(current, playing)
                assertEquals(
                    "current=$current playing=$playing",
                    when {
                        !current -> AlbumPlayAction.START_ALBUM
                        playing -> AlbumPlayAction.PAUSE
                        else -> AlbumPlayAction.RESUME
                    },
                    action,
                )
            }
        }
    }
}
