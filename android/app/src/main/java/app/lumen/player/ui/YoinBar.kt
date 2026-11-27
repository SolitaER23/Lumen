package app.lumen.player.ui

import app.lumen.player.util.L10n
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.gpo.yoin.symbols.YoinSymbols

/**
 * 底部迷你播放条（M3 Expressive surface）：
 *
 * - 整条是 `Surface` 的「点击即直达播放页」，圆角用 `shapes.extraLarge`；
 * - 封面由调用方注入共享元素 modifier（迷你条不在页面作用域内，必须用自身的
 *   AnimatedVisibilityScope），这样它和播放页封面是同一个元素；
 * - 播放进度用官方 `LinearWavyProgressIndicator` 贴在底部，和播放页观感一致。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YoinBar(
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    coverUrl: String,
    playing: Boolean,
    enabled: Boolean,
    /** 封面修饰：由调用方注入共享元素 key（迷你条不在页面作用域内） */
    coverModifier: Modifier = Modifier,
    /** 是否显示进度条（低频状态：有没有歌、时长是否已知） */
    showProgress: Boolean = false,
    /**
     * 进度 0f–1f；null 表示未知/不可用。
     * 用 lambda 而不是 Float：它被 LinearProgressIndicator 在绘制阶段读取，
     * 调用方每 250ms 推进的 positionMs 不会让迷你条每秒重组 4 次。
     */
    progressProvider: () -> Float? = { null },
    onOpenPlayer: () -> Unit,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpenPlayer,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = LumenSizes.s).height(LumenSizes.miniBarHeight),
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxSize().padding(start = LumenSizes.xs, end = LumenSizes.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (enabled) {
                    Box(coverModifier.size(46.dp).clip(MaterialTheme.shapes.medium)) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(LumenSizes.xs))
                    // 动效规格必须在组合期取好，transitionSpec 不是 @Composable
                    val trackSpatial = MaterialTheme.motionScheme
                        .defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                    val trackFast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            // 切换曲目是同类内容替换：横向 spatial + 快速 effects，与全应用同一套节奏
                            (slideInHorizontally(trackSpatial) { it / 3 } + fadeIn(trackFast)) togetherWith
                                (slideOutHorizontally(trackSpatial) { -it / 3 } + fadeOut(trackFast))
                        },
                        label = "track",
                        modifier = Modifier.weight(1f),
                    ) { t ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                t,
                                style = MaterialTheme.typography.bodyMediumEmphasized,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    FilledIconButton(onClick = onToggle, modifier = Modifier.size(44.dp)) {
                        Icon(
                            if (playing) YoinSymbols.PauseFilled else YoinSymbols.PlayFilled,
                            contentDescription = if (playing) L10n.t("暂停") else L10n.t("播放"),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Icon(
                        YoinSymbols.MusicNote,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp).padding(start = LumenSizes.xs),
                    )
                    Spacer(Modifier.width(LumenSizes.s))
                    Column(Modifier.weight(1f)) {
                        Text(
                            L10n.tf("还没有播放", "Nothing playing"),
                            style = MaterialTheme.typography.bodyMediumEmphasized,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            L10n.tf("从专辑里挑一张开始", "Pick an album to start"),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (enabled && showProgress) {
                LinearProgressIndicator(
                    progress = { (progressProvider() ?: 0f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // 条本身是 28dp 的大圆角：左右让出圆角半径，进度线才不会从圆角处"露出去"
                        .padding(start = 36.dp, end = 36.dp, bottom = 7.dp)
                        .height(3.dp),
                    color = scheme.primary,
                    trackColor = scheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}
