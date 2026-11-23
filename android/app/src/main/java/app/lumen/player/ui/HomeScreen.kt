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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.shadow
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
import coil3.compose.AsyncImage
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
import app.lumen.player.ui.HomeCard
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
    analyzing: Boolean = false,
    analysisDone: Int = 0,
    analysisTotal: Int = 0,
    analysisPhase: String = "",
    analysisTrack: String = "",
    analysisMode: String = "",
    analysisModeDetail: String = "",
    downloadedIds: Set<String> = emptySet(),
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
    onOpenRadio: () -> Unit = {},
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
            analyzing = analyzing,
            analysisDone = analysisDone,
            analysisTotal = analysisTotal,
            analysisPhase = analysisPhase,
            analysisTrack = analysisTrack,
            analysisMode = analysisMode,
            analysisModeDetail = analysisModeDetail,
            onTab = onTabChange,
            onOpenRadio = onOpenRadio,
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
                // 点击标签：走 pager 的横向滑动（shared axis X），连点会被新动画接管
                LaunchedEffect(tab) {
                    if (pagerState.currentPage != tab) {
                        pagerState.animateScrollToPage(
                            tab,
                            animationSpec = androidx.compose.animation.core.tween(
                                260,
                                easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f),
                            ),
                        )
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
                        3 -> StarredList(sortedStarred, downloadedIds, onPlayStarred)
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
                            downloadedIds = downloadedIds,
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
private fun StarredList(tracks: List<Track>, downloadedIds: Set<String> = emptySet(), onPlay: (Int) -> Unit) {
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
                if (track.id in downloadedIds) {
                    Icon(
                        YoinSymbols.DownloadDone,
                        contentDescription = L10n.t("已下载"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
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
    analyzing: Boolean = false,
    analysisDone: Int = 0,
    analysisTotal: Int = 0,
    analysisPhase: String = "",
    analysisTrack: String = "",
    analysisMode: String = "",
    analysisModeDetail: String = "",
    onTab: (Int) -> Unit,
    onOpenRadio: () -> Unit = {},
    onSettings: () -> Unit,
    onSort: () -> Unit,
    onSearch: () -> Unit,
    onStats: () -> Unit = {},
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                    if (analyzing) {
                        Spacer(Modifier.width(8.dp))
                        AnalysisChip(
                            done = analysisDone,
                            total = analysisTotal,
                            phase = analysisPhase,
                            track = analysisTrack,
                            mode = analysisMode,
                            modeDetail = analysisModeDetail,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            HomeCircleButton(onClick = onStats) {
                val c = MaterialTheme.colorScheme.onBackground
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
                Icon(YoinSymbols.Search, contentDescription = L10n.t("搜索"), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            HomeCircleButton(onClick = onSort) {
                val sortColor = MaterialTheme.colorScheme.onBackground
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
                Icon(YoinSymbols.Settings, contentDescription = L10n.t("设置"), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(21.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        // 胶囊分段标签：选中项白底黑字，其余灰字
        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
        ) {
            listOf(L10n.t("专辑"), L10n.t("艺人"), L10n.t("最近"), L10n.t("收藏")).forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            if (i == selected) MaterialTheme.colorScheme.primaryContainer
                            else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .combinedClickable(
                            onClick = { onTab(i) },
                            onLongClick = {
                                if (i == 0) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onOpenRadio()
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (i == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
    // P2：按下时轻微缩放，反馈更"实"，不再只有 ripple
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "circle-press",
    )
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SectionRow(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}




// 首页中性色板（设计稿）：颜色交给封面，界面只用灰阶 + 一种强调色
internal val HomeBg = androidx.compose.ui.graphics.Color(0xFF202126)

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
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    // 解码尺寸对齐：两列、左右各 20dp、列间距 24dp
    val cellPx = with(density) { (((screenW - 64) / 2f).coerceAtLeast(120f).dp).roundToPx() }

    // 分页渲染：首屏只组合 24 张，滚动接近底部再追加，冷启动更快
    // F4：分页数量必须可恢复，否则返回首页会退回 24 张并跳回顶部
    var shown by androidx.compose.runtime.saveable.rememberSaveable(albums.size) {
        androidx.compose.runtime.mutableIntStateOf(minOf(albums.size, 24))
    }
    androidx.compose.runtime.LaunchedEffect(gridState.layoutInfo.visibleItemsInfo.size, shown) {
        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (last >= shown - 4 && shown < albums.size) {
            shown = minOf(albums.size, shown + 24)
        }
    }

    // 预取下一屏：可见范围之后 8 个格子的封面先解码进内存
    androidx.compose.runtime.LaunchedEffect(gridState.layoutInfo.visibleItemsInfo.size, albums.size) {
        val info = gridState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        val until = (last + 8).coerceAtMost(albums.lastIndex)
        if (until <= last) return@LaunchedEffect
        val loader = coil3.SingletonImageLoader.get(ctx)
        for (i in last + 1..until) {
            val a = albums.getOrNull(i) ?: continue
            loader.enqueue(
                coil3.request.ImageRequest.Builder(ctx)
                    .data(coverUrl(a.coverId))
                    .size(cellPx)
                    .build(),
            )
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums.take(shown), key = { it.id }) { album ->
            AlbumCard(
                album = album,
                url = coverUrl(album.coverId),
                coverModifier = coverShared(album.id),
                progress = progressOf(album),
                coverSizePx = cellPx,
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
    downloaded: Boolean = false,
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
        if (downloaded) {
            Icon(
                YoinSymbols.DownloadDone,
                contentDescription = L10n.t("已下载"),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
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
    downloadedIds: Set<String> = emptySet(),
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
                        RecentTrackRow(track, coverUrl, track.id in downloadedIds, onPlayTrack)
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
    /** 解码尺寸（px）：与格子实际像素对齐，避免超采样解码 */
    coverSizePx: Int? = null,
    onClick: () -> Unit,
) {
    Column(modifier.clickable(onClick = onClick)) {
        LumooCover(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .onGloballyPositioned { c ->
                    runCatching {
                        app.lumen.player.ui.NavOriginStore.put(album.id, c.boundsInRoot())
                    }
                },
            albumId = album.id,
            cornerRadius = 20.dp,
            elevation = 14.dp,
        ) {
            val imgCtx = androidx.compose.ui.platform.LocalContext.current
            val model = remember(url, coverSizePx) {
                if (coverSizePx == null) {
                    url
                } else {
                    coil3.request.ImageRequest.Builder(imgCtx)
                        .data(url)
                        .size(coverSizePx)
                        .build()
                }
            }
            AsyncImage(
                model = model,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = coverModifier.fillMaxSize(),
            )
            // 播放中：底部细进度线，贴合卡片圆角
            if (progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            album.name,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            album.artist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 「正在分析」气泡（重建：原实现随文件截断丢失，功能与文案保持一致）：
 * 收起态是胶囊，点开后展开进度、阶段、取流方式与说明，点空白处关闭。
 */
@Composable
private fun AnalysisChip(
    done: Int,
    total: Int,
    phase: String,
    track: String,
    mode: String,
    modeDetail: String,
) {
    var open by remember { mutableStateOf(false) }
    val progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val pct = (progress * 100).toInt()

    Box {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            modifier = Modifier.clickable { open = true },
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { if (progress > 0f) progress else 0.08f },
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.6.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    L10n.t("正在分析"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (open) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(0, 56),
                onDismissRequest = { open = false },
                properties = androidx.compose.ui.window.PopupProperties(
                    focusable = true,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true,
                ),
            ) {
                val pop = androidx.compose.runtime.remember {
                    androidx.compose.animation.core.Animatable(0f)
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    pop.animateTo(
                        1f,
                        androidx.compose.animation.core.tween(
                            160,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    )
                }
                Column(
                    Modifier
                        .width(320.dp)
                        .graphicsLayer {
                            alpha = pop.value
                            scaleX = 0.94f + 0.06f * pop.value
                            scaleY = 0.94f + 0.06f * pop.value
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        }
                        .shadow(18.dp, androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            L10n.t("正在后台分析"),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$pct%",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$done",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            L10n.tf(" / $total 首", " / $total tracks"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    if (phase.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            L10n.t("当前阶段：") + phase + if (track.isNotBlank()) " · " + track else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 2,
                        )
                    }
                    if (mode.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (modeDetail.isBlank()) mode else mode + " · " + modeDetail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        L10n.t(
                            "每首只读取开头、中间、结尾各 30 秒音频（约 1~2 秒/首），用于封面配色、氛围电台与节拍动效；" +
                                "不会保存整首歌，也不占用离线下载空间。全部在本机完成，不上传音频。" +
                                "分析期间可能有短暂卡顿或发热，结束后自动恢复。",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        L10n.t("轻点空白处关闭"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
