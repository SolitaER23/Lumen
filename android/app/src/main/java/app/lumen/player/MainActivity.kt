package app.lumen.player

import app.lumen.player.util.L10n
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import app.lumen.player.ui.albumSharedElement
import app.lumen.player.ui.coverSharedElement
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
        // 0) 本地网络权限（Android 17）：被拒时表现为"无法连接"，必须优先判定
        lower.contains("access_local_network") || lower.contains("permission denial") ->
            L10n.tf(
                "系统拦截了本地网络访问。请在弹窗中允许「本地网络」权限后重试。",
                "Local network access was blocked. Allow the Local Network permission and retry.",
            )
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
        // HTTP 层：给出可区分的状态提示
        lower.contains("403") || lower.contains("forbidden") ->
            L10n.tf("服务器拒绝访问（403）。请检查账号权限，或反向代理是否限制了访问来源。", "Forbidden (403). Check account permissions or reverse-proxy rules.")
        lower.contains("401") || lower.contains("unauthorized") ->
            L10n.tf("认证失败（401）。请确认用户名与密码，或服务器是否要求 token 认证。", "Unauthorized (401). Check the credentials or token auth.")
        lower.contains("404") || lower.contains("not found") ->
            L10n.tf("接口不存在（404）。请确认地址指向 Navidrome 的 Subsonic 接口根路径。", "Not found (404). Verify the Subsonic base path.")
        lower.contains("500") || lower.contains("502") || lower.contains("503") ->
            L10n.tf("服务器内部错误（5xx）。请查看 Navidrome 日志，可能正在扫描曲库。", "Server error (5xx). Check the Navidrome logs.")
        else -> message.ifBlank { L10n.tf("连接失败，请检查地址、用户名和密码", "Connection failed. Check the address, username and password.") }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.lumen.player.util.L10n.init(this)
        // Pixel 10a 是 120Hz 面板：明确请求固定源 120Hz，动画期间才不会被降到 60
        window.attributes = window.attributes.apply { preferredRefreshRate = 120f }

        app.lumen.player.ui.AmbientFx.init(this)

    setContent {
            App()
        }
    }
}

/** 页面栈的动画键：屏幕 + 当前专辑，用来判断方向与触发转场 */
private data class NavKey(val screen: Int, val album: Album?, val artistId: String? = null)

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
    var allowPublicCleartext by remember {
        mutableStateOf(app.lumen.player.data.NetworkSettings.allowPublicCleartext(ctx))
    }
    var accounts by remember { mutableStateOf<List<app.lumen.player.data.Account>>(emptyList()) }
    var activeAccountId by remember { mutableStateOf<String?>(null) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loggedIn by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
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
        gradient = gradientOn,
    )
    var fadeMs by remember { mutableLongStateOf(app.lumen.player.data.Appearance.fadeMs(ctx)) }
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
    val navStack = remember { androidx.compose.runtime.mutableStateListOf<NavKey>() }
    // 转场方向：true = 返回（performBack）。页面转场用它区分"从入口展开"与"收缩回入口"
    var navBack by remember { mutableStateOf(false) }

    // 返回首页后让入口按钮回弹一下（仅视觉，不影响导航）
    var lastScreenForBounce by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(screen) {
        if (screen == 0 && lastScreenForBounce != 0) {
            val key = when (lastScreenForBounce) {
                3 -> "settings"
                4 -> "downloads"
                6 -> "search"
                7 -> "stats"
                else -> null
            }
            if (key != null) app.lumen.player.ui.EntryBounce.trigger(key)
        }
        lastScreenForBounce = screen
    }

    /** 原路返回：回到压栈时的位置（页面 + 专辑上下文），转场按"返回"方向播放 */
    fun performBack() {
        val prev = navStack.removeLastOrNull() ?: return
        navBack = true
        screen = prev.screen
        openAlbum = prev.album
        prev.artistId?.let { id -> selectedArtist = selectedArtist?.takeIf { it.id == id } }
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
                    // 服务器不可达：保留会话，用本地缓存进入离线模式，而不是退回登录页
                    val cached = withContext(Dispatchers.IO) { AlbumCache.load(ctx) }
                    if (cached.isNotEmpty()) {
                        albums = cached
                        libraryVersion += 1
                        offline = true
                        error = null
                        loggedIn = true
                    } else {
                        error = L10n.tf(
                            "连接超时，且本机还没有曲库缓存。请检查网络后重试",
                            "Connection timed out and no local library cache exists. Check your network and retry.",
                        )
                        loggedIn = false
                    }
                } else {
                    albums = list
                    libraryVersion += 1
                    withContext(Dispatchers.IO) { AlbumCache.save(ctx, list) }
                    // 认证已成功，此时才把凭据写盘；写不进去要明确告知（否则下次启动会「丢密码」）
                    if (!SettingsStore.save(ctx, cfg)) {
                        android.widget.Toast.makeText(
                            ctx,
                            L10n.tf(
                                "无法安全保存密码（系统密钥库不可用），下次启动需要重新输入。",
                                "Could not securely store the password (keystore unavailable); you will need to re-enter it next launch.",
                            ),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                    app.lumen.player.data.Accounts.ensure(ctx, cfg)
                    accounts = app.lumen.player.data.Accounts.list(ctx)
                    activeAccountId = app.lumen.player.data.Accounts.activeId(ctx)
                    error = null
                    offline = false
                    loggedIn = true
                    // 不再自动开始分析：由首页弹窗提示用户手动触发
                }
            } catch (e: Exception) {
                val cached = withContext(Dispatchers.IO) { AlbumCache.load(ctx) }
                if (cached.isNotEmpty()) {
                    albums = cached
                    libraryVersion += 1
                    offline = true
                    error = null
                    loggedIn = true
                } else {
                    error = loginErrorMessage(e)
                    loggedIn = false
                }
            } finally {
                loading = false
            }
        }
    }


    // 离线提示：进入离线态时提示一次（本地缓存仍可用）
    val offlineToastCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(offline) {
        if (offline) {
            android.widget.Toast.makeText(
                offlineToastCtx,
                L10n.tf("离线模式：使用本地缓存，恢复网络后会自动刷新", "Offline: using local cache; will refresh when reconnected"),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    // 离线自动重试：网络或服务器恢复后自动刷新曲库并退出离线态
    LaunchedEffect(offline, cfg) {
        if (!offline || !cfg.complete) return@LaunchedEffect
        while (offline && cfg.complete) {
            kotlinx.coroutines.delay(20_000)
            val ok = kotlinx.coroutines.withTimeoutOrNull(8_000) {
                withContext(Dispatchers.IO) { runCatching { SubsonicClient(cfg).albums() }.getOrNull() }
            }
            if (ok != null) {
                albums = ok
                libraryVersion += 1
                offline = false
                withContext(Dispatchers.IO) { AlbumCache.save(ctx, ok) }
                // 不自动开始分析：恢复联网只刷新曲库，分析仍由用户手动触发
                break
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

    // 本地网络权限（Android 17）：点「连接并同步」时若未授权，先弹系统授权框，授权后自动重试
    var pendingConfig by remember { mutableStateOf<ServerConfig?>(null) }
    val localNetworkPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingConfig
        pendingConfig = null
        if (granted && pending != null) {
            // 同样先验证后落盘（成功后由下面的认证流程写盘）
            cfg = pending
            error = null
            loginNonce += 1
        } else if (!granted) {
            error = L10n.tf(
                "未授予「本地网络」权限，无法连接局域网服务器。请在设置中允许后重试。",
                "Local Network permission denied; LAN servers are unreachable.",
            )
        }
    }

    // 登录页含密码输入框：禁止截屏与最近任务缩略图，避免锁屏通知/分享截图泄露。
    // Context 可能是 ContextThemeWrapper 链，必须往上解包才能拿到 Activity 的 window。
    androidx.compose.runtime.DisposableEffect(loggedIn) {
        // LocalView.current 只能在组合中读取，这里用外层已取好的 rootView
        val window = generateSequence(rootView.context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>()
            .firstOrNull()
            ?.window
        if (window != null) {
            if (!loggedIn) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        } else {
            app.lumen.player.util.Logx.w("LumenUi", "未找到 Activity window，无法设置 FLAG_SECURE")
        }
        onDispose { }
    }

    if (!loggedIn) {
        // 登录页也要套主题，否则用的是 M3 默认浅色 → 深色字压在黑底上看不清
        MaterialTheme(colorScheme = FallbackScheme) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                LoginScreen(
                    initial = cfg,
                    loading = loading,
                    error = error,
                    allowPublicCleartext = allowPublicCleartext,
                    onAllowPublicCleartextChange = { enabled ->
                        allowPublicCleartext = enabled
                        app.lumen.player.data.NetworkSettings.setAllowPublicCleartext(ctx, enabled)
                    },
                    onRequestLocalNetwork = {
                        val target = cfg
                        if (target != null) {
                            pendingConfig = target
                            runCatching {
                                localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                            }
                        }
                    },
                ) { c ->
                    error = null
                    // Android 17：先确保本地网络权限，再发起连接；授权成功后自动重试
                    val needsLocalNetwork = Build.VERSION.SDK_INT >= 37 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_LOCAL_NETWORK) !=
                        PackageManager.PERMISSION_GRANTED
                    if (needsLocalNetwork) {
                        pendingConfig = c
                        SettingsStore.save(ctx, c)
                        runCatching {
                            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                        }
                    } else {
                        // 先验证再落盘：连接成功后才写凭据，避免打错的地址/密码被持久化
                        loading = true
                        cfg = c
                        loginNonce += 1
                    }
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
        if (a == null) {
            error = L10n.tf(
                "无法安全保存该账号的密码（系统密钥库不可用）。请解锁设备后重试。",
                "Could not securely store this account's password (keystore unavailable). Unlock the device and retry.",
            )
            return
        }
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
            // 统一入口：新增落盘 store 只需在 LocalData 登记一次
            runCatching { app.lumen.player.local.LocalData.wipe(ctx) }
                .onFailure {
                    error = L10n.tf(
                        "清除数据失败：" + (it.message ?: ""),
                        "Failed to wipe data: " + (it.message ?: ""),
                    )
                }
            cfg = ServerConfig("", "", "")
            loggedIn = false
            loading = false
            albums = emptyList()
            downloads = emptyList()
            accounts = emptyList()
            activeAccountId = null
            navStack.clear()
            screen = 0
            openAlbum = null
            starred = emptyList()
            starredIds = emptySet()
            downloadedTrackIds = emptySet()
            lyrics = null
            lyricsTrackId = null
            rating = 0f
            nowPlayingId = null
            scrobbledId = null
        }
    }

    // 进入动画：以最后一次按下位置为原点展开
    /** 去下一页，并把「来处」压栈，这样返回时必然回到点击的位置。 */
    fun navigate(target: NavKey) {
        if (target.screen == screen && target.album?.id == openAlbum?.id) return
        navBack = false
        navStack.add(NavKey(screen, openAlbum))
        screen = target.screen
        openAlbum = target.album
    }

    /**
     * 返回：不再跟手拖动，直接改变导航状态。
     * 让 AnimatedContent + SharedTransitionLayout 播放与进入完全对称的反向转场
     * （封面从大图缩回卡片、页面淡出），路径和时长与进入一致。
     */
    fun goBack() {
        if (navStack.isEmpty()) return
        performBack()
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
    LaunchedEffect(fadeMs) {
        playback.fadeMs = fadeMs
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
    val current = playback.current

    // 下载状态：事件驱动（下载开始/进度/完成/删除时刷新），不再按秒轮询
    LaunchedEffect(screen, homeTab) {
        val watched = screen == 3 || screen == 4 || (screen == 0 && homeTab == 2)
        if (!watched) return@LaunchedEffect
        val onChange: () -> Unit = {
            scope.launch { downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) } }
        }
        Downloads.observe(ctx, onChange)
        downloads = withContext(Dispatchers.IO) { Downloads.summaries(ctx) }
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            Downloads.unobserve(onChange)
        }
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
        gradientOn,
        materialYouOn,
        pinnedAlbumId,
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
        // ③ 封面取色
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
            themeOpts,
        )
        if (s != null) {
            scheme = s
            paletteStatus = "封面取色"
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

    // 返回手势与返回键统一走 goBack()：与进入同一条共享元素转场，方向相反
    androidx.activity.compose.BackHandler(enabled = screen > 0) {
        when {
            navStack.isNotEmpty() -> goBack()
            // 兜底（正常导航不会走到这里）：回到专辑上下文 / 首页，同样按"返回"方向播放
            screen == 2 && openAlbum != null -> {
                navBack = true
                screen = 1
            }
            else -> {
                navBack = true
                screen = 0
                openAlbum = null
            }
        }
    }

    val onOpenAlbum: (Album) -> Unit = remember {
        { al -> navigate(NavKey(1, al)) }
    }
    val onOpenSettings: () -> Unit = remember { { navigate(NavKey(3, null)) } }
    val onOpenSearch: () -> Unit = remember { { navigate(NavKey(6, null)) } }
    val onOpenArtist: (Artist) -> Unit = remember { { a -> selectedArtist = a; navigate(NavKey(5, null, a.id)) } }
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
                        onPlayTrack = { list, idx ->
                            playback.play(list, idx, L10n.tf("搜索", "Search"))
                        },
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
                        onBack = { goBack() },
                        onLogout = {
                            // 登出 = 断开当前连接：停播、清队列与凭据，但保留账号列表与外观偏好
                            runCatching { playback.stopAndClear() }
                            app.lumen.player.data.SettingsStore.clearCredentials(ctx)
                            albums = emptyList()
                            downloadedTrackIds = emptySet()
                            lyrics = null
                            lyricsTrackId = null
                            nowPlayingId = null
                            scrobbledId = null
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
            // 只在需要时读一次按压位置的老实现已删除：入场方向现在由 NavOriginStore + 共享元素决定
            Box(Modifier.fillMaxSize()) {
                val sharedMotion = app.lumen.player.ui.rememberBackdropMotion()
                app.lumen.player.ui.ExpressiveBackdrop(
                    Modifier.matchParentSize(),
                    sharedMotion = sharedMotion,
                )
                // 返回不再跟手：压暗层只做极轻微的过渡底色，避免转场时露出直射背景
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.06f)),
                )
                // 共享元素宿主：页面切换与常驻迷你条必须在同一个 SharedTransitionLayout 里，
                // 否则迷你条封面无法与播放页封面互相 morph（浮层不在页面作用域内）。
                app.lumen.player.ui.SharedElementHost(Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        // 页面切换：AnimatedContent 只负责页面，共享作用域由外层 SharedElementHost 提供
                        val fadeSpecForPage =
                            androidx.compose.material3.MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                        val spatialSpecForPage =
                            androidx.compose.material3.MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                        val navDensity = androidx.compose.ui.platform.LocalDensity.current
                        val navConfig = androidx.compose.ui.platform.LocalConfiguration.current
                        val screenWidthPx = with(navDensity) { navConfig.screenWidthDp.dp.toPx() }
                        val screenHeightPx = with(navDensity) { navConfig.screenHeightDp.dp.toPx() }
                        app.lumen.player.ui.SharedPageSwitcher(
                            target = NavKey(screen, openAlbum),
                            transitionSpec = {
                                // 容器变换：从被点击的入口展开 / 收回，其余情况用标准淡入淡出
                                val from = initialState as NavKey
                                val to = targetState as NavKey
                                fun originKeyOf(k: NavKey): String? = when (k.screen) {
                                    // 专辑详情：封面用共享元素飞行，页面不再整体缩放
                                    5 -> k.artistId
                                    3 -> "settings"
                                    4 -> "downloads"
                                    6 -> "search"
                                    7 -> "stats"
                                    else -> null
                                }
                                // 方向由导航动作决定：navigate() 是进入，performBack() 是返回。
                                // 不能用"目标是不是首页"判断——从播放页返回专辑页同样是返回，
                                // 否则专辑页会错误地从首页入口的位置再"长"出来一次。
                                val returning = navBack
                                val key = if (returning) originKeyOf(from) else originKeyOf(to)
                                val origin = app.lumen.player.ui.NavOriginStore
                                    .transformOrigin(key, screenWidthPx, screenHeightPx)
                                val startScale = app.lumen.player.ui.NavOriginStore
                                    .initialScale(key, screenWidthPx)
                                if (origin != null) {
                                    if (returning) {
                                        androidx.compose.animation.fadeIn(fadeSpecForPage).togetherWith(
                                            androidx.compose.animation.fadeOut(fadeSpecForPage) +
                                                androidx.compose.animation.scaleOut(
                                                    targetScale = startScale,
                                                    animationSpec = spatialSpecForPage,
                                                    transformOrigin = origin,
                                                ),
                                        ).using(null)
                                    } else {
                                        (
                                            androidx.compose.animation.fadeIn(fadeSpecForPage) +
                                                androidx.compose.animation.scaleIn(
                                                    initialScale = startScale,
                                                    animationSpec = spatialSpecForPage,
                                                    transformOrigin = origin,
                                                )
                                            ).togetherWith(
                                            androidx.compose.animation.fadeOut(fadeSpecForPage),
                                        ).using(null)
                                    }
                                } else {
                                    androidx.compose.animation.fadeIn(fadeSpecForPage)
                                        .togetherWith(androidx.compose.animation.fadeOut(fadeSpecForPage))
                                        .using(null)
                                }
                            },
                        ) { pageKey ->
                            Box(Modifier.fillMaxSize()) {
                                app.lumen.player.ui.ExpressiveBackdrop(
                                    Modifier.matchParentSize(),
                                    animate = playback.playing && !powerSave.value,
                                    sharedMotion = sharedMotion,
                                )
                                screenContent(pageKey as NavKey)
                            }
                        }

                        // 播放页有自己的控制区，不再显示底栏（用动画隐显，避免"啪"地出现）
                        androidx.compose.animation.AnimatedVisibility(
                            // 播放页有自己的控制区，不再叠加迷你条
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
                            // 迷你条不属于任何页面：显式传它自己的 AnimatedVisibilityScope，
                            // 封面才能与播放页封面成为同一个共享元素
                            val barScope: androidx.compose.animation.AnimatedVisibilityScope = this
                            YoinBar(
                                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp),
                                title = current?.title ?: "",
                                artist = current?.artist ?: "",
                                coverUrl = current?.let { client.coverUrl(it.albumId, 200) } ?: "",
                                coverModifier = current?.let { track ->
                                    Modifier.coverSharedElement("np-" + track.albumId, barScope)
                                } ?: Modifier,
                                playing = playback.playing,
                                enabled = current != null,
                                onOpenPlayer = { navigate(NavKey(2, openAlbum)) },
                                onToggle = { current?.let { playback.toggle() } },
                            )
                        }
                    }
                }

            }
        }
    }
}
