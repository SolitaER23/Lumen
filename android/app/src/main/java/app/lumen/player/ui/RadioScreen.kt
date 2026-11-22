package app.lumen.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.RadioVibe
import app.lumen.player.data.Track
import app.lumen.player.util.L10n
import coil.compose.AsyncImage
import com.gpo.yoin.symbols.YoinSymbols

/** 氛围电台：本地 521 维相似度生成，不展示标签或队列列表。 */
@Composable
fun RadioScreen(
    vibe: RadioVibe,
    loading: Boolean,
    progress: Float,
    error: String?,
    current: Track?,
    coverUrl: String,
    playing: Boolean,
    queueSize: Int,
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
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val float by transition.animateFloat(
        initialValue = -0.02f,
        targetValue = 0.02f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
        label = "float",
    )
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 18f / 255f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(w * (0.15f + 0.1f * drift), h * 0.12f),
                    radius = w * 0.8f,
                ),
                radius = w * 0.8f,
                center = androidx.compose.ui.geometry.Offset(w * (0.15f + 0.1f * drift), h * 0.12f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(secondary.copy(alpha = 14f / 255f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(w * (0.9f - 0.1f * drift), h * 0.65f),
                    radius = w * 0.7f,
                ),
                radius = w * 0.7f,
                center = androidx.compose.ui.geometry.Offset(w * (0.9f - 0.1f * drift), h * 0.65f),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    "RADIO",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.16f)) {
                    Text(
                        if (loading) L10n.tf("构建中", "BUILDING") else L10n.tf("本机生成", "ON DEVICE"),
                        color = accent,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            Text(
                L10n.tf("氛围电台", "Ambient Radio"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                L10n.tf(
                    "用 521 维音频特征，从你的曲库连成一段连续氛围。",
                    "A continuous station built from 521-dim audio features in your own library.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            RadioHero(
                current = current,
                coverUrl = coverUrl,
                vibe = vibe,
                playing = playing,
                loading = loading,
                float = float,
                modifier = Modifier.padding(top = 20.dp),
            )

            if (loading) {
                LoadingRadio(progress)
            } else if (error != null) {
                RadioError(error, onRetry, onOpenMood)
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioVibe.entries.forEach { option ->
                        val active = option == vibe
                        val color by animateColorAsState(
                            if (active) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                            tween(260),
                            label = "vibe-${option.name}",
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { onVibe(option) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                option.label(),
                                color = if (active) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accent,
                        modifier = Modifier.size(78.dp),
                    ) {
                        Box(
                            Modifier.fillMaxSize().clickable(onClick = onToggle),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(78.dp)
                                    .graphicsLayer {
                                        scaleX = if (playing) pulse else 1f
                                        scaleY = if (playing) pulse else 1f
                                    },
                            )
                            Icon(
                                if (playing) YoinSymbols.PauseFilled else YoinSymbols.PlayFilled,
                                contentDescription = L10n.t("播放"),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            L10n.tf("电台曲目", "Station queue"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            L10n.tf("$queueSize 首 · 本地生成", "$queueSize tracks · local"),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            L10n.tf("一次连续的氛围", "One continuous vibe"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = onNext,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text("→", fontSize = 18.sp) }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onRegenerate,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = CircleShape,
                    ) {
                        Text(L10n.tf("换一段氛围", "New station"), fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onOpenMood,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = CircleShape,
                    ) {
                        Text(L10n.tf("分析更多", "Analyze more"), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(110.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun RadioHero(
    current: Track?,
    coverUrl: String,
    vibe: RadioVibe,
    playing: Boolean,
    loading: Boolean,
    float: Float,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(if (playing) 1f else 0.97f, tween(500), label = "cover-scale")
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp, max = 340.dp)
            .graphicsLayer {
                translationY = float * 40.dp.toPx()
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(38.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = current?.album ?: L10n.tf("电台封面", "Station cover"),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                ),
        )
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Bottom) {
            Surface(shape = CircleShape, color = accent) {
                Text(
                    vibe.label(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            AnimatedContent(
                targetState = current?.title ?: if (loading) L10n.tf("正在构建电台", "Building station") else L10n.tf("等待曲目", "Waiting for a track"),
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                label = "radio-title",
                modifier = Modifier.padding(top = 12.dp),
            ) { title ->
                Text(
                    title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                current?.artist ?: L10n.tf("本机音频特征", "On-device audio features"),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun LoadingRadio(progress: Float) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(
                L10n.tf("正在扫描曲库并计算相似度…", "Scanning library and computing similarity…"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun RadioError(message: String, onRetry: () -> Unit, onOpenMood: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                L10n.tf("还无法生成电台", "Cannot build a station yet"),
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
                Button(onClick = onRetry, shape = CircleShape) { Text(L10n.tf("重试", "Retry"), fontSize = 12.sp) }
                OutlinedButton(onClick = onOpenMood, shape = CircleShape) {
                    Text(L10n.tf("去分析", "Analyze"), fontSize = 12.sp)
                }
            }
        }
    }
}
