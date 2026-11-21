package com.example.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.app.data.Track
import com.gpo.yoin.symbols.YoinSymbols
import com.gpo.yoin.symbols.rememberPlayPauseSymbolPainter

/** 胶囊圆形操作按钮：图标 + 文字标签 */
@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    shape: androidx.compose.ui.graphics.Shape,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(66.dp)
                .clip(shape)
                .background(container)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = content,
                modifier = Modifier.size(27.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * 播放页：大圆角封面 + 标题/艺术家 + M3 滑块 + 圆形传输键。
 *
 * 进度由上层每 500ms 喂进来（[positionMs] / [durationMs]），拖动时本地覆盖，
 * 松手再 seek，避免和播放器互相打架。
 */
@Composable
fun PlayingScreen(
    track: Track?,
    coverUrl: String,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    albumName: String = "",
    starred: Boolean = false,
    trackDownloaded: Boolean = false,
    onToggleStar: () -> Unit = {},
    onDownloadTrack: () -> Unit = {},
    coverModifier: Modifier = Modifier,
) {
    // duration 未知（刚起播 / 刚切歌）时不要把比例算成 100%，拖动时用本地值覆盖
    val known = durationMs > 0L
    var dragging by remember(track?.id) { mutableStateOf(false) }
    var dragMs by remember(track?.id) { mutableStateOf(0L) }
    val shownMs = if (dragging) dragMs else positionMs
    val frac = if (!known) 0f else (shownMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 76.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(YoinSymbols.Back, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "正在播放",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                albumName,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 16.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = track?.title,
                contentScale = ContentScale.Crop,
                modifier = coverModifier
                    .size(232.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.weight(1f))
            // 封面右侧：单曲下载 / 收藏，两个圆形按钮竖排（靠近右侧边缘）
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 2.dp),
            ) {
                CircleAction(
                    icon = if (trackDownloaded) YoinSymbols.DownloadDone else YoinSymbols.Download,
                    label = if (trackDownloaded) "已下载" else "下载",
                    shape = RoundedCornerShape(20.dp),
                    container = if (trackDownloaded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer,
                    content = if (trackDownloaded) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { if (!trackDownloaded) onDownloadTrack() },
                )
                CircleAction(
                    icon = if (starred) YoinSymbols.StarFilled else YoinSymbols.Star,
                    label = if (starred) "已收藏" else "收藏",
                    shape = CircleShape,
                    container = if (starred) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiaryContainer,
                    content = if (starred) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onToggleStar,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Column(Modifier.padding(horizontal = 28.dp)) {
            Text(
                track?.title ?: "未在播放",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                track?.artist ?: "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(20.dp))
        Column(Modifier.padding(horizontal = 28.dp)) {
            Slider(
                value = frac,
                onValueChange = { v ->
                    if (known) {
                        dragging = true
                        dragMs = (v * durationMs).toLong()
                    }
                },
                onValueChangeFinished = {
                    if (dragging) {
                        onSeek(dragMs)
                        dragging = false
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(shownMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (known) "-" + fmt(durationMs - shownMs) else "--:--",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
                Icon(YoinSymbols.SkipPrevious, contentDescription = "上一首", tint = MaterialTheme.colorScheme.onSurface)
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                val painter = rememberPlayPauseSymbolPainter(playing)
                IconButton(onClick = onToggle, modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painter,
                        contentDescription = if (playing) "暂停" else "播放",
                        modifier = Modifier.size(32.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
                Icon(YoinSymbols.SkipNext, contentDescription = "下一首", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
