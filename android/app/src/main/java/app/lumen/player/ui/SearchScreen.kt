package app.lumen.player.ui

import app.lumen.player.util.L10n
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import app.lumen.player.data.Album
import app.lumen.player.data.Artist
import app.lumen.player.data.SubsonicClient
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import app.lumen.player.data.Track
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Surface

/** 搜索：艺人 + 专辑，输入停顿 300ms 后请求 `search3`。 */
@Composable
fun SearchScreen(
    client: SubsonicClient,
    coverUrl: (String) -> String,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
) {
    // 从搜索图标的圆形边界展开：进入时圆角由 999dp 收到 0
    var searchCorner by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(999f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.animation.core.animate(
            999f, 0f,
            animationSpec = androidx.compose.animation.core.tween(320),
        ) { v, _ -> searchCorner = v }
    }
    // 页面内返回按钮 / 系统返回统一走同一入口
    androidx.activity.compose.BackHandler { onBack() }
    var query by remember { mutableStateOf("") }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var songs by remember { mutableStateOf<List<Track>>(emptyList()) }
    // 0=全部 1=曲目 2=专辑 3=艺人
    var tab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val histCtx = androidx.compose.ui.platform.LocalContext.current
    val histPrefs = remember { histCtx.getSharedPreferences("lumen_search_hist", android.content.Context.MODE_PRIVATE) }
    var history by remember {
      androidx.compose.runtime.mutableStateOf(
        histPrefs.getString("items", "")?.split("\n")?.filter { it.isNotBlank() }?.take(8) ?: emptyList()
      )
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 1) {
            artists = emptyList()
            albums = emptyList()
            songs = emptyList()
            error = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        delay(300)
        try {
            val (a, al, sg) = withContext(Dispatchers.IO) { client.search(q) }
            artists = a
            albums = al
            songs = sg
            if (q.length >= 2 && (a.isNotEmpty() || al.isNotEmpty() || sg.isNotEmpty())) {
                history = (listOf(q) + history.filter { it != q }).take(8)
                histPrefs.edit().putString("items", history.joinToString("\n")).apply()
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(searchCorner.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(L10n.t("搜索曲目、专辑、艺人")) },
                singleLine = true,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
        }
        // 结果出现时淡入，避免"空 → 满"的硬切（纯修饰符改动，不动布局结构）
        val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (loading) 0f else 1f,
            animationSpec = androidx.compose.animation.core.tween(160),
            label = "search-fade",
        )
        if (query.isBlank() && history.isNotEmpty()) {
            // search-history：输入为空时展示最近搜索，点击即填回
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    L10n.t("最近搜索"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    history.forEach { h ->
                        Surface(
                            onClick = { query = h },
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                h,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha },
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ) {
                    listOf(L10n.t("全部"), L10n.t("曲目"), L10n.t("专辑"), L10n.t("艺人")).forEachIndexed { i, label ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = { Text(label, fontSize = 13.sp) },
                        )
                    }
                }
            }

            if ((tab == 0 || tab == 3) && artists.isNotEmpty()) {
                item {
                    Text(
                        L10n.t("艺人"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(artists, key = { "a-" + it.id }) { artist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onOpenArtist(artist) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 艺人结果不显示头像，只显示名字（与艺人页保持一致）
                        Text(
                            artist.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if ((tab == 0 || tab == 1) && songs.isNotEmpty()) {
                item {
                    Text(
                        L10n.t("曲目"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(songs, key = { "t-" + it.id }) { song ->
                    val idx = songs.indexOf(song)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onPlayTrack(songs, idx) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.artist + (if (song.album.isBlank()) "" else " · " + song.album),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "%d:%02d".format(song.seconds / 60, song.seconds % 60),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if ((tab == 0 || tab == 2) && albums.isNotEmpty()) {
                item {
                    Text(
                        L10n.t("专辑"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(albums, key = { "b-" + it.id }) { album ->
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
                                album.artist,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
