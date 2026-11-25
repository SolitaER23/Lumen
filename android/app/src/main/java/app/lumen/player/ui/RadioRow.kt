package app.lumen.player.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.RadioVibe
import app.lumen.player.util.L10n
import com.gpo.yoin.symbols.YoinSymbols

/**
 * 首页电台行的状态。只摊平 MainActivity 已有的数据，不引入第二套数据源。
 */
data class RadioRailState(
    /** 已有分析结果，可以直接生成电台。 */
    val ready: Boolean = false,
    val analyzing: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
)

/** 首屏只放 5 个高频场景：三类语境各取样，其余交给「全部」。 */
internal val RailVibes = listOf(
    RadioVibe.FOCUS,
    RadioVibe.NIGHT,
    RadioVibe.COMMUTE,
    RadioVibe.RUNNING,
    RadioVibe.RELAX,
)

/**
 * 首页常驻电台行（MD3 Expressive：胶囊 chip + 中性容器）。
 *
 * 入口位置的理由：电台是"选什么听"，和专辑网格同属内容轴，不该挤进头部的工具按钮组。
 * 点击 chip = 以正在播放的歌为种子、直接开始这个场景；右侧「全部」进电台页微调。
 */
@Composable
fun RadioRow(
    state: RadioRailState,
    onOpenAll: () -> Unit,
    onPick: (RadioVibe) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 转场原点登记在这个 26dp 圆点：进电台页时从它长出来，而不是整页淡入
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(26.dp)
                    .onGloballyPositioned { c -> NavOriginStore.put("radio", c.boundsInRoot()) },
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        YoinSymbols.Sparkle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    L10n.tf("电台", "Radio"),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    subtitleOf(state),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onOpenAll)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    L10n.tf("全部 ${RadioVibe.entries.size} 台", "All ${RadioVibe.entries.size}"),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    YoinSymbols.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        if (state.ready) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(RailVibes) { vibe ->
                    RailChip(label = vibe.label()) { onPick(vibe) }
                }
            }
        } else {
            // 骨架：保持这一块的高度稳定，分析完成后 chip 原地出现，不跳版
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RailVibes.take(4).forEach { vibe ->
                    Box(
                        Modifier
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            vibe.label(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun subtitleOf(state: RadioRailState): String = when {
    state.ready && state.analyzing -> L10n.tf(
        "可直接开台 · 仍在分析 ${state.done}/${state.total}",
        "Ready · still analyzing ${state.done}/${state.total}",
    )
    state.ready -> L10n.tf("以正在播放的歌为种子", "Seeded by what you're playing")
    state.analyzing -> L10n.tf(
        "分析中 ${state.done}/${state.total}，完成后可开台",
        "Analyzing ${state.done}/${state.total} — ready when finished",
    )
    else -> L10n.tf("先在设置里完成分析即可开台", "Finish the analysis to unlock stations")
}

@Composable
private fun RailChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
