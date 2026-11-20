package com.example.lumen.ui

import androidx.compose.foundation.Image
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
import com.example.lumen.data.Track
import com.gpo.yoin.symbols.YoinSymbols
import com.gpo.yoin.symbols.rememberPlayPauseSymbolPainter

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
) {
    val duration = durationMs.coerceAtLeast(1L)
    val frac = (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(YoinSymbols.Back, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("正在播放", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(12.dp))
        AsyncImage(
            model = coverUrl,
            contentDescription = track?.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .height(330.dp)
                .clip(RoundedCornerShape(28.dp)),
        )

        Spacer(Modifier.height(24.dp))
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
                onValueChange = { onSeek((it * duration).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(positionMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("-" + fmt(duration - positionMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
