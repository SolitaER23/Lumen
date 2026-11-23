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
import androidx.compose.ui.graphics.toArgb
import app.lumen.player.ui.FallbackScheme
import app.lumen.player.ui.LicensesScreen
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

private fun loginErrorMessage(e: Exception): String {
    val message = e.message.orEmpty()
    val lower = message.lowercase()
    return when {
        lower.contains("invalid url port") || lower.contains("invalid url") ->
            L10n.tf(
                "服务器地址格式不正确。请包含 http:// 或 https://，端口示例：http://192.168.1.2:4533",
                "Invalid server address. Include http:// or https:// and the port, e.g. http://192.168.1.2:4533",
            )
        e is java.net.UnknownHostException ->
            L10n.tf("找不到服务器，请检查地址拼写或网络连接", "Server not found. Check the address and network.")
        e is java.net.ConnectException || lower.contains("failed to connect") ->
            L10n.tf("无法连接服务器，请确认服务已启动且端口正确", "Cannot connect. Check that the server is running and the port is correct.")
        e is java.net.SocketTimeoutException || lower.contains("timeout") ->
            L10n.tf("连接超时，请检查服务器地址与网络后重试", "Connection timed out. Check the server address and network, then retry.")
        e is javax.net.ssl.SSLHandshakeException ->
            L10n.tf("HTTPS 证书校验失败，请检查服务器证书或改用受信任的 HTTPS", "HTTPS certificate check failed. Verify the server certificate.")
        lower.contains("subsonic error 40") || lower.contains("wrong username") || lower.contains("wrong password") ->
            L10n.tf("用户名或密码错误，请修改后重新连接", "Wrong username or password. Edit and retry.")
        lower.contains("subsonic error 41") ->
            L10n.tf("该账号没有访问权限，请检查服务器权限配置", "This account cannot access the library. Check server permissions.")
        else -> message.ifBlank { L10n.tf("连接失败，请检查地址、用户名和密码", "Connection failed. Check the address, username and password.") }
    }
}

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
    var loggedIn by remember { mutableStateOf(false) }
    var loginNonce by remember { mutableStateOf(0) }
    var openAlbum by remember { mutableStateOf<Album?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    // 0 = 首页, 1 = 专辑页, 2 = 播放页
    var screen by remember { mutableStateOf(0) }
    var scheme by remember { mutableStateOf(FallbackScheme) }
    val animatedScheme = app.lumen.player.ui.rememberAnimatedColorScheme(scheme)
    var contrast by remember { mutableFloatStateOf(app.lumen.player.data.Appearance.contrast(ctx)) }
    var themeMode by remember { mutableStateOf(app.lumen.player.data.Appearance.themeMode(ctx)) }
    var paletteStyle by remember { mutableStateOf(app.lumen.player.data.Appearance.paletteStyle(ctx)) }
    var amoledOn by remember { mutableStateOf(app.lumen.player.data.Appearance.amoled(ctx)) }
    var moodTintOn by remember { mutableStateOf(app.lumen.player.data.Appearance.moodTint(ctx)) }
    var gradientOn by remember { mutableStateOf(app.lumen.player.data.Appearance.gradientTheme(ctx)) }
    var materialYouOn by remember { mutableStateOf(app.lumen.player.data.Appearance.materialYou(ctx)) }
    var perPageColorOn by remember { mutableStateOf(app.lumen.player.data.Appearance.perPageColor(ctx)) }
    var pinnedAlbumId by remember { mutableStateOf(app.lumen.player.data.Appearance.pinnedAlbum(ctx)) }
    var paletteStatus by remember { mutableStateOf("") }
    val powerSave = remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        while (true) {
            powerSave.value = pm.isPowerSaveMode
            kotlinx.coroutines.delay(5_000)
        }
    }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    // 浅色模式下把状态栏/导航栏图标切成深色
    val rootView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.LaunchedEffect(isDarkTheme) {
        val act = rootView.context as? android.app.Activity
        if (act != null) {
            androidx.core.view.WindowCompat.getInsetsController(act.window, rootView).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }
    val themeOpts = app.lumen.player.ui.ThemeOptions(
        contrast = contrast,
        isDark = isDarkTheme,
        style = app.lumen.player.ui.PaletteStyle.of(paletteStyle),
        amoled = amoledOn,
        moodTint = moodTintOn,
        gradient = gradientOn,
    )
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
    var termsRead by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var privacyRead by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
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
        12 -> "privacy"
        13 -> "terms"
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

    // 后台分析提示：每次安装只提示一次
    var showMoodHint by remember { mutableStateOf(false) }
    LaunchedEffect(loggedIn) {
        val prefs = ctx.getSharedPreferences("lumen_mood", android.content.Context.MODE_PRIVATE)
        if (loggedIn && !prefs.getBoolean("hint_shown", false)) showMoodHint = true
    }

    // 登录尝试与表单状态解耦：每次提交（loginNonce）都重新认证；
    // 失败后保持登录页并恢复按钮，成功后才进入主界面。
    LaunchedEffect(loginNonce, cfg) {
        if (!loggedIn && cfg.complete) {
            loading = true
            error = null
            try {
                val list = kotlinx.coroutines.withTimeoutOrNull(20_000) {
                    withContext(Dispatchers.IO) { SubsonicClient(cfg).albums() }
                }
                if (list == null) {
                    error = L10n.tf(
                        "连接超时，请检查服务器地址与网络后重试",
                        "Connection timed out. Check the server address and network, then retry.",
                    )
                    loggedIn = false
                } else {
                    albums = list
                    libraryVersion += 1
                    withContext(Dispatchers.IO) { AlbumCache.save(ctx, list) }
                    app.lumen.player.data.Accounts.ensure(ctx, cfg)
                    accounts = app.lumen.player.data.Accounts.list(ctx)
                    activeAccountId = app.lumen.player.data.Accounts.activeId(ctx)
                    error = null
                    loggedIn = true
                    // 曲库拉取完成即开始后台分析（每次启动续跑未完成的曲目）
                    runCatching {
                        app.lumen.player.playback.MoodAnalysisRunner.start(ctx, SubsonicClient(cfg), list)
                    }
                }
            } catch (e: Exception) {
                error = loginErrorMessage(e)
                loggedIn = false
            } finally {
                loading = false
            }
        }
    }


    if (!legalAccepted) {
        MaterialTheme(colorScheme = FallbackScheme) {
            when (legalDoc) {
                "terms" -> app.lumen.player.ui.TermsScreen(
                    onBack = { legalDoc = null },
                    onReachedBottom = { termsRead = true },
                )
                "privacy" -> app.lumen.player.ui.PrivacyScreen(
                    onBack = { legalDoc = null },
                    onReachedBottom = { privacyRead = true },
                )
                else -> app.lumen.player.ui.ConsentScreen(
                    termsRead = termsRead,
                    privacyRead = privacyRead,
                    onOpenTerms = { legalDoc = "terms" },
                    onOpenPrivacy = { legalDoc = "privacy" },
                    onAccept = {
                        if (termsRead && privacyRead) {
                            app.lumen.player.data.Legal.accept(ctx)
                            legalAccepted = true
                        }
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

    if (!loggedIn) {
        // 登录页也要套主题，否则用的是 M3 默认浅色 → 深色字压在黑底上看不清
        MaterialTheme(colorScheme = FallbackScheme) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                LoginScreen(cfg, loading, error) { c ->
                    error = null
                    loading = true
                    cfg = c
                    SettingsStore.save(ctx, c)
                    loginNonce += 1
                }
            }
        }
        return
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(loggedIn) {
        if (loggedIn && Build.VERSION.SDK_INT >= 33 &&
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
            loggedIn = false
            loading = false
            error = null
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
    // 容器变换：进入时从该矩形长出，返回时收缩回去
    var enterRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var exitRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var enterJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    /** 去下一页，并把L10n.t("来处")压栈；从点击位置展开新页面 */
    fun navigate(target: NavKey) {
        if (target.screen == screen && target.album?.id == openAlbum?.id) return
        enterRect = app.lumen.player.ui.NavOriginStore.get(target.album?.id)
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
        // 只有"返回首页"才收缩到卡片；播放页→专辑页应走普通反向转场
        exitRect = if (navStack.lastOrNull()?.screen == 0) {
            app.lumen.player.ui.NavOriginStore.get(openAlbum?.id)
        } else {
            null
        }
        heldBack = navStack.lastOrNull()
        scope.launch {
            if (backAnim.value < 1.4f) {
                // 把当前页完全送出屏幕，再无缝切换（Expressive emphasized 缓动）
                backAnim.animateTo(
                    1.4f,
                    tween(
                        300,
                        easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f),
                    ),
                )
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
    LaunchedEffect(loggedIn, playback) {
        if (loggedIn && playback.queue.isEmpty()) {
            val saved = withContext(Dispatchers.IO) { app.lumen.player.playback.PlaybackStateStore.load(ctx) }
            if (saved != null && saved.tracks.isNotEmpty()) {
                playback.restore(saved.tracks, saved.index, saved.positionMs, saved.albumId)
                delay(500)
                playback.enforcePaused()
            }
        }
    }
    // 登录后就常驻媒体会话服务：这样它能在L10n.t("开始播放")这一刻收到事件并挂出通知/前台服务
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
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

    // 下载完成情况 → 单曲是否已下载（含专辑组里已完成的曲目）
    LaunchedEffect(downloads) {
        downloadedTrackIds = withContext(Dispatchers.IO) { Downloads.completedTrackIds(ctx) }
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

    // 封面分档预热：切歌时把播放页要用的 600px 版本放进 Coil 缓存，
    // 列表用的是 200px，两层互不覆盖，进播放页时 0 等待
    LaunchedEffect(current?.albumId) {
        val t = current ?: return@LaunchedEffect
        runCatching {
            val loader = coil3.SingletonImageLoader.get(ctx)
            loader.enqueue(
                coil3.request.ImageRequest.Builder(ctx)
                    .data(client.coverUrl(t.albumId, 600))
                    .size(600, 600)
                    .build(),
            )
        }
    }

    // 预取：曲库加载完后台把前几首专辑的曲目写进磁盘缓存，首次点开即秒开
    LaunchedEffect(albums.isNotEmpty()) {
        if (albums.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            albums.take(6).forEach { a ->
                runCatching {
                    if (app.lumen.player.data.AlbumTracksCache.load(ctx, a.id) == null) {
                        val list = client.tracks(a.id)
                        app.lumen.player.data.AlbumTracksCache.save(ctx, a.id, list)
                    }
                }
            }
        }
    }

    // 封面取色：主色跟着正在播的专辑走（可被 Material You / 专辑锁色覆盖）
    LaunchedEffect(
        current?.albumId,
        contrast,
        paletteStyle,
        isDarkTheme,
        amoledOn,
        moodTintOn,
        gradientOn,
        materialYouOn,
        pinnedAlbumId,
        mood,
    ) {
        // ① Material You（Android 12+）
        if (materialYouOn && android.os.Build.VERSION.SDK_INT >= 31) {
            val you = runCatching {
                if (isDarkTheme) androidx.compose.material3.dynamicDarkColorScheme(ctx)
                else androidx.compose.material3.dynamicLightColorScheme(ctx)
            }.getOrNull()
            if (you != null) {
                scheme = if (amoledOn && isDarkTheme) app.lumen.player.ui.amoledOf(you) else you
                paletteStatus = "系统配色（Material You）"
                return@LaunchedEffect
            }
        }
        // ② 按专辑锁色
        val pinned = app.lumen.player.data.Appearance.pinnedSeeds(ctx)
        if (pinnedAlbumId != null && pinned != null) {
            scheme = app.lumen.player.ui.buildScheme(pinned, themeOpts)
            paletteStatus = "已锁定专辑配色"
            return@LaunchedEffect
        }
        // ③ 封面 + 情绪
        val t = current
        if (t == null) {
            scheme = app.lumen.player.ui.fallbackScheme(isDarkTheme, amoledOn)
            paletteStatus = "默认配色"
            return@LaunchedEffect
        }
        val s = schemeFromCover(
            ctx,
            t.albumId,
            client.coverUrl(t.albumId, 200),
            if (moodTintOn) mood else null,
            themeOpts,
        )
        if (s != null) {
            scheme = s
            paletteStatus = if (moodTintOn && mood != null) "封面取色 · 已叠加情绪" else "封面取色"
        } else {
            // P1-3：取色失败显式降级，不再沿用上一张封面
            scheme = app.lumen.player.ui.fallbackScheme(isDarkTheme, amoledOn)
            paletteStatus = "已降级：本张封面取色失败"
        }
    }

    // 首页再按一次返回才退出，避免误触直接杀掉应用
    var lastBackAt by remember { mutableLongStateOf(0L) }
    androidx.activity.compose.BackHandler(enabled = screen == 0) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBackAt < 2000L) {
            (ctx as? android.app.Activity)?.finish()
        } else {
            lastBackAt = now
            android.widget.Toast.makeText(ctx, "再按一次退出", android.widget.Toast.LENGTH_SHORT).show()
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
                        tracksOf = { key -> Downloads.tracksOf(ctx, key) },
                        onBack = { goBack() },
                        onDelete = { id ->
                            Downloads.delete(ctx, id)
                            scope.launch { downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) } }
                        },
                        onDeleteTrack = { trackId ->
                            Downloads.deleteTrack(ctx, trackId)
                            scope.launch { downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) } }
                        },
                        onPauseAll = { Downloads.pauseAll(ctx) },
                        onResumeAll = { Downloads.resumeAll(ctx) },
                        onClearAll = {
                            downloads.forEach { Downloads.delete(ctx, it.key) }
                            downloads = emptyList()
                        },
                    )
                    key.screen == 13 -> app.lumen.player.ui.TermsScreen(
                        onBack = { goBack() },
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
                        onRetry = {
                            radioTracks = emptyList()
                            app.lumen.player.data.Radio.clearCache(ctx)
                            loadRadio(radioVibe)
                        },
                    )
                    key.screen == 3 -> SettingsScreen(
                        cfg = cfg,
                        downloadCount = downloads.sumOf { it.total },
                        downloadBytes = downloads.sumOf { it.bytes },
                        onManageDownloads = { navigate(NavKey(4, null)) },
                        accounts = accounts,
                        activeAccountId = activeAccountId,
                        onSwitchAccount = { switchAccount(it) },
                        onAddAccount = { base, user, password -> addAccount(base, user, password) },
                        onRemoveAccount = { removeAccount(it) },
                        onOpenLicenses = { navigate(NavKey(9, null)) },
                        versionName = BuildConfig.VERSION_NAME,
                        contrast = contrast,
                        onContrast = { v ->
                            contrast = v
                            app.lumen.player.data.Appearance.setContrast(ctx, v)
                        },
                        themeMode = themeMode,
                        onThemeMode = {
                            themeMode = it
                            app.lumen.player.data.Appearance.setThemeMode(ctx, it)
                        },
                        paletteStyle = paletteStyle,
                        onPaletteStyle = {
                            paletteStyle = it
                            app.lumen.player.data.Appearance.setPaletteStyle(ctx, it)
                        },
                        amoled = amoledOn,
                        onAmoled = {
                            amoledOn = it
                            app.lumen.player.data.Appearance.setAmoled(ctx, it)
                        },
                        moodTint = moodTintOn,
                        onMoodTint = {
                            moodTintOn = it
                            app.lumen.player.data.Appearance.setMoodTint(ctx, it)
                        },
                        gradientTheme = gradientOn,
                        onGradientTheme = {
                            gradientOn = it
                            app.lumen.player.data.Appearance.setGradientTheme(ctx, it)
                        },
                        materialYou = materialYouOn,
                        onMaterialYou = {
                            materialYouOn = it
                            app.lumen.player.data.Appearance.setMaterialYou(ctx, it)
                        },
                        perPageColor = perPageColorOn,
                        onPerPageColor = {
                            perPageColorOn = it
                            app.lumen.player.data.Appearance.setPerPageColor(ctx, it)
                        },
                        pinned = pinnedAlbumId != null,
                        paletteStatus = paletteStatus,
                        onPinColor = {
                            val t = current
                            if (t != null) {
                                app.lumen.player.data.Appearance.setPinned(
                                    ctx,
                                    t.albumId,
                                    listOf(
                                        scheme.primary.toArgb(),
                                        scheme.secondary.toArgb(),
                                        scheme.tertiary.toArgb(),
                                    ),
                                )
                                pinnedAlbumId = t.albumId
                            }
                        },
                        onUnpinColor = {
                            app.lumen.player.data.Appearance.clearPinned(ctx)
                            pinnedAlbumId = null
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
                        onOpenTerms = { navigate(NavKey(13, null)) },
                        onClearSongCache = {
                            withContext(Dispatchers.IO) {
                                MediaCache.clearSongCache(ctx, Downloads.downloadedUris(ctx))
                            }
                        },
                        onClearImageCache = { withContext(Dispatchers.IO) { MediaCache.clearImageCache(ctx) } },
                        onClearLyricsCache = { withContext(Dispatchers.IO) { app.lumen.player.data.LyricsCache.clear(ctx) } },
                        onClearMoodAbnormal = {
                            withContext(Dispatchers.IO) { app.lumen.player.data.MoodStore.clearAbnormal(ctx) }
                        },
                        onIgnoreFailedTracks = {
                            withContext(Dispatchers.IO) { app.lumen.player.data.MoodStore.ignoreFailures(ctx) }
                        },
                        onRestoreIgnoredTracks = {
                            withContext(Dispatchers.IO) { app.lumen.player.data.MoodStore.clearIgnored(ctx) }
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
                            loggedIn = false
                            loading = false
                            error = null
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
                        MaterialTheme(
                            colorScheme = app.lumen.player.ui.mutedForHome(animatedScheme, isDarkTheme),
                            typography = lumenTypography(),
                        ) {
                        HomeScreen(
                            client = client,
                            albums = albums,
                            analyzing = app.lumen.player.playback.MoodAnalysisRunner.running,
                            analysisDone = app.lumen.player.playback.MoodAnalysisRunner.done,
                            analysisTotal = app.lumen.player.playback.MoodAnalysisRunner.total,
                            analysisPhase = app.lumen.player.playback.MoodAnalysisRunner.phase,
                            analysisTrack = app.lumen.player.playback.MoodAnalysisRunner.currentTitle,
                            analysisMode = app.lumen.player.playback.MoodAnalysisRunner.modeLabel,
                            analysisModeDetail = app.lumen.player.playback.MoodAnalysisRunner.modeDetail,
                            downloadedIds = downloadedTrackIds,
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
                                // ① 先用磁盘缓存秒开（可能略旧），避免白屏等待
                                val cached = withContext(Dispatchers.IO) {
                                    app.lumen.player.data.AlbumTracksCache.load(ctx, album.id)
                                }
                                if (cached != null && albumTracksCache[album.id] == null) {
                                    albumTracksCache[album.id] = cached
                                }
                                // ② 后台刷新，成功则覆写内存与磁盘
                                val fetched = try {
                                    withContext(Dispatchers.IO) { client.tracks(album.id) }.also {
                                        app.lumen.player.util.Logx.i(
                                            "LumenAlbum",
                                            "album=${album.id} tracks=${it.size} discs=${it.map { t -> t.disc }.distinct()}",
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("LumenAlbum", "album=${album.id} fetch failed", e)
                                    if (cached == null) error = e.message
                                    emptyList()
                                }
                                // 不要把空结果缓存下来：否则一次网络抖动会让这张专辑一直空白
                                if (fetched.isNotEmpty()) {
                                    albumTracksCache[album.id] = fetched
                                    withContext(Dispatchers.IO) {
                                        app.lumen.player.data.AlbumTracksCache.save(ctx, album.id, fetched)
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
                            downloadedIds = downloadedTrackIds,
                            themeOpts = themeOpts,
                            mood = if (current?.albumId == album.id) mood else null,
                            selfColor = perPageColorOn,
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
                val sharedMotion = app.lumen.player.ui.rememberBackdropMotion()
                app.lumen.player.ui.ExpressiveBackdrop(
                    Modifier.matchParentSize(),
                    event = currentMoment.event,
                    sharedMotion = sharedMotion,
                )
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
                                            var s = 1f - 0.07f * p.coerceAtMost(1f)
                                            var tx = 0f
                                            var ty = 0f
                                            // 返回：向来源卡片收缩（容器变换的反向）
                                            val r = exitRect
                                            if (r != null && size.width > 0f && size.height > 0f) {
                                                val startScale = maxOf(r.width / size.width, r.height / size.height)
                                                    .coerceIn(0.35f, 1f)
                                                val k = p.coerceAtMost(1f)
                                                s *= 1f + (startScale - 1f) * k
                                                tx = (r.center.x - size.width / 2f) * k
                                                ty = (r.center.y - size.height / 2f) * k
                                            }
                                            scaleX = s
                                            scaleY = s
                                            translationX += tx
                                            translationY = ty
                                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                                        } else if (e < 0.999f) {
                                            // 进入：从来源卡片的矩形长成整页
                                            val r = enterRect
                                            if (r != null && size.width > 0f && size.height > 0f) {
                                                val startScale = maxOf(r.width / size.width, r.height / size.height)
                                                    .coerceIn(0.35f, 1f)
                                                val k = 1f - e
                                                val s = startScale + (1f - startScale) * e
                                                scaleX = s
                                                scaleY = s
                                                translationX = (r.center.x - size.width / 2f) * k
                                                translationY = (r.center.y - size.height / 2f) * k
                                                alpha = 0.35f + 0.65f * e
                                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                                            } else {
                                                val s = 0.90f + 0.10f * e
                                                scaleX = s
                                                scaleY = s
                                                alpha = 0.55f + 0.45f * e
                                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(enterOriginX, enterOriginY)
                                            }
                                        }
                                    } else {
                                        val p2 = p.coerceAtMost(1f)
                                        val s = 0.94f + 0.06f * p2
                                        scaleX = s
                                        scaleY = s
                                        // 背景页做反向视差：往左让出 -8%，跟随手指更自然
                                        translationX = -size.width * (0.05f + 0.03f * p2) * (1f - p2) -
                                            size.width * 0.03f * p2
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
                            // P0-1：只有前景页绘制动态背景；退到后面的页用纯底色，转场少一半绘制
                            app.lumen.player.ui.ExpressiveBackdrop(
                                Modifier.matchParentSize(),
                                drivePulse = isFront,
                                event = currentMoment.event,
                                animate = isFront && playback.playing && !powerSave.value,
                                sharedMotion = sharedMotion,
                            )
                            screenContent(pageKey)
                        }
                    }
                }


                // 播放页有自己的控制区，不再显示底栏（用动画隐显，避免"啪"地出现）
                androidx.compose.animation.AnimatedVisibility(
                    visible = screen != 2,
                    enter = androidx.compose.animation.fadeIn(
                        androidx.compose.animation.core.tween(180),
                    ) + androidx.compose.animation.slideInVertically(
                        androidx.compose.animation.core.tween(220),
                    ) { it / 2 },
                    exit = androidx.compose.animation.fadeOut(
                        androidx.compose.animation.core.tween(140),
                    ) + androidx.compose.animation.slideOutVertically(
                        androidx.compose.animation.core.tween(180),
                    ) { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    YoinBar(
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

                if (showMoodHint) {
                    MoodNoticeDialog(
                        onDismiss = {
                            showMoodHint = false
                            ctx.getSharedPreferences("lumen_mood", android.content.Context.MODE_PRIVATE)
                                .edit().putBoolean("hint_shown", true).apply()
                        },
                    )
                }
            }
        }
    }
}


/** 首次登录后的后台分析提示：说明可能的短暂卡顿/发热，分析结束自动恢复。 */
@Composable
private fun MoodNoticeDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                L10n.t("正在后台分析曲库"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                L10n.t(
                    "为了让配色、氛围电台与节拍动效贴合每首歌，Lumen 会在后台逐首分析曲库（每首约 1~2 秒）。" +
                        "分析期间可能出现短暂卡顿或设备发热，属于正常现象，全部分析结束后会自动恢复正常。" +
                        "你可以继续听歌，切到后台也会继续。",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(L10n.t("知道了"), color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
