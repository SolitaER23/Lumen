@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.lumen.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.lumen.player.data.Album
import app.lumen.player.data.Artist
import app.lumen.player.data.SubsonicClient
import app.lumen.player.util.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 艺人页（M3 Expressive）：顶栏 + 艺人标题 + 专辑卡片网格。
 *
 * 首次进入时，首屏专辑卡按网格阅读顺序（左到右、再下一行）错峰飞入：
 * - 所有封面统一从左侧水平飞入（不再有纵向位移），错峰顺序保持左到右、再下一行；
 * - 入场用 MaterialTheme.motionScheme 的 default spatial + default effects，同一档位、
 *   同一个感知事件；位移负责"飞来"，透明度负责"显影"，不从中心缩放（M3 不用无锚点缩放）；
 * - 错峰总预算 300ms，卡片再多也只让前 12 张参与首屏入场，后面的卡片在滚动进来时保持静止；
 * - 每次从艺人列表/搜索"前向"进入、从专辑页/播放页返回都会重新播放；旋转不做特殊处理；
 *   系统关闭动画时仍直接落到静止态，封面、名称、年份和点击都保持可见可用（O-001）。
 */
@Composable
fun ArtistScreen(
    client: SubsonicClient,
    artist: Artist,
    /** 每次前向进入艺人页时递增；返回专辑页时保持不变，用来区分"新进入"与"返回" */
    entryNonce: Int = 0,
    coverUrl: (String) -> String,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
) {
    androidx.activity.compose.BackHandler { onBack() }
    var albums by remember(artist.id) { mutableStateOf(ArtistAlbumCache.get(artist.id)) }
    var error by remember(artist.id) { mutableStateOf<String?>(null) }
    var reload by remember(artist.id) { mutableIntStateOf(0) }
    // 入场按"这一次进入"计：entryNonce 每次前向进入都会变；从专辑页/播放页返回时
    // 页面会被重新组合，remember（非 saveable）随之归零，因此会重播入场。
    var entrancePlayed by remember(artist.id, entryNonce) { mutableStateOf(false) }

    LaunchedEffect(artist.id, reload) {
        if (albums != null) return@LaunchedEffect
        try {
            val loaded = withContext(Dispatchers.IO) { client.artistDetail(artist.id)?.second ?: emptyList() }
            albums = loaded
            ArtistAlbumCache.put(artist.id, loaded)
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }

    // 数据就绪后等一帧，让首屏卡片先完成组合（拿到 animate=true），再落下"已入场"标记。
    // 否则 LazyVerticalGrid 的 item 是测量阶段才组合的，标记可能抢在它们前面，首屏反而全都不动。
    // 这一帧只用于排序，不属于动画时长；之后滚动出来的卡片保持在静止状态。
    LaunchedEffect(albums) {
        if (albums == null || entrancePlayed) return@LaunchedEffect
        withFrameNanos { }
        entrancePlayed = true
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { LumenTopBar(title = L10n.t("艺人"), onBack = onBack) },
    ) { inner ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(
                start = LumenSizes.screenH,
                end = LumenSizes.screenH,
                bottom = LumenSizes.contentBottom,
            ),
            horizontalArrangement = Arrangement.spacedBy(LumenSizes.s),
            verticalArrangement = Arrangement.spacedBy(LumenSizes.m),
        ) {
            item(
                key = "artist-header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header",
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = LumenSizes.xs),
                ) {
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        L10n.tf(
                            artist.albumCount.toString() + " 张专辑",
                            artist.albumCount.toString() + " albums",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                error != null -> item(
                    key = "artist-error",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "state",
                ) {
                    LumenErrorState(
                        message = error!!,
                        retryLabel = L10n.t("重试"),
                        onRetry = {
                            error = null
                            reload++
                        },
                    )
                }
                albums == null -> item(
                    key = "artist-loading",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "state",
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LumenLoading()
                    }
                }
                else -> {
                    val list = albums ?: emptyList()
                    itemsIndexed(
                        items = list,
                        key = { _, album -> album.id },
                        contentType = { _, _ -> "album" },
                    ) { index, album ->
                        val startDelay = albumEnterDelayMs(index, list.size)
                        AnimatedArtistAlbumCard(
                            album = album,
                            coverUrl = coverUrl,
                            animate = !entrancePlayed && startDelay != null,
                            startDelayMs = startDelay ?: 0,
                            entranceNonce = entryNonce,
                            onOpen = { onOpenAlbum(album) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单张专辑卡：入场位移与淡入分开走 spatial / effects 两套默认 spring，
 * 同时起步、同一个档位；动画值只在 layout/draw 阶段读取（offset 与 graphicsLayer 的 lambda），
 * 不触发逐帧重组。
 */
@Composable
private fun AnimatedArtistAlbumCard(
    album: Album,
    coverUrl: (String) -> String,
    animate: Boolean,
    startDelayMs: Int,
    entranceNonce: Int,
    onOpen: () -> Unit,
) {
    // 第一次组合时把 animate 固定下来：父层随后把 entrancePlayed 置 true 引发的重组，
    // 不会让已经在跑的卡片中途丢失入场动画。
    // nonce 变化时重置卡片状态；页面从专辑页/播放页返回后会重新组合，同样从头播放。
    val animateOnce = remember(entranceNonce) { animate }
    // 0 = 起点（在最终位置左侧），1 = 归位；所有卡片同一方向。
    val slide = remember(entranceNonce) { Animatable(if (animateOnce) 0f else 1f) }
    val fade = remember(entranceNonce) { Animatable(if (animateOnce) 0f else 1f) }
    // 48dp 的水平行程让"从左往右飞入"在卡片尺寸下可读，同时仍只是位移、不是缩放。
    val slidePx = with(LocalDensity.current) { LumenSizes.xxl.toPx() }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    LaunchedEffect(album.id, entranceNonce) {
        if (!animateOnce) return@LaunchedEffect
        val durationScale = coroutineContext[MotionDurationScale]
        // 系统"移除动画"：不排队等待，也不停在半透明/半位移，直接落到终态。
        if ((durationScale?.scaleFactor ?: 1f) <= 0f) {
            slide.snapTo(1f)
            fade.snapTo(1f)
            return@LaunchedEffect
        }
        if (startDelayMs > 0) delay(startDelayMs.toLong())
        // 延时期间用户可能关掉动画；再读一次，避免停在不可见状态。
        if ((durationScale?.scaleFactor ?: 1f) <= 0f) {
            slide.snapTo(1f)
            fade.snapTo(1f)
            return@LaunchedEffect
        }
        coroutineScope {
            launch { slide.animateTo(1f, spatial) }
            launch { fade.animateTo(1f, effects) }
        }
    }

    Box(
        Modifier
            .offset { IntOffset(((1f - slide.value) * -slidePx).roundToInt(), 0) }
            .graphicsLayer { alpha = fade.value },
    ) {
        ArtistAlbumCard(
            album = album,
            coverUrl = coverUrl,
            onOpen = onOpen,
        )
    }
}

/** 与首页专辑网格同一种卡片语言：方形封面 + 两行文字，封面沿用共享元素 key。 */
@Composable
private fun ArtistAlbumCard(
    album: Album,
    coverUrl: (String) -> String,
    onOpen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberLumenHaptics()
    Surface(
        onClick = {
            haptics.navigate()
            onOpen()
        },
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(bottom = LumenSizes.xxs)) {
            AlbumArt(
                url = coverUrl(album.coverId),
                contentDescription = album.name,
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
            Spacer(Modifier.height(LumenSizes.xxs))
            Text(
                L10n.tf(
                    album.year.toString() + " · " + album.songCount + " 首",
                    album.year.toString() + " · " + album.songCount + " tracks",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = LumenSizes.xxs),
            )
        }
    }
}

/** 艺人专辑列表的内存缓存，避免返回/重新进入时出现加载态闪烁。 */
object ArtistAlbumCache {
    private val map = HashMap<String, List<Album>>()
    fun get(artistId: String): List<Album>? = map[artistId]
    fun put(artistId: String, albums: List<Album>) {
        map[artistId] = albums
    }
}
