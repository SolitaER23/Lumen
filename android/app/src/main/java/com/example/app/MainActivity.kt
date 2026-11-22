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
import com.example.app.data.Album
import com.example.app.data.AlbumCache
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
import com.example.app.playback.MediaCache
import com.example.app.playback.PlaybackEngine
import com.example.app.playback.PlaybackService
import com.example.app.ui.AlbumScreen
import com.example.app.ui.ArtistScreen
import com.example.app.ui.SearchScreen
import com.example.app.ui.FallbackScheme
import com.example.app.ui.LicensesScreen
import com.example.app.ui.MoodScreen
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
    var accounts by remember { mutableStateOf<List<com.example.app.data.Account>>(emptyList()) }
    var activeAccountId by remember { mutableStateOf<String?>(null) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var openAlbum by remember { mutableStateOf<Album?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    // 0 = 首页, 1 = 专辑页, 2 = 播放页
    var screen by remember { mutableStateOf(0) }
    var scheme by remember { mutableStateOf(FallbackScheme) }
    var contrast by remember { mutableFloatStateOf(com.example.app.data.Appearance.contrast(ctx)) }
    var downloads by remember { mutableStateOf<List<AlbumDownload>>(emptyList()) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var starred by remember { mutableStateOf<List<Track>>(emptyList()) }
    var starredIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var homeTab by remember { mutableIntStateOf(0) }
    val homeCache = remember { HomeCache() }
    val albumTracksCache = remember { mutableStateMapOf<String, List<Track>>() }
    val saveableStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    fun stateKeyOf(k: NavKey): String = when (k.screen) {
        0 -> "home"
        1 -> "album:${k.album?.id ?: ""}"
        2 -> "player"
        3 -> "settings"
        4 -> "downloads"
        5 -> "artist:${selectedArtist?.id ?: ""}"
        6 -> "search"
        7 -> "stats"
        else -> "s${k.screen}"
    }
    var lyrics by remember { mutableStateOf<List<LyricLine>?>(null) }
    var lyricsTrackId by remember { mutableStateOf<String?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(true) }
    var rating by remember { mutableFloatStateOf(0f) }
    var scrobbledId by remember { mutableStateOf<String?>(null) }
    var nowPlayingId by remember { mutableStateOf<String?>(null) }
    var mood by remember { mutableStateOf<com.example.app.data.MoodResult?>(null) }
    var moodAnalyzing by remember { mutableStateOf(false) }
    var moodPhase by remember { mutableStateOf("") }
    val navStack = remember { androidx.compose.runtime.mutableStateListOf<NavKey>() }

    /** 原路返回（立即执行，手势/动画在外部包一层） */
    fun performBack() {
        val prev = navStack.removeLastOrNull() ?: return
        screen = prev.screen
        openAlbum = prev.album
    }


    suspend fun loadAlbums(c: ServerConfig) {
        if (albums.isEmpty()) {
            val cached = withContext(Dispatchers.IO) { AlbumCache.load(ctx) }
            if (cached.isNotEmpty()) albums = cached
        }
        loading = albums.isEmpty()
        error = null
        var attempt = 0
        while (attempt < 3) {
            try {
                albums = withContext(Dispatchers.IO) { SubsonicClient(c).albums() }
                error = null
                withContext(Dispatchers.IO) { AlbumCache.save(ctx, albums) }
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
        if (cfg.complete) {
            if (accounts.isEmpty()) {
                com.example.app.data.Accounts.ensure(ctx, cfg)
                accounts = com.example.app.data.Accounts.list(ctx)
                activeAccountId = com.example.app.data.Accounts.activeId(ctx)
            }
            if (albums.isEmpty()) loadAlbums(cfg)
        }
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

    fun reloadForAccount(c: ServerConfig) {
        albums = emptyList()
        starred = emptyList()
        starredIds = emptySet()
        albumTracksCache.clear()
        openAlbum = null
        selectedArtist = null
        navStack.clear()
        screen = 0
        homeTab = 0
        scope.launch {
            loadAlbums(c)
            runCatching {
                val list = withContext(Dispatchers.IO) { client.starredTracks() }
                starred = list
                starredIds = list.map { it.id }.toSet()
            }
        }
    }

    fun switchAccount(account: com.example.app.data.Account) {
        val c = com.example.app.data.Accounts.activate(ctx, account)
        cfg = c
        accounts = com.example.app.data.Accounts.list(ctx)
        activeAccountId = com.example.app.data.Accounts.activeId(ctx)
        reloadForAccount(c)
    }

    fun addAccount(base: String, user: String, password: String) {
        val a = com.example.app.data.Accounts.add(ctx, base, user, password)
        switchAccount(a)
    }

    fun removeAccount(account: com.example.app.data.Account) {
        com.example.app.data.Accounts.remove(ctx, account)
        accounts = com.example.app.data.Accounts.list(ctx)
        activeAccountId = com.example.app.data.Accounts.activeId(ctx)
        if (accounts.isEmpty()) {
            com.example.app.data.SettingsStore.clear(ctx)
            cfg = ServerConfig("", "", "")
        } else {
            cfg = com.example.app.data.SettingsStore.load(ctx)
            reloadForAccount(cfg)
        }
    }

    // 进入动画：以最后一次按下位置为原点展开
    val enterAnim = remember { Animatable(1f) }
    var enterOriginX by remember { mutableFloatStateOf(0.5f) }
    var enterOriginY by remember { mutableFloatStateOf(0.5f) }
    var enterJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    /** 去下一页，并把"来处"压栈；从点击位置展开新页面 */
    fun navigate(target: NavKey) {
        if (target.screen == screen && target.album?.id == openAlbum?.id) return
        enterJob?.cancel()
        enterJob = scope.launch {
            enterAnim.snapTo(0f)
            navStack.add(NavKey(screen, openAlbum))
            screen = target.screen
            openAlbum = target.album
            enterAnim.animateTo(
                1f,
                spring(dampingRatio = 0.84f, stiffness = 340f),
            )
        }
    }

    // 手势/按钮共用的返回进度：跟手拖动，松手后补完剩余动画再真正出栈
    val backAnim = remember { Animatable(0f) }
    // 返回出栈时跳过 AnimatedContent 的淡入/缩放，避免“上一页刚落地又弹一下”
    var instantNav by remember { mutableStateOf(false) }
    // 返回手势开始时的“上一页” key，出栈后继续保持组合，避免切换瞬间重建
    var heldBack by remember { mutableStateOf<NavKey?>(null) }
    fun goBack() {
        if (navStack.isEmpty()) return
        heldBack = navStack.lastOrNull()
        scope.launch {
            if (backAnim.value < 1.4f) {
                // 把当前页完全送出屏幕，再无缝切换
                backAnim.animateTo(1.4f, tween(260, easing = FastOutSlowInEasing))
            }
            // 等这一帧把“页面已送出”的画面渲染出来，再出栈，避免空白帧
            kotlinx.coroutines.delay(48)
            instantNav = true
            performBack()
            backAnim.snapTo(0f)
            androidx.compose.runtime.withFrameNanos { }
            instantNav = false
        }
    }

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
    // 冷启动恢复上次队列与进度（保持暂停，不自动播放）
    LaunchedEffect(cfg.complete, playback) {
        if (cfg.complete && playback.queue.isEmpty()) {
            val saved = withContext(Dispatchers.IO) { com.example.app.playback.PlaybackStateStore.load(ctx) }
            if (saved != null && saved.tracks.isNotEmpty()) {
                playback.restore(saved.tracks, saved.index, saved.positionMs, saved.albumId)
                delay(500)
                playback.enforcePaused()
            }
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
                scrobbledId = null
                mood = com.example.app.data.MoodStore.get(ctx, tr.id)
                android.util.Log.i(
                    "LumenMood",
                    "load mood id=${tr.id} found=${mood != null} beats=${mood?.beatsMs?.size ?: -1} tempo=${mood?.tempo ?: -1f}",
                )
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
            delay(250)
        }
    }
    // 播放上报：Now Playing + 达到一半/4 分钟时 submission（Navidrome Stat 靠它记账）
    LaunchedEffect(playback.current?.id, playback.playing) {
        val tr = playback.current
        if (playback.playing && tr != null && nowPlayingId != tr.id) {
            nowPlayingId = tr.id
            withContext(Dispatchers.IO) {
                val r = runCatching { client.scrobble(tr.id, submission = false) }
                android.util.Log.i("LumenScrobble", "nowPlaying ${tr.id} ok=${r.isSuccess} err=${r.exceptionOrNull()?.message}")
            }
        }
    }
    LaunchedEffect(playback.positionMs, playback.playing, playback.current?.id) {
        val tr = playback.current ?: return@LaunchedEffect
        if (!playback.playing || scrobbledId == tr.id) return@LaunchedEffect
        val duration = playback.durationMs.takeIf { it > 0 } ?: (tr.seconds * 1000L)
        val threshold = if (duration > 0) minOf(duration / 2, 240_000L) else 120_000L
        if (playback.positionMs >= threshold) {
            scrobbledId = tr.id
            withContext(Dispatchers.IO) {
                val r = runCatching { client.scrobble(tr.id, submission = true) }
                android.util.Log.i("LumenScrobble", "submit ${tr.id} ok=${r.isSuccess} err=${r.exceptionOrNull()?.message}")
            }
        }
    }
    LaunchedEffect(backAnim.value) {
        if (backAnim.value == 0f) heldBack = null
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

    LaunchedEffect(current?.id, playback.playing, mood) {
        com.example.app.playback.MusicPulse.sync(current?.id, playback.playing, mood)
    }
    LaunchedEffect(playback.positionMs) {
        com.example.app.playback.MusicPulse.setPosition(playback.positionMs)
    }

    // 封面取色：主色跟着正在播的专辑走
    LaunchedEffect(current?.albumId, contrast, mood) {
        current?.let { t ->
            schemeFromCover(client.coverUrl(t.albumId, 200), contrast, mood)?.let { scheme = it }
        }
    }

    PredictiveBackHandler(enabled = screen > 0) { progress ->
        heldBack = navStack.lastOrNull()
        try {
            progress.collect { backAnim.snapTo(it.progress) }
            // 手势结束：补完剩余位移再出栈，保证与手指连续
            if (backAnim.value < 1.4f) {
                backAnim.animateTo(1.4f, tween(230, easing = FastOutSlowInEasing))
            }
            // 等这一帧把“页面已送出”的画面渲染出来，再出栈，避免空白帧
            kotlinx.coroutines.delay(48)
            // 出栈瞬间跳过 AnimatedContent 过渡，避免新页面再缩放闪一下
            instantNav = true
            // 手势返回必须走页面栈，否则从「专辑歌曲列表」会直接跳回首页/艺人页
            when {
                navStack.isNotEmpty() -> performBack()
                // 从底栏直接进播放页时没有专辑上下文，返回应回首页
                screen == 2 && openAlbum != null -> screen = 1
                else -> {
                    screen = 0
                    openAlbum = null
                }
            }
            backAnim.snapTo(0f)
            kotlinx.coroutines.delay(90)
            instantNav = false
        } catch (_: CancellationException) {
            // 手势取消：回到原位
            backAnim.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    val onOpenAlbum: (Album) -> Unit = remember {
        { al -> navigate(NavKey(1, al)) }
    }
    val onGoHome: () -> Unit = remember { { navStack.clear(); screen = 0; openAlbum = null } }
    val onOpenSettings: () -> Unit = remember { { navigate(NavKey(3, null)) } }
    val onOpenSearch: () -> Unit = remember { { navigate(NavKey(6, null)) } }
    val onOpenArtist: (Artist) -> Unit = remember { { a -> selectedArtist = a; navigate(NavKey(5, null)) } }
    val onOpenArtistFromSearch: (Artist) -> Unit = remember { { a -> selectedArtist = a; navigate(NavKey(5, null)) } }

    val screenContent: @Composable (NavKey) -> Unit = { key ->
                saveableStates.SaveableStateProvider(stateKeyOf(key)) {
                when {
                    key.screen == 9 -> LicensesScreen(onBack = { goBack() })
                    key.screen == 8 -> MoodScreen(onBack = { goBack() })
                    key.screen == 7 -> com.example.app.ui.StatsScreen(
                        initial = com.example.app.data.StatsStore.load(ctx),
                        onBack = { goBack() },
                    )
                    key.screen == 6 -> SearchScreen(
                        client = client,
                        coverUrl = { client.coverUrl(it) },
                        onBack = { goBack() },
                        onOpenAlbum = { al -> navigate(NavKey(1, al)) },
                        onOpenArtist = onOpenArtistFromSearch,
                    )
                    key.screen == 5 -> selectedArtist?.let { a ->
                        ArtistScreen(
                            client = client,
                            artist = a,
                            coverUrl = { client.coverUrl(it) },
                            onBack = { goBack() },
                            onOpenAlbum = { al -> navigate(NavKey(1, al)) },
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
                        accounts = accounts,
                        activeAccountId = activeAccountId,
                        onSwitchAccount = { switchAccount(it) },
                        onAddAccount = { base, user, password -> addAccount(base, user, password) },
                        onRemoveAccount = { removeAccount(it) },
                        onOpenMood = { navigate(NavKey(8, null)) },
                        onOpenLicenses = { navigate(NavKey(9, null)) },
                        versionName = BuildConfig.VERSION_NAME,
                        contrast = contrast,
                        onContrast = { v ->
                            contrast = v
                            com.example.app.data.Appearance.setContrast(ctx, v)
                        },
                        onClearCache = {
                            // 只清流媒体/图片缓存；下载曲目的 cache key（URI）保留
                            MediaCache.clearStreamingCache(ctx, Downloads.downloadedUris(ctx))
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
                        playMode = playback.playMode,
                        onCyclePlayMode = { playback.cyclePlayMode() },
                        coverDirection = playback.coverDirection,
                        onSeekPlay = { ms ->
                            playback.seekTo(ms)
                            if (!playback.playing) playback.toggle()
                        },
                        rating = rating,
                        mood = mood,
                        moodAnalyzing = moodAnalyzing,
                        moodPhase = moodPhase,
                        onAnalyzeMood = {
                            current?.let { tr ->
                                moodAnalyzing = true
                                moodPhase = "准备"
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            com.example.app.data.MoodAnalyzer(ctx).analyze(
                                                tr.id,
                                                streamUrl(client, tr),
                                            ) { phase, p -> moodPhase = "$phase ${(p * 100).toInt()}%" }
                                        }
                                    }.onSuccess {
                                        com.example.app.data.MoodStore.put(ctx, it)
                                        mood = it
                                    }.onFailure {
                                        android.util.Log.e("LumenMood", "mood analysis failed", it)
                                    }
                                    moodAnalyzing = false
                                }
                            }
                        },
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
                        durationMs = playback.durationMs.takeIf { it > 0 } ?: ((current?.seconds ?: 0) * 1000L),
                        playing = playback.playing,
                        onBack = { goBack() },
                        onToggle = { playback.toggle() },
                        onPrev = { playback.prev() },
                        onNext = { playback.next() },
                        onSeek = { playback.seekTo(it) },
                    )
                    else -> if (key.album == null) {
                        MaterialTheme(colorScheme = HomeScheme, typography = lumenTypography()) {
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
                            current = current,
                            playing = playback.playing,
                            positionMs = playback.positionMs,
                            durationMs = playback.durationMs.takeIf { it > 0 } ?: ((current?.seconds ?: 0) * 1000L),
                            onToggle = { if (current != null) playback.toggle() },
                            onOpenPlayer = { navigate(NavKey(2, null)) },
                            onOpenStats = { navigate(NavKey(7, null)) },
                            tab = homeTab,
                            onTabChange = { homeTab = it },
                            ratingOf = { id -> Ratings.get(ctx, id) },
                            downloads = downloads,
                            cache = homeCache,
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
                        }
                    } else {
                        val album = key.album
                        val albumTracks = albumTracksCache[album.id]
                        if (albumTracks == null) {
                            LaunchedEffect(album.id) {
                                val fetched = try {
                                    withContext(Dispatchers.IO) { client.tracks(album.id) }.also {
                                        android.util.Log.i(
                                            "LumenAlbum",
                                            "album=${album.id} tracks=${it.size} discs=${it.map { t -> t.disc }.distinct()}",
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("LumenAlbum", "album=${album.id} fetch failed", e)
                                    error = e.message
                                    emptyList()
                                }
                                // 不要把空结果缓存下来：否则一次网络抖动会让这张专辑一直空白
                                if (fetched.isNotEmpty()) albumTracksCache[album.id] = fetched
                            }
                        }
                        // 进入专辑即静默分析缺失曲目（无 UI、无提示）
                        LaunchedEffect(album.id, albumTracksCache[album.id]?.size) {
                            val list = albumTracksCache[album.id].orEmpty()
                            if (list.isNotEmpty()) {
                                withContext(Dispatchers.IO) {
                                    val analyzer = com.example.app.data.MoodAnalyzer(ctx)
                                    list.forEach { tr ->
                                        if (com.example.app.data.MoodStore.get(ctx, tr.id) == null) {
                                            runCatching { analyzer.analyze(tr.id, streamUrl(client, tr)) { _, _ -> } }
                                                .onSuccess { com.example.app.data.MoodStore.put(ctx, it) }
                                        }
                                    }
                                }
                            }
                        }
                        AlbumScreen(
                            album = album,
                            coverModifier = Modifier,
                            tracks = albumTracksCache[album.id].orEmpty(),
                            coverUrl = client.coverUrl(album.coverId),
                            currentTrackId = current?.id,
                            onBack = { goBack() },
                            download = downloads.firstOrNull { it.key == "album:${album.id}" },
                            contrast = contrast,
                            onDownload = {
                                Downloads.enqueue(ctx, album, albumTracksCache[album.id].orEmpty()) { tr -> streamUrl(client, tr) }
                                scope.launch {
                                    delay(600)
                                    downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) }
                                }
                            },
                            onShuffle = {
                                val list = albumTracksCache[album.id].orEmpty()
                                if (list.isNotEmpty()) {
                                    playback.playShuffled(list, album.id)
                                    playback.tick()
                                }
                            },
                            onPlay = { start ->
                                val list = albumTracksCache[album.id].orEmpty()
                                if (list.isNotEmpty()) {
                                    playback.play(list, start, album.id)
                                    playback.tick()
                                }
                            },
                        )
                    }
                }
                }
    }

    val animatedScheme = com.example.app.ui.rememberAnimatedColorScheme(scheme)
    MaterialTheme(colorScheme = animatedScheme, typography = lumenTypography()) {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) {
                                    event.changes.firstOrNull()?.position?.let { pos ->
                                        if (size.width > 0 && size.height > 0) {
                                            enterOriginX = (pos.x / size.width).coerceIn(0f, 1f)
                                            enterOriginY = (pos.y / size.height).coerceIn(0f, 1f)
                                        }
                                    }
                                }
                            }
                        }
                    },
            ) {
                com.example.app.ui.ExpressiveBackdrop(Modifier.matchParentSize())
                // 预测性返回：底层压暗，露出背景；上层内容跟手右移 + 缩放 + 圆角
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Color.Black.copy(
                                alpha = 0.32f * backAnim.value.coerceAtMost(1f),
                            ),
                        ),
                )
                // 页面层：始终保留「上一页 + 当前页」两份实时组合；返回时不重建，避免到位后闪一下
                val pageBack = if (backAnim.value > 0.001f) (heldBack ?: navStack.lastOrNull()) else null
                val pageKeys = listOfNotNull(pageBack, NavKey(screen, openAlbum)).distinct()
                pageKeys.forEachIndexed { idx, pageKey ->
                    val isFront = idx == pageKeys.lastIndex
                    key(pageKey) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val p = backAnim.value
                                    val e = enterAnim.value
                                    if (isFront) {
                                        if (p > 0f) {
                                            val over = (p - 1f).coerceAtLeast(0f)
                                            translationX = size.width * (0.24f * p + 1.9f * over)
                                            alpha = if (p > 1.2f) ((1.4f - p) / 0.2f).coerceIn(0f, 1f) else 1f
                                            val s = 1f - 0.07f * p.coerceAtMost(1f)
                                            scaleX = s
                                            scaleY = s
                                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                        } else if (e < 0.999f) {
                                            // 进入：从点击位置展开
                                            val s = 0.90f + 0.10f * e
                                            scaleX = s
                                            scaleY = s
                                            alpha = 0.55f + 0.45f * e
                                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(enterOriginX, enterOriginY)
                                        }
                                    } else {
                                        val p2 = p.coerceAtMost(1f)
                                        val s = 0.94f + 0.06f * p2
                                        scaleX = s
                                        scaleY = s
                                        translationX = -size.width * 0.05f * (1f - p2)
                                    }
                                }
                                .clip(
                                    RoundedCornerShape(
                                        when {
                                            !isFront -> 0.dp
                                            backAnim.value > 0f ->
                                                (26.dp * backAnim.value.coerceAtMost(1f)).coerceAtLeast(0.dp)
                                            enterAnim.value < 1f ->
                                                (24.dp * (1f - enterAnim.value.coerceIn(0f, 1f))).coerceAtLeast(0.dp)
                                            else -> 0.dp
                                        },
                                    ),
                                ),
                        ) {
                            // 每一页都带自己的背景：从底层升到前景时亮度不变，避免切换瞬间闪一下
                            if (isFront || backAnim.value > 0.01f) {
                                com.example.app.ui.ExpressiveBackdrop(
                                    Modifier.matchParentSize(),
                                    drivePulse = isFront,
                                )
                            }
                            screenContent(pageKey)
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
                        onPrev = { playback.prev() },
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

/** Google Sans Flex（OFL 1.1）：拉丁与数字用自定义字体，中文自动回退系统思源黑体。 */
private val LumenFontFamily = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.google_sans_flex_regular, androidx.compose.ui.text.font.FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.google_sans_flex_medium, androidx.compose.ui.text.font.FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.google_sans_flex_semibold, androidx.compose.ui.text.font.FontWeight.SemiBold),
)

/** 把默认 Typography 的每个层级都换成 Google Sans Flex。 */
private fun lumenTypography(): androidx.compose.material3.Typography {
    val base = androidx.compose.material3.Typography()
    fun androidx.compose.ui.text.TextStyle.gsf() = copy(fontFamily = LumenFontFamily)
    return androidx.compose.material3.Typography(
        displayLarge = base.displayLarge.gsf(),
        displayMedium = base.displayMedium.gsf(),
        displaySmall = base.displaySmall.gsf(),
        headlineLarge = base.headlineLarge.gsf(),
        headlineMedium = base.headlineMedium.gsf(),
        headlineSmall = base.headlineSmall.gsf(),
        titleLarge = base.titleLarge.gsf(),
        titleMedium = base.titleMedium.gsf(),
        titleSmall = base.titleSmall.gsf(),
        bodyLarge = base.bodyLarge.gsf(),
        bodyMedium = base.bodyMedium.gsf(),
        bodySmall = base.bodySmall.gsf(),
        labelLarge = base.labelLarge.gsf(),
        labelMedium = base.labelMedium.gsf(),
        labelSmall = base.labelSmall.gsf(),
    )
}

class HomeCache {
    var artists by androidx.compose.runtime.mutableStateOf<List<Artist>>(emptyList())
    var newest by androidx.compose.runtime.mutableStateOf<List<Album>>(emptyList())
    var recent by androidx.compose.runtime.mutableStateOf<List<Album>>(emptyList())
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
    current: Track? = null,
    playing: Boolean = false,
    positionMs: Long = 0,
    durationMs: Long = 0,
    onToggle: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    cache: HomeCache = remember { HomeCache() },
    coverShared: @Composable (String) -> Modifier = { Modifier },
) {
    var sortIndex by remember(tab) { mutableIntStateOf(0) }
    var showSort by remember { mutableStateOf(false) }
    // tab 由上层持有，返回首页时能保持原来的标签
    val artists = cache.artists
    val newest = cache.newest
    val recent = cache.recent

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
        tabLoading = true
        tabError = null
        try {
            when (tab) {
                1 -> if (artists.isEmpty()) cache.artists = withContext(Dispatchers.IO) { client.artists() }
                2 -> {
                    if (newest.isEmpty()) cache.newest = withContext(Dispatchers.IO) { client.newestAlbums() }
                    if (recent.isEmpty()) cache.recent = withContext(Dispatchers.IO) { client.recentAlbums() }
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
            title = when (tab) {
                1 -> "艺人"
                2 -> "最近"
                3 -> "收藏"
                else -> "专辑"
            },
            subtitle = when (tab) {
                1 -> "${artists.size} 位艺人 · ${sortOptions.getOrElse(sortIndex) { "名称" }}"
                2 -> "最近添加 ${newest.size} · 最近播放 ${recent.size}"
                3 -> "${starred.size} 首单曲 · ${sortOptions.getOrElse(sortIndex) { "收藏顺序" }}"
                else -> "${albums.size} 张专辑 · ${sortOptions.getOrElse(sortIndex) { "字母顺序" }}"
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
                ) { Text("重试") }
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
                            recent = sortedRecent,
                            client = client,
                            downloads = downloads,
                            albums = albums,
                            coverUrl = coverUrl,
                            onOpen = onOpen,
                            onPlayTrack = onPlayTrack,
                            loading = tabLoading && newest.isEmpty(),
                        )
                        else -> Column(Modifier.fillMaxSize()) {
                            SectionRow("全部专辑", "共 ${albums.size} 张")
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
                Icon(YoinSymbols.Search, contentDescription = "搜索", tint = HomeText, modifier = Modifier.size(22.dp))
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
                Icon(YoinSymbols.Settings, contentDescription = "设置", tint = HomeText, modifier = Modifier.size(21.dp))
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
            listOf("专辑", "艺人", "最近", "收藏").forEachIndexed { i, label ->
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
private val HomeCard = androidx.compose.ui.graphics.Color(0xFF2A2B31)
private val HomeText = androidx.compose.ui.graphics.Color(0xFFF5F5F7)
private val HomeSub = androidx.compose.ui.graphics.Color(0xFF8B8B94)
private val HomeAccent = androidx.compose.ui.graphics.Color(0xFFE5484D)
private val HomeBg = androidx.compose.ui.graphics.Color(0xFF202126)

/** 首页专用的中性灰阶配色：颜色交给封面，界面只用灰阶 + 一种强调色。 */
private val HomeScheme = androidx.compose.material3.darkColorScheme(
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
    var collapsed by rememberSaveable { mutableStateOf(emptyList<Int>()) }
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
    onPrev: () -> Unit = {},
) {
    val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(pulseKey) {
        pulse.snapTo(1.02f)
        pulse.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 480f))
    }
    Row(
        modifier.fillMaxWidth().graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
        }.padding(horizontal = 12.dp).height(68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {

        // 迷你播放条：独立浮层，深灰底、白字、白色圆形播放键
        Row(
            Modifier
                .weight(1f)
                .height(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HomeCard)
                .clickable(enabled = enabled, onClick = onOpenPlayer)
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (enabled) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(10.dp))
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
                            color = HomeText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            artist, fontSize = 11.sp, color = HomeSub,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text(
                    "还没有播放",
                    color = HomeSub,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
            IconButton(onClick = onPrev, enabled = enabled, modifier = Modifier.size(38.dp)) {
                Icon(
                    YoinSymbols.SkipPrevious,
                    contentDescription = "上一首",
                    tint = if (enabled) HomeSub else HomeSub.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White)
                    .clickable(enabled = enabled, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) YoinSymbols.PauseFilled else YoinSymbols.PlayFilled,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = androidx.compose.ui.graphics.Color(0xFF202126),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}