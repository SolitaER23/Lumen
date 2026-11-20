package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.app.data.Album
import com.example.app.data.Artist
import com.example.app.data.ServerConfig
import com.example.app.data.SettingsStore
import com.example.app.data.SubsonicClient
import com.example.app.data.Track
import com.example.app.playback.Playback
import com.example.app.playback.AlbumDownload
import com.example.app.playback.Downloads
import com.example.app.playback.PlaybackEngine
import com.example.app.playback.PlaybackService
import com.example.app.ui.AlbumScreen
import com.example.app.ui.ArtistScreen
import com.example.app.ui.SearchScreen
import com.example.app.ui.FallbackScheme
import com.example.app.ui.PlayingScreen
import com.example.app.ui.DownloadsScreen
import com.example.app.ui.SettingsScreen
import com.example.app.ui.schemeFromCover
import com.gpo.yoin.symbols.YoinSymbols
import com.gpo.yoin.symbols.rememberPlayPauseSymbolPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LumenColors = darkColorScheme(
    primary = Color(0xFFE04A5F),
    onPrimary = Color(0xFF1C0A0D),
    primaryContainer = Color(0xFF5A1E27),
    onPrimaryContainer = Color(0xFFFFD9DD),
    surface = Color(0xFF0E0F12),
    surfaceContainer = Color(0xFF1A1B1F),
    surfaceContainerHigh = Color(0xFF222327),
    onSurface = Color(0xFFE8E9ED),
    onSurfaceVariant = Color(0xFFA9ABB3),
    outlineVariant = Color(0xFF3A3B40),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pixel 10a 是 120Hz 面板：明确请求固定源 120Hz，动画期间才不会被降到 60
        window.attributes = window.attributes.apply { preferredRefreshRate = 120f }

        setContent {
            App()
        }
    }
}

/** m4a（ALAC）在 Pixel 上能"播"但无声，统一走 320k MP3 转码 */
private fun streamUrl(client: SubsonicClient, t: Track): String =
    if (t.suffix.equals("m4a", ignoreCase = true)) {
        client.streamUrl(t.id) + "&format=mp3&maxBitRate=320"
    } else {
        client.streamUrl(t.id)
    }

@Composable
private fun App() {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(SettingsStore.load(ctx)) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var openAlbum by remember { mutableStateOf<Album?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    // 0 = 首页, 1 = 专辑页, 2 = 播放页
    var screen by remember { mutableStateOf(0) }
    var scheme by remember { mutableStateOf(FallbackScheme) }
    var downloads by remember { mutableStateOf<List<AlbumDownload>>(emptyList()) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }


    suspend fun loadAlbums(c: ServerConfig) {
        loading = true
        error = null
        var attempt = 0
        while (attempt < 3) {
            try {
                albums = withContext(Dispatchers.IO) { SubsonicClient(c).albums() }
                error = null
                break
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
                attempt++
                if (attempt < 3) delay(800L * attempt) // 退避后重试
            }
        }
        loading = false
    }

    LaunchedEffect(cfg.complete) {
        if (cfg.complete && albums.isEmpty()) loadAlbums(cfg)
    }

    if (!cfg.complete) {
        // 登录页也要套主题，否则用的是 M3 默认浅色 → 深色字压在黑底上看不清
        MaterialTheme(colorScheme = FallbackScheme) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                LoginScreen(cfg, loading, error) { c ->
                    loading = true
                    cfg = c
                    SettingsStore.save(ctx, c)
                }
            }
        }
        return
    }

    val client = remember(cfg) { SubsonicClient(cfg) }
    val scope = rememberCoroutineScope()
    // 全进程唯一播放器：Activity 与前台服务（通知/锁屏）共用
    val playback = remember(client) {
        PlaybackEngine.get(ctx).also { p ->
            p.urlFor = { t -> streamUrl(client, t) }
            p.transcodeUrl = { t -> client.streamUrl(t.id) + "&format=mp3&maxBitRate=320" }
        }
    }
    // 登录后就常驻媒体会话服务：这样它能在"开始播放"这一刻收到事件并挂出通知/前台服务
    LaunchedEffect(cfg.complete) {
        if (cfg.complete) {
            runCatching { ctx.startService(android.content.Intent(ctx, PlaybackService::class.java)) }
        }
    }
    DisposableEffect(Unit) { onDispose { playback.release() } }
    LaunchedEffect(playback.playing) {
        while (playback.playing) {
            playback.tick()
            delay(500)
        }
    }
    val current = playback.current

    // 下载状态轮询：只在相关页面刷新，避免全局定时器
    LaunchedEffect(screen, openAlbum?.id) {
        if (screen == 1 || screen == 3 || screen == 4) {
            while (true) {
                downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) }
                delay(1000)
            }
        }
    }

    // 封面取色：主色跟着正在播的专辑走
    LaunchedEffect(current?.albumId) {
        current?.let { t ->
            schemeFromCover(client.coverUrl(t.albumId, 200))?.let { scheme = it }
        }
    }

    var backProgress by remember { mutableStateOf(0f) }
    PredictiveBackHandler(enabled = screen > 0) { progress ->
        try {
            progress.collect { backProgress = it.progress }
            when {
                // 从底栏直接进播放页时没有专辑上下文，返回应回首页
                screen == 2 -> screen = if (openAlbum != null) 1 else 0
                else -> { screen = 0; openAlbum = null }
            }
        } catch (_: CancellationException) {
            // 手势取消：留在原页
        } finally {
            backProgress = 0f
        }
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val s = 1f - 0.06f * backProgress
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.12f * backProgress
                },
            ) {
                when {
                    screen == 6 -> SearchScreen(
                        client = client,
                        coverUrl = { client.coverUrl(it) },
                        onBack = { screen = 0 },
                        onOpenAlbum = { al -> openAlbum = al; tracks = emptyList(); screen = 1 },
                        onOpenArtist = { a -> selectedArtist = a; screen = 5 },
                    )
                    screen == 5 -> selectedArtist?.let { a ->
                        ArtistScreen(
                            client = client,
                            artist = a,
                            coverUrl = { client.coverUrl(it) },
                            onBack = { screen = 0 },
                            onOpenAlbum = { al -> openAlbum = al; tracks = emptyList(); screen = 1 },
                        )
                    }
                    screen == 4 -> DownloadsScreen(
                        summaries = downloads,
                        onBack = { screen = 3 },
                        onDelete = { id ->
                            Downloads.delete(ctx, id)
                            downloads = Downloads.summaries(ctx)
                        },
                        onClearAll = {
                            downloads.forEach { Downloads.delete(ctx, it.albumId) }
                            downloads = emptyList()
                        },
                    )
                    screen == 3 -> SettingsScreen(
                        cfg = cfg,
                        cacheDir = java.io.File(ctx.cacheDir, "media"),
                        downloadCount = downloads.sumOf { it.total },
                        downloadBytes = downloads.sumOf { it.bytes },
                        onManageDownloads = { screen = 4 },
                        onClearCache = {
                            // 缓存与下载共用 SimpleCache：清缓存要把下载记录一起删掉，否则管理页会显示"已下载"但文件没了
                            downloads.forEach { Downloads.delete(ctx, it.albumId) }
                            java.io.File(ctx.cacheDir, "media").deleteRecursively()
                            downloads = emptyList()
                        },
                        onBack = { screen = 0 },
                        onLogout = {
                            ctx.getSharedPreferences("lumen_settings", android.content.Context.MODE_PRIVATE)
                                .edit().clear().apply()
                            albums = emptyList()
                            openAlbum = null
                            screen = 0
                            cfg = ServerConfig("", "", "")
                        },
                    )
                    screen == 2 -> PlayingScreen(
                        track = current,
                        coverUrl = current?.let { client.coverUrl(it.albumId, 600) } ?: "",
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        playing = playback.playing,
                        onBack = { screen = 1 },
                        onToggle = { playback.toggle() },
                        onPrev = { playback.prev() },
                        onNext = { playback.next() },
                        onSeek = { playback.seekTo(it) },
                    )
                    else -> if (openAlbum == null) {
                        HomeScreen(
                            client = client,
                            albums = albums,
                            loading = loading,
                            error = error,
                            coverUrl = { client.coverUrl(it) },
                            onOpen = { album ->
                                openAlbum = album
                                tracks = emptyList()
                                screen = 1
                            },
                            onSettings = { screen = 3 },
                            onRetry = { scope.launch { loadAlbums(cfg) } },
                            onSearch = { screen = 6 },
                            onOpenArtist = { a -> selectedArtist = a; screen = 5 },
                        )
                    } else {
                        val album = openAlbum!!
                        if (tracks.isEmpty()) {
                            LaunchedEffect(album.id) {
                                tracks = try {
                                    withContext(Dispatchers.IO) { client.tracks(album.id) }
                                } catch (e: Exception) {
                                    error = e.message
                                    emptyList()
                                }
                            }
                        }
                        AlbumScreen(
                            album = album,
                            tracks = tracks,
                            coverUrl = client.coverUrl(album.coverId),
                            currentTrackId = current?.id,
                            onBack = { screen = 0; openAlbum = null },
                            download = downloads.firstOrNull { it.albumId == album.id },
                            onDownload = {
                                Downloads.enqueue(ctx, album, tracks) { tr -> streamUrl(client, tr) }
                            },
                            onPlay = { start ->
                                if (tracks.isNotEmpty()) {
                                    playback.play(tracks, start, album.id)
                                    playback.tick()
                                }
                            },
                        )
                    }
                }

                // 每一页都有底栏（含播放页）
                    YoinBar(
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp),
                        title = current?.title ?: "",
                        artist = current?.artist ?: "",
                        coverUrl = current?.let { client.coverUrl(it.albumId, 200) } ?: "",
                        playing = playback.playing,
                        enabled = current != null,
                        onHome = { screen = 0; openAlbum = null },
                        onOpenPlayer = { screen = 2 },
                        onToggle = {
                            if (current != null) playback.toggle()
                            else if (albums.isNotEmpty()) openAlbum = albums.first()
                        },
                    )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    initial: ServerConfig,
    loading: Boolean,
    error: String?,
    onSubmit: (ServerConfig) -> Unit,
) {
    var base by remember { mutableStateOf(initial.base.ifBlank { "http://" }) }
    var user by remember { mutableStateOf(initial.user) }
    var pass by remember { mutableStateOf(initial.password) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Lumen", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text("连接曲库", color = MaterialTheme.colorScheme.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = base, onValueChange = { base = it }, label = { Text("服务器地址") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it }, label = { Text("用户名") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it }, label = { Text("密码") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Button(
            onClick = { onSubmit(ServerConfig(base.trim(), user.trim(), pass)) },
            enabled = !loading && base.startsWith("http") && user.isNotBlank() && pass.isNotBlank(),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("连接并同步", fontSize = 15.sp)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
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
) {
    var tab by remember { mutableIntStateOf(0) } // 0 专辑 / 1 艺人 / 2 最近
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var newest by remember { mutableStateOf<List<Album>>(emptyList()) }
    var recent by remember { mutableStateOf<List<Album>>(emptyList()) }
    var artistCovers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var tabLoading by remember { mutableStateOf(false) }
    var tabError by remember { mutableStateOf<String?>(null) }

    // 启动就拉一次艺人，让标签上的数量一开始就是对的
    LaunchedEffect(Unit) {
        if (artists.isEmpty()) {
            runCatching { artists = withContext(Dispatchers.IO) { client.artists() } }
        }
    }

    // 艺人没有独立封面时，用其第一张专辑的封面，避免一片灰色占位图
    LaunchedEffect(artists) {
        if (artists.isEmpty()) return@LaunchedEffect
        artistCovers = withContext(Dispatchers.IO) {
            artists.mapNotNull { a ->
                runCatching { client.artistDetail(a.id)?.second?.firstOrNull()?.coverId }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { a.id to it }
            }.toMap()
        }
    }

    LaunchedEffect(tab) {
        if (tab == 0) return@LaunchedEffect
        tabLoading = true
        tabError = null
        try {
            when (tab) {
                1 -> if (artists.isEmpty()) artists = withContext(Dispatchers.IO) { client.artists() }
                2 -> {
                    if (newest.isEmpty()) newest = withContext(Dispatchers.IO) { client.newestAlbums() }
                    if (recent.isEmpty()) recent = withContext(Dispatchers.IO) { client.recentAlbums() }
                }
            }
        } catch (e: Exception) {
            tabError = e.message ?: "加载失败"
        } finally {
            tabLoading = false
        }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
        HomeHeader(
            selected = tab,
            albumCount = albums.size,
            artistCount = artists.size,
            onTab = { tab = it },
            onSettings = onSettings,
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
                ) { Text("重试") }
            }
            tab == 1 -> ArtistGrid(artists, artistCovers, coverUrl, onOpenArtist)
            tab == 2 -> RecentList(
                newest = newest,
                recent = recent,
                coverUrl = coverUrl,
                onOpen = onOpen,
                loading = tabLoading && newest.isEmpty(),
            )
            else -> AlbumGrid(albums, coverUrl, onOpen)
        }
        }

        // 搜索：悬浮在底栏右上方
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 84.dp)
                .size(60.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                YoinSymbols.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun HomeHeader(
    selected: Int,
    albumCount: Int,
    artistCount: Int,
    onTab: (Int) -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabLabel("专辑", albumCount, 0, selected, onTab)
        Spacer(Modifier.width(20.dp))
        TabLabel("艺人", artistCount, 1, selected, onTab)
        Spacer(Modifier.width(20.dp))
        TabLabel("最近", null, 2, selected, onTab)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            Icon(
                YoinSymbols.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun TabLabel(title: String, count: Int?, index: Int, selected: Int, onTab: (Int) -> Unit) {
    val active = selected == index
    Column(
        Modifier.clickable { onTab(index) },
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            title,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 26.sp,
            fontWeight = if (active) FontWeight.Normal else FontWeight.Light,
        )
        // 固定高度占位，保证三个标签基线一致（没有数量的「最近」也不歪）
        Box(Modifier.height(16.dp), contentAlignment = Alignment.TopStart) {
            if (count != null) {
                Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, coverUrl: (String) -> String, onOpen: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album, coverUrl(album.coverId)) { onOpen(album) }
        }
    }
}

@Composable
private fun ArtistGrid(
    artists: List<Artist>,
    artistCovers: Map<String, String>,
    coverUrl: (String) -> String,
    onOpen: (Artist) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(artists, key = { it.id }) { artist ->
            Column(
                Modifier.clickable { onOpen(artist) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = coverUrl(artistCovers[artist.id] ?: artist.coverId),
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(percent = 50)),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    artist.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${artist.albumCount} 张专辑",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun RecentList(
    newest: List<Album>,
    recent: List<Album>,
    coverUrl: (String) -> String,
    onOpen: (Album) -> Unit,
    loading: Boolean,
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item { SectionTitle("最近添加") }
        items(newest.chunked(2)) { pair ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEach { album ->
                    AlbumCard(album, coverUrl(album.coverId), Modifier.weight(1f)) { onOpen(album) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
        item { SectionTitle("最近播放") }
        items(recent.chunked(2)) { pair ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEach { album ->
                    AlbumCard(album, coverUrl(album.coverId), Modifier.weight(1f)) { onOpen(album) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
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

    val transition = rememberInfiniteTransition(label = "artist")
    val offset by transition.animateFloat(
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
                .graphicsLayer { translationY = if (overflow > 0) offset else 0f },
        )
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    url: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        // 封面保持正方形，右侧留一条竖排艺人名的窄列
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val strip = 26.dp
            val cover = maxWidth - strip
            Row {
                AsyncImage(
                    model = url,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(cover).clip(
                        // 只圆左上角，其余保持直角
                        RoundedCornerShape(topStart = 20.dp),
                    ),
                )
                VerticalArtistName(
                    text = album.artist,
                    modifier = Modifier.width(strip).height(cover),
                )
            }
        }
        // 专辑名固定一行；过长时自动横向滚动
        Text(
            album.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).basicMarquee(),
        )
    }
}
@Composable
private fun YoinBar(
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    coverUrl: String,
    playing: Boolean,
    enabled: Boolean,
    onHome: () -> Unit,
    onOpenPlayer: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 首页：独立圆形按钮
        Surface(
            onClick = onHome,
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(60.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    YoinSymbols.Home,
                    contentDescription = "首页",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        // 曲目胶囊：封面 + 歌名 + 播放键全在一个框里
        Surface(
            onClick = onOpenPlayer,
            enabled = enabled,
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Row(
                Modifier.fillMaxSize().padding(start = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (enabled) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    // 换歌：旧名向左退场、新名从右边推入
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        },
                        label = "track",
                        modifier = Modifier.weight(1f),
                    ) { t ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Text(
                        "还没有播放",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
                val painter = rememberPlayPauseSymbolPainter(playing)
                IconButton(
                    onClick = onToggle,
                    enabled = enabled,
                    modifier = Modifier.size(44.dp),
                ) {
                    Image(
                        painter = painter,
                        contentDescription = if (playing) "暂停" else "播放",
                        modifier = Modifier.size(26.dp),
                        colorFilter = ColorFilter.tint(
                            if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f),
                        ),
                    )
                }
            }
        }
    }
}
