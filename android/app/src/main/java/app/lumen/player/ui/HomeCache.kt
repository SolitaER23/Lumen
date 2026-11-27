package app.lumen.player.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lumen.player.data.Artist

/** 首页「艺人」标签的内存缓存（进程内有效）。 */
class HomeCache {
    var artists by mutableStateOf<List<Artist>>(emptyList())
}
