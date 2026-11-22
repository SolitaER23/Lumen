package app.lumen.player

import app.lumen.player.util.L10n
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import app.lumen.player.ui.HomeScreen
import app.lumen.player.ui.HomeScheme
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
        app.lumen.player.util.L10n.init(this)
        // Pixel 10a 是 120Hz 面板：明确请求固定源 120Hz，动画期间才不会被降到 60
        window.attributes = window.attributes.apply { preferredRefreshRate = 120f }

        setContent {
            App()
        }
    }
}

/** 页面栈的动画键：屏幕 + 当前专辑，用来判断方向与触发转场 */
private data class NavKey(val screen: Int, val album: Album?)

/** m4a（ALAC）在 Pixel 上能L10n.t("播")但无声，统一走 320k MP3 转码 */
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
    var accounts by remember { mutableStateOf<List<app.lumen.player.data.Account>>(emptyList()) }
    var activeAccountId by remember { mutableStateOf<String?>(null) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var openAlbum by remember { mutableStateOf<Album?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    // 0 = 首页, 1 = 专辑页, 2 = 播放页
    var screen by remember { mutableStateOf(0) }
    var scheme by remember { mutableStateOf(FallbackScheme) }
    var contrast by remember { mutableFloatStateOf(app.lumen.player.data.Appearance.contrast(ctx)) }
    var fadeMs by remember { mutableLongStateOf(app.lumen.player.data.Appearance.fadeMs(ctx)) }
    var smartFade by remember { mutableStateOf(app.lumen.player.data.Appearance.smartFade(ctx)) }
    var radioVibe by remember { mutableStateOf(app.lumen.player.data.RadioVibe.NIGHT) }
    var radioTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var radioLoading by remember { mutableStateOf(false) }
    var radioProgress by remember { mutableFloatStateOf(0f) }
    var radioError by remember { mutableStateOf<String?>(null) }
    var langMode by remember { mutableStateOf(app.lumen.player.util.L10n.mode(ctx)) }
    var showOnboarding by remember { mutableStateOf(!app.lumen.player.data.Onboarding.completed(ctx)) }
    var legalAccepted by remember { mutableStateOf(app.lumen.player.data.Legal.accepted(ctx)) }
    var legalDoc by remember { mutableStateOf<String?>(null) }
    var libraryVersion by remember { mutableIntStateOf(0) }
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
        10 -> "radio"
        11 -> "changelog"
        else -> "s${k.screen}"
    }
    var lyrics by remember { mutableStateOf<List<LyricLine>?>(null) }
    var lyricsTrackId by remember { mutableStateOf<String?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    // 歌词 LRU：最多缓存 40 首，避免来回切歌重复请求
    val lyricsCache = remember { LinkedHashMap<String, List<LyricLine>?>() }
    var showLyrics by remember { mutableStateOf(true) }
    var rating by remember { mutableFloatStateOf(0f) }
    var scrobbledId by remember { mutableStateOf<String?>(null) }
    var nowPlayingId by remember { mutableStateOf<String?>(null) }
    var mood by remember { mutableStateOf<app.lumen.player.data.MoodResult?>(null) }
    var moodAnalyzing by remember { mutableStateOf(false) }
    var moodPhase by remember { mutableStateOf("") }
    val navStack = remember { androidx.compose.runtime.mutableStateListOf<NavKey>() }

    /** 原路返回（立即执行，手势/动画在外部包一层） */
    fun performBack() {
        val prev = navStack.removeLastOrNull() ?: return
        screen = prev.screen
        openAlbum = prev.album
    }


    suspend fun loadAlbums(c: ServerConfig, useCache: Boolean = true) {
        if (useCache && albums.isEmpty()) {
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
                libraryVersion += 1
                withContext(Dispatchers.IO) { AlbumCache.save(ctx, albums) }
                break
            } catch (e: Exception) {
                error = e.message ?: L10n.t("加载失败")
                attempt++
                if (attempt < 3) delay(800L * attempt) // 退避后重试
            }
        }
        loading = false
    }

    LaunchedEffect(cfg.complete) {
        if (cfg.complete) {
            if (accounts.isEmpty()) {
                app.lumen.player.data.Accounts.ensure(ctx, cfg)
                accounts = app.lumen.player.data.Accounts.list(ctx)
                activeAccountId = app.lumen.player.data.Accounts.activeId(ctx)
            }
            if (albums.isEmpty()) loadAlbums(cfg)
        }
    }


    if (!legalAccepted) {
        MaterialTheme(colorScheme = FallbackScheme) {
            when (legalDoc) {
                "terms" -> app.lumen.player.ui.TermsScreen(onBack = { legalDoc = null })
                "privacy" -> app.lumen.player.ui.PrivacyScreen(onBack = { legalDoc = null })
                else -> app.lumen.player.ui.ConsentScreen(
                    onOpenTerms = { legalDoc = "terms" },
                    onOpenPrivacy = { legalDoc = "privacy" },
                    onAccept = {
                        app.lumen.player.data.Legal.accept(ctx)
                        legalAccepted = true
                    },
                    onDecline = { (ctx as? android.app.Activity)?.finish() },
                )
            }
        }
        return
    }

    if (showOnboarding) {
        MaterialTheme(colorScheme = FallbackScheme) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                app.lumen.player.ui.OnboardingScreen(
                    onDone = {
                        app.lumen.player.data.Onboarding.complete(ctx)
                        showOnboarding = false
                    },
                )
            }
        }
        return
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

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(cfg.complete) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val client = remember(cfg) { SubsonicClient(cfg) }
    val coverOf = remember(client) { { id: String -> client.coverUrl(id) } }
    val scope = rememberCoroutineScope()

    fun reloadForAccount(c: ServerConfig) {
        // 先停播并清空队列，避免旧账号的曲目/进度串到新账号
        app.lumen.player.playback.PlaybackEngine.get(ctx).stopAndClear()
        albums = emptyList()
        starred = emptyList()
        starredIds = emptySet()
        albumTracksCache.clear()
        homeCache.artists = emptyList()
        homeCache.newest = emptyList()
        homeCache.recentTracks = emptyList()
        radioTracks = emptyList()
        app.lumen.player.data.Radio.clearCache(ctx)
        openAlbum = null
        selectedArtist = null
        navStack.clear()
        screen = 0
        homeTab = 0
        scope.launch {
            loadAlbums(c, useCache = false)
            downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) }
            runCatching {
                val fresh = app.lumen.player.data.SubsonicClient(c)
                val list = withContext(Dispatchers.IO) { fresh.starredTracks() }
                starred = list
                starredIds = list.map { it.id }.toSet()
            }
        }
    }

    fun switchAccount(account: app.lumen.player.data.Account) {
        val c = app.lumen.player.data.Accounts.activate(ctx, account)
        cfg = c
        accounts = app.lumen.player.data.Accounts.list(ctx)
        activeAccountId = app.lumen.player.data.Accounts.activeId(ctx)
        reloadForAccount(c)
    }

    fun addAccount(base: String, user: String, password: String) {
        val a = app.lumen.player.data.Accounts.add(ctx, base, user, password)
        switchAccount(a)
    }

    fun removeAccount(account: app.lumen.player.data.Account) {
        app.lumen.player.data.Accounts.remove(ctx, account)
        accounts = app.lumen.player.data.Accounts.list(ctx)
        activeAccountId = app.lumen.player.data.Accounts.activeId(ctx)
        if (accounts.isEmpty()) {
            app.lumen.player.data.Onboarding.reset(ctx)
            app.lumen.player.data.Legal.reset(ctx)
            app.lumen.player.data.SettingsStore.clear(ctx)
            cfg = ServerConfig("", "", "")
        } else {
            cfg = app.lumen.player.data.SettingsStore.load(ctx)
            reloadForAccount(cfg)
        }
    }

    fun clearLocalData() {
        scope.launch {
            app.lumen.player.playback.PlaybackEngine.get(ctx).stopAndClear()
            withContext(Dispatchers.IO) {
                runCatching {
                    Downloads.summaries(ctx).forEach { Downloads.delete(ctx, it.key) }
                }
                runCatching { MediaCache.clearStreamingCache(ctx, emptySet()) }
                app.lumen.player.data.Accounts.clearAll(ctx)
                app.lumen.player.data.PlayHistory.clear(ctx)
                app.lumen.player.data.Ratings.clear(ctx)
                app.lumen.player.data.StatsStore.clear(ctx)
                app.lumen.player.data.MoodStore.clear(ctx)
                app.lumen.player.playback.PlaybackStateStore.clear(ctx)
            }
            app.lumen.player.data.SettingsStore.clear(ctx)
            cfg = ServerConfig("", "", "")
            albums = emptyList()
            downloads = emptyList()
            accounts = emptyList()
            activeAccountId = null
            navStack.clear()
            screen = 0
            openAlbum = null
        }
    }

    // 进入动画：以最后一次按下位置为原点展开
    val enterAnim = remember { Animatable(1f) }
    var enterOriginX by remember { mutableFloatStateOf(0.5f) }
    var enterOriginY by remember { mutableFloatStateOf(0.5f) }
    var enterJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    /** 去下一页，并把L10n.t("来处")压栈；从点击位置展开新页面 */
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
            performBack()
            backAnim.snapTo(0f)
            androidx.compose.runtime.withFrameNanos { }
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
    val currentMoment = remember(mood, playback.positionMs, playback.durationMs) {
        val m = mood ?: return@remember app.lumen.player.data.AudioMoment(null, null)
        val pos = playback.positionMs
        val seg = m.segments.firstOrNull { pos >= it.startMs && pos < it.endMs }
            ?: m.segments.firstOrNull { pos < it.startMs }
            ?: m.segments.lastOrNull { pos >= it.endMs }
        val tags: List<String> = when {
            seg != null -> seg.tags
            m.segments.isNotEmpty() -> m.segments.firstOrNull()?.tags.orEmpty()
            else -> {
                val names = m.tags.map { it.name }
                if (names.isEmpty()) {
                    emptyList()
                } else {
                    val p = if (playback.durationMs > 0) (pos.toFloat() / playback.durationMs).coerceIn(0f, 0.999f) else 0f
                    listOf(names[(p * names.size).toInt().coerceIn(0, names.lastIndex)])
                }
            }
        }
        val event = app.lumen.player.data.AudioEvent.fromTags(tags)
        app.lumen.player.data.AudioMoment(
            event = event,
            detail = app.lumen.player.data.AudioEvent.detailFor(tags) ?: event?.label(),
        )
    }

    fun startRadio(vibe: app.lumen.player.data.RadioVibe, reseed: Boolean = false) {
        if (radioTracks.isEmpty()) return
        val seedId = if (reseed) null else playback.current?.id
        val queue = app.lumen.player.data.Radio.build(ctx, radioTracks, seedId, vibe)
        if (queue.isEmpty()) {
            radioError = L10n.tf(
                "还没有足够的分析数据。先运行心情分析，再回来生成电台。",
                "Not enough analyzed tracks yet. Run mood analysis, then come back.",
            )
            return
        }
        radioError = null
        playback.play(queue, 0, L10n.tf("氛围电台", "Ambient Radio"))
    }

    fun loadRadio(vibe: app.lumen.player.data.RadioVibe) {
        if (radioLoading) return
        radioLoading = true
        radioError = null
        radioProgress = 0f
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    app.lumen.player.data.Radio.loadLibrary(ctx, client) { p ->
                        scope.launch { radioProgress = p }
                    }
                }
                radioTracks = list
                if (list.isEmpty()) {
                    radioError = L10n.tf("曲库为空，无法生成电台。", "The library is empty; cannot build a station.")
                } else {
                    startRadio(vibe)
                }
            } catch (e: Exception) {
                radioError = e.message ?: L10n.tf("生成电台失败", "Failed to build station")
            } finally {
                radioLoading = false
            }
        }
    }

    fun openRadio() {
        navigate(NavKey(10, null))
        if (radioTracks.isNotEmpty()) startRadio(radioVibe) else loadRadio(radioVibe)
    }

    LaunchedEffect(fadeMs) {
        playback.fadeMs = fadeMs
    }
    LaunchedEffect(smartFade) {
        playback.smartFade = smartFade
    }
    // 冷启动恢复上次队列与进度（保持暂停，不自动播放）
    LaunchedEffect(cfg.complete, playback) {
        if (cfg.complete && playback.queue.isEmpty()) {
            val saved = withContext(Dispatchers.IO) { app.lumen.player.playback.PlaybackStateStore.load(ctx) }
            if (saved != null && saved.tracks.isNotEmpty()) {
                playback.restore(saved.tracks, saved.index, saved.positionMs, saved.albumId)
                delay(500)
                playback.enforcePaused()
            }
        }
    }
    // 登录后就常驻媒体会话服务：这样它能在L10n.t("开始播放")这一刻收到事件并挂出通知/前台服务
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
        if (tr != null) app.lumen.player.data.PlayHistory.record(ctx, tr)
        libraryVersion += 1
        if (lyricsTrackId != tr?.id) {
            lyrics = null
            lyricsTrackId = tr?.id
            if (tr != null) {
                rating = withContext(Dispatchers.IO) { Ratings.get(ctx, tr.id) }
                scrobbledId = null
                mood = withContext(Dispatchers.IO) { app.lumen.player.data.MoodStore.get(ctx, tr.id) }
                app.lumen.player.util.Logx.i(
                    "LumenMood",
                    "load mood id=${tr.id} found=${mood != null} beats=${mood?.beatsMs?.size ?: -1} tempo=${mood?.tempo ?: -1f}",
                )
                val cachedLyrics = lyricsCache[tr.id]
                if (lyricsCache.containsKey(tr.id)) {
                    lyrics = cachedLyrics
                    lyricsLoading = false
                } else {
                    lyricsLoading = true
                    runCatching {
                        lyrics = withContext(Dispatchers.IO) { client.lyrics(tr.id, tr.title, tr.artist) }
                    }
                    lyricsCache[tr.id] = lyrics
                    if (lyricsCache.size > 40) {
                        lyricsCache.keys.firstOrNull()?.let { lyricsCache.remove(it) }
                    }
                    lyricsLoading = false
                }
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
                app.lumen.player.util.Logx.i("LumenScrobble", "nowPlaying ${tr.id} ok=${r.isSuccess} err=${r.exceptionOrNull()?.message}")
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
                app.lumen.player.util.Logx.i("LumenScrobble", "submit ${tr.id} ok=${r.isSuccess} err=${r.exceptionOrNull()?.message}")
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
        app.lumen.player.playback.MusicPulse.sync(current?.id, playback.playing, mood)
    }
    LaunchedEffect(playback.positionMs) {
        app.lumen.player.playback.MusicPulse.setPosition(playback.positionMs)
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
                    key.screen == 7 -> app.lumen.player.ui.StatsScreen(
                        initial = app.lumen.player.data.StatsStore.load(ctx),
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
                    key.screen == 12 -> app.lumen.player.ui.PrivacyScreen(
                        onBack = { goBack() },
                    )
                    key.screen == 11 -> app.lumen.player.ui.ChangelogScreen(
                        onBack = { goBack() },
                    )
                    key.screen == 10 -> app.lumen.player.ui.RadioScreen(
                        vibe = radioVibe,
                        loading = radioLoading,
                        progress = radioProgress,
                        error = radioError,
                        current = playback.current,
                        playing = playback.playing,
                        onBack = { goBack() },
                        onVibe = { vibe ->
                            radioVibe = vibe
                            if (radioTracks.isEmpty()) loadRadio(vibe) else startRadio(vibe)
                        },
                        onRegenerate = {
                            if (radioTracks.isEmpty()) loadRadio(radioVibe) else startRadio(radioVibe, reseed = true)
                        },
                        onToggle = { playback.toggle() },
                        onNext = { playback.next() },
                        onOpenMood = { navigate(NavKey(8, null)) },
                        onRetry = {
                            radioTracks = emptyList()
                            app.lumen.player.data.Radio.clearCache(ctx)
                            loadRadio(radioVibe)
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
                            app.lumen.player.data.Appearance.setContrast(ctx, v)
                        },
                        fadeMs = fadeMs,
                        langMode = langMode,
                        onLangMode = { mode ->
                            app.lumen.player.util.L10n.setMode(ctx, mode)
                            langMode = mode
                        },
                        onFadeMs = { v ->
                            fadeMs = v
                            app.lumen.player.data.Appearance.setFadeMs(ctx, v)
                        },
                        onClearLocalData = { clearLocalData() },
                        onOpenGuide = { showOnboarding = true },
                        onOpenChangelog = { navigate(NavKey(11, null)) },
                        onOpenPrivacy = { navigate(NavKey(12, null)) },
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
                                moodPhase = L10n.t("准备")
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            app.lumen.player.data.MoodAnalyzer(ctx).analyze(
                                                tr.id,
                                                streamUrl(client, tr),
                                            ) { phase, p -> moodPhase = "$phase ${(p * 100).toInt()}%" }
                                        }
                                    }.onSuccess {
                                        app.lumen.player.data.MoodStore.put(ctx, it)
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
                            onOpenRadio = { openRadio() },
                            onOpenStats = { navigate(NavKey(7, null)) },
                            tab = homeTab,
                            onTabChange = { homeTab = it },
                            ratingOf = { id -> Ratings.get(ctx, id) },
                            downloads = downloads,
                            libraryVersion = libraryVersion,
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
                                        app.lumen.player.util.Logx.i(
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
                                    app.lumen.player.data.MoodAnalyzer(ctx).use { analyzer ->
                                        list.forEach { tr ->
                                            if (app.lumen.player.data.MoodStore.get(ctx, tr.id) == null) {
                                                runCatching { analyzer.analyze(tr.id, client.streamUrl(tr.id) + "&format=mp3&maxBitRate=96") { _, _ -> } }
                                                    .onSuccess { app.lumen.player.data.MoodStore.put(ctx, it) }
                                            }
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

    val animatedScheme = app.lumen.player.ui.rememberAnimatedColorScheme(scheme)
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
                app.lumen.player.ui.ExpressiveBackdrop(Modifier.matchParentSize(), event = currentMoment.event)
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
                                app.lumen.player.ui.ExpressiveBackdrop(
                                    Modifier.matchParentSize(),
                                    drivePulse = isFront,
                                    event = currentMoment.event,
                                )
                            }
                            screenContent(pageKey)
                        }
                    }
                }


                // 播放页有自己的控制区，不再显示底栏
                if (screen != 2) {
                    YoinBar(
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp),
                        title = current?.title ?: "",
                        artist = current?.artist ?: "",
                        coverUrl = current?.let { client.coverUrl(it.albumId, 200) } ?: "",
                        playing = playback.playing,
                        enabled = current != null,
                        eventLabel = currentMoment.detail,
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
