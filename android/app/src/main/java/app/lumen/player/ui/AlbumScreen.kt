package app.lumen.player.ui

import app.lumen.player.util.L10n
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lumen.player.data.Album
import app.lumen.player.data.Track
import app.lumen.player.data.absoluteStart
import app.lumen.player.data.discsOf
import app.lumen.player.data.tracksOfDisc
import com.gpo.yoin.symbols.YoinSymbols

/**
 * 专辑页（M3 Expressive）：
 * - `MediumFlexibleTopAppBar`：专辑名 + 艺人，滚动时收起成大标题的小字；
 * - hero 封面用共享元素 key，和首页卡片、播放页封面同一条动画；
 * - 主操作是 filled Button：本专辑正在播放时显示「暂停」，暂停时显示「继续播放」，
 *   其余情况才是「播放」（语义见 [albumPlayAction]）；次操作 FilledTonalButton「随机」；
 * - 多碟用 FilterChip 切换，曲目行用 Surface + M3 字阶，当前曲目用 primaryContainer。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumScreen(
    album: Album,
    tracks: List<Track>,
    coverUrl: String,
    currentTrackId: String?,
    /** 是否处于播放/缓冲中（含刚点播放、还没出声的窗口；由播放器回调驱动，不是页面自己猜的） */
    isPlaying: Boolean,
    /** 当前曲目是否属于这张专辑：决定主按钮是「播放本专辑」还是「暂停/继续」 */
    isCurrentAlbum: Boolean,
    onBack: () -> Unit,
    /** 从某首开始播（本专辑不是当前队列时，或点了非当前曲目） */
    onPlay: (startIndex: Int) -> Unit,
    /** 暂停 / 继续当前播放（不重建队列，保留进度） */
    onToggle: () -> Unit,
    onShuffle: () -> Unit,
) {
    androidx.activity.compose.BackHandler { onBack() }
    val scheme = MaterialTheme.colorScheme

    var showList by rememberSaveable(album.id) { mutableStateOf(false) }
    LaunchedEffect(album.id) {
        withFrameNanos { }
        showList = true
    }
    val discKeys = remember(tracks) { discsOf(tracks) }
    var discIndex by rememberSaveable(album.id) { mutableIntStateOf(0) }
    val discKey = discKeys.getOrNull(discIndex.coerceIn(0, (discKeys.size - 1).coerceAtLeast(0))) ?: 1
    val list = remember(tracks, discKey) { tracksOfDisc(tracks, discKey) }
    val startIndex = remember(tracks, discKey) { absoluteStart(tracks, discKey) }

    // 主按钮的唯一状态来源：由「当前曲目是否属于本专辑 + 播放器是否在播」推导。
    // 不再用页面内 boolean 缓存，离开页面/切歌/在播放页暂停后回到这里都能立即对齐。
    val playAction = albumPlayAction(isCurrentAlbum = isCurrentAlbum, isPlaying = isPlaying)

    // 列表增删/移动统一走 M3 动效规格（效果 = 淡入淡出；位置 = 空间 spring）
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()

    // 固定高度顶栏：此前用 MediumFlexibleTopAppBar + 折叠滚动，
    // 顶栏高度随滚动连续变化，封面（列表首项）会在"展开的标题区"和播放按钮之间来回跳。
    // 现在顶栏高度恒定，列表视口不随滚动变化，封面只随滚动位移，快速上下拖也不再抖。
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            LumenTopBar(
                title = album.name,
                onBack = onBack,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = LumenSizes.contentBottom),
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(top = LumenSizes.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    AlbumArt(
                        url = coverUrl,
                        contentDescription = album.name,
                        modifier = Modifier.size(220.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        sharedKey = albumCoverKey(album.id),
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = LumenSizes.l, vertical = LumenSizes.s),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        L10n.tf(
                            "${album.artist} · ${album.year} · ${album.songCount} 首 · ${discKeys.size} 碟",
                            "${album.artist} · ${album.year} · ${album.songCount} tracks · ${discKeys.size} disc",
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = LumenSizes.l),
                    horizontalArrangement = Arrangement.spacedBy(LumenSizes.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            when (playAction) {
                                AlbumPlayAction.START_ALBUM -> onPlay(startIndex)
                                // 暂停与继续都走同一个 toggle：不重建队列，
                                // 所以播放中再点是暂停、暂停后再点是从原进度继续
                                AlbumPlayAction.PAUSE, AlbumPlayAction.RESUME -> onToggle()
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = ButtonDefaults.shape,
                    ) {
                        Icon(
                            if (playAction == AlbumPlayAction.PAUSE) YoinSymbols.PauseFilled
                            else YoinSymbols.PlayFilled,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(LumenSizes.xs))
                        Text(
                            when (playAction) {
                                AlbumPlayAction.START_ALBUM -> L10n.t("播放")
                                AlbumPlayAction.PAUSE -> L10n.t("暂停")
                                AlbumPlayAction.RESUME -> L10n.t("继续播放")
                            },
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                    }
                    FilledTonalButton(
                        onClick = onShuffle,
                        modifier = Modifier.height(56.dp),
                        shape = ButtonDefaults.shape,
                    ) {
                        Icon(YoinSymbols.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(LumenSizes.xs))
                        Text(L10n.t("随机"), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (showList && discKeys.size > 1) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = LumenSizes.l, vertical = LumenSizes.m),
                        horizontalArrangement = Arrangement.spacedBy(LumenSizes.xs),
                    ) {
                        discKeys.forEachIndexed { i, d ->
                            FilterChip(
                                selected = i == discIndex,
                                onClick = { discIndex = i },
                                label = { Text("DISC $d") },
                            )
                        }
                    }
                }
            }
            if (showList && tracks.isEmpty()) {
                items(count = 6, contentType = { "skeleton" }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LumenSizes.l, vertical = LumenSizes.xxs)
                            .height(56.dp)
                            .background(scheme.surfaceContainerHigh, MaterialTheme.shapes.large),
                    )
                }
            }
            if (showList) {
                itemsIndexed(list, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { i, track ->
                    val active = track.id == currentTrackId
                    Surface(
                        // 点当前这首等价于按播放/暂停：播放中暂停、暂停中继续，
                        // 不重建队列也就不会从头重播；点其他曲目才切歌
                        onClick = { if (active) onToggle() else onPlay(startIndex + i) },
                        shape = MaterialTheme.shapes.large,
                        color = if (active) scheme.primaryContainer else Color.Transparent,
                        contentColor = if (active) scheme.onPrimaryContainer else scheme.onSurface,
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = fastEffects,
                                placementSpec = placementSpec,
                                fadeOutSpec = fastEffects,
                            )
                            .fillMaxWidth()
                            .padding(horizontal = LumenSizes.s, vertical = 1.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = LumenSizes.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "%02d".format(i + 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                                modifier = Modifier.width(32.dp),
                            )
                            Text(
                                track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(LumenSizes.xs))
                            Text(
                                "%d:%02d".format(track.seconds / 60, track.seconds % 60),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
