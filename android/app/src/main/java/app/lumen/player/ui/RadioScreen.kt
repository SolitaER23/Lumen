package app.lumen.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.RadioVibe
import app.lumen.player.data.Track
import app.lumen.player.util.L10n
import com.gpo.yoin.symbols.YoinSymbols

/** 氛围电台：参考播放页的封面 + 控制布局，下方直接选择场景。 */
@Composable
fun RadioScreen(
    vibe: RadioVibe,
    loading: Boolean,
    progress: Float,
    error: String?,
    current: Track?,
    playing: Boolean,
    onBack: () -> Unit,
    onVibe: (RadioVibe) -> Unit,
    onRegenerate: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpenMood: () -> Unit,
    onRetry: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val transition = rememberInfiniteTransition(label = "radio-bg")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Column {
                        Text(
                            L10n.tf("氛围电台", "Ambient radio"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            letterSpacing = 0.6.sp,
                        )
                        Text(
                            vibe.label(),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item {
                AlbumHeading(current = current)
            }

            item {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    AnimatedContent(
                        targetState = current?.title ?: if (loading) L10n.tf("正在准备电台", "Preparing station") else L10n.tf("等待曲目", "Waiting for a track"),
                        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                        label = "radio-title",
                    ) { title ->
                        Text(
                            title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        current?.artist ?: L10n.tf("本地曲库", "Local library"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accent,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable(onClick = onToggle)
                                .graphicsLayer {
                                    scaleX = if (playing) pulse else 1f
                                    scaleY = if (playing) pulse else 1f
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (playing) YoinSymbols.PauseFilled else YoinSymbols.PlayFilled,
                                contentDescription = L10n.t("播放"),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onNext,
                        modifier = Modifier.height(48.dp),
                        shape = CircleShape,
                    ) {
                        Text(L10n.tf("下一首", "Next"), fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onRegenerate) {
                        Text(L10n.tf("换一批", "Shuffle"), fontSize = 13.sp)
                    }
                }
            }

            if (loading) {
                item { RadioLoading(progress, Modifier.padding(top = 18.dp)) }
            } else if (error != null) {
                item { RadioError(error, onRetry, onOpenMood, Modifier.padding(top = 18.dp)) }
            }

            item {
                Text(
                    L10n.tf("选择电台", "Choose a station"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp, top = 26.dp, bottom = 10.dp),
                )
            }

            items(RadioVibe.entries.chunked(2).size) { rowIndex ->
                val pair = RadioVibe.entries.chunked(2)[rowIndex]
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { option ->
                        StationCard(
                            vibe = option,
                            active = option == vibe,
                            onClick = { onVibe(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AlbumHeading(current: Track?) {
    val albumName = current?.album?.takeIf { it.isNotBlank() }
        ?: L10n.tf("本地曲库", "Local library")
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        AnimatedContent(
            targetState = albumName,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
            label = "radio-album",
        ) { album ->
            Text(
                album,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 42.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.6).sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun StationCard(
    vibe: RadioVibe,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        tween(220),
        label = "station-${vibe.name}",
    )
    val border = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier
            .height(76.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(color)
            .border(1.2.dp, border, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            vibe.label(),
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            vibe.description(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun RadioLoading(progress: Float, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                L10n.tf("正在准备电台…", "Preparing station…"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun RadioError(message: String, onRetry: () -> Unit, onOpenMood: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                L10n.tf("还不能生成电台", "Cannot build a station yet"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text(L10n.tf("重试", "Retry"), fontSize = 12.sp) }
                TextButton(onClick = onOpenMood) { Text(L10n.tf("去分析", "Analyze"), fontSize = 12.sp) }
            }
        }
    }
}
