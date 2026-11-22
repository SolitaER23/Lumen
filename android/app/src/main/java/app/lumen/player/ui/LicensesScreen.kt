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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class OssItem(
    val name: String,
    val version: String,
    val use: String,
    val license: String,
    val copyright: String,
)

private data class OssGroup(val title: String, val items: List<OssItem>)

private val OSS_GROUPS = listOf(
    OssGroup(
        "应用与界面",
        listOf(
            OssItem("Kotlin", "2.3.20", "开发语言与协程", "Apache-2.0", "© JetBrains s.r.o. and contributors"),
            OssItem("Jetpack Compose", "BOM 2024.10.01", "声明式 UI 框架", "Apache-2.0", "© 2019-2024 The Android Open Source Project"),
            OssItem("Material 3", "Compose BOM 2024.10.01", "组件、动效与设计规范", "Apache-2.0", "© The Android Open Source Project"),
            OssItem("AndroidX Core / Activity / Lifecycle", "1.15.0 / 1.9.3 / 2.8.7", "系统能力与生命周期", "Apache-2.0", "© The Android Open Source Project"),
            OssItem("WorkManager", "2.9.1", "充电 + Wi-Fi 时的后台分析任务", "Apache-2.0", "© The Android Open Source Project"),
            OssItem("Yoin Symbols", "0.2.0", "界面图标", "MIT", "© p2o51"),
            OssItem("Material Color Utilities", "2025 源码快照", "封面取色与配色方案（vendored）", "Apache-2.0", "© 2021-2025 Google LLC"),
            OssItem("Google Sans Flex", "内嵌字体", "界面字体", "OFL-1.1", "© 2015 The Google Sans Flex Authors"),
        ),
    ),
    OssGroup(
        "媒体与图片",
        listOf(
            OssItem("AndroidX Media3 / ExoPlayer", "1.5.0", "音频播放、队列与转码兜底", "Apache-2.0", "© The Android Open Source Project"),
            OssItem("Coil", "2.7.0", "封面与图片加载缓存", "Apache-2.0", "© Coil Contributors"),
            OssItem("AndroidX Palette", "1.0.0", "封面主色提取", "Apache-2.0", "© The Android Open Source Project"),
        ),
    ),
    OssGroup(
        "网络与数据",
        listOf(
            OssItem("Retrofit", "2.11.0", "Subsonic HTTP 接口调用", "Apache-2.0", "© Square, Inc."),
            OssItem("OkHttp", "4.12.0", "网络传输与连接池", "Apache-2.0", "© Square, Inc."),
            OssItem("Gson", "2.11.0", "JSON 解析", "Apache-2.0", "© Google LLC"),
            OssItem("kotlinx.serialization", "1.9.0", "设置与播放状态的序列化", "Apache-2.0", "© JetBrains s.r.o."),
            OssItem("SQLite（Android 内置）", "系统版本", "心情结果、评分与统计缓存", "Public Domain", "© SQLite Authors"),
        ),
    ),
    OssGroup(
        "端侧音乐理解",
        listOf(
            OssItem("TensorFlow Lite", "2.16.1", "YAMNet 推理运行时", "Apache-2.0", "© The TensorFlow Authors"),
            OssItem("YAMNet", "TensorFlow Models", "预训练音频事件分类模型", "Apache-2.0", "© 2019 The TensorFlow Authors"),
            OssItem("ONNX Runtime", "1.20.0", "CNN14 推理运行时", "MIT", "© Microsoft Corporation"),
            OssItem("CNN14 / PANNs", "Zenodo 3987831", "预训练音乐标签模型（int8 量化版）", "CC BY 4.0", "© Qiuqiang Kong et al.，Lumen 量化修改"),
        ),
    ),
    OssGroup(
        "服务端与协议",
        listOf(
            OssItem("Navidrome", "自建服务", "自托管音乐服务器（仅协议对接）", "GPL-3.0", "© Navidrome Contributors"),
            OssItem("Subsonic API", "公开规范", "客户端与服务器通信协议", "公开规范", "—"),
            OssItem("OpenSubsonic", "公开规范", "扩展字段与歌词接口", "公开规范", "—"),
            OssItem("Navidrome Stat", "自建服务", "播放统计服务（仅 API 对接）", "MIT", "© StepaniaH"),
        ),
    ),
)

private data class LicenseDoc(val title: String, val file: String, val note: String)

private val LICENSE_DOCS = listOf(
    LicenseDoc("Apache License 2.0", "Apache-2.0.txt", "Kotlin、Compose、Media3、OkHttp、Retrofit、Gson、Coil、TFLite、YAMNet、MCU 等"),
    LicenseDoc("MIT License", "MIT.txt", "ONNX Runtime、Yoin Symbols、Navidrome Stat，以及 Lumen 自身"),
    LicenseDoc("SIL Open Font License 1.1", "OFL-1.1.txt", "Google Sans Flex 字体"),
    LicenseDoc("Creative Commons Attribution 4.0", "CC-BY-4.0.txt", "CNN14 / PANNs 预训练模型权重"),
)

/** 开源信息页：技术栈、许可与完整许可文本。 */
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
                "Lumen 以 MIT 许可开源，并站在这些项目之上",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            SectionTitle("本项目许可")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Lumen", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "MIT License · Copyright (c) 2026 Lumen contributors",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        LicenseChip("MIT")
                    }
                    Text(
                        "可自由使用、修改与再分发，需保留版权与许可声明。仓库根目录 LICENSE 为正式文本。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        OSS_GROUPS.forEach { group ->
            item {
                SectionTitle(group.title)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        group.items.forEachIndexed { index, item ->
                            if (index > 0) Divider()
                            OssRow(item)
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("许可全文")
            Text(
                "点击展开完整文本；与仓库 android/app/src/main/assets/licenses/ 下的文件一致。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }

        LICENSE_DOCS.forEach { doc ->
            item { LicenseDocCard(doc) }
        }

        item {
            SectionTitle("特别说明")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "CNN14 预训练模型来自 PANNs（Qiuqiang Kong 等），权重以 CC BY 4.0 发布；本应用分发的 cnn14_int8.onnx 为量化后的修改版本，已注明作者与修改事实。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    Text(
                        "YAMNet 模型来自 TensorFlow Models，Apache-2.0，未修改。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    Text(
                        "Navidrome、Subsonic、Navidrome Stat 仅作为服务端/协议被对接，代码未打包进本应用。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    Text(
                        "以上信息基于各项目公开材料整理，具体条款以对应项目仓库为准。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseDocCard(doc: LicenseDoc) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val text by produceState("", doc.file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ctx.assets.open("licenses/${doc.file}").bufferedReader().use { it.readText() }
            }.getOrDefault("许可文本缺失，请查看仓库中的 assets/licenses/${doc.file}")
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open = !open }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(doc.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            doc.note,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(
                        if (open) "收起" else "展开",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                    )
                }
                AnimatedVisibility(
                    visible = open,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Text(
                        text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            .height(1.dp),
    )
}

@Composable
private fun OssRow(item: OssItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "  ${item.version}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
            Text(
                item.use,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                item.copyright,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        LicenseChip(item.license)
    }
}

@Composable
private fun LicenseChip(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}
