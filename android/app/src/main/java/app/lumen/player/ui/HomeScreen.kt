package app.lumen.player.ui

import app.lumen.player.util.L10n
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import app.lumen.player.data.Album
import app.lumen.player.data.Artist
import app.lumen.player.data.HomeSort
import app.lumen.player.data.SubsonicClient
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext

/**
 * 首页（M3 Expressive）：
 * - `LargeFlexibleTopAppBar`：大标题 + 副标题（数量/排序），滚动收成小标题；
 * - `SegmentedButton` 切换「专辑 / 艺人」，左右滑动由 Pager 承担；
 * - 专辑两列网格，封面用共享元素 key 与专辑页、播放页串成一条连续动画。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    tab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    // 排序方式由调用方（App）持有并落盘：页面切换/进程重启后仍能恢复，
    // 不放在本组件的 remember 里（那样离开首页就会退回默认）
    albumSort: Int = HomeSort.DEFAULT,
    artistSort: Int = HomeSort.DEFAULT,
    onSortChange: (tab: Int, mode: Int) -> Unit = { _, _ -> },
    /**
     * 当前正在播放的专辑 id（而不是曲目对象）。
     * 只传 id：它一换歌才变，不像 positionMs 每 250ms 变一次，首页不会因此整页重组。
     */
    playingAlbumId: String? = null,
    /**
     * 播放进度（0f–1f）。必须是 lambda：它被 LinearProgressIndicator 在**绘制阶段**读取，
     * 因此调用方每 250ms 推进的 positionMs 不再让首页（含网格、顶栏、Pager）每秒重组 4 次。
     */
    progressProvider: () -> Float? = { null },
    onOpenStats: () -> Unit = {},
    cache: HomeCache = remember { HomeCache() },
) {
    // 每页用自己的排序方式：专辑页按 albumSort、艺人页按 artistSort，
    // 切 tab 不会把另一页的选择覆盖掉（与旧版「按 tab 记忆排序」一致）
    val tabSort = if (tab == HomeSort.TAB_ARTISTS) artistSort else albumSort
    val sortIndex = HomeSort.sanitize(tab, tabSort)
    var showSort by remember { mutableStateOf(false) }
    val artists = cache.artists

    var tabLoading by remember { mutableStateOf(false) }
    var tabError by remember { mutableStateOf<String?>(null) }

    // 原来这里是三个 LaunchedEffect（Unit 预取 + tab 加载 + tab 关闭排序面板）：
    // 同一个 key 上挂多个 effect 会多分配 effect 节点与协程启动器，合并成一个即可。
    LaunchedEffect(tab) {
        showSort = false
        if (artists.isNotEmpty()) return@LaunchedEffect
        if (tab == 0) {
            // 停在专辑页时顺手预取艺人列表，切过去就不用等
            runCatching { cache.artists = withContext(Dispatchers.IO) { client.artists() } }
            return@LaunchedEffect
        }
        tabLoading = true
        tabError = null
        try {
            cache.artists = withContext(Dispatchers.IO) { client.artists() }
        } catch (e: Exception) {
            tabError = e.message ?: L10n.t("加载失败")
        } finally {
            tabLoading = false
        }
    }

    val sortOptions = when (tab) {
        1 -> listOf(L10n.t("名称"), L10n.t("专辑数量"))
        else -> listOf(L10n.t("字母顺序"), L10n.t("发行年份"), L10n.t("歌曲数量"), L10n.t("艺人"))
    }
    // 排序只在「列表或排序方式变化」时算一次：positionMs 每 250ms 推进一次，本页每秒会重组 4 次，
    // 之前每次重组都对整个曲库重做一遍 O(N log N) 排序（比较器里还带 lowercase() 分配）。
    val sortedAlbums = remember(albums, albumSort) { HomeSort.albums(albums, albumSort) }
    val sortedArtists = remember(artists, artistSort) { HomeSort.artists(artists, artistSort) }

    val scheme = MaterialTheme.colorScheme
    val haptics = rememberLumenHaptics()
    val sortLabel = sortOptions.getOrElse(sortIndex) { L10n.t("字母顺序") }
    val sectionTitle = if (tab == 1) L10n.t("全部艺人") else L10n.t("全部专辑")
    val sectionTrailing = if (tab == 1) {
        L10n.tf("共 ${artists.size} 位 · $sortLabel", "${artists.size} total · $sortLabel")
    } else {
        L10n.tf("共 ${albums.size} 张 · $sortLabel", "${albums.size} total · $sortLabel")
    }

    // 顶栏浮在内容之上：状态栏 + 56dp。内容用同样的顶部内边距起排，
    // 滚动时从顶栏的渐变遮罩下穿过，不会在某一行被"切一刀"。
    val topInset = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    val barHeight = topInset + LumenSizes.homeTopBar

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val activeError = if (tab == 0) error else tabError
            when {
                tab == 0 && loading && albums.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(top = barHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    LumenLoading()
                }
                activeError != null -> LumenErrorState(
                    message = activeError,
                    retryLabel = L10n.t("重试"),
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = barHeight + LumenSizes.xl),
                )
                else -> {
                    val pagerState = rememberPagerState(pageCount = { 2 })
                    val programmaticScroll = remember { mutableStateOf(false) }
                    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                    LaunchedEffect(tab) {
                        if (pagerState.currentPage != tab) {
                            programmaticScroll.value = true
                            try {
                                pagerState.animateScrollToPage(tab, animationSpec = spatial)
                            } finally {
                                programmaticScroll.value = false
                            }
                        }
                    }
                    val latestTab = rememberUpdatedState(tab)
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }
                            .drop(1)
                            .collect { page ->
                                if (!programmaticScroll.value && page != latestTab.value) onTabChange(page)
                            }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        key = { it },
                    ) { page ->
                        when (page) {
                            1 -> ArtistList(
                                artists = sortedArtists,
                                loading = tabLoading,
                                sectionTitle = sectionTitle,
                                sectionTrailing = sectionTrailing,
                                topPadding = barHeight,
                                onOpen = onOpenArtist,
                            )
                            else -> AlbumGrid(
                                albums = sortedAlbums,
                                coverUrl = coverUrl,
                                sectionTitle = sectionTitle,
                                sectionTrailing = sectionTrailing,
                                topPadding = barHeight,
                                onOpen = onOpen,
                                playingAlbumId = playingAlbumId,
                                progressProvider = progressProvider,
                            )
                        }
                    }
                }
            }
        }

        HomeTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            tab = tab,
            onTab = onTabChange,
            onSort = { showSort = true },
            onStats = onOpenStats,
            onSearch = onSearch,
            onSettings = onSettings,
        )
    }

    if (showSort) {
        ModalBottomSheet(
            onDismissRequest = { showSort = false },
            containerColor = scheme.surfaceContainer,
        ) {
            Text(
                if (tab == 1) L10n.t("艺人排序") else L10n.t("专辑排序"),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = scheme.onSurface,
                modifier = Modifier.padding(horizontal = LumenSizes.l, vertical = LumenSizes.xs),
            )
            Spacer(Modifier.height(LumenSizes.xs))
            sortOptions.forEachIndexed { i, label ->
                ListItem(
                    headlineContent = {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        RadioButton(selected = i == sortIndex, onClick = null)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .padding(horizontal = LumenSizes.xs)
                        .clickable {
                            haptics.segment()
                            onSortChange(tab, i)
                            showSort = false
                        },
                )
            }
            Spacer(Modifier.height(LumenSizes.xxl).navigationBarsPadding())
        }
    }
}

/**
 * 首页顶栏：所有入口收进同一行，不再有独立的大标题行。
 *
 * 左：专辑 / 艺人（文字入口）+ 排序；右：统计 / 搜索 / 设置（图标入口）。
 * 所有元素都是 44dp 高的同一种竖直中心线，间距统一 4dp，左右两组用剩余空间顶开，
 * 所以中文标签和图标在同一基线上，不会出现"标题行比右上角入口低半行"的错位。
 */
@Composable
private fun HomeTopBar(
    modifier: Modifier = Modifier,
    tab: Int,
    onTab: (Int) -> Unit,
    onSort: () -> Unit,
    onStats: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .background(scheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(LumenSizes.homeTopBar)
                .padding(horizontal = LumenSizes.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeBarText(L10n.t("专辑"), selected = tab == 0) { onTab(0) }
            Spacer(Modifier.width(LumenSizes.xxs))
            HomeBarText(L10n.t("艺人"), selected = tab == 1) { onTab(1) }
            Spacer(Modifier.width(LumenSizes.xxs))
            HomeBarIcon(onClick = onSort) {
                Icon(
                    YoinSymbols.Sort,
                    contentDescription = L10n.t("排序"),
                    tint = scheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            HomeBarIcon("stats", onStats) {
                Icon(YoinSymbols.Insights, contentDescription = L10n.t("聆听统计"), tint = scheme.onSurface, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(LumenSizes.xxs))
            HomeBarIcon("search", onSearch) {
                Icon(YoinSymbols.Search, contentDescription = L10n.t("搜索"), tint = scheme.onSurface, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(LumenSizes.xxs))
            HomeBarIcon("settings", onSettings) {
                Icon(YoinSymbols.Settings, contentDescription = L10n.t("设置"), tint = scheme.onSurface, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** 顶栏文字入口：与图标入口同为 44dp 的方形容器，文字居中，选中用主色 + Emphasized。 */
@Composable
private fun HomeBarText(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberLumenHaptics()
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable {
                haptics.segment()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            style = if (selected) MaterialTheme.typography.titleSmallEmphasized
            else MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
    }
}

/** 顶栏图标入口：44dp 容器、22dp 图标；给了 originKey 时记录坐标供返回转场收缩。 */
@Composable
private fun HomeBarIcon(
    originKey: String? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = rememberLumenHaptics()
    IconButton(
        onClick = {
            haptics.navigate()
            onClick()
        },
        modifier = Modifier
            .size(44.dp)
            .then(
                if (originKey != null) {
                    Modifier.onGloballyPositioned { c ->
                        runCatching { NavOriginStore.put(originKey, c.boundsInRoot()) }
                    }
                } else {
                    Modifier
                },
            ),
    ) { content() }
}

@Composable
private fun AlbumGrid(
    albums: List<Album>,
    coverUrl: (String) -> String,
    sectionTitle: String,
    sectionTrailing: String,
    topPadding: androidx.compose.ui.unit.Dp,
    onOpen: (Album) -> Unit,
    /** 只有正在播放的那张专辑显示进度条 */
    playingAlbumId: String? = null,
    progressProvider: () -> Float? = { null },
) {
    val ctx = LocalContext.current
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = HomeScrollStore.albumIndex,
        initialFirstVisibleItemScrollOffset = HomeScrollStore.albumOffset,
    )
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                HomeScrollStore.albumIndex = index
                HomeScrollStore.albumOffset = offset
                // 不要在每一帧滚动都落盘：firstVisibleItemIndex/Offset 每移动 1px 就触发一次，
                // 之前 force=true 绕过了 HomeScrollStore 里 400ms 的节流，等于滚动时每帧写一次 SharedPreferences。
                HomeScrollStore.persist(ctx)
            }
    }
    val density = LocalDensity.current
    val screenW = LocalConfiguration.current.screenWidthDp
    val cellPx = with(density) { (((screenW - 48) / 2f).coerceAtLeast(120f).dp).roundToPx() }

    // 初始只渲染 24 张，滚动到底部附近再放行下一批（长曲库首帧不全量组合）。
    // 不要用 albums.size 当 key：曲库一旦变化就会把 shown 打回 24，用户会被弹回列表开头。
    var shown by rememberSaveable { mutableIntStateOf(24) }
    // 关键：不要把 gridState.layoutInfo 写进 composition（包括 LaunchedEffect 的 key）。
    // 它是快照状态，组合期读它 = 每翻过一行就整页重组一次；改成在协程里 snapshotFlow。
    LaunchedEffect(gridState, albums, cellPx) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (last < 0) return@collect
                if (shown < albums.size && last >= shown - 4) {
                    shown = minOf(albums.size, shown + 24)
                }
                val until = (last + 8).coerceAtMost(albums.lastIndex)
                if (until <= last) return@collect
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
    }

    // take() 每次调用都会复制一个新 List；本页每秒重组 4 次，等于每秒多分配 4 个列表。
    val visible = remember(albums, shown) { albums.take(shown) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(
            start = LumenSizes.screenH,
            end = LumenSizes.screenH,
            top = topPadding,
            bottom = LumenSizes.contentBottom,
        ),
        horizontalArrangement = Arrangement.spacedBy(LumenSizes.m),
        verticalArrangement = Arrangement.spacedBy(LumenSizes.l),
        modifier = Modifier.fillMaxSize(),
    ) {
        // 分区标题跟着列表一起滚走：滚动时不会被固定在一条直线上把封面"切一刀"
        item(
            key = "album-section",
            contentType = "section",
            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
        ) {
            LumenSectionHeader(
                title = sectionTitle,
                trailing = sectionTrailing,
                horizontalPadding = 0.dp,
            )
        }
        items(visible, key = { it.id }, contentType = { "album" }) { album ->
            AlbumCard(
                album = album,
                url = coverUrl(album.coverId),
                showProgress = playingAlbumId != null && playingAlbumId == album.id,
                progressProvider = progressProvider,
                coverSizePx = cellPx,
                onClick = { onOpen(album) },
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    url: String,
    showProgress: Boolean,
    progressProvider: () -> Float?,
    coverSizePx: Int?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberLumenHaptics()
    Surface(
        onClick = {
            haptics.navigate()
            onClick()
        },
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(bottom = LumenSizes.xxs)) {
            AlbumArt(
                url = url,
                contentDescription = album.name,
                // 专辑封面转场靠共享元素 key，不再需要每张卡片记录根坐标（滚动时省掉逐项布局回调）
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = MaterialTheme.shapes.extraLarge,
                sharedKey = albumCoverKey(album.id),
            )
            Spacer(Modifier.height(LumenSizes.xs))
            Text(
                album.name,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = LumenSizes.xxs),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = LumenSizes.xxs),
            )
            if (showProgress) {
                Spacer(Modifier.height(LumenSizes.xs))
                LinearProgressIndicator(
                    // lambda 形式：M3 在绘制阶段求值，进度推进不会重组这张卡片
                    progress = { (progressProvider() ?: 0f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(horizontal = LumenSizes.xxs),
                    color = scheme.primary,
                    trackColor = scheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun ArtistList(
    artists: List<Artist>,
    loading: Boolean,
    sectionTitle: String,
    sectionTrailing: String,
    topPadding: androidx.compose.ui.unit.Dp,
    onOpen: (Artist) -> Unit,
) {
    if (loading && artists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LumenLoading() }
        return
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // 列表自身的根坐标只在它真的移动时更新（页面转场、insets 变化），滚动时不变。
    val listOrigin = remember { FloatArray(2) }
    /**
     * 转场起点改成「点击那一刻按需算」：
     * 旧实现给每一行挂 onGloballyPositioned，每次布局/滚动回调都会分配一个 Rect 并写全局表，
     * 滚动时等于每帧 N 次分配 + N 次写入。现在只用一个列表级回调 + layoutInfo 推算，
     * 和 Compose 文档推荐的「在事件里按需取坐标」一致。
     */
    fun originOf(artist: Artist): Rect? {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == artist.id } ?: return null
        val padPx = with(density) { LumenSizes.xs.toPx() }
        val vPadPx = with(density) { 2.dp.toPx() }
        val left = listOrigin[0] + padPx
        val right = listOrigin[0] + info.viewportSize.width - padPx
        if (right <= left) return null
        val top = listOrigin[1] + item.offset + vPadPx
        return Rect(left, top, right, top + item.size)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { c ->
                val p = c.positionInRoot()
                listOrigin[0] = p.x
                listOrigin[1] = p.y
            },
        contentPadding = PaddingValues(
            start = LumenSizes.xs,
            end = LumenSizes.xs,
            top = topPadding,
            bottom = LumenSizes.contentBottom,
        ),
    ) {
        item(key = "artist-section", contentType = "section") {
            LumenSectionHeader(
                title = sectionTitle,
                trailing = sectionTrailing,
                horizontalPadding = LumenSizes.xs,
            )
        }
        items(artists, key = { it.id }, contentType = { "artist" }) { artist ->
            val haptics = rememberLumenHaptics()
            Surface(
                onClick = {
                    haptics.navigate()
                    originOf(artist)?.let { NavOriginStore.put(artist.id, it) }
                    onOpen(artist)
                },
                shape = MaterialTheme.shapes.large,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LumenSizes.xs, vertical = 2.dp),
            ) {
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
                    leadingContent = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    artist.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Icon(
                            YoinSymbols.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
