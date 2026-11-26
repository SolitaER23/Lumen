package app.lumen.player.ui

import app.lumen.player.util.L10n
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import app.lumen.player.data.Album
import app.lumen.player.data.absoluteStart
import app.lumen.player.data.discsOf
import app.lumen.player.data.tracksOfDisc
import app.lumen.player.data.Track
import app.lumen.player.playback.AlbumDownload
import com.gpo.yoin.symbols.YoinSymbols

/**
 * 专辑页：整页可滚动（顶栏、封面、播放键、碟标签、曲目表都在同一个 LazyColumn 里），
 * 滚动时头图自然收起，把空间让给曲目。
 *
 * 多碟按 `disc` 分组，标签只影响列表；点曲目传**绝对下标**，跨碟连续播放。
 */
@Composable
fun AlbumScreen(
    album: Album,
    tracks: List<Track>,
    coverUrl: String,
    currentTrackId: String?,
    onBack: () -> Unit,
    onPlay: (startIndex: Int) -> Unit,
    onShuffle: () -> Unit,
    download: AlbumDownload?,
    onDownload: () -> Unit,
    downloadedIds: Set<String> = emptySet(),
    themeOpts: ThemeOptions = ThemeOptions(),
    /** P1-1 / 扩展：每页独立取色；关闭时跟随全局主题 */
    selfColor: Boolean = false,
    coverModifier: Modifier = Modifier,
) {
    // 页面内返回按钮 / 系统返回统一走同一入口
    androidx.activity.compose.BackHandler { onBack() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // P1-2：与播放页同一口径（封面 + 同一套选项）
    // P0-F1：首帧就用内存里的配色，避免"先全局色再切专辑色"的一帧闪烁
    var albumScheme by remember(album.id) {
        mutableStateOf(schemeFromCache(album.id, themeOpts))
    }
    LaunchedEffect(album.id, themeOpts) {
        albumScheme = schemeFromCover(ctx, album.id, coverUrl, themeOpts)
    }
    val s = if (selfColor) (albumScheme ?: MaterialTheme.colorScheme) else MaterialTheme.colorScheme

    // 重进专辑页时直接恢复（否则曲目表先消失一帧会把滚动位置夹回 0）
    var showList by rememberSaveable(album.id) { mutableStateOf(false) }
    LaunchedEffect(album.id) { withFrameNanos { }; showList = true }
    val discKeys = remember(tracks) { discsOf(tracks) }
    var discIndex by rememberSaveable(album.id) { mutableIntStateOf(0) }
    val discKey = discKeys.getOrNull(discIndex.coerceIn(0, (discKeys.size - 1).coerceAtLeast(0))) ?: 1
    val list = remember(tracks, discKey) { tracksOfDisc(tracks, discKey) }
    val startIndex = remember(tracks, discKey) { absoluteStart(tracks, discKey) }

    MaterialTheme(colorScheme = s) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(L10n.t("专辑"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier
                        .size(150.dp)
                        .albumSharedElement("cover-${album.id}")
                        .clip(RoundedCornerShape(24.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        album.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(album.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(
                        L10n.tf("${album.year} · ${album.songCount} 首", "${album.year} · ${album.songCount} tracks"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item {
            // 三个按钮：形状 / 颜色各不相同，颜色都从当前专辑封面取色
            // 下载状态要同时看两处：专辑整组任务（download） 与 逐曲已完成集合（downloadedIds）。
            // 只有一首歌的专辑若单独下载了那首，之前会一直显示「下载」。
            val albumTotal = if (tracks.isNotEmpty()) tracks.size else (download?.total ?: 0)
            val albumDone = maxOf(download?.done ?: 0, tracks.count { it.id in downloadedIds })
            val finished = albumTotal > 0 && albumDone >= albumTotal
            val dlLabel = when {
                albumTotal == 0 -> L10n.t("下载")
                finished -> L10n.t("已下载")
                albumDone == 0 -> L10n.t("下载")
                else -> albumDone.toString() + "/" + albumTotal
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 随机：胶囊 + secondary 色
                Surface(
                    onClick = { onShuffle() },
                    shape = RoundedCornerShape(percent = 50),
                    color = s.secondary,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(YoinSymbols.Shuffle, contentDescription = null, tint = s.onSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(L10n.t("随机"), color = s.onSecondary, fontSize = 13.sp)
                    }
                }
                // 播放：大圆角方块 + primary 色（最显眼）
                Surface(
                    onClick = { onPlay(startIndex) },
                    shape = RoundedCornerShape(18.dp),
                    color = s.primary,
                    modifier = Modifier.weight(1.25f).height(56.dp),
                ) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(YoinSymbols.PlayFilled, contentDescription = null, tint = s.onPrimary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(L10n.t("播放"), color = s.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                // 下载：切角形状 + tertiary 容器色；下载中在底部显示进度条
                Surface(
                    onClick = { if (!finished) onDownload() },
                    shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
                    color = s.tertiaryContainer,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (finished) YoinSymbols.DownloadDone else YoinSymbols.Download,
                                contentDescription = null,
                                tint = s.onTertiaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(dlLabel, color = s.onTertiaryContainer, fontSize = 12.sp)
                        }
                        val busy = albumTotal > 0 && !finished && albumDone > 0
                        if (busy) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth((albumDone.toFloat() / albumTotal).coerceIn(0f, 1f))
                                    .height(3.dp)
                                    .background(s.onTertiaryContainer.copy(alpha = 0.55f)),
                            )
                        }
                    }
                }
            }
        }
        if (showList && discKeys.size > 1) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    discKeys.forEachIndexed { i, d ->
                        val selected = i == discIndex
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                )
                                .clickable { discIndex = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "DISC $d",
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        // 曲目还在路上（首次进入 / 弱网）：先铺占位行，避免页面"空着等"
        if (showList && tracks.isEmpty()) {
            items(7) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                )
            }
        }
        if (showList) itemsIndexed(list, key = { _, t -> t.id }) { i, track ->
            val active = track.id == currentTrackId
            Row(
                Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    )
                    .clickable { onPlay(startIndex + i) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "%02d".format(i + 1),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    track.title,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (track.id in downloadedIds) {
                    Icon(
                        YoinSymbols.DownloadDone,
                        contentDescription = L10n.t("已下载"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    "%d:%02d".format(track.seconds / 60, track.seconds % 60),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
    }
    }
}
