package app.lumen.player.ui

import app.lumen.player.util.L10n
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import app.lumen.player.data.Album
import app.lumen.player.data.Artist
import app.lumen.player.data.SubsonicClient
import app.lumen.player.data.Track
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 搜索（M3 Expressive）：
 * 顶栏 + 胶囊搜索框 + 主标签页；结果用 ListItem，分区标题沿用 [LumenSectionHeader]。
 * 输入停顿 300ms 再请求 `search3`，失败可原地重试。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    client: SubsonicClient,
    coverUrl: (String) -> String,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
) {
    androidx.activity.compose.BackHandler { onBack() }
    var query by remember { mutableStateOf("") }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var songs by remember { mutableStateOf<List<Track>>(emptyList()) }
    // 0=全部 1=曲目 2=专辑 3=艺人
    var tab by remember { mutableIntStateOf(0) }
    var retryNonce by remember { mutableIntStateOf(0) }
    val histCtx = LocalContext.current
    val histPrefs = remember {
        histCtx.getSharedPreferences("lumen_search_hist", android.content.Context.MODE_PRIVATE)
    }
    var history by remember {
        mutableStateOf(
            histPrefs.getString("items", "")?.split("\n")?.filter { it.isNotBlank() }?.take(8) ?: emptyList(),
        )
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(query, retryNonce) {
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

    val haptics = rememberLumenHaptics()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { LumenTopBar(title = L10n.t("搜索"), onBack = onBack) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).imePadding()) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(L10n.t("搜索曲目、专辑、艺人")) },
                leadingIcon = { Icon(YoinSymbols.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        androidx.compose.material3.IconButton(
                            onClick = {
                                haptics.tap()
                                query = ""
                            },
                        ) {
                            Icon(YoinSymbols.Close, contentDescription = L10n.t("清空"))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LumenSizes.screenH, vertical = LumenSizes.xs)
                    .focusRequester(focus),
            )

            when {
                loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    LumenLoading()
                }
                error != null -> LumenErrorState(
                    message = error!!,
                    retryLabel = L10n.t("重试"),
                    onRetry = { retryNonce += 1 },
                    modifier = Modifier.padding(top = LumenSizes.m),
                )
                else -> {
                    if (query.isBlank() && history.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = LumenSizes.screenH, vertical = LumenSizes.xs)) {
                            Text(
                                L10n.t("最近搜索"),
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = LumenSizes.xxs, bottom = LumenSizes.xs),
                            )
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(LumenSizes.xs),
                                verticalArrangement = Arrangement.spacedBy(LumenSizes.xxs),
                            ) {
                                history.forEach { h ->
                                    SuggestionChip(
                                        onClick = {
                                            haptics.segment()
                                            query = h
                                        },
                                        label = { Text(h) },
                                    )
                                }
                            }
                        }
                    }

                    if (query.isNotBlank() && !loading && error == null &&
                        artists.isEmpty() && albums.isEmpty() && songs.isEmpty()
                    ) {
                        LumenEmptyState(
                            title = L10n.t("没有找到结果"),
                            hint = L10n.t("换个关键词，或只输入前几个字"),
                            modifier = Modifier.padding(top = LumenSizes.xl),
                        )
                    }

                    LumenSegmentedTabs(
                        labels = listOf(L10n.t("全部"), L10n.t("曲目"), L10n.t("专辑"), L10n.t("艺人")),
                        selected = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.padding(horizontal = LumenSizes.screenH, vertical = LumenSizes.xxs),
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LumenSizes.contentBottom),
                    ) {

                        if ((tab == 0 || tab == 3) && artists.isNotEmpty()) {
                            item { LumenSectionHeader(L10n.t("艺人")) }
                            items(artists, key = { "a-" + it.id }, contentType = { "artist" }) { artist ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            artist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            L10n.tf("${artist.albumCount} 张专辑", "${artist.albumCount} albums"),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            YoinSymbols.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier
                                        .padding(horizontal = LumenSizes.xs)
                                        .clickable {
                                            haptics.navigate()
                                            onOpenArtist(artist)
                                        },
                                )
                            }
                        }

                        if ((tab == 0 || tab == 1) && songs.isNotEmpty()) {
                            item { LumenSectionHeader(L10n.t("曲目")) }
                            itemsIndexed(songs, key = { _, song -> "t-" + song.id }, contentType = { _, _ -> "song" }) { idx, song ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            song.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            song.artist + (if (song.album.isBlank()) "" else " · " + song.album),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    trailingContent = {
                                        Text(
                                            "%d:%02d".format(song.seconds / 60, song.seconds % 60),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            YoinSymbols.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier
                                        .padding(horizontal = LumenSizes.xs)
                                        .clickable {
                                            haptics.confirm()
                                            onPlayTrack(songs, idx)
                                        },
                                )
                            }
                        }

                        if ((tab == 0 || tab == 2) && albums.isNotEmpty()) {
                            item { LumenSectionHeader(L10n.t("专辑")) }
                            items(albums, key = { "b-" + it.id }, contentType = { "album" }) { album ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            album.name,
                                            style = MaterialTheme.typography.titleSmallEmphasized,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            album.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingContent = {
                                        AsyncImage(
                                            model = coverUrl(album.coverId),
                                            contentDescription = album.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(48.dp),
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier
                                        .padding(horizontal = LumenSizes.xs)
                                        .clickable {
                                            haptics.navigate()
                                            onOpenAlbum(album)
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
