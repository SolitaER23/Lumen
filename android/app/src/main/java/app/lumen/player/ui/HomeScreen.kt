package app.lumen.player.ui

import app.lumen.player.util.L10n
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.HorizontalPager
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.HorizontalPager
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.lumen.player.data.Album
import app.lumen.player.data.AlbumCache
import app.lumen.player.data.LyricLine
import app.lumen.player.data.Ratings
import app.lumen.player.data.Artist
import app.lumen.player.data.ServerConfig
import app.lumen.player.data.SettingsStore
import app.lumen.player.data.SubsonicClient
import app.lumen.player.data.Track
import app.lumen.player.playback.Playback
import app.lumen.player.playback.AlbumDownload
import app.lumen.player.playback.Downloads
import app.lumen.player.playback.MediaCache
import app.lumen.player.playback.PlaybackEngine
import app.lumen.player.playback.PlaybackService
import app.lumen.player.ui.AlbumScreen
import app.lumen.player.ui.ArtistScreen
import app.lumen.player.ui.SearchScreen
import app.lumen.player.ui.FallbackScheme
import app.lumen.player.ui.LicensesScreen
import app.lumen.player.ui.MoodScreen
import app.lumen.player.ui.HomeCard
import app.lumen.player.ui.HomeText
import app.lumen.player.ui.HomeSub
import app.lumen.player.ui.HomeAccent
import app.lumen.player.ui.YoinBar
import app.lumen.player.ui.HomeCache
import app.lumen.player.ui.LoginScreen
import app.lumen.player.ui.lumenTypography
import app.lumen.player.ui.PlayingScreen
import app.lumen.player.ui.DownloadsScreen
import app.lumen.player.ui.SettingsScreen
import app.lumen.player.ui.schemeFromCover
import com.gpo.yoin.symbols.YoinSymbols
import com.gpo.yoin.symbols.rememberPlayPauseSymbolPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    client: SubsonicClient,
    albums: List<Album>,
    loading: Boolean,
    error: String?,
    coverUrl: (String) -> String,
    onOpen: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onRetry: () -> Unit,
    starred: List<Track> = emptyList(),
    onPlayStarred: (Int) -> Unit = {},
    tab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    ratingOf: (String) -> Float = { 0f },
    downloads: List<AlbumDownload> = emptyList(),
    onPlayTrack: (Track) -> Unit = {},
    current: Track? = null,
    playing: Boolean = false,
    positionMs: Long = 0,
    durationMs: Long = 0,
    onToggle: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    cache: HomeCache = remember { HomeCache() },
    libraryVersion: Int = 0,
    coverShared: @Composable (String) -> Modifier = { Modifier },
) {
    val ctx = LocalContext.current
    var sortIndex by remember(tab) { mutableIntStateOf(0) }
    var showSort by remember { mutableStateOf(false) }
    // tab 由上层持有，返回首页时能保持原来的标签
    val artists = cache.artists
    val newest = cache.newest
    val recentTracks = cache.recentTracks

    var tabLoading by remember { mutableStateOf(false) }
    var tabError by remember { mutableStateOf<String?>(null) }

    // 启动就拉一次艺人，让标签上的数量一开始就是对的
    LaunchedEffect(Unit) {
        if (artists.isEmpty()) {
            runCatching { cache.artists = withContext(Dispatchers.IO) { client.artists() } }
        }
    }


    LaunchedEffect(tab) {
        if (tab == 0) return@LaunchedEffect
        val firstLoad = (tab == 1 && artists.isEmpty()) ||
            (tab == 2 && (newest.isEmpty() || recentTracks.isEmpty()))
        if (firstLoad) tabLoading = true
        tabError = null
        try {
            when (tab) {
                1 -> if (artists.isEmpty()) cache.artists = withContext(Dispatchers.IO) { client.artists() }
                2 -> {
                    // 每次进入/有新播放都刷新；最近播放用本机单曲历史
                    cache.newest = withContext(Dispatchers.IO) { client.newestAlbums() }
                    cache.recentTracks = withContext(Dispatchers.IO) {
                        app.lumen.player.data.PlayHistory.load(ctx)
                    }
                }
            }
        } catch (e: Exception) {
            tabError = e.message ?: L10n.t("加载失败")
        } finally {
            tabLoading = false
        }
    }


    // 停留在「最近」时定时静默刷新，外部新增/播放后不需要重进页面
    LaunchedEffect(tab) {
        if (tab != 2) return@LaunchedEffect
        while (true) {
            delay(30_000L)
            runCatching {
                val n = withContext(Dispatchers.IO) { client.newestAlbums() }
                cache.newest = n
                cache.recentTracks = withContext(Dispatchers.IO) {
                    app.lumen.player.data.PlayHistory.load(ctx)
                }
            }
        }
    }

    LaunchedEffect(tab) { showSort = false }
    val sortOptions = when (tab) {
        1 -> listOf(L10n.t("名称"), L10n.t("专辑数量"))
        2 -> listOf(L10n.t("最近添加"), L10n.t("名称"), L10n.t("发行年份"))
        3 -> listOf(L10n.t("收藏顺序"), L10n.t("名称"), L10n.t("艺人"), L10n.t("我的评分"))
        else -> listOf(L10n.t("字母顺序"), L10n.t("发行年份"), L10n.t("歌曲数量"), L10n.t("艺人"))
    }
    val sortedAlbums = sortAlbums(albums, sortIndex)
    val sortedArtists = sortArtists(artists, sortIndex)
    val sortedNewest = sortAlbums(newest, sortIndex)

    val sortedStarred = sortStarred(starred, sortIndex, ratingOf)

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
        HomeHeader(
            selected = tab,
            title = when (tab) {
                1 -> L10n.t("艺人")
                2 -> L10n.t("最近")
                3 -> L10n.t("收藏")
                else -> L10n.t("专辑")
            },
            subtitle = when (tab) {
                1 -> "${artists.size} 位艺人 · ${sortOptions.getOrElse(sortIndex) { L10n.t("名称") }}"
                2 -> L10n.tf("最近添加 ${newest.size} · 最近播放 ${recentTracks.size}", "Recently added ${newest.size} · recently played ${recentTracks.size}")
                3 -> L10n.tf("${starred.size} 首单曲 · ${sortOptions.getOrElse(sortIndex) { L10n.t("收藏顺序") }}", "${starred.size} tracks · ${sortOptions.getOrElse(sortIndex) { L10n.t("收藏顺序") }}")
                else -> L10n.tf("${albums.size} 张专辑 · ${sortOptions.getOrElse(sortIndex) { L10n.t("字母顺序") }}", "${albums.size} albums · ${sortOptions.getOrElse(sortIndex) { L10n.t("字母顺序") }}")
            },
            onTab = onTabChange,
            onSettings = onSettings,
            onSort = { showSort = true },
            onSearch = onSearch,
            onStats = onOpenStats,
        )

        val activeError = if (tab == 0) error else tabError
        when {
            tab == 0 && loading && albums.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            activeError != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(activeError, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) { Text(L10n.t("重试")) }
            }
            else -> {
                val pagerState = rememberPagerState(pageCount = { 4 })
                // 点击标签：直接跳页（不做动画），快速连点不会互相打断
                LaunchedEffect(tab) {
                    if (pagerState.currentPage != tab) {
                        pagerState.scrollToPage(tab)
                    }
                }
                // 滑动过程中就跟随 currentPage 回写标签；用 rememberUpdatedState 读最新 tab，避免闭包捕获旧值
                val latestTab = rememberUpdatedState(tab)
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }
                        .drop(1)
                        .collect { page -> if (page != latestTab.value) onTabChange(page) }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    when (page) {
                        3 -> StarredList(sortedStarred, onPlayStarred)
                        1 -> ArtistGrid(sortedArtists, onOpenArtist)
                        2 -> RecentList(
                            newest = sortedNewest,
                            recentTracks = recentTracks,
                            client = client,
                            downloads = downloads,
                            albums = albums,
                            coverUrl = coverUrl,
                            onOpen = onOpen,
                            onPlayTrack = onPlayTrack,
                            loading = tabLoading && newest.isEmpty(),
                        )
                        else -> Column(Modifier.fillMaxSize()) {
                            SectionRow(L10n.t("全部专辑"), L10n.tf("共 ${albums.size} 张", "${albums.size} total"))
                            Box(Modifier.weight(1f)) {
                                AlbumGrid(
                                    albums = sortedAlbums,
                                    coverUrl = coverUrl,
                                    onOpen = onOpen,
                                    coverShared = coverShared,
                                    progressOf = { al ->
                                        if (current?.albumId == al.id && durationMs > 0L) {
                                            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                        } else null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        if (showSort) {
            ModalBottomSheet(
                onDismissRequest = { showSort = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    when (tab) {
                        1 -> L10n.t("艺人排序")
                        2 -> L10n.t("最近排序")
                        3 -> L10n.t("收藏排序")
                        else -> L10n.t("专辑排序")
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(4.dp))
                sortOptions.forEachIndexed { i, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortIndex = i
                                showSort = false
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(
                                    if (i == sortIndex) 6.dp else 2.dp,
                                    if (i == sortIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            label,
                            color = if (i == sortIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = if (i == sortIndex) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

    }
}

private fun sortAlbums(list: List<Album>, mode: Int): List<Album> = when (mode) {
    1 -> list.sortedWith(compareByDescending<Album> { it.year }.thenBy { it.name.lowercase() })
    2 -> list.sortedByDescending { it.songCount }
    3 -> list.sortedBy { it.artist.lowercase() + "|" + it.name.lowercase() }
    else -> list.sortedBy { it.name.lowercase() }
}

private fun sortArtists(list: List<Artist>, mode: Int): List<Artist> =
    if (mode == 1) list.sortedByDescending { it.albumCount } else list.sortedBy { it.name.lowercase() }

private fun sortStarred(list: List<Track>, mode: Int, ratingOf: (String) -> Float): List<Track> = when (mode) {
    1 -> list.sortedBy { it.title.lowercase() }
    2 -> list.sortedBy { it.artist.lowercase() + "|" + it.title.lowercase() }
    3 -> list.sortedByDescending { ratingOf(it.id) }
    else -> list
}

@Composable
private fun StarredList(tracks: List<Track>, onPlay: (Int) -> Unit) {
    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(L10n.t("还没有收藏的单曲"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        items(tracks, key = { it.id }) { track ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPlay(tracks.indexOf(track)) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        listOfNotNull(track.artist.takeIf { it.isNotBlank() }, track.album.takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    YoinSymbols.StarFilled,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    selected: Int,
    title: String,
    subtitle: String,
    onTab: (Int) -> Unit,
    onSettings: () -> Unit,
    onSort: () -> Unit,
    onSearch: () -> Unit,
    onStats: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = HomeText, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = HomeSub, fontSize = 12.sp)
            }
            HomeCircleButton(onClick = onStats) {
                val c = HomeText
                Canvas(Modifier.size(20.dp)) {
                    val barW = size.width / 5f
                    val heights = floatArrayOf(0.45f, 0.75f, 1f)
                    heights.forEachIndexed { i, h ->
                        val left = i * barW * 1.6f
                        val barH = size.height * h
                        drawRoundRect(
                            color = c,
                            topLeft = androidx.compose.ui.geometry.Offset(left, size.height - barH),
                            size = androidx.compose.ui.geometry.Size(barW, barH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f),
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            HomeCircleButton(onClick = onSearch) {
                Icon(YoinSymbols.Search, contentDescription = L10n.t("搜索"), tint = HomeText, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            HomeCircleButton(onClick = onSort) {
                val sortColor = HomeText
                Canvas(Modifier.size(20.dp)) {
                    val stroke = 2.dp.toPx()
                    val w = size.width
                    val third = size.height / 4f
                    val cap = androidx.compose.ui.graphics.StrokeCap.Round
                    drawLine(start = androidx.compose.ui.geometry.Offset(0f, third), end = androidx.compose.ui.geometry.Offset(w, third), color = sortColor, strokeWidth = stroke, cap = cap)
                    drawLine(start = androidx.compose.ui.geometry.Offset(0f, third * 2), end = androidx.compose.ui.geometry.Offset(w * 0.7f, third * 2), color = sortColor, strokeWidth = stroke, cap = cap)
                    drawLine(start = androidx.compose.ui.geometry.Offset(0f, third * 3), end = androidx.compose.ui.geometry.Offset(w * 0.42f, third * 3), color = sortColor, strokeWidth = stroke, cap = cap)
                }
            }
            Spacer(Modifier.width(10.dp))
            HomeCircleButton(onClick = onSettings) {
                Icon(YoinSymbols.Settings, contentDescription = L10n.t("设置"), tint = HomeText, modifier = Modifier.size(21.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        // 胶囊分段标签：选中项白底黑字，其余灰字
        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(HomeCard)
                .padding(4.dp),
        ) {
            listOf(L10n.t("专辑"), L10n.t("艺人"), L10n.t("最近"), L10n.t("收藏")).forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (i == selected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onTab(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (i == selected) androidx.compose.ui.graphics.Color(0xFF202126) else HomeSub,
                        fontSize = 13.sp,
                        fontWeight = if (i == selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(HomeCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SectionRow(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = HomeText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(trailing, color = HomeSub, fontSize = 11.sp)
    }
}




// 首页中性色板（设计稿）：颜色交给封面，界面只用灰阶 + 一种强调色
private val HomeBg = androidx.compose.ui.graphics.Color(0xFF202126)

/** 首页专用的中性灰阶配色：颜色交给封面，界面只用灰阶 + 一种强调色。 */
internal val HomeScheme = androidx.compose.material3.darkColorScheme(
    primary = HomeAccent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = HomeCard,
    onPrimaryContainer = HomeText,
    secondary = HomeSub,
    onSecondary = HomeBg,
    tertiary = HomeSub,
    background = HomeBg,
    onBackground = HomeText,
    surface = HomeBg,
    onSurface = HomeText,
    surfaceVariant = HomeCard,
    onSurfaceVariant = HomeSub,
    surfaceContainer = HomeCard,
    surfaceContainerHigh = HomeCard,
    surfaceContainerHighest = HomeCard,
    surfaceContainerLow = HomeCard,
    outline = HomeSub,
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF2A2A30),
)

@Composable
private fun AlbumGrid(
    albums: List<Album>,
    coverUrl: (String) -> String,
    onOpen: (Album) -> Unit,
    coverShared: @Composable (String) -> Modifier,
    progressOf: (Album) -> Float? = { null },
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(
                album = album,
                url = coverUrl(album.coverId),
                coverModifier = coverShared(album.id),
                progress = progressOf(album),
                onClick = { onOpen(album) },
            )
        }
    }
}

@Composable
/** 艺人：只显示名字，不显示头像。 */
private fun ArtistGrid(
    artists: List<Artist>,
    onOpen: (Artist) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
    ) {
        items(artists, key = { it.id }) { artist ->
            Text(
                artist.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(artist) }
                    .padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun RecentTrackRow(
    track: Track,
    coverUrl: (String) -> String,
    onPlay: (Track) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onPlay(track) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverUrl(track.albumId),
            contentDescription = track.album,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                track.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            YoinSymbols.PlayFilled,
            contentDescription = L10n.t("播放"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RecentEmpty(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentList(
    client: SubsonicClient,
    newest: List<Album>,
    recentTracks: List<Track>,
    downloads: List<AlbumDownload>,
    albums: List<Album>,
    coverUrl: (String) -> String,
    onOpen: (Album) -> Unit,
    onPlayTrack: (Track) -> Unit,
    loading: Boolean,
) {
    // 单曲下载：按 trackId 拉一次元数据（封面/标题/艺人），供卡片显示与播放
    val singleTracks = remember { mutableStateMapOf<String, Track>() }
    val singles = downloads.filter { it.isSingle }
    LaunchedEffect(downloads) {
        singles.forEach { d ->
            val id = d.key.removePrefix("track:")
            if (id.isNotBlank() && id !in singleTracks) {
                val remote = runCatching { withContext(Dispatchers.IO) { client.song(id) } }.getOrNull()
                singleTracks[id] = remote ?: Track(
                    id = id,
                    title = d.name.substringBefore(" · "),
                    artist = d.artist,
                    albumId = d.albumId,
                    album = d.albumName,
                )
            }
        }
    }
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val ctx = LocalContext.current
    // 固定顺序：最近播放、最近添加、已下载
    val order = remember { listOf(1, 0, 2) }
    var selected by rememberSaveable { mutableIntStateOf(order.firstOrNull() ?: 0) }

    LaunchedEffect(order) {
        if (selected !in order) selected = order.firstOrNull() ?: 0
    }

    val sections = mapOf(
        0 to L10n.t("最近添加"),
        1 to L10n.t("最近播放"),
        2 to L10n.t("已下载"),
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        L10n.t("最近"),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                                    }
            }

            // 三个分区横向并排，点选后下方展示该分区原有内容
            item(key = "switch") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    order.forEach { section ->
                        val count = when (section) {
                            0 -> newest.size
                            1 -> recentTracks.size
                            else -> downloads.size
                        }
                        val active = section == selected
                        val container = if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer
                        val titleColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                        val countColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(container)
                                .clickable { selected = section }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            Text(
                                sections[section] ?: "",
                                color = titleColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    count.toString(),
                                    color = countColor,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Light,
                                )
                                Text(
                                    when (section) {
                                        2 -> L10n.t(" 项")
                                        1 -> L10n.t(" 首")
                                        else -> L10n.t(" 张")
                                    },
                                    color = titleColor,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            when (selected) {
                0 -> if (newest.isEmpty()) {
                    item(key = "nempty") { RecentEmpty(L10n.t("暂无最近添加的专辑")) }
                } else {
                    items(rowsOf(newest), key = { "n${it.firstOrNull()?.id}" }) { row ->
                        HorizontalAlbumRow(row, coverUrl, onOpen)
                    }
                }
                1 -> if (recentTracks.isEmpty()) {
                    item(key = "rempty") { RecentEmpty(L10n.t("暂无最近播放的歌曲")) }
                } else {
                    items(recentTracks, key = { "r${it.id}" }) { track ->
                        RecentTrackRow(track, coverUrl, onPlayTrack)
                    }
                }
                else -> if (downloads.isEmpty()) {
                    item(key = "dempty") { RecentEmpty(L10n.t("还没有下载内容")) }
                } else {
                    items(rowsOfDownloads(downloads), key = { "d${it.firstOrNull()?.key}" }) { row ->
                        HorizontalDownloadRow(row, albums, singleTracks, coverUrl, onOpen, onPlayTrack)
                    }
                }
            }
        }


    }
}

private fun loadRecentOrder(ctx: android.content.Context): List<Int> {
    val raw = ctx.getSharedPreferences("lumen_recent", android.content.Context.MODE_PRIVATE)
        .getString("order", null) ?: return listOf(0, 1, 2)
    val parsed = raw.split(",").mapNotNull { it.toIntOrNull() }.filter { it in 0..2 }.distinct()
    return if (parsed.size == 3) parsed else listOf(0, 1, 2)
}

private fun rowsOfDownloads(downloads: List<AlbumDownload>): List<List<AlbumDownload>> {
    if (downloads.isEmpty()) return emptyList()
    if (downloads.size == 1) return listOf(downloads)
    val half = (downloads.size + 1) / 2
    return listOf(downloads.take(half), downloads.drop(half)).filter { it.isNotEmpty() }
}



@Composable
private fun HorizontalDownloadRow(
    row: List<AlbumDownload>,
    albums: List<Album>,
    tracks: Map<String, Track>,
    coverUrl: (String) -> String,
    onOpen: (Album) -> Unit,
    onPlayTrack: (Track) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
    ) {
        items(row, key = { it.key }) { item ->
            val album = albums.firstOrNull { "album:${it.id}" == item.key }
            if (album != null) {
                AlbumCard(album, coverUrl(album.coverId), Modifier.width(148.dp)) { onOpen(album) }
            } else {
                val track = tracks[item.key.removePrefix("track:")]
                val title = track?.title?.takeIf { it.isNotBlank() } ?: item.name.substringBefore(" · ")
                val subtitle = listOfNotNull(
                    track?.artist?.takeIf { it.isNotBlank() },
                    track?.album?.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { L10n.t("单曲") }
                Column(
                    Modifier
                        .width(148.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = track != null) { track?.let(onPlayTrack) },
                ) {
                    Box(
                        Modifier
                            .width(148.dp)
                            .height(148.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (track != null && track.albumId.isNotBlank()) {
                            AsyncImage(
                                model = coverUrl(track.albumId),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                YoinSymbols.DownloadDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        // 右下角小徽标：单曲下载
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                YoinSymbols.DownloadDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 把列表切成两行（每行左右滑动），第一行拿前半、第二行拿后半 */
private fun rowsOf(albums: List<Album>): List<List<Album>> {
    if (albums.isEmpty()) return emptyList()
    if (albums.size == 1) return listOf(albums)
    val half = (albums.size + 1) / 2
    return listOf(albums.take(half), albums.drop(half)).filter { it.isNotEmpty() }
}

@Composable
private fun HorizontalAlbumRow(row: List<Album>, coverUrl: (String) -> String, onOpen: (Album) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
    ) {
        items(row, key = { it.id }) { album ->
            AlbumCard(album, coverUrl(album.coverId), Modifier.width(148.dp)) { onOpen(album) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 20.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/** 竖排艺人名：内容比卡片高时自动上下往复滚动。 */
@Composable
private fun VerticalArtistName(text: String, modifier: Modifier = Modifier) {
    val chars = text.take(12).toCharArray().joinToString("\n")
    var boxHeight by remember { mutableIntStateOf(0) }
    var textHeight by remember { mutableIntStateOf(0) }
    val overflow = (textHeight - boxHeight).coerceAtLeast(0)

    // 只有真的溢出才启动无限动画，否则静止（否则每张卡都在跑一个时钟，明显耗帧）
    val offset = if (overflow > 0) {
        val transition = rememberInfiniteTransition(label = "artist")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = -overflow.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = (overflow * 45).coerceAtLeast(1200),
                    easing = androidx.compose.animation.core.LinearEasing,
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "artistScroll",
        )
        v
    } else {
        0f
    }

    Box(
        modifier.onSizeChanged { boxHeight = it.height }.clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            chars,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            // 不受父级高度限制地测量，才能算出真实溢出量
            modifier = Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .onSizeChanged { textHeight = it.height }
                .graphicsLayer { translationY = offset },
        )
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    url: String,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(HomeCard),
        ) {
            AsyncImage(
                model = url,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = coverModifier.fillMaxSize(),
            )
            // 播放中的专辑：封面下沿 3dp 进度线（不叠加任何角标/描边）
            if (progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(HomeAccent),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            album.name,
            color = HomeText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            album.artist,
            color = HomeSub,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
