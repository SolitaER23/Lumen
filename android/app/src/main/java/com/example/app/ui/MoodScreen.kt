package com.example.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.data.MoodStore
import com.example.app.playback.MoodWorker
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.delay

/** 心情分析页：进度、控制、分布与说明。 */
@Composable
fun MoodScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var done by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var analyzed by remember { mutableStateOf(0) }
    var dist by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val (d, t, r) = MoodWorker.progress(ctx)
            done = d
            total = t
            running = r
            analyzed = MoodStore.count(ctx)
            dist = MoodStore.distribution(ctx)
            delay(1500)
        }
    }

    val ratio = when {
        total > 0 -> (analyzed.toFloat() / total).coerceIn(0f, 1f)
        analyzed > 0 -> 1f
        else -> 0f
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(YoinSymbols.Back, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("心情分析", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text("心情分析", color = MaterialTheme.colorScheme.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Text(
                "在本机提取节奏与情绪，让背景和配色跟着音乐变化",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(ratio, Modifier.size(120.dp))
                        Spacer(Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${(ratio * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "已分析 $analyzed 首",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                            )
                            Text(
                                if (total > 0) "本库 $total 首" else "等待首次扫描",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            StatusPill(running = running, hasData = analyzed > 0, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoodPill("开始分析", primary = true) { MoodWorker.start(ctx) }
                        MoodPill("暂停") { MoodWorker.cancel(ctx) }
                        MoodPill("清空") { confirmClear = true }
                    }
                }
            }
        }

        item {
            SectionCard("它会影响什么") {
                InfoRow("动态背景", "随节拍与能量轻微呼吸，不打断阅读")
                InfoRow("界面配色", "情绪决定色温，封面决定主色")
                InfoRow("歌词与播放", "不修改音频、不上传文件，全部在本机完成")
            }
        }

        if (dist.isNotEmpty()) {
            item {
                SectionCard("情绪分布") {
                    dist.entries.forEach { (label, n) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(percent = 50)).background(labelColor(label)))
                            Text(
                                labelCn(label),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 10.dp).weight(1f),
                            )
                            Text("$n 首", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "后台分析需要充电并连接 Wi-Fi，可随时暂停；结果保存在本机数据库，清空后可重新分析。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 18.dp, start = 4.dp, end = 4.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空心情数据？") },
            text = { Text("已分析的情绪与节拍结果会被删除，需要重新分析才能恢复。不会影响下载与播放。") },
            confirmButton = {
                TextButton(onClick = {
                    MoodStore.clear(ctx)
                    confirmClear = false
                }) { Text("清空", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val stroke = 12.dp.toPx()
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        if (progress > 0f) {
            drawArc(
                color = fg,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        drawCircle(
            color = fg,
            radius = 4.dp.toPx(),
            center = Offset(size.width, size.height) / 2f + Offset(0f, 0f),
            alpha = 0f,
        )
    }
}

@Composable
private fun StatusPill(running: Boolean, hasData: Boolean, modifier: Modifier = Modifier) {
    val text = when {
        running -> "进行中"
        hasData -> "已暂停"
        else -> "未开始"
    }
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun MoodPill(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        Spacer(Modifier.size(12.dp))
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun labelCn(label: String): String = when (label) {
    "Happy" -> "愉悦"
    "Tender" -> "温柔"
    "Calm" -> "平静"
    "Angry" -> "激烈"
    "Tense" -> "紧张"
    "Sad" -> "伤感"
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
