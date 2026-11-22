package com.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.app.data.Album
import com.example.app.data.Artist
import com.example.app.data.SubsonicClient
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 艺人页：艺人名 + 该艺人的专辑列表。 */
@Composable
fun ArtistScreen(
    client: SubsonicClient,
    artist: Artist,
    coverUrl: (String) -> String,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
) {
    // 内存缓存：返回手势时上一页会被实时重新组合，命中缓存可避免出现一帧加载态
    var albums by remember(artist.id) {
        mutableStateOf(ArtistAlbumCache.get(artist.id))
    }
    var error by remember(artist.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(artist.id) {
        if (albums != null) return@LaunchedEffect
        try {
            val loaded = withContext(Dispatchers.IO) { client.artistDetail(artist.id)?.second ?: emptyList() }
            albums = loaded
            ArtistAlbumCache.put(artist.id, loaded)
        } catch (e: Exception) {
            error = e.message
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(YoinSymbols.Back, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("艺人", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        item {
            // 不显示艺人头像，只留名字与张数
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(artist.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Light)
                Text("${artist.albumCount} 张专辑", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
        when {
            error != null -> item {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
            }
            albums == null -> item {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> items(albums!!, key = { it.id }) { album ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onOpenAlbum(album) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = coverUrl(album.coverId),
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            album.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${album.year} · ${album.songCount} 首",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 艺人专辑列表的内存缓存，避免返回/重新进入时出现加载态闪烁。 */
object ArtistAlbumCache {
    private val map = HashMap<String, List<Album>>()
    fun get(artistId: String): List<Album>? = map[artistId]
    fun put(artistId: String, albums: List<Album>) {
        map[artistId] = albums
    }
}