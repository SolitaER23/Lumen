package com.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.data.ServerConfig
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** 设置页：连接、外观、心情分析、缓存、下载、账号与关于。 */
@Composable
fun SettingsScreen(
    cfg: ServerConfig,
    cacheDir: File,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    downloadCount: Int,
    downloadBytes: Long,
    onManageDownloads: () -> Unit,
    onClearCache: () -> Unit,
    contrast: Float = 0f,
    onContrast: (Float) -> Unit = {},
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var cacheBytes by remember { mutableStateOf(0L) }
    var freed by remember { mutableStateOf(false) }

    LaunchedEffect(freed) {
        cacheBytes = withContext(Dispatchers.IO) {
            runCatching {
                val total = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                (total - downloadBytes).coerceAtLeast(0L)
            }.getOrDefault(0L)
        }
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
                Text("设置", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text("设置", color = MaterialTheme.colorScheme.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Text(
                "账户、外观与数据",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            SettingSection("连接") {
                SettingRow("服务器", cfg.base)
                SettingRow("用户名", cfg.user)
            }
        }

        item {
            SettingSection("外观") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0f to "标准", 0.5f to "中", 1f to "高").forEach { (value, label) ->
                        val selected = kotlin.math.abs(contrast - value) < 0.01f
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .clickable { onContrast(value) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                Text(
                    "对比度越高，文字与容器越清晰（MCU 动态取色）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            SettingSection("心情分析") {
                var moodDone by remember { mutableStateOf(0) }
                var moodTotal by remember { mutableStateOf(0) }
                var moodRunning by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    while (true) {
                        val (d, t, r) = com.example.app.playback.MoodWorker.progress(ctx)
                        moodDone = d
                        moodTotal = t
                        moodRunning = r
                        delay(1500)
                    }
                }
                Text(
                    if (moodTotal == 0) "尚未开始（充电 + Wi-Fi 时后台分析整库）"
                    else "已分析 $moodDone / $moodTotal 首" + if (moodRunning) " · 进行中" else "",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PillButton("开始分析", primary = true) { com.example.app.playback.MoodWorker.start(ctx) }
                    PillButton("暂停") { com.example.app.playback.MoodWorker.cancel(ctx) }
                    PillButton("清空") { com.example.app.data.MoodStore.clear(ctx) }
                }
            }
        }

        item {
            SettingSection("缓存") {
                SettingRow(
                    "清理缓存",
                    "${"%.1f".format(cacheBytes / 1024f / 1024f)} MB · 不含已下载",
                ) {
                    IconButton(onClick = {
                        onClearCache()
                        freed = !freed
                    }) {
                        Icon(YoinSymbols.Delete, contentDescription = "清理缓存", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (freed) {
                    Text("缓存已清理（下载不受影响）", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }

        item {
            SettingSection("离线下载") {
                SettingRow("已下载", "$downloadCount 首 · ${"%.1f".format(downloadBytes / 1024f / 1024f)} MB") {
                    TextButton(onClick = onManageDownloads) {
                        Text("管理", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            SettingSection("账号") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onLogout)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text("退出登录", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            }
        }

        item {
            SettingSection("关于") {
                SettingRow("界面", "Jetpack Compose · Material 3")
                SettingRow("音频", "Media3 ExoPlayer")
                SettingRow("图标", "yoin-symbols")
                SettingRow("目标设备", "Pixel 10a · 120Hz")
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), content = content)
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

@Composable
private fun PillButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}
