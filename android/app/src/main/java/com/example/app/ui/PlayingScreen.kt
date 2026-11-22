package com.example.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.text.style.TextAlign
import com.example.app.data.LyricLine
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import coil.compose.AsyncImage
import com.example.app.data.Track
import com.gpo.yoin.symbols.YoinSymbols
import kotlin.math.PI
import kotlin.math.sin

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * 播放页：封面 + 单曲信息 + 紧凑控制行。
 *
 * 控制行从左到右：播放/暂停圆钮 → 歌词按钮 → **波浪形进度条**（MD3 Expressive 风格，
 * 波幅随播放状态脉动，可点按/拖动 seek）。
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
    lyrics: List<LyricLine>? = null,
    lyricsLoading: Boolean = false,
    showLyrics: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    playMode: com.example.app.playback.PlayMode = com.example.app.playback.PlayMode.LIST,
    onCyclePlayMode: () -> Unit = {},
    onSeekPlay: (Long) -> Unit = {},
    mood: com.example.app.data.MoodResult? = null,
    moodAnalyzing: Boolean = false,
    moodPhase: String = "",
    onAnalyzeMood: () -> Unit = {},
    rating: Float = 0f,
    onRating: (Float) -> Unit = {},
    coverModifier: Modifier = Modifier,
) {
    val known = durationMs > 0L
    val progress = if (known) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(bottom = 20.dp)) {
            // 顶栏：返回 + 正在播放 + 专辑名
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

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = track?.title,
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier.size(232.dp).clip(RoundedCornerShape(24.dp)),
                )
                Box(
                    Modifier.weight(1f).height(232.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PlayerAction(
                            icon = if (trackDownloaded) YoinSymbols.DownloadDone else YoinSymbols.Download,
                            shape = RoundedCornerShape(22.dp),
                            container = if (trackDownloaded) MaterialTheme.colorScheme.surfaceContainerHighest
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            content = if (trackDownloaded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            onClick = { if (!trackDownloaded) onDownloadTrack() },
                        )
                        PlayerAction(
                            icon = if (starred) YoinSymbols.StarFilled else YoinSymbols.Star,
                            shape = CircleShape,
                            container = if (starred) MaterialTheme.colorScheme.surfaceContainerHighest
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            content = if (starred) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            onClick = onToggleStar,
                        )
                        PlayerAction(
                            icon = when (playMode) {
                                com.example.app.playback.PlayMode.ONE -> YoinSymbols.RepeatOne
                                com.example.app.playback.PlayMode.SHUFFLE -> YoinSymbols.Shuffle
                                else -> YoinSymbols.Repeat
                            },
                            shape = RoundedCornerShape(22.dp),
                            container = if (playMode == com.example.app.playback.PlayMode.LIST)
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            content = if (playMode == com.example.app.playback.PlayMode.LIST)
                                MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.primary,
                            onClick = onCyclePlayMode,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            // 单曲名
            Text(
                track?.title ?: "未在播放",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                track?.artist ?: "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 28.dp),
            )

            Spacer(Modifier.height(10.dp))
            // 第一行：播放/暂停（左） + 波浪进度（右，时间在两侧）
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) YoinSymbols.PauseFilled else YoinSymbols.PlayFilled,
                        contentDescription = if (playing) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt(positionMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    WavyProgress(
                        progress = progress,
                        active = playing,
                        modifier = Modifier.weight(1f).height(48.dp),
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.outlineVariant,
                        onSeek = { f -> if (known) onSeek((f * durationMs).toLong()) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (known) "-" + fmt(durationMs - positionMs) else "--:--",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // 第二行：歌词 + 上一首 + 下一首（靠左，不贴右）

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (showLyrics) MaterialTheme.colorScheme.inversePrimary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        .border(
                            1.5.dp,
                            if (showLyrics) MaterialTheme.colorScheme.inversePrimary
                            else MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.55f),
                            RoundedCornerShape(18.dp),
                        )
                        .clickable(onClick = onToggleLyrics),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        YoinSymbols.Lyrics,
                        contentDescription = "歌词",
                        tint = if (showLyrics) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.inversePrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable(onClick = onPrev),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        YoinSymbols.SkipPrevious,
                        contentDescription = "上一首",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(20.dp))
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .clickable(onClick = onNext),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        YoinSymbols.SkipNext,
                        contentDescription = "下一首",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                RatingControl(
                    value = rating,
                    onChange = onRating,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (showLyrics) {
                LyricsArea(
                    lines = lyrics,
                    loading = lyricsLoading,
                    positionMs = positionMs,
                    onSeekPlay = onSeekPlay,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }


    }
}

/** MD3 Expressive 波浪进度条：波幅随播放脉动，支持点击/拖动 seek。 */
@Composable
private fun WavyProgress(
    progress: Float,
    active: Boolean,
    modifier: Modifier,
    activeColor: Color,
    inactiveColor: Color,
    onSeek: (Float) -> Unit,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }
    val shown = if (dragging) dragProgress else progress

    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    // 近似"节奏"：约 130 BPM 的脉动，播放时幅度更大
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(460, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Canvas(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    onSeek((off.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        dragging = true
                        dragProgress = (off.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek(dragProgress)
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                }
            },
    ) {
        // 暂停时冻结波形（相位与脉动都停住），只保留静态起伏
        val amp = (if (active) 14.dp.toPx() else 7.dp.toPx()) * (if (active) pulse else 0.7f)
        val phaseNow = if (active) phase else 0f
        val midY = size.height / 2f
        val steps = 180
        val inactive = Path()
        val activePath = Path()
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = size.width * t
            // 越靠右波幅越大，像声波一样
            val env = 0.45f + 0.55f * t
            val y = midY + sin(t * 6f * PI.toFloat() + phaseNow) * amp * env
            if (i == 0) {
                inactive.moveTo(x, y)
                activePath.moveTo(x, y)
            } else {
                inactive.lineTo(x, y)
                if (t <= shown) activePath.lineTo(x, y)
            }
        }
        drawPath(inactive, inactiveColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(activePath, activeColor, style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** 播放页下方的歌词区：按时间轴高亮当前行、自动滚动、上下渐隐。 */
@Composable
private fun LyricsArea(
    lines: List<LyricLine>?,
    loading: Boolean,
    positionMs: Long,
    onSeekPlay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val hasTimeline = lines?.any { it.startMs > 0L } == true
    val currentIndex = if (lines.isNullOrEmpty() || !hasTimeline) -1
    else lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)

    val appear = remember { Animatable(0f) }
    LaunchedEffect(lines) {
        if (!lines.isNullOrEmpty()) {
            appear.snapTo(0f)
            appear.animateTo(
                1f,
                tween(520, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            )
        }
    }

    BoxWithConstraints(modifier) {
        val fadeRange = maxHeight * 0.42f
        val fadePx = with(density) { fadeRange.toPx() }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            lines.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无歌词", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            else -> {
                // 上下留白 = 半个视口，配合 scrollToItem 让当前行自然落在视觉中心
                val vPad = (maxHeight / 2) - 13.dp
                LaunchedEffect(currentIndex) {
                    if (currentIndex >= 0) {
                        runCatching { listState.animateScrollToItem(currentIndex) }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.16f to Color.Black,
                                    0.84f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                        .graphicsLayer { alpha = appear.value },
                    contentPadding = PaddingValues(top = vPad, bottom = vPad, start = 24.dp, end = 24.dp),
                ) {
                    itemsIndexed(lines) { i, line ->
                        // 层次只由“离当前行的距离”决定，不读取滚动布局，滚动中不会反复重排
                        val d = kotlin.math.abs(i - currentIndex).toFloat()
                        val smooth = kotlin.math.exp(-d / 2.0f)
                        val alpha = 0.12f + 0.88f * smooth
                        val isCurrent = i == currentIndex
                        val scale = if (isCurrent) 1.0f else 0.97f
                        Text(
                            line.text,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 26.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = hasTimeline && line.startMs > 0L) {
                                    onSeekPlay(line.startMs)
                                }
                                .padding(vertical = 9.dp)
                                .graphicsLayer {
                                    this.alpha = alpha
                                    scaleX = scale
                                    scaleY = scale
                                },
                        )
                    }
                }
            }
        }
    }
}

/** 横向拖动打分条：0.0 – 10.0，步径 0.1；加宽轨道、无刻度线、固定数值宽度避免拖动抖动。 */
@Composable
private fun RatingControl(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) dragValue else value
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface

    Row(
        modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            Modifier
                .weight(1f)
                .height(52.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun quantize(x: Float): Float {
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            return ((x / w * 100f).roundToInt() / 10f).coerceIn(0f, 10f)
                        }
                        dragValue = quantize(down.position.x)
                        dragging = true
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            if (!change.pressed) break
                            dragValue = quantize(change.position.x)
                            change.consume()
                        }
                        dragging = false
                        onChange(dragValue)
                    }
                },
        ) {
            val trackH = 12.dp.toPx()
            val cy = size.height / 2f
            val top = cy - trackH / 2f
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, trackH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f),
            )
            if (shown > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(tertiary, primary),
                        startX = 0f,
                        endX = size.width,
                    ),
                    topLeft = Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(size.width * (shown / 10f), trackH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f),
                )
            }
            // 无刻度、无把手：只用填充长度表达数值
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "%.1f".format(shown),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
    }
}

/** 圆形/方形操作按钮：图标 + 文字标签 */
@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    shape: androidx.compose.ui.graphics.Shape,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    showLabel: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .clip(shape)
                .background(container)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(30.dp))
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
/** 播放页右侧的统一操作按钮（下载/收藏共用一套尺寸与圆角）。 */
@Composable
private fun PlayerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    text: String? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(56.dp)
            .clip(shape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        } else if (text != null) {
            Text(text, color = content, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}