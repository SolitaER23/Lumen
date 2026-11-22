package app.lumen.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.util.L10n
import com.gpo.yoin.symbols.YoinSymbols

private data class PrivacyBlock(
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
)

@Composable
fun PrivacyScreen(onBack: () -> Unit, onReachedBottom: () -> Unit = {}) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    var reachedBottom by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            info.totalItemsCount > 0 &&
                last != null &&
                last.index == info.totalItemsCount - 1 &&
                last.offset + last.size >= info.viewportEndOffset - info.afterContentPadding - 8 &&
                listState.canScrollBackward
        }.distinctUntilChanged().collect { atBottom ->
            if (atBottom && !reachedBottom) {
                reachedBottom = true
                onReachedBottom()
            }
        }
    }
    val blocks = listOf(
        PrivacyBlock(
            title = "1. 适用范围与承诺",
            paragraphs = listOf(
                "本隐私条款适用于 Lumen Android 客户端，由 Lumen 个人开发者维护。Lumen 是一款连接用户自建 Navidrome / OpenSubsonic 服务的本地音乐播放器，不运营云端曲库，也不以广告或用户画像为目的处理数据。",
                "我们遵循最小必要原则：仅处理完成播放、同步、离线与端侧分析所必需的信息，并尽可能将数据保留在用户设备或用户自己的服务器上。",
            ),
        ),
        PrivacyBlock(
            title = "2. 我们处理的信息",
            bullets = listOf(
                "连接配置：服务器地址、用户名、密码或访问令牌。密码与统计令牌使用 Android Keystore 的 AES-GCM 加密后保存。",
                "曲库缓存：专辑、艺人、曲目元数据与封面地址，用于加速首页、搜索和离线播放。",
                "播放数据：最近播放、播放队列与进度、评分、收藏状态；用于恢复播放、最近列表与个性化展示。",
                "端侧分析结果：YAMNet 音频标签、段落标签、521 维特征、情绪坐标、节拍与能量包络；仅保存在本机数据库，用于动态背景、配色、氛围电台与统计。",
                "可选统计服务：仅在用户主动填写时保存 Navidrome Stat 地址与令牌，用于展示自建统计。",
                "本地文件：离线下载的音频、图片与缓存文件，保存在应用私有目录。",
            ),
        ),
        PrivacyBlock(
            title = "3. 未收集的信息",
            bullets = listOf(
                "不收集通讯录、短信、精确位置、IMEI、MAC 地址、广告标识符或剪贴板内容。",
                "不包含第三方广告、用户画像、行为分析或崩溃上报 SDK。",
                "不上传音频文件、曲库列表、播放历史或端侧分析结果。",
            ),
        ),
        PrivacyBlock(
            title = "4. 使用目的",
            paragraphs = listOf(
                "上述信息仅用于：连接并同步用户指定的音乐服务器；播放、缓存与离线下载；恢复上次播放；展示最近播放、评分、收藏与统计；在设备端生成情绪、节拍、声音类型与氛围电台；以及在用户主动反馈时通过外部链接联系开发者。",
                "我们不会将信息用于广告投放、出售、出租或与数据经纪商交换。",
            ),
        ),
        PrivacyBlock(
            title = "5. 存储与保护",
            bullets = listOf(
                "账号凭据与可选统计令牌使用 Android Keystore 加密；本地数据库与偏好设置位于应用私有目录，其他应用无法直接读取。",
                "默认不启用云备份；卸载应用或执行「清除本机数据」会删除本机数据。",
                "与自建服务器的连接由用户配置。若使用 HTTP，数据可能在局域网内以明文传输；建议在可信网络使用，或为服务器配置 HTTPS。",
                "端侧音频分析完全在设备上执行，音频不会离开设备；分析结果用于显示与推荐，不改变音频文件。",
            ),
        ),
        PrivacyBlock(
            title = "6. 共享与第三方",
            bullets = listOf(
                "我们不出售、不出租、不向第三方共享用户数据。",
                "用户主动配置的 Navidrome / OpenSubsonic 服务器由用户自行运营，客户端仅按其协议发起请求；服务器端的数据处理适用该服务器的隐私政策。",
                "Navidrome Stat 为可选的第三方统计服务，仅在用户主动配置后访问。",
                "「问题反馈」与隐私条款中的 B 站主页为外部链接；点击后由浏览器或 B 站客户端打开，适用对应平台的隐私政策。",
                "LiteRT、Coil 等组件在本地运行，不向开发者回传数据。",
            ),
        ),
        PrivacyBlock(
            title = "7. 用户权利",
            bullets = listOf(
                "访问与更正：可在设置中查看账号、服务器、下载、分析与缓存信息，并修改连接配置。",
                "删除：可在设置 → 账号 →「清除本机数据」中删除账号、播放历史、评分、心情数据、统计配置、离线下载与缓存；也可直接卸载应用。",
                "撤回同意：停止使用可选统计服务、删除对应配置即可；端侧分析可在心情分析页清空并暂停。",
                "数据导出：本机数据库与缓存位于应用私有目录，应用不启用云备份；卸载或清除数据前，请自行记录必要信息，或通过下面的联系方式咨询迁移说明。",
            ),
        ),
        PrivacyBlock(
            title = "8. 未成年人",
            paragraphs = listOf(
                "Lumen 面向具备自建音乐服务能力的用户，不面向未成年人单独设计。未成年人应在监护人指导下使用，并由监护人负责服务器账号与数据管理。",
            ),
        ),
        PrivacyBlock(
            title = "9. 条款更新",
            paragraphs = listOf(
                "我们可能因功能变化或法律要求更新本条款。更新后的条款随应用版本发布，并在「设置 → 关于 → 长按版本号」的更新日志中说明。继续使用即表示接受更新后的条款；如不同意，请停止使用并清除本机数据。",
                "生效日期：2026 年 11 月 22 日。",
            ),
        ),
        PrivacyBlock(
            title = "10. 联系方式",
            paragraphs = listOf(
                "如对本条款、数据处理或删除请求有疑问，可通过 B 站联系开发者。",
            ),
        ),
    )

    BackHandler { onBack() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text(
                        L10n.tf("隐私条款", "Privacy policy"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                    )
                    Text(
                        L10n.tf("数据与隐私", "Data and privacy"),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                L10n.tf("隐私条款", "Privacy policy"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "Lumen · 2026-11-22",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                L10n.tf("在 B 站查看官方在线版本 ↗", "View official online version on Bilibili ↗"),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { ExternalLinks.open(ctx, ExternalLinks.PRIVACY_URL) },
            )
        }

        blocks.forEachIndexed { index, block ->
            item {
                Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                    Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "%02d".format(index + 1),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            block.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            block.paragraphs.forEach { text ->
                                Text(
                                    text,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 19.sp,
                                )
                            }
                            block.bullets.forEach { text ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text("•", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                            }
                            if (block.title.startsWith("10.")) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    modifier = Modifier.clickable {
                                        ExternalLinks.open(ctx, ExternalLinks.BILIBILI_URL)
                                    },
                                ) {
                                    Text(
                                        ExternalLinks.FEEDBACK_NAME,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    )
                                }
                                Text(
                                    ExternalLinks.BILIBILI_URL,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                if (reachedBottom) {
                    L10n.tf("已阅读至条款底部，请返回并继续下一步", "You have read to the bottom. Go back to continue.")
                } else {
                    L10n.tf("继续向下滚动，阅读至最底部", "Scroll to the very bottom to finish reading")
                },
                color = if (reachedBottom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            )
        }
    }
}
