package com.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpo.yoin.symbols.YoinSymbols

private data class OssItem(val name: String, val use: String, val license: String)
private data class OssGroup(val title: String, val items: List<OssItem>)

private val OSS_GROUPS = listOf(
    OssGroup(
        "应用与界面",
        listOf(
            OssItem("Kotlin", "开发语言与协程", "Apache-2.0"),
            OssItem("Jetpack Compose", "声明式 UI 框架", "Apache-2.0"),
            OssItem("Material 3", "组件、动效与设计规范", "Apache-2.0"),
            OssItem("AndroidX Core / Activity / Lifecycle", "系统能力与生命周期", "Apache-2.0"),
            OssItem("WorkManager", "充电 + Wi-Fi 时的心跳分析任务", "Apache-2.0"),
            OssItem("Yoin Symbols", "界面图标", "MIT"),
            OssItem("Material Color Utilities", "封面取色与配色方案", "Apache-2.0"),
        ),
    ),
    OssGroup(
        "媒体与图片",
        listOf(
            OssItem("AndroidX Media3 / ExoPlayer", "音频播放、队列与转码兜底", "Apache-2.0"),
            OssItem("Coil", "封面与图片加载缓存", "Apache-2.0"),
        ),
    ),
    OssGroup(
        "网络与数据",
        listOf(
            OssItem("Retrofit", "Subsonic HTTP 接口调用", "Apache-2.0"),
            OssItem("OkHttp", "网络传输与连接池", "Apache-2.0"),
            OssItem("Gson", "JSON 解析", "Apache-2.0"),
            OssItem("kotlinx.serialization", "设置与播放状态的序列化", "Apache-2.0"),
            OssItem("SQLite（Android 内置）", "心情结果、评分与统计缓存", "Public Domain"),
        ),
    ),
    OssGroup(
        "端侧音乐理解",
        listOf(
            OssItem("TensorFlow Lite", "YAMNet 推理运行时", "Apache-2.0"),
            OssItem("YAMNet", "预训练音频事件分类模型", "Apache-2.0"),
            OssItem("ONNX Runtime", "CNN14 推理运行时", "MIT"),
            OssItem("CNN14 / PANNs", "预训练音乐标签模型（含权重）", "MIT"),
        ),
    ),
    OssGroup(
        "服务端与协议",
        listOf(
            OssItem("Navidrome", "自托管音乐服务器", "GPL-3.0"),
            OssItem("Subsonic API", "客户端与服务器通信协议", "公开规范"),
            OssItem("OpenSubsonic", "扩展字段与歌词接口", "公开规范"),
            OssItem("Navidrome Stat", "播放统计服务（通过 API 对接）", "MIT"),
        ),
    ),
)

/** 开源信息页：分组列出所用技术栈、用途与许可。 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(YoinSymbols.Back, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("关于", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text("开源信息", color = MaterialTheme.colorScheme.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Text(
                "Lumen 站在这些开源项目之上",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        OSS_GROUPS.forEach { group ->
            item {
                Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                    Text(
                        group.title,
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
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            group.items.forEachIndexed { index, item ->
                                if (index > 0) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .size(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    )
                                }
                                OssRow(item)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                Text(
                    "说明",
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Lumen 自身为个人项目，仓库暂未附带独立许可证文件。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        Text(
                            "以上名称与许可基于各项目公开信息整理，具体条款以对应项目仓库为准。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        Text(
                            "Navidrome、Subsonic、Navidrome Stat 仅作为服务端/协议被对接，代码未打包进本应用。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OssRow(item: OssItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(item.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                item.use,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(item.license, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}
