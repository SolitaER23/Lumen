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
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.app.data.Album
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
    albums: List<Album>,
    loading: Boolean,
    error: String?,
    coverUrl: (String) -> String,
    onOpen: (Album) -> Unit,
    onSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        when {
            // 重试期间优先显示进度，避免"点了没反应"
            loading && albums.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) { Text("重试") }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                // 标题行吸顶：滚动时「专辑 + 设置」固定在上方
                stickyHeader {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        HomeHeader(albums.size, onSettings)
                    }
                }
                items(albums.chunked(2)) { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { album ->
                            AlbumCard(
                                album = album,
                                url = coverUrl(album.coverId),
                                modifier = Modifier.weight(1f),
                            ) { onOpen(album) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(count: Int, onSettings: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "专辑",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Icon(
                    YoinSymbols.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            if (count == 0) "正在加载…" else "$count 张专辑",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
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
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = url,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                album.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                album.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Yoin 式底栏：左「首页」/ 中曲目胶囊 / 右播放键。
 * 宽度守恒：按住一侧时它向外撑开 [grow]，中间胶囊吸收；没有曲目时胶囊收为零。
 */
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
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    YoinSymbols.Home,
                    contentDescription = "首页",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // 曲目胶囊：封面 + 歌名 + 播放键全在一个框里
        Surface(
            onClick = onOpenPlayer,
            enabled = enabled,
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Text(
                        "还没有播放",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        ),
                    )
                }
            }
        }
    }
}
