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
import com.example.app.ui.AlbumScreen
import com.example.app.ui.FallbackScheme
import com.example.app.ui.PlayingScreen
import com.example.app.ui.SettingsScreen
import com.example.app.ui.schemeFromCover
import com.gpo.yoin.symbols.YoinSymbols
import com.gpo.yoin.symbols.rememberPlayPauseSymbolPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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


    suspend fun loadAlbums(c: ServerConfig) {
        loading = true
        error = null
        try {
            albums = withContext(Dispatchers.IO) { SubsonicClient(c).albums() }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(cfg.complete) {
        if (cfg.complete && albums.isEmpty()) loadAlbums(cfg)
    }

    if (!cfg.complete) {
        LoginScreen(cfg, loading, error) { c ->
            loading = true
            cfg = c
            SettingsStore.save(ctx, c)
        }
        return
    }

    val client = remember(cfg) { SubsonicClient(cfg) }
    val playback = remember(client) {
        Playback(ctx) { t -> client.streamUrl(t.id) + "&format=mp3&maxBitRate=320" }
    }
    DisposableEffect(Unit) { onDispose { playback.release() } }
    LaunchedEffect(playback.playing) {
        while (playback.playing) {
            playback.tick()
            delay(500)
        }
    }
    val current = playback.current

    // 封面取色：主色跟着正在播的专辑走
    LaunchedEffect(current?.albumId) {
        current?.let { t ->
            schemeFromCover(client.coverUrl(t.albumId, 300))?.let { scheme = it }
        }
    }

    var backProgress by remember { mutableStateOf(0f) }
    PredictiveBackHandler(enabled = screen > 0) { progress ->
        try {
            progress.collect { backProgress = it.progress }
            when {
                screen == 2 -> screen = 1
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
                    screen == 3 -> SettingsScreen(
                        cfg = cfg,
                        cacheDir = java.io.File(ctx.cacheDir, "media"),
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
                            onPlay = { start ->
                                if (tracks.isNotEmpty()) {
                                    playback.play(tracks, start, album.id) { client.streamUrl(it.id) }
                                    playback.tick()
                                }
                            },
                        )
                    }
                }

                // 播放页不画底栏（那里已有整套播控）
                if (screen != 2) {
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

@Composable
private fun HomeScreen(
    albums: List<Album>,
    loading: Boolean,
    error: String?,
    coverUrl: (String) -> String,
    onOpen: (Album) -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Lumen", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(YoinSymbols.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("专辑", color = MaterialTheme.colorScheme.onSurface, fontSize = 36.sp, fontWeight = FontWeight.Light)
            Text(
                if (albums.isEmpty()) "正在加载…" else "${albums.size} 张专辑",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            )
        }
        when {
            error != null -> Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
            albums.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumCard(album, coverUrl(album.coverId)) { onOpen(album) }
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, url: String, onClick: () -> Unit) {
    Column(
        Modifier
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
    val homeSrc = remember { MutableInteractionSource() }
    val playSrc = remember { MutableInteractionSource() }
    val homePressed by homeSrc.collectIsPressedAsState()
    val playPressed by playSrc.collectIsPressedAsState()

    val spec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val grow = 16.dp
    val base = 56.dp
    val leftW by animateDpAsState(if (homePressed) base + grow else base, spec, label = "left")
    val rightW by animateDpAsState(if (playPressed) base + grow else base, spec, label = "right")

    Box(modifier.fillMaxWidth().height(72.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 7.dp.toPx()
            val r = 1.25.dp.toPx()
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(Color.White.copy(alpha = 0.10f), r, Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.width(leftW).height(56.dp).clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onHome, interactionSource = homeSrc) {
                    Icon(YoinSymbols.Home, contentDescription = "首页", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (enabled) {
                Row(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(onClick = onOpenPlayer)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    // 换歌：旧名向左退场、新名从右边推入（空间弹簧）
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        },
                        label = "track",
                    ) { t ->
                        Column {
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
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box(
                Modifier.width(rightW).height(56.dp).clip(RoundedCornerShape(percent = 50)),
                contentAlignment = Alignment.Center,
            ) {
                val painter = rememberPlayPauseSymbolPainter(playing)
                IconButton(onClick = onToggle, interactionSource = playSrc) {
                    Image(
                        painter = painter,
                        contentDescription = if (playing) "暂停" else "播放",
                        modifier = Modifier.size(30.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}
