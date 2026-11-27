package app.lumen.player.ui

/**
 * 专辑页主按钮的语义。
 *
 * 按钮显示什么、点击做什么，必须由「播放器现在在放什么」决定，而不是页面自己攒一个
 * boolean：页面离开再回来、切歌、在别的页面暂停，都会让本地状态和播放器对不上。
 *
 * - [START_ALBUM]：当前播放的不是这张专辑（或没有播放内容）→ 从本页选中的碟开始播放；
 * - [PAUSE]：这张专辑的曲目正在播放 → 暂停；
 * - [RESUME]：这张专辑的曲目已暂停（含冷启动恢复出来的队列）→ 从原进度继续，
 *   不重建队列，所以不会「暂停后再点一次又从 0:00 重播」。
 *
 * 抽成纯函数是为了单测：按钮文案、图标、点击行为都从这里推导，三者不会各写一份判断。
 */
enum class AlbumPlayAction {
    START_ALBUM,
    PAUSE,
    RESUME,
}

fun albumPlayAction(isCurrentAlbum: Boolean, isPlaying: Boolean): AlbumPlayAction = when {
    !isCurrentAlbum -> AlbumPlayAction.START_ALBUM
    isPlaying -> AlbumPlayAction.PAUSE
    else -> AlbumPlayAction.RESUME
}
