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

private data class TermBlock(val title: String, val body: List<String>)

@Composable
fun TermsScreen(onBack: () -> Unit, onReachedBottom: () -> Unit = {}) {
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
        TermBlock(
            "1. 协议范围与接受",
            listOf(
                "本用户协议（下称“本协议”）是你与 Lumen 开发者之间关于使用 Lumen Android 客户端的约定。",
                "下载、安装、启动或继续使用 Lumen，即表示你已阅读并同意本协议与《隐私条款》。如不同意，请停止使用并卸载应用。",
            ),
        ),
        TermBlock(
            "2. 服务说明",
            listOf(
                "Lumen 是一款连接用户自建 Navidrome / OpenSubsonic 服务的本地音乐客户端。应用不提供云端曲库、内容分发或账号托管服务。",
                "播放、离线下载、歌词、评分、端侧声音分析与氛围电台等功能均依赖你配置的服务器和本机数据。",
            ),
        ),
        TermBlock(
            "3. 账号与服务器",
            listOf(
                "你需要自行准备并维护 Navidrome / OpenSubsonic 服务器、网络与账号，并确保你对曲库内容拥有合法使用权。",
                "服务器地址、账号与密码由你提供；客户端使用 Android Keystore 加密保存密码，但不对服务器本身的安全、可用性或数据完整性作出保证。",
            ),
        ),
        TermBlock(
            "4. 使用规范",
            listOf(
                "不得使用 Lumen 侵犯版权、传播违法内容、绕过访问控制或干扰他人服务器。",
                "不得对应用进行恶意篡改、反向利用或用于违反适用法律的行为。",
            ),
        ),
        TermBlock(
            "5. 内容与知识产权",
            listOf(
                "Lumen 客户端代码以 MIT 许可发布；第三方组件、字体与模型按各自许可证提供，详见应用内「开源信息」。",
                "曲库、封面、歌词等内容的权利归原权利人所有；本应用不授予你任何内容授权。",
            ),
        ),
        TermBlock(
            "6. 可用性、变更与免责",
            listOf(
                "应用按“现状”提供，可能因设备、系统版本、服务器配置或网络环境出现中断、转码失败、分析缓慢等问题。",
                "开发者可能更新、调整或停止部分功能；重要变更会在应用内更新日志中说明。",
                "在适用法律允许的范围内，开发者不对因使用或无法使用本应用造成的间接损失承担责任。",
            ),
        ),
        TermBlock(
            "7. 责任限制",
            listOf(
                "因用户服务器、网络、第三方服务、设备故障或用户误操作导致的数据丢失、播放中断或账号问题，由用户自行承担。",
                "建议在卸载或更换设备前，自行记录必要的服务器与账号信息，并了解本机数据不会随卸载保留。",
            ),
        ),
        TermBlock(
            "8. 终止",
            listOf(
                "你可以随时停止使用并卸载应用。",
                "如你违反本协议或适用法律，开发者可停止提供支持或更新；已安装客户端的本地功能不受影响。",
            ),
        ),
        TermBlock(
            "9. 隐私",
            listOf(
                "账号、播放历史、评分与端侧分析数据的处理方式见《隐私条款》。使用本应用即表示你已阅读该条款。",
            ),
        ),
        TermBlock(
            "10. 条款更新与联系",
            listOf(
                "条款可能随功能或法律要求更新，更新版本随应用发布并在更新日志中说明；继续使用即表示接受更新后的条款。",
                "生效日期：2026 年 11 月 22 日。",
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
                        L10n.tf("用户协议", "Terms of Service"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                    )
                    Text(
                        L10n.tf("使用条款", "Terms"),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                L10n.tf("用户协议", "Terms of Service"),
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
                    .clickable { ExternalLinks.open(ctx, ExternalLinks.TERMS_URL) },
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
                            block.body.forEach { text ->
                                Text(
                                    text,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 19.sp,
                                )
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
                    L10n.tf("已阅读至协议底部，请返回并继续下一步", "You have read to the bottom. Go back to continue.")
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
