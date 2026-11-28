package app.lumen.player.ui

import app.lumen.player.util.L10n
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
        L10n.t("应用与界面"),
        listOf(
            OssItem("Kotlin", "2.4.21", L10n.t("开发语言与协程"), "Apache-2.0", "© JetBrains s.r.o. and contributors"),
            OssItem("Jetpack Compose", "BOM 2026.11.01", L10n.t("声明式 UI 框架"), "Apache-2.0", "© 2019-2024 The Android Open Source Project"),
            OssItem("Material 3", "Compose BOM 2026.11.01", L10n.t("组件、动效与设计规范"), "Apache-2.0", "© The Android Open Source Project"),
            OssItem("AndroidX Core / Activity / Lifecycle", "1.19.1 / 1.13.0 / 2.11.1", L10n.t("系统能力与生命周期"), "Apache-2.0", "© The Android Open Source Project"),
            OssItem("Yoin Symbols", "0.2.0", L10n.t("界面图标"), "MIT", "© p2o51"),
            OssItem("Material Color Utilities", L10n.t("2026 源码快照"), L10n.t("封面取色与配色方案（vendored）"), "Apache-2.0", "© 2021-2026 Google LLC"),
            OssItem("Google Sans Flex", L10n.t("内嵌字体"), L10n.t("界面字体"), "OFL-1.1", "© 2015 The Google Sans Flex Authors"),
        ),
    ),
    OssGroup(
        L10n.t("媒体与图片"),
        listOf(
            OssItem("AndroidX Media3 / ExoPlayer", "1.11.1", L10n.t("音频播放、队列与转码兜底"), "Apache-2.0", "© The Android Open Source Project"),
            OssItem("Coil", "3.6.3", L10n.t("封面与图片加载缓存"), "Apache-2.0", "© Coil Contributors"),
        ),
    ),
    OssGroup(
        L10n.t("网络与数据"),
        listOf(
            OssItem("Retrofit", "3.0.0", L10n.t("Subsonic HTTP 接口调用"), "Apache-2.0", "© Square, Inc."),
            OssItem("OkHttp", "5.5.1", L10n.t("网络传输与连接池"), "Apache-2.0", "© Square, Inc."),
            OssItem("Gson", "2.15.0", L10n.t("JSON 解析"), "Apache-2.0", "© Google LLC"),
            OssItem("kotlinx.serialization", "1.13.0", L10n.t("设置与播放状态的序列化"), "Apache-2.0", "© JetBrains s.r.o."),
            OssItem(L10n.t("SQLite（Android 内置）"), L10n.t("系统版本"), L10n.t("统计缓存"), "Public Domain", "© SQLite Authors"),
        ),
    ),
    OssGroup(
        L10n.t("服务端与协议"),
        listOf(
            OssItem("Navidrome", L10n.t("自建服务"), L10n.t("自托管音乐服务器（仅协议对接）"), "GPL-3.0", "© Navidrome Contributors"),
            OssItem("Subsonic API", L10n.t("公开规范"), L10n.t("客户端与服务器通信协议"), L10n.t("公开规范"), "—"),
            OssItem("OpenSubsonic", L10n.t("公开规范"), L10n.t("扩展字段与歌词接口"), L10n.t("公开规范"), "—"),
            OssItem("Navidrome Stat", L10n.t("自建服务"), L10n.t("播放统计服务（仅 API 对接）"), "MIT", "© StepaniaH"),
        ),
    ),
)

private data class LicenseDoc(val title: String, val file: String, val note: String)

private val LICENSE_DOCS = listOf(
    LicenseDoc("Apache License 2.0", "Apache-2.0.txt", L10n.t("Kotlin、Compose、Media3、OkHttp、Retrofit、Gson、Coil、Material Color Utilities 等")),
    LicenseDoc("MIT License", "MIT.txt", L10n.t("Yoin Symbols、Navidrome Stat，以及 Lumen 自身")),
    LicenseDoc("SIL Open Font License 1.1", "OFL-1.1.txt", L10n.t("Google Sans Flex 字体")),
)

/** 开源信息页：技术栈、许可与完整许可文本。 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    // 页面内返回按钮 / 系统返回统一走同一入口
    androidx.activity.compose.BackHandler { onBack() }
    val haptics = rememberLumenHaptics()
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        haptics.tap()
                        onBack()
                    },
                ) {
                    Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(L10n.t("关于"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text(L10n.t("开源信息"), color = MaterialTheme.colorScheme.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Text(
                L10n.t("Lumen 以 MIT 许可开源，并站在这些项目之上"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            SectionTitle(L10n.t("本项目许可"))
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
                        L10n.t("可自由使用、修改与再分发，需保留版权与许可声明。仓库根目录 LICENSE 为正式文本。"),
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
            SectionTitle(L10n.t("许可全文"))
            Text(
                L10n.t("点击展开完整文本；与仓库 android/app/src/main/assets/licenses/ 下的文件一致。"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }

        LICENSE_DOCS.forEach { doc ->
            item { LicenseDocCard(doc) }
        }

        item {
            SectionTitle(L10n.t("特别说明"))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LicenseNote(
                        L10n.tf("许可范围", "Scope"),
                        L10n.tf(
                            "Lumen 客户端以 MIT 许可发布；随 APK 分发的第三方组件均保留其原始许可与版权声明。上表所列许可文本与仓库中的 assets/licenses/ 保持一致。",
                            "The Lumen client is released under MIT. Every third-party component shipped in the APK retains its original license and copyright notice. The texts listed above match assets/licenses/ in the repository.",
                        ),
                    )
                    LicenseNote(
                        L10n.tf("服务端与协议", "Server and protocol"),
                        L10n.tf(
                            "Navidrome、Subsonic / OpenSubsonic 与 Navidrome Stat 通过公开 HTTP 接口对接，其服务端代码不随本应用分发；Navidrome 的 GPL-3.0 与 Navidrome Stat 的 MIT 义务不涉及本客户端。",
                            "Navidrome, Subsonic / OpenSubsonic and Navidrome Stat are integrated through public HTTP APIs. Their server code is not distributed with this app, so their GPL-3.0 and MIT obligations do not extend to the client.",
                        ),
                    )
                    LicenseNote(
                        L10n.tf("本地数据", "Local data"),
                        L10n.tf(
                            "服务器凭据使用 Android Keystore 加密保存；播放历史仅存储在本机，应用不包含统计或分析 SDK，也不会上传曲库与使用数据。",
                            "Server credentials are encrypted with the Android Keystore. Play history stays on device. The app contains no analytics SDK and never uploads library or usage data.",
                        ),
                    )
                    LicenseNote(
                        L10n.tf("文本效力", "Authoritative texts"),
                        L10n.tf(
                            "应用内许可文本依据各上游项目公开发布的材料整理；如与项目仓库存在差异，以对应仓库的 LICENSE / NOTICE 文件为准。",
                            "In-app license texts are derived from each upstream project's published materials. If a discrepancy exists, the LICENSE / NOTICE files in the corresponding repository prevail.",
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseNote(title: String, body: String) {
    Column {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun LicenseDocCard(doc: LicenseDoc) {
    val haptics = rememberLumenHaptics()
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val text by produceState("", doc.file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ctx.assets.open("licenses/${doc.file}").bufferedReader().use { it.readText() }
            }.getOrDefault(L10n.tf("许可文本缺失，请查看仓库中的 assets/licenses/${doc.file}", "License text missing; see assets/licenses/${doc.file} in the repo"))
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
                        .clickable {
                            haptics.segment()
                            open = !open
                        }
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
                        if (open) L10n.t("收起") else L10n.t("展开"),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                    )
                }
                // 展开/收起与设置页同规格：高度走 spatial spring，透明度走 effects
                val expandSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
                val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                AnimatedVisibility(
                    visible = open,
                    enter = expandVertically(expandSpec) + fadeIn(fadeSpec),
                    exit = shrinkVertically(expandSpec) + fadeOut(fadeOutSpec),
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
