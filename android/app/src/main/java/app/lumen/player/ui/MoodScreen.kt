package app.lumen.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.MoodStore
import app.lumen.player.playback.MoodWorker
import app.lumen.player.util.L10n
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 心情分析页：进度、控制、分布与说明。 */
@Composable
fun MoodScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var total by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var analyzed by remember { mutableIntStateOf(0) }
    var dist by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var timeline by remember { mutableStateOf<List<app.lumen.player.data.MoodStore.MoodDay>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val (_, t, r) = MoodWorker.progress(ctx)
            total = t
            running = r
            val (count, distMap, days) = withContext(Dispatchers.IO) {
                Triple(MoodStore.count(ctx), MoodStore.distribution(ctx), MoodStore.recentDays(ctx))
            }
            analyzed = count
            dist = distMap
            timeline = days
            delay(1200)
        }
    }

    val pending = (total - analyzed).coerceAtLeast(0)
    val ratio = when {
        total > 0 -> (analyzed.toFloat() / total).coerceIn(0f, 1f)
        analyzed > 0 -> 1f
        else -> 0f
    }
    val complete = total > 0 && analyzed >= total

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        L10n.t("心情分析"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(
                            "YAMNet · DSP",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Text(
                    L10n.t("心情分析"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    L10n.t("在本机提取节奏与情绪，不上传文件"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                HeroCard(
                    ratio = ratio,
                    analyzed = analyzed,
                    total = total,
                    pending = pending,
                    running = running,
                    complete = complete,
                    onStart = { MoodWorker.start(ctx) },
                    onPause = { MoodWorker.cancel(ctx) },
                    onClear = { confirmClear = true },
                )
            }

            item {
                SectionCard(L10n.t("它会影响什么")) {
                    EffectRow(L10n.t("动态背景"), L10n.t("随节拍与能量轻微呼吸，不打断阅读"), MaterialTheme.colorScheme.primary)
                    EffectRow(L10n.t("界面配色"), L10n.t("情绪决定色温，封面决定主色"), MaterialTheme.colorScheme.tertiary)
                    EffectRow(L10n.t("本机分析"), L10n.t("不修改音频、不上传文件，全部在本机完成"), MaterialTheme.colorScheme.secondary)
                }
            }

            if (timeline.any { it.count > 0 }) {
                item {
                    SectionCard(L10n.tf("近 7 天", "Last 7 days")) {
                        MoodTimeline(timeline)
                    }
                }
            }

            item {
                SectionCard(L10n.t("情绪分布")) {
                    if (dist.isEmpty()) {
                        EmptyDistribution()
                    } else {
                        val max = dist.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                        dist.entries.sortedByDescending { it.value }.forEach { (label, count) ->
                            DistributionRow(
                                label = label,
                                count = count,
                                fraction = count.toFloat() / max,
                                percent = if (analyzed > 0) count.toFloat() / analyzed else 0f,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    L10n.tf("分析在后台运行，可随时暂停；建议连接 Wi-Fi 以减少流量。结果保存在本机数据库。", "Analysis runs in the background and can be paused anytime. Wi-Fi is recommended to save data. Results are stored locally."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(L10n.t("清空心情数据？")) },
            text = { Text(L10n.t("已分析的情绪与节拍结果会被删除，需要重新分析才能恢复。不会影响下载与播放。")) },
            confirmButton = {
                TextButton(onClick = {
                    MoodStore.clear(ctx)
                    confirmClear = false
                }) { Text(L10n.t("清空"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(L10n.t("取消")) }
            },
        )
    }
}

@Composable
private fun HeroCard(
    ratio: Float,
    analyzed: Int,
    total: Int,
    pending: Int,
    running: Boolean,
    complete: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoodRing(ratio, Modifier.size(132.dp))
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${(ratio * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 44.sp,
                    )
                    StatusChip(
                        running = running,
                        complete = complete,
                        analyzed = analyzed,
                        total = total,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        when {
                            total > 0 -> L10n.tf("已分析 $analyzed / $total 首", "Analyzed $analyzed / $total")
                            analyzed > 0 -> L10n.tf("已分析 $analyzed 首 · 曲库待扫描", "Analyzed $analyzed · library not scanned")
                            else -> L10n.t("等待首次扫描")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (running) {
                        Text(
                            L10n.tf("后台分析中，可退出此页", "Analyzing in background; you can leave"),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                    .padding(vertical = 10.dp),
            ) {
                StatCell(L10n.tf("已分析", "Analyzed"), "$analyzed", Modifier.weight(1f))
                StatCell(L10n.tf("待分析", "Pending"), "$pending", Modifier.weight(1f))
                StatCell(
                    L10n.tf("覆盖率", "Coverage"),
                    "${(ratio * 100).toInt()}%",
                    Modifier.weight(1f),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { if (running) onPause() else onStart() },
                    enabled = !complete || running,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                        contentColor = if (running) MaterialTheme.colorScheme.onTertiary
                        else MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        when {
                            running -> L10n.tf("暂停分析", "Pause")
                            complete -> L10n.tf("分析已完成", "Complete")
                            analyzed > 0 -> L10n.tf("继续分析", "Resume")
                            else -> L10n.t("开始分析")
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = analyzed > 0,
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                ) {
                    Text(L10n.t("清空"), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun MoodRing(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(650), label = "mood-progress")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val start = MaterialTheme.colorScheme.primary
    val end = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val stroke = 12.dp.toPx()
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        if (animated > 0f) {
            drawArc(
                brush = Brush.sweepGradient(listOf(start, end, start)),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun StatusChip(
    running: Boolean,
    complete: Boolean,
    analyzed: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val text = when {
        running -> L10n.t("进行中")
        complete -> L10n.tf("已完成", "Complete")
        analyzed > 0 && total == 0 -> L10n.tf("已保存", "Saved")
        analyzed > 0 -> L10n.t("已暂停")
        else -> L10n.t("未开始")
    }
    val color = when {
        running -> MaterialTheme.colorScheme.primary
        complete -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            val transition = rememberInfiniteTransition(label = "mood-dot")
            val alpha by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "dot-alpha",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MoodTimeline(days: List<app.lumen.player.data.MoodStore.MoodDay>) {
    val max = days.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    Row(
        Modifier
            .fillMaxWidth()
            .height(136.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val ratio = day.count.toFloat() / max
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(if (day.count > 0) (6f + 62f * ratio).dp else 0.dp)
                            .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp))
                            .background(
                                if (day.count > 0) labelColor(day.label ?: "")
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                    )
                }
                Text(
                    day.date,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DistributionRow(label: String, count: Int, fraction: Float, percent: Float) {
    val width by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "bar-$label")
    Column(Modifier.padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(labelColor(label)))
            Text(
                labelCn(label),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 9.dp).weight(1f),
            )
            Text(
                "$count · ${(percent * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .height(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(width)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(labelColor(label)),
            )
        }
    }
}

@Composable
private fun EmptyDistribution() {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text("◌", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp)
        }
        Text(
            L10n.tf("还没有情绪分布", "No mood data yet"),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            L10n.tf("开始分析后，这里会显示每种情绪的比例", "Start analyzing to see the mood breakdown"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun EffectRow(title: String, body: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
        }
    }
}

private fun labelCn(label: String): String = when (label) {
    "Happy" -> L10n.t("愉悦")
    "Tender" -> L10n.t("温柔")
    "Calm" -> L10n.t("平静")
    "Angry" -> L10n.t("激烈")
    "Tense" -> L10n.t("紧张")
    "Sad" -> L10n.t("伤感")
    else -> label
}

private fun labelColor(label: String): Color = when (label) {
    "Happy" -> Color(0xFFF2B45A)
    "Tender" -> Color(0xFFE39BA8)
    "Calm" -> Color(0xFF7FB6D9)
    "Angry" -> Color(0xFFD96A5A)
    "Tense" -> Color(0xFFC98A4B)
    "Sad" -> Color(0xFF7C86B8)
    else -> Color(0xFF9A9A9A)
}
