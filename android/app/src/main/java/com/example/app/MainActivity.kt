package com.example.app

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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.app.data.LyricLine
import com.example.app.data.Ratings
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

/** 页面栈的动画键：屏幕 + 当前专辑，用来判断方向与触发转场 */
private data class NavKey(val screen: Int, val album: Album?)

/** m4a（ALAC）在 Pixel 上能"播"但无声，统一走 320k MP3 转码 */
private fun streamUrl(client: SubsonicClient, t: Track): String =
    if (t.suffix.equals("m4a", ignoreCase = true)) {
        client.streamUrl(t.id) + "&format=mp3&maxBitRate=320"
    } else {
        client.streamUrl(t.id)
    }


@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
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
    var starred by remember { mutableStateOf<List<Track>>(emptyList()) }
    var starredIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var homeTab by remember { mutableIntStateOf(0) }
    var lyrics by remember { mutableStateOf<List<LyricLine>?>(null) }
    var lyricsTrackId by remember { mutableStateOf<String?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(true) }
    var rating by remember { mutableFloatStateOf(0f) }
    val navStack = remember { androidx.compose.runtime.mutableStateListOf<NavKey>() }

    /** 去下一页，并把"来处"压栈 */
    fun navigate(target: NavKey) {
        navStack.add(NavKey(screen, openAlbum))
        screen = target.screen
        openAlbum = target.album
    }

    /** 原路返回 */
    fun goBack() {
        val prev = navStack.removeLastOrNull() ?: return
        screen = prev.screen
        openAlbum = prev.album
    }


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
    val coverOf = remember(client) { { id: String -> client.coverUrl(id) } }
    val scope = rememberCoroutineScope()

    fun toggleStar(track: Track) {
        scope.launch {
            val on = starredIds.contains(track.id)
            runCatching {
                withContext(Dispatchers.IO) {
                    if (on) client.unstar(track.id) else client.star(track.id)
                }
            }.onSuccess {
                starredIds = if (on) starredIds - track.id else starredIds + track.id
                starred = if (on) starred.filterNot { it.id == track.id }
                else starred + track.copy(starred = true)
            }
        }
    }
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
            downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) }
            runCatching {
                val list = withContext(Dispatchers.IO) { client.starredTracks() }
                starred = list
                starredIds = list.map { it.id }.toSet()
            }
        }
    }

    // 换歌自动加载歌词（播放页下方常驻显示）
    LaunchedEffect(playback.current?.id) {
        val tr = playback.current
        if (lyricsTrackId != tr?.id) {
            lyrics = null
            lyricsTrackId = tr?.id
            if (tr != null) {
                rating = Ratings.get(ctx, tr.id)
                lyricsLoading = true
                runCatching {
                    lyrics = withContext(Dispatchers.IO) { client.lyrics(tr.id, tr.title, tr.artist) }
                }
                lyricsLoading = false
            }
        }
    }

    // 下载完成情况 → 单曲是否已下载
    LaunchedEffect(downloads) {
        downloadedTrackIds = downloads
            .filter { it.isSingle && it.total > 0 && it.done == it.total }
            .map { it.key.removePrefix("track:") }
            .toSet()
    }
    LaunchedEffect(playback.playing) {
        while (playback.playing) {
            playback.tick()
            delay(500)
        }
    }
    val current = playback.current

    // 下载状态：设置/下载管理页才轮询；专辑页只在进入时查一次，避免转场期间每秒重组整个 App
    LaunchedEffect(screen, openAlbum?.id, homeTab) {
        when (screen) {
            3, 4 -> while (true) {
                downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) }
                delay(1000)
            }
            2 -> while (true) {
                downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) }
                delay(1500)
            }
            0 -> if (homeTab == 2) {
                while (true) {
                    downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) }
                    delay(2000)
                }
            }
            // 1 -> 专辑页暂不查询下载状态（避免转场瞬间触发整页重组）
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

    val onOpenAlbum: (Album) -> Unit = remember {
        { al -> tracks = emptyList(); navigate(NavKey(1, al)) }
    }
    val onGoHome: () -> Unit = remember { { navStack.clear(); screen = 0; openAlbum = null } }
    val onOpenSettings: () -> Unit = remember { { navigate(NavKey(3, null)) } }
    val onOpenSearch: () -> Unit = remember { { navigate(NavKey(6, null)) } }
    val onOpenArtist: (Artist) -> Unit = remember { { a -> selectedArtist = a; navigate(NavKey(5, null)) } }
    val onOpenArtistFromSearch: (Artist) -> Unit = remember { { a -> selectedArtist = a; navigate(NavKey(5, null)) } }

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
                run {
                AnimatedContent(
                    targetState = NavKey(screen, openAlbum),
                    transitionSpec = {
                        val forward = targetState.screen >= initialState.screen
                        val spec = spring<Float>(dampingRatio = 0.85f, stiffness = 420f)
                        val enter = fadeIn(tween<Float>(260)) + scaleIn(
                            initialScale = if (forward) 0.90f else 1.06f,
                            animationSpec = spec,
                        )
                        val exit = fadeOut(tween<Float>(180)) + scaleOut(
                            targetScale = if (forward) 1.06f else 0.90f,
                            animationSpec = spec,
                        )
                        (enter togetherWith exit).using(androidx.compose.animation.SizeTransform(clip = false))
                    },
                    label = "screen",
                    modifier = Modifier.fillMaxSize(),
                ) { key ->
                when {
                    key.screen == 6 -> SearchScreen(
                        client = client,
                        coverUrl = { client.coverUrl(it) },
                        onBack = { goBack() },
                        onOpenAlbum = { al -> tracks = emptyList(); navigate(NavKey(1, al)) },
                        onOpenArtist = onOpenArtistFromSearch,
                    )
                    key.screen == 5 -> selectedArtist?.let { a ->
                        ArtistScreen(
                            client = client,
                            artist = a,
                            coverUrl = { client.coverUrl(it) },
                            onBack = { goBack() },
                            onOpenAlbum = { al -> tracks = emptyList(); navigate(NavKey(1, al)) },
                        )
                    }
                    key.screen == 4 -> DownloadsScreen(
                        summaries = downloads,
                        onBack = { goBack() },
                        onDelete = { id ->
                            Downloads.delete(ctx, id)
                            downloads = Downloads.summaries(ctx)
                        },
                        onClearAll = {
                            downloads.forEach { Downloads.delete(ctx, it.key) }
                            downloads = emptyList()
                        },
                    )
                    key.screen == 3 -> SettingsScreen(
                        cfg = cfg,
                        cacheDir = java.io.File(ctx.cacheDir, "media"),
                        downloadCount = downloads.sumOf { it.total },
                        downloadBytes = downloads.sumOf { it.bytes },
                        onManageDownloads = { navigate(NavKey(4, null)) },
                        onClearCache = {
                            // 缓存与下载共用 SimpleCache：清缓存要把下载记录一起删掉，否则管理页会显示"已下载"但文件没了
                            downloads.forEach { Downloads.delete(ctx, it.key) }
                            java.io.File(ctx.cacheDir, "media").deleteRecursively()
                            downloads = emptyList()
                        },
                        onBack = { goBack() },
                        onLogout = {
                            ctx.getSharedPreferences("lumen_settings", android.content.Context.MODE_PRIVATE)
                                .edit().clear().apply()
                            albums = emptyList()
                            openAlbum = null
                            navStack.clear()
                            screen = 0
                            cfg = ServerConfig("", "", "")
                        },
                    )
                    key.screen == 2 -> PlayingScreen(
                        coverModifier = Modifier,
                        albumName = current?.album?.takeIf { it.isNotBlank() } ?: (key.album?.name ?: ""),
                        starred = current?.let { starredIds.contains(it.id) } ?: false,
                        trackDownloaded = current?.let { downloadedTrackIds.contains(it.id) } ?: false,
                        lyrics = lyrics,
                        lyricsLoading = lyricsLoading,
                        showLyrics = showLyrics,
                        onToggleLyrics = { showLyrics = !showLyrics },
                        onSeekPlay = { ms ->
                            playback.seekTo(ms)
                            if (!playback.playing) playback.toggle()
                        },
                        rating = rating,
                        onRating = { v ->
                            rating = v
                            current?.let { Ratings.set(ctx, it.id, v) }
                        },
                        onToggleStar = { current?.let { toggleStar(it) } },
                        onDownloadTrack = {
                            current?.let { tr ->
                                Downloads.enqueueTrack(ctx, tr) { tt -> streamUrl(client, tt) }
                                scope.launch {
                                    delay(800)
                                    downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) }
                                }
                            }
                        },
                        track = current,
                        coverUrl = current?.let { client.coverUrl(it.albumId, 600) } ?: "",
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        playing = playback.playing,
                        onBack = { goBack() },
                        onToggle = { playback.toggle() },
                        onPrev = { playback.prev() },
                        onNext = { playback.next() },
                        onSeek = { playback.seekTo(it) },
                    )
                    else -> if (key.album == null) {
                        HomeScreen(
                            client = client,
                            albums = albums,
                            loading = loading,
                            error = error,
                            coverUrl = coverOf,
                            onOpen = onOpenAlbum,
                            onSettings = onOpenSettings,
                            onRetry = { scope.launch { loadAlbums(cfg) } },
                            onSearch = onOpenSearch,
                            onOpenArtist = onOpenArtist,
                            starred = starred,
                            tab = homeTab,
                            onTabChange = { homeTab = it },
                            ratingOf = { id -> Ratings.get(ctx, id) },
                            downloads = downloads,
                            onPlayTrack = { tr ->
                                playback.play(listOf(tr), 0, tr.album)
                                playback.tick()
                            },
                            onPlayStarred = { index ->
                                if (starred.isNotEmpty()) {
                                    playback.play(starred, index, "starred")
                                    playback.tick()
                                    homeTab = 3
                                    navigate(NavKey(2, null))
                                }
                            },

                        )
                    } else {
                        val album = key.album
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
                            coverModifier = Modifier,
                            tracks = tracks,
                            coverUrl = client.coverUrl(album.coverId),
                            currentTrackId = current?.id,
                            onBack = { goBack() },
                            download = downloads.firstOrNull { it.key == "album:${album.id}" },
                            onDownload = {
                                Downloads.enqueue(ctx, album, tracks) { tr -> streamUrl(client, tr) }
                                scope.launch {
                                    delay(600)
                                    downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) }
                                }
                            },
                            onShuffle = {
                                if (tracks.isNotEmpty()) {
                                    playback.playShuffled(tracks, album.id)
                                    playback.tick()
                                }
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
                }
                }

                // 播放页有自己的控制区，不再显示底栏
                if (screen != 2) {
                    YoinBar(
                        pulseKey = screen,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp),
                        title = current?.title ?: "",
                        artist = current?.artist ?: "",
                        coverUrl = current?.let { client.coverUrl(it.albumId, 200) } ?: "",
                        playing = playback.playing,
                        enabled = current != null,
                        onHome = { navStack.clear(); screen = 0; openAlbum = null },
                        onOpenPlayer = { navigate(NavKey(2, openAlbum)) },
                        onToggle = {
                            if (current != null) playback.toggle()
                            else if (albums.isNotEmpty()) openAlbum = albums.first()
                        },
                    )
                }
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    starred: List<Track> = emptyList(),
    onPlayStarred: (Int) -> Unit = {},
    tab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    ratingOf: (String) -> Float = { 0f },
    downloads: List<AlbumDownload> = emptyList(),
    onPlayTrack: (Track) -> Unit = {},
    coverShared: @Composable (String) -> Modifier = { Modifier },
) {
    var sortIndex by remember(tab) { mutableIntStateOf(0) }
    var showSort by remember { mutableStateOf(false) }
    // tab 由上层持有，返回首页时能保持原来的标签
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var newest by remember { mutableStateOf<List<Album>>(emptyList()) }
    var recent by remember { mutableStateOf<List<Album>>(emptyList()) }

    var tabLoading by remember { mutableStateOf(false) }
    var tabError by remember { mutableStateOf<String?>(null) }

    // 启动就拉一次艺人，让标签上的数量一开始就是对的
    LaunchedEffect(Unit) {
        if (artists.isEmpty()) {
            runCatching { artists = withContext(Dispatchers.IO) { client.artists() } }
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

    LaunchedEffect(tab) { showSort = false }
    val sortOptions = when (tab) {
        1 -> listOf("名称", "专辑数量")
        2 -> listOf("最近添加", "名称", "发行年份")
        3 -> listOf("收藏顺序", "名称", "艺人", "我的评分")
        else -> listOf("字母顺序", "发行年份", "歌曲数量", "艺人")
    }
    val sortedAlbums = sortAlbums(albums, sortIndex)
    val sortedArtists = sortArtists(artists, sortIndex)
    val sortedNewest = sortAlbums(newest, sortIndex)
    val sortedRecent = sortAlbums(recent, sortIndex)
    val sortedStarred = sortStarred(starred, sortIndex, ratingOf)

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
        HomeHeader(
            selected = tab,
            albumCount = albums.size,
            artistCount = artists.size,
            starredCount = starred.size,
            onTab = onTabChange,
            onSettings = onSettings,
            onSort = { showSort = true },
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
            else -> AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween<Float>(220)) + slideInHorizontally { dir * it / 10 }) togetherWith
                        (fadeOut(tween<Float>(160)) + slideOutHorizontally { -dir * it / 10 })
                },
                label = "tab",
                modifier = Modifier.fillMaxSize(),
            ) { t ->
                when (t) {
                    3 -> StarredList(sortedStarred, onPlayStarred)
                    1 -> ArtistGrid(sortedArtists, onOpenArtist)
                    2 -> RecentList(
                        newest = sortedNewest,
                        recent = sortedRecent,
                        client = client,
                        downloads = downloads,
                        albums = albums,
                        coverUrl = coverUrl,
                        onOpen = onOpen,
                        onPlayTrack = onPlayTrack,
                        loading = tabLoading && newest.isEmpty(),
                    )
                    else -> AlbumGrid(sortedAlbums, coverUrl, onOpen, coverShared)
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
                        1 -> "艺人排序"
                        2 -> "最近排序"
                        3 -> "收藏排序"
                        else -> "专辑排序"
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

        // 搜索：悬浮在底栏右上方
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 84.dp)
                .size(60.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50))
                .clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                YoinSymbols.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
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
            Text("还没有收藏的单曲", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
    albumCount: Int,
    artistCount: Int,
    starredCount: Int,
    onTab: (Int) -> Unit,
    onSettings: () -> Unit,
    onSort: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TabLabel("专辑", 0, selected, onTab, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        TabLabel("艺人", 1, selected, onTab, MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(16.dp))
        TabLabel("最近", 2, selected, onTab, MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(16.dp))
        TabLabel("收藏", 3, selected, onTab, MaterialTheme.colorScheme.inversePrimary)
        Spacer(Modifier.weight(1f))
        // 排序：收藏与设置之间，描边圆钮 + 自绘排序图标
        Box(Modifier.height(38.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                    .clickable(onClick = onSort),
                contentAlignment = Alignment.Center,
            ) {
                val sortColor = MaterialTheme.colorScheme.secondary
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
        }
        Spacer(Modifier.width(10.dp))
        // 与标题行居中对齐；描边圆钮，与实心首页键形成对比
        Box(Modifier.height(38.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50))
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    YoinSymbols.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun TabLabel(title: String, index: Int, selected: Int, onTab: (Int) -> Unit, accent: androidx.compose.ui.graphics.Color) {
    val active = selected == index
    Column(Modifier.clickable { onTab(index) }) {
        Text(
            title,
            // 选中的标签用自己的强调色，未选中用低对比灰
            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 26.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Light,
        )
    }
}

@Composable
private fun AlbumGrid(
    albums: List<Album>,
    coverUrl: (String) -> String,
    onOpen: (Album) -> Unit,
    coverShared: @Composable (String) -> Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album, coverUrl(album.coverId), coverModifier = coverShared(album.id)) { onOpen(album) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentList(
    client: SubsonicClient,
    newest: List<Album>,
    recent: List<Album>,
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
    var order by remember { mutableStateOf(loadRecentOrder(ctx)) }
    var collapsed by remember { mutableStateOf(emptySet<Int>()) }
    var showOrderSheet by remember { mutableStateOf(false) }

    fun persist(newOrder: List<Int>) {
        order = newOrder
        ctx.getSharedPreferences("lumen_recent", android.content.Context.MODE_PRIVATE)
            .edit().putString("order", newOrder.joinToString(",")).apply()
    }

    val sections = mapOf(
        0 to "最近添加",
        1 to "最近播放",
        2 to "已下载",
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
                        "最近",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "调整顺序",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable { showOrderSheet = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            order.forEach { section ->
                val isCollapsed = section in collapsed
                item(key = "h$section") {
                    SectionHeader(
                        title = sections[section] ?: "",
                        count = when (section) {
                            0 -> newest.size
                            1 -> recent.size
                            else -> downloads.size
                        },
                        collapsed = isCollapsed,
                        onToggle = {
                            collapsed = if (isCollapsed) collapsed - section else collapsed + section
                        },
                    )
                }
                if (!isCollapsed) {
                    when (section) {
                        0 -> items(rowsOf(newest), key = { "n${it.firstOrNull()?.id}" }) { row ->
                            HorizontalAlbumRow(row, coverUrl, onOpen)
                        }
                        1 -> items(rowsOf(recent), key = { "r${it.firstOrNull()?.id}" }) { row ->
                            HorizontalAlbumRow(row, coverUrl, onOpen)
                        }
                        else -> if (downloads.isEmpty()) {
                            item(key = "dempty") {
                                Text(
                                    "还没有下载内容",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                        } else {
                            items(rowsOfDownloads(downloads), key = { "d${it.firstOrNull()?.key}" }) { row ->
                                HorizontalDownloadRow(row, albums, singleTracks, coverUrl, onOpen, onPlayTrack)
                            }
                        }
                    }
                }
            }
        }

        if (showOrderSheet) {
            ModalBottomSheet(
                onDismissRequest = { showOrderSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    "调整分区顺序",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(4.dp))
                order.forEachIndexed { index, section ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            sections[section] ?: "",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "↑",
                            color = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = index > 0) {
                                    persist(order.toMutableList().also {
                                        val a = it[index]; it[index] = it[index - 1]; it[index - 1] = a
                                    })
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        Text(
                            "↓",
                            color = if (index < order.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = index < order.lastIndex) {
                                    persist(order.toMutableList().also {
                                        val a = it[index]; it[index] = it[index + 1]; it[index + 1] = a
                                    })
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
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

/** 分区标题：点击折叠/展开，右侧小三角随状态旋转 */
@Composable
private fun SectionHeader(title: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    val angle by animateFloatAsState(if (collapsed) -90f else 0f, tween(220), label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(8.dp))
        Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        val chevronColor = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(
            Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = angle },
        ) {
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
            val stroke = 2.dp.toPx()
            drawLine(
                start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.4f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.7f),
                color = chevronColor,
                strokeWidth = stroke,
                cap = cap,
            )
            drawLine(
                start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.7f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.4f),
                color = chevronColor,
                strokeWidth = stroke,
                cap = cap,
            )
        }
    }
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
                ).joinToString(" · ").ifBlank { "单曲" }
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
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        // 封面：正方形区域（右侧 26dp 留给竖排艺人名），避免每张卡都做一次子组合
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            AsyncImage(
                model = url,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = coverModifier.fillMaxSize().padding(end = 26.dp).clip(RoundedCornerShape(topStart = 20.dp)),
            )
            VerticalArtistName(
                text = album.artist,
                modifier = Modifier.align(Alignment.CenterEnd).width(26.dp).fillMaxHeight(),
            )
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
    pulseKey: Int = 0,
    title: String,
    artist: String,
    coverUrl: String,
    playing: Boolean,
    enabled: Boolean,
    onHome: () -> Unit,
    onOpenPlayer: () -> Unit,
    onToggle: () -> Unit,
) {
    val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(pulseKey) {
        pulse.snapTo(1.03f)
        pulse.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 480f))
    }
    Row(
        modifier.fillMaxWidth().graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
        }.padding(horizontal = 12.dp).height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 首页：独立圆形按钮
        Surface(
            onClick = onHome,
            shape = RoundedCornerShape(22.dp),
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
