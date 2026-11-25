package app.lumen.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.lumen.player.data.MoodStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

/**
 * 开发用「分析自检」：长按首页设置按钮打开。
 * 直接读数据库，展示分析数据链路是否完整（行数、env、窗级字段分布）。
 */
@Composable
fun AnalysisSelfCheck(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var stats by remember { mutableStateOf<MoodStore.MoodStats?>(null) }
    var rows by remember { mutableStateOf<List<MoodStore.AnalyzedRow>>(emptyList()) }
    var titleMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        stats = MoodStore.stats(ctx)
        rows = MoodStore.analyzedRows(ctx)
        titleMap = runCatching {
            app.lumen.player.playback.MoodAnalysisRunner.cachedTracks(ctx)
                .associate { it.id to (it.title + " · " + it.artist) }
        }.getOrDefault(emptyMap())
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "分析自检",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                val s = stats
                if (s == null) {
                    Text("读取中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Metric("总行数", s.rows.toString(), Modifier.weight(1f))
                        Metric("有 env", s.withEnv.toString(), Modifier.weight(1f))
                        Metric("有窗边界", s.withStarts.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "窗数分布：" + if (s.startsDistribution.isEmpty()) "无"
                        else s.startsDistribution.entries.sortedBy { it.key }
                            .joinToString("  ") { it.key.toString() + " 窗 × " + it.value },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "模型：" + s.models.joinToString("  ") { it.first + " × " + it.second },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    val ok = s.rows > 0 && s.withEnv == s.rows && s.startsDistribution[5] == s.rows
                    Text(
                        if (ok) "✅ 数据链路完整（全部 5 窗）"
                        else "⚠️ 数据不完整：需要重新分析缺失曲目",
                        fontSize = 12.sp,
                        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "已分析曲目（" + rows.size + "）",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                rows.take(30).forEach { r ->
                    Text(
                        (titleMap[r.trackId] ?: r.trackId.take(10)) + "  " + r.windows + "窗  " +
                            (if (r.label.isNotEmpty()) r.label else "-") + "  " +
                            (if (r.hasEnv) "env " else "") +
                            (if (r.hasVec) "vec " else "") +
                            (if (r.hasBeats) "beat " else "") +
                            (if (r.hasTempo) "bpm " else "") +
                            (if (r.hasVa) "va" else ""),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = {
                            val n = MoodStore.clearNonFiveWindow(ctx)
                            onDismiss()
                            android.widget.Toast.makeText(
                                ctx,
                                "已清除 " + n + " 条旧数据，下次分析会重跑",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        },
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "清除旧数据",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    Surface(
                        onClick = {
                            val text = s?.let {
                                "rows=" + it.rows + " env=" + it.withEnv + " starts=" + it.withStarts +
                                    " dist=" + it.startsDistribution + " models=" + it.models
                            } ?: "no data"
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("lumen-mood", text))
                            android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text("复制诊断", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "点空白处关闭",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
