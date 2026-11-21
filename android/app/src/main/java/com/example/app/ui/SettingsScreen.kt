package com.example.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.data.ServerConfig
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 设置页：连接信息、缓存占用与清理、关于。 */
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

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(YoinSymbols.Back, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("设置", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text("设置", color = MaterialTheme.colorScheme.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(20.dp))

        Section("连接")
        InfoRow("服务器", cfg.base)
        InfoRow("用户名", cfg.user)

        Spacer(Modifier.height(16.dp))
        Section("外观")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(0f to "标准", 0.5f to "中", 1f to "高").forEach { (value, label) ->
                val selected = kotlin.math.abs(contrast - value) < 0.01f
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
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
            "对比度越高，文字与容器越清晰（Material Color Utilities 动态取色）",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        Section("缓存")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable {
                    onClearCache()
                    freed = !freed
                }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (freed) "缓存已清理（下载不受影响）" else "清理缓存（${"%.1f".format(cacheBytes / 1048576.0)} MB，不含已下载）",
                color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(YoinSymbols.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        Section("离线下载")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onManageDownloads)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$downloadCount 首 · ${"%.1f".format(downloadBytes / 1048576.0)} MB",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text("管理", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        Section("账号")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onLogout)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Text("退出登录", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }

        Spacer(Modifier.height(24.dp))
        Section("关于")
        InfoRow("界面", "Jetpack Compose · Material 3")
        InfoRow("音频", "Media3 ExoPlayer")
        InfoRow("图标", "yoin-symbols")
        InfoRow("目标设备", "Pixel 10a · 120Hz")
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.width(72.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
    }
}
