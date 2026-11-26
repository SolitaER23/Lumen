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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
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

/** 速度显示：不足 1 KB/s 时给字节数，避免出现 "0.0 MB/s"。 */
private fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1048576L -> "%.1f MB/s".format(java.util.Locale.ROOT, bytesPerSec / 1048576.0)
    bytesPerSec >= 1024L -> "%d KB/s".format(java.util.Locale.ROOT, bytesPerSec / 1024)
    else -> bytesPerSec.toString() + " B/s"
}

/** 剩余时间：一小时以内 mm:ss，超出 h:mm:ss。 */
private fun formatEta(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    return if (s >= 3600L) {
        "%d:%02d:%02d".format(java.util.Locale.ROOT, s / 3600, (s % 3600) / 60, s % 60)
    } else {
        "%d:%02d".format(java.util.Locale.ROOT, s / 60, s % 60)
    }
}

/**
 * 离线下载管理：专辑可展开查看曲目，支持单独删除、整组删除与暂停/继续。
 */
@Composable
fun DownloadsScreen(
    summaries: List<AlbumDownload>,
    tracksOf: (String) -> List<Downloads.DownloadTrack>,
    onBack: () -> Unit,
    onDelete: (key: String) -> Unit,
    onDeleteTrack: (requestId: String) -> Unit,
    onRetry: (key: String) -> Unit = {},
    onRetryTrack: (requestId: String) -> Unit = {},
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearAll: () -> Unit,
    onPauseGroup: (key: String) -> Unit = {},
    onResumeGroup: (key: String) -> Unit = {},
    /** 「仅 Wi-Fi 下载」开启且当前在计费网络：队列在等待网络，而非失败。 */
    waitingForNetwork: Boolean = false,
) {
    // 页面内返回按钮 / 系统返回统一走同一入口
    androidx.activity.compose.BackHandler { onBack() }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
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
                        "已下载 $totalTracks 首 · ${"%.1f".format(java.util.Locale.ROOT, totalBytes / 1048576.0)} MB",
                        "Downloaded $totalTracks tracks · ${"%.1f".format(java.util.Locale.ROOT, totalBytes / 1048576.0)} MB",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        if (waitingForNetwork) {
            item {
                Text(
                    L10n.t("等待网络：已按「仅 Wi-Fi 下载」暂停，连上 Wi-Fi 后自动继续"),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
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
                        modifier = Modifier.clickable { confirmClearAll = true },
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
                                    append("%.1f".format(java.util.Locale.ROOT, s.bytes / 1048576.0))
                                    if (s.bytesTotal > s.bytes) {
                                        append(" / ")
                                        append("%.1f".format(java.util.Locale.ROOT, s.bytesTotal / 1048576.0))
                                    }
                                    append(" MB")
                                    if (s.speedBytesPerSec > 0L) {
                                        append(" · ")
                                        append(formatSpeed(s.speedBytesPerSec))
                                        if (s.etaSeconds > 0L) {
                                            append(L10n.tf(" · 剩余 ", " · left "))
                                            append(formatEta(s.etaSeconds))
                                        }
                                    }
                                    when {
                                        s.failed -> append(L10n.t(" · 有失败"))
                                        s.paused -> append(L10n.t(" · 已暂停"))
                                    }
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            if (s.failure.isNotBlank()) {
                                Text(
                                    L10n.tf("失败原因：", "Failed: ") + s.failure,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            // 进度条：专辑下载时能看到整体推进
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                // 有总字节时按字节算（单曲下载也能看到真实推进），否则退回曲目数
                                val p = s.progress
                                Box(
                                    Modifier
                                        .fillMaxWidth(p.coerceIn(0f, 1f))
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }

                        if (!finished && !s.failed) {
                            Text(
                                if (s.paused) L10n.t("继续") else L10n.t("暂停"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable {
                                        if (s.paused) onResumeGroup(s.key) else onPauseGroup(s.key)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                        if (s.failed || s.paused) {
                            Text(
                                L10n.t("重试"),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { onRetry(s.key) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                        IconButton(onClick = { pendingDelete = s.key }) {
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
                        // 明细来自内存快照（无 IO），直接取即可；不再用 remember 缓存
                        val tracks = if (open) tracksOf(s.key) else emptyList()
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
                                            "%.1f".format(java.util.Locale.ROOT, t.bytes / 1048576.0) + " MB" + when {
                                                t.failed -> L10n.t(" · 失败")
                                                t.done -> L10n.t(" · 已完成")
                                                t.paused -> L10n.t(" · 已暂停")
                                                else -> ""
                                            },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                        )
                                        if (t.failure.isNotBlank()) {
                                            Text(
                                                t.failure,
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    if (t.failed || t.paused) {
                                        Text(
                                            L10n.t("重试"),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp,
                                            modifier = Modifier
                                                .clickable { onRetryTrack(t.id) }
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
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

    // 危险操作二次确认：删除整组与「全部删除」都不可撤销，之前是一点就删
    pendingDelete?.let { key ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(L10n.t("删除这组离线内容？")) },
            text = { Text(L10n.t("已下载的歌曲会从本机删除，需要时重新下载。")) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(key)
                }) { Text(L10n.t("删除")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(L10n.t("取消")) }
            },
        )
    }
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(L10n.t("删除全部离线内容？")) },
            text = { Text(L10n.t("已下载的歌曲会从本机删除，需要时重新下载。")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    onClearAll()
                }) { Text(L10n.t("删除")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text(L10n.t("取消")) }
            },
        )
    }
}
