package com.example.app.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
import com.example.app.data.Album
import com.example.app.data.absoluteStart
import com.example.app.data.discsOf
import com.example.app.data.tracksOfDisc
import com.example.app.data.Track
import com.example.app.playback.AlbumDownload
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
    coverModifier: Modifier = Modifier,
) {
    // 首帧只画头部，曲目表推到下一帧，避免一次组合太多导致掉帧
    // 用当前专辑封面取色（Palette 有缓存），失败时回退到全局主题
    var albumScheme by remember(coverUrl) { mutableStateOf<ColorScheme?>(null) }
    LaunchedEffect(coverUrl) { albumScheme = schemeFromCover(coverUrl) }
    val s = albumScheme ?: MaterialTheme.colorScheme

    var showList by remember(album.id) { mutableStateOf(false) }
    LaunchedEffect(album.id) { withFrameNanos { }; showList = true }
    val discKeys = remember(tracks) { discsOf(tracks) }
    var discIndex by remember(album.id) { mutableIntStateOf(0) }
    val discKey = discKeys.getOrNull(discIndex.coerceIn(0, (discKeys.size - 1).coerceAtLeast(0))) ?: 1
    val list = remember(tracks, discKey) { tracksOfDisc(tracks, discKey) }
    val startIndex = remember(tracks, discKey) { absoluteStart(tracks, discKey) }

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
                    Icon(YoinSymbols.Back, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("专辑", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier.size(150.dp).clip(RoundedCornerShape(24.dp)),
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
                        "${album.year} · ${album.songCount} 首",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item {
            // 三个按钮：形状 / 颜色各不相同，颜色都从当前专辑封面取色
            val finished = download != null && download.total > 0 && download.done == download.total
            val dlLabel = when {
                download == null || download.total == 0 -> "下载"
                finished -> "已下载"
                else -> "下载 ${download.done}/${download.total}"
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
                        Text("随机", color = s.onSecondary, fontSize = 13.sp)
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
                        Text("播放", color = s.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                // 下载：切角形状 + tertiary 容器色
                Surface(
                    onClick = { if (!finished) onDownload() },
                    shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
                    color = s.tertiaryContainer,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
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
        if (showList) itemsIndexed(list, key = { _, t -> t.id }) { i, track ->
            val active = track.id == currentTrackId
            Row(
                Modifier
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
                Text(
                    "%d:%02d".format(track.seconds / 60, track.seconds % 60),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
