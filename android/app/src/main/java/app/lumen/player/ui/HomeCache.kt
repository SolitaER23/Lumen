package app.lumen.player.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lumen.player.data.Album
import app.lumen.player.data.Artist
import app.lumen.player.data.Track

/** 首页各 Tab 的内存缓存（进程内有效）。 */
class HomeCache {
    var artists by mutableStateOf<List<Artist>>(emptyList())
    var newest by mutableStateOf<List<Album>>(emptyList())
    var recent by mutableStateOf<List<Album>>(emptyList())
    var recentTracks by mutableStateOf<List<Track>>(emptyList())
}
