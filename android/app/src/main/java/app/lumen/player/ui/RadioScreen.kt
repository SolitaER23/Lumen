package app.lumen.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.RadioGroup
import app.lumen.player.data.RadioVibe
import app.lumen.player.data.Track
import app.lumen.player.util.L10n
import coil3.compose.AsyncImage
import com.gpo.yoin.symbols.YoinSymbols

/**
 * 氛围电台。
 *
 * 两条互斥的呈现路径：
 * - 预览（默认）：队列已构建但**没有**动播放器，主按钮是「用这个电台播放」；
 * - 正在播（[radioLive]）：显示当前曲目与播放 / 下一首。
 *
 * 进入本页不再劫持播放队列——这是 0.74 及以前最大的行为缺陷。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioScreen(
    vibe: RadioVibe,
    loading: Boolean,
    progress: Float,
    error: String?,
    analyzedCount: Int,
    seedTitle: String?,
    preview: List<Track>,
    current: Track?,
    coverUrl: String?,
    /** 正在播放栏的封面修饰（与播放页封面共用共享元素 key） */
    coverModifier: Modifier = Modifier,
    playing: Boolean,
    radioLive: Boolean,
    strength: Float,
    onStrength: (Float) -> Unit,
    autoPick: Boolean,
    onAutoPick: (Boolean) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onVibe: (RadioVibe) -> Unit,
    onRegenerate: () -> Unit,
    onReseed: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onContinueAnalysis: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            TopBar(vibe = vibe, radioLive = radioLive, onBack = onBack)
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp),
            ) {
                Text(
                    vibe.label(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 32.sp,
                    lineHeight = 37.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    vibe.description(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 2,
                )
            }
        }

        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp),
            ) {
                when {
                    radioLive && current != null -> NowPlaying(
                        track = current,
                        coverUrl = coverUrl,
                        coverModifier = coverModifier,
                        onClick = onOpenPlayer,
                    )
                    preview.isNotEmpty() -> UpNext(preview = preview, seedTitle = seedTitle)
                    else -> Placeholder(loading = loading)
                }
            }
        }

        item {
            MetaLine(
                seedTitle = seedTitle,
                count = if (preview.isNotEmpty()) preview.size else 0,
                analyzed = analyzedCount,
                onReseed = onReseed,
                reseedEnabled = !loading,
            )
        }

        item {
            ActionRow(
                loading = loading,
                radioLive = radioLive,
                playing = playing,
                canStart = preview.isNotEmpty(),
                onStart = onStart,
                onToggle = onToggle,
                onNext = onNext,
                onRegenerate = onRegenerate,
            )
        }

        if (loading) {
            item { LoadingBlock(progress) }
        }
        if (error != null) {
            item {
                ErrorBlock(
                    message = error,
                    analyzed = analyzedCount,
                    onRetry = onRetry,
                    onContinueAnalysis = onContinueAnalysis,
                )
            }
        }

        item { SectionLabel(L10n.tf("选择场景", "Choose a station")) }

        RadioGroup.entries.forEach { group ->
            item(key = "group-" + group.name) {
                GroupBlock(
                    group = group,
                    selected = vibe,
                    enabled = !loading,
                    onVibe = onVibe,
                )
            }
        }

        item {
            TuningBlock(
                strength = strength,
                onStrength = onStrength,
                autoPick = autoPick,
                onAutoPick = onAutoPick,
            )
        }
    }
}
@Composable
private fun TopBar(
    vibe: RadioVibe,
    radioLive: Boolean,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                YoinSymbols.Back,
                contentDescription = L10n.t("返回"),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column {
            Text(
                L10n.tf("氛围电台", "Ambient radio"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
            )
            Text(
                if (radioLive) L10n.tf("正在播放", "Now playing") else L10n.tf("预览", "Preview"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NowPlaying(
    track: Track,
    coverUrl: String?,
    coverModifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = coverModifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                track.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            YoinSymbols.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun UpNext(preview: List<Track>, seedTitle: String?) {
    val first = preview.first()
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (seedTitle != null) {
                L10n.tf("从《$seedTitle》开始", "Starting from “$seedTitle”")
            } else {
                L10n.tf("根据整个曲库挑选", "Picked from your library")
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            targetState = first.title,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            label = "radio-preview-title",
        ) { title ->
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            first.artist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (preview.size > 1) {
            Spacer(Modifier.height(10.dp))
            preview.drop(1).take(2).forEach { t ->
                Text(
                    "· " + t.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Placeholder(loading: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (loading) L10n.tf("正在准备电台", "Preparing station") else L10n.tf("还没有电台", "No station yet"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            L10n.tf("选一个场景，或先完成曲库分析", "Pick a scene, or finish the library analysis first"),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun MetaLine(
    seedTitle: String?,
    count: Int,
    analyzed: Int,
    onReseed: () -> Unit,
    reseedEnabled: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val parts = buildList {
            seedTitle?.let { add(L10n.tf("种子《$it》", "Seed “$it”")) }
            if (count > 0) add(L10n.tf("$count 首", "$count tracks"))
            // 有预览时不再追"已分析 N 首"：长种子标题会被它把整行挤成"已分析…"的半句话
            if (count == 0 && analyzed > 0) add(L10n.tf("已分析 $analyzed 首", "$analyzed analyzed"))
        }
        Text(
            parts.joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (seedTitle != null) {
            TextButton(onClick = onReseed, enabled = reseedEnabled) {
                Text(
                    L10n.tf("换种子", "Reseed"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
@Composable
private fun ActionRow(
    loading: Boolean,
    radioLive: Boolean,
    playing: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (radioLive) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) YoinSymbols.PauseFilled else YoinSymbols.PlayFilled,
                    contentDescription = if (playing) L10n.t("暂停") else L10n.t("播放"),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            OutlinedButton(
                onClick = onNext,
                shape = CircleShape,
                modifier = Modifier.height(46.dp),
            ) {
                Text(L10n.tf("下一首", "Next"), fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = onStart,
                enabled = canStart && !loading,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 20.dp),
                modifier = Modifier.height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(YoinSymbols.PlayFilled, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    L10n.tf("用这个电台播放", "Play this station"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRegenerate, enabled = !loading) {
            Icon(
                YoinSymbols.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                L10n.tf("换一批", "Shuffle"),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun LoadingBlock(progress: Float) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
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
                .height(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun ErrorBlock(
    message: String,
    analyzed: Int,
    onRetry: () -> Unit,
    onContinueAnalysis: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp),
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
            if (analyzed > 0) {
                Text(
                    L10n.tf("本机已分析 $analyzed 首", "$analyzed tracks analyzed on device"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onContinueAnalysis,
                    shape = CircleShape,
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text(L10n.tf("继续分析", "Continue analysis"), fontSize = 12.sp)
                }
                TextButton(onClick = onRetry, modifier = Modifier.height(40.dp)) {
                    Text(L10n.tf("重试", "Retry"), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 24.dp, top = 26.dp, bottom = 10.dp),
    )
}

@Composable
private fun GroupBlock(
    group: RadioGroup,
    selected: RadioVibe,
    enabled: Boolean,
    onVibe: (RadioVibe) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(
            group.label(),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            group.vibes().forEach { vibe ->
                StationChip(
                    label = vibe.label(),
                    selected = vibe == selected,
                    enabled = enabled,
                    onClick = { onVibe(vibe) },
                )
            }
        }
    }
}

@Composable
private fun StationChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            },
        ),
    ) {
        Text(
            label,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun TuningBlock(
    strength: Float,
    onStrength: (Float) -> Unit,
    autoPick: Boolean,
    onAutoPick: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 26.dp),
    ) {
        Text(
            L10n.tf("选曲倾向", "Selection bias"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                L10n.tf("更像我正在听的", "Closer to what's playing"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                L10n.tf("更贴合场景", "Closer to the scene"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        // 用 SliderState 重载：旧的 value/onValueChange 版本在当前 Material3 已标记 deprecated
        val sliderState = rememberSliderState(strength, 0, 0f..1f)
        Slider(
            state = sliderState,
            onValueChange = onStrength,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    L10n.tf("跟随当前歌曲", "Follow the current track"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                )
                Text(
                    L10n.tf("按正在播放的声音特征自动挑场景", "Picks the scene from the audio of what's playing"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Switch(checked = autoPick, onCheckedChange = onAutoPick)
        }
    }
}
