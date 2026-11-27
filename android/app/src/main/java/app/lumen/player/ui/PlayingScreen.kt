package app.lumen.player.ui

import app.lumen.player.util.L10n
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lumen.player.data.LyricLine
import app.lumen.player.data.Track
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.flow.distinctUntilChanged

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * 播放页（M3 Expressive「Now Playing」）：
 *
 * 自上而下：顶栏 → 共享元素封面 → 曲名/艺人 → Slider + 时间 → 控制行 → 歌词。
 * - 封面 key 与首页卡片 / 专辑页 hero / 迷你条一致，转场时是同一个元素在移动；
 * - 进度用官方 `Slider`（可拖、可点、带焦点语义），不再自绘波形；
 * - 控制按钮全部是 M3 组件：`FilledIconButton`（主操作）+ `FilledTonalIconButton`（次操作）
 *   + `FilledTonalIconToggleButton`（歌词开关），按下有形状 morph。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayingScreen(
    track: Track?,
    coverUrl: String,
    /**
     * 播放位置。用 lambda 提供：只有进度条/时间/歌词三块读它，
     * 封面、曲名与控制按钮不再跟着每 250ms 的位置推进一起重组。
     */
    positionMsProvider: () -> Long = { 0L },
    durationMs: Long,
    playing: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    albumName: String = "",
    lyrics: List<LyricLine>? = null,
    lyricsLoading: Boolean = false,
    showLyrics: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    playMode: app.lumen.player.playback.PlayMode = app.lumen.player.playback.PlayMode.LIST,
    onCyclePlayMode: () -> Unit = {},
    coverDirection: Int = 1,
    onSeekPlay: (Long) -> Unit = {},
    coverKey: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val known = durationMs > 0L

    val modeIcon = when (playMode) {
        app.lumen.player.playback.PlayMode.ONE -> YoinSymbols.RepeatOne
        app.lumen.player.playback.PlayMode.SHUFFLE -> YoinSymbols.Shuffle
        else -> YoinSymbols.Repeat
    }
    val modeLabel = when (playMode) {
        app.lumen.player.playback.PlayMode.ONE -> L10n.t("单曲循环")
        app.lumen.player.playback.PlayMode.SHUFFLE -> L10n.t("随机播放")
        else -> L10n.t("列表循环")
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            LumenTopBar(
                title = L10n.t("正在播放"),
                onBack = onBack,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .navigationBarsPadding()
                .padding(bottom = LumenSizes.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AlbumArt(
                url = coverUrl,
                contentDescription = track?.title,
                modifier = Modifier.size(248.dp),
                shape = MaterialTheme.shapes.extraLarge,
                sharedKey = coverKey,
            )
            Spacer(Modifier.height(LumenSizes.l))
            Text(
                track?.title ?: L10n.t("未在播放"),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = LumenSizes.l),
            )
            Spacer(Modifier.height(LumenSizes.xxs))
            Text(
                track?.artist ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = LumenSizes.l),
            )
            if (albumName.isNotBlank()) {
                Text(
                    albumName,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = LumenSizes.l),
                )
            }

            Spacer(Modifier.height(LumenSizes.s))
            // 进度区自成组件：位置只在这里被读到，把「每秒 4 次重组」限制在这一小块
            SeekSection(
                positionProvider = positionMsProvider,
                durationMs = durationMs,
                known = known,
                onSeek = onSeek,
            )

            Spacer(Modifier.height(LumenSizes.s))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = LumenSizes.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconToggleButton(
                    checked = showLyrics,
                    onCheckedChange = { onToggleLyrics() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        YoinSymbols.Lyrics,
                        contentDescription = L10n.t("歌词"),
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                    Icon(
                        YoinSymbols.SkipPrevious,
                        contentDescription = L10n.t("上一首"),
                        tint = scheme.onSurface,
                        modifier = Modifier.size(32.dp),
                    )
                }
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(78.dp)) {
                    Icon(
                        if (playing) YoinSymbols.PauseFilled else YoinSymbols.PlayFilled,
                        contentDescription = if (playing) L10n.t("暂停") else L10n.t("播放"),
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(
                        YoinSymbols.SkipNext,
                        contentDescription = L10n.t("下一首"),
                        tint = scheme.onSurface,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(48.dp)) {
                    Icon(
                        modeIcon,
                        contentDescription = modeLabel,
                        tint = if (playMode == app.lumen.player.playback.PlayMode.LIST) scheme.onSurfaceVariant else scheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            if (showLyrics) {
                LyricsArea(
                    lines = lyrics,
                    loading = lyricsLoading,
                    positionProvider = positionMsProvider,
                    onSeekPlay = onSeekPlay,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 播放页下方的歌词区：按时间轴高亮、自动跟随、可手动滚动、上下渐隐；点按任意行跳转。 */
@Composable
private fun LyricsArea(
    lines: List<LyricLine>?,
    loading: Boolean,
    positionProvider: () -> Long,
    onSeekPlay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 有没有时间轴只需要在歌词变化时算一次（原来每次重组都 any{} 一遍）
    val hasTimeline = remember(lines) { lines?.any { it.startMs >= 0L } == true }
    // 当前行是「位置」的函数，但位置每 250ms 变、行号几秒才变一次。
    // 用 derivedStateOf 把两者解耦：只有行号真的变了才重组列表。
    val highlightState = remember(lines, hasTimeline) {
        derivedStateOf {
            val list = lines
            if (list.isNullOrEmpty() || !hasTimeline) -1 else lyricIndexAt(list, positionProvider())
        }
    }
    val currentIndex by highlightState

    var followPausedUntil by remember { mutableStateOf(0L) }
    var programmaticScroll by remember { mutableStateOf(false) }
    // 不要拿 isScrollInProgress 当 effect key（那是组合期读状态）：在协程里 snapshotFlow
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling && !programmaticScroll) {
                    followPausedUntil = System.currentTimeMillis() + 3_000
                }
            }
    }

    BoxWithConstraints(modifier) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LumenLoading()
            }
            lines.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        YoinSymbols.Lyrics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(LumenSizes.xs))
                    Text(
                        L10n.t("这首歌没有歌词"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                val lyricLines = lines
                val vPad = (maxHeight / 2) - 14.dp
                val highlight = currentIndex
                LaunchedEffect(highlight) {
                    if (highlight < 0 || System.currentTimeMillis() < followPausedUntil) return@LaunchedEffect
                    programmaticScroll = true
                    try {
                        listState.animateScrollToItem(highlight)
                    } finally {
                        // 动画被下一次切行取消时也要复位：否则「手动滚动后暂停跟随」会永久失效
                        programmaticScroll = false
                    }
                }
                LazyColumn(
                    state = listState,
                    userScrollEnabled = true,
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
                        },
                    contentPadding = PaddingValues(top = vPad, bottom = vPad, start = LumenSizes.l, end = LumenSizes.l),
                ) {
                    itemsIndexed(lyricLines, contentType = { _, _ -> "lyric" }) { i, line ->
                        val d = kotlin.math.abs(i - highlight).toFloat()
                        val smooth = kotlin.math.exp(-d / 2.0f)
                        val alpha = 0.16f + 0.84f * smooth
                        val isCurrent = i == highlight
                        Text(
                            line.text,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            style = if (isCurrent) MaterialTheme.typography.titleMediumEmphasized
                            else MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { this.alpha = alpha }
                                .padding(vertical = LumenSizes.xs)
                                .clickable { onSeekPlay(line.startMs) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 进度条 + 时间标签。
 *
 * 单独成组件的原因：它依赖每 250ms 变一次的播放位置，而封面、曲名、控制行都不依赖。
 * 把读取限制在这个小组件里，播放期间每秒最多只重组这一小块，而不是整页。
 */
@Composable
private fun SeekSection(
    positionProvider: () -> Long,
    durationMs: Long,
    known: Boolean,
    onSeek: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val positionMs = positionProvider()
    val progress = if (known) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val shown = scrubbing ?: progress
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown,
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                val f = scrubbing
                if (f != null && known) onSeek((f * durationMs).toLong())
                scrubbing = null
            },
            enabled = known,
            modifier = Modifier.fillMaxWidth().padding(horizontal = LumenSizes.l),
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.surfaceContainerHighest,
            ),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = LumenSizes.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                fmt(if (known) (shown * durationMs).toLong() else positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (known) "-" + fmt(durationMs - positionMs) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** 二分查当前行：歌词按 startMs 升序，用 O(log N) 取代原来的 O(N) 线性扫描。 */
private fun lyricIndexAt(lines: List<LyricLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.lastIndex
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].startMs <= positionMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found.coerceAtLeast(0)
}
