package app.lumen.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.playback.AlbumDownload
import app.lumen.player.playback.Downloads
import app.lumen.player.util.L10n
import com.gpo.yoin.symbols.YoinSymbols

/**
 * 离线下载管理：专辑可展开查看曲目，支持单独删除、整组删除与暂停/继续。
 */
@Composable
fun DownloadsScreen(
    summaries: List<AlbumDownload>,
    tracksOf: (String) -> List<Downloads.DownloadTrack>,
    onBack: () -> Unit,
    onDelete: (key: String) -> Unit,
    onDeleteTrack: (trackId: String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    // 页面内返回按钮 / 系统返回统一走同一入口
    androidx.activity.compose.BackHandler { onBack() }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    val totalTracks = summaries.sumOf { it.total }
    val totalBytes = summaries.sumOf { it.bytes }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    L10n.t("离线下载"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    L10n.tf(
                        "已下载 $totalTracks 首 · ${"%.1f".format(totalBytes / 1048576.0)} MB",
                        "Downloaded $totalTracks tracks · ${"%.1f".format(totalBytes / 1048576.0)} MB",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        if (summaries.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text(L10n.t("还没有离线内容"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        L10n.t("全部暂停"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onPauseAll),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        L10n.t("全部继续"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onResumeAll),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        L10n.t("全部删除"),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onClearAll),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            items(summaries, key = { it.key }) { s ->
                val open = s.key in expanded
                val finished = s.total > 0 && s.done == s.total
                Column(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(1f).clickable {
                                expanded = if (open) expanded - s.key else expanded + s.key
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    s.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (!s.isSingle) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (open) L10n.t("收起") else L10n.t("展开"),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            Text(
                                buildString {
                                    if (s.isSingle) append(L10n.t("单曲 · "))
                                    append(L10n.tf("${s.done}/${s.total} 首", "${s.done}/${s.total} tracks"))
                                    append(" · ")
                                    append("%.1f".format(s.bytes / 1048576.0))
                                    append(" MB")
                                    if (s.failed) append(L10n.t(" · 有失败"))
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            // 进度条：专辑下载时能看到整体推进
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                val p = if (s.total > 0) s.done.toFloat() / s.total else 0f
                                Box(
                                    Modifier
                                        .fillMaxWidth(p.coerceIn(0f, 1f))
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }

                        IconButton(onClick = { onDelete(s.key) }) {
                            Icon(
                                YoinSymbols.Delete,
                                contentDescription = L10n.t("删除"),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = open,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        val tracks = remember(s.key, open, s.done) {
                            if (open) tracksOf(s.key) else emptyList()
                        }
                        Column(Modifier.padding(start = 14.dp, end = 8.dp, bottom = 10.dp)) {
                            tracks.forEach { t ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            t.title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "%.1f".format(t.bytes / 1048576.0) + " MB" +
                                                if (t.failed) L10n.t(" · 失败") else if (t.done) L10n.t(" · 已完成") else "",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    IconButton(onClick = { onDeleteTrack(t.id) }) {
                                        Icon(
                                            YoinSymbols.Delete,
                                            contentDescription = L10n.t("删除单曲"),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            if (tracks.isEmpty()) {
                                Text(
                                    L10n.t("暂无曲目明细"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
