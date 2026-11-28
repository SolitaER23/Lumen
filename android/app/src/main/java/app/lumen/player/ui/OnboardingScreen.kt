package app.lumen.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.util.L10n
import kotlinx.coroutines.launch

/**
 * 首次使用引导（简约版）。
 *
 * 设计取向：**去掉一切装饰**——没有异形 hero、没有每页插画、没有圆角卡片与错位叠层，
 * 只有「编号 / 标题 / 一句正文 / 两条要点」的排版，翻页用 Pager 原生滑动，
 * 底部一条细进度线 + 两个按钮。信息更短、层次更少，一屏能读完一页。
 *
 * 性能：不再有逐帧动画背景与 per-page 变换，也就不需要把 pager 状态读进
 * graphicsLayer 的延迟读取技巧——整页只在翻页时重组一次。
 */
private data class GuidePage(
    val tag: String,
    val title: String,
    val body: String,
    val bullets: List<String>,
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        GuidePage(
            tag = L10n.tf("连接", "CONNECT"),
            title = L10n.tf("把曲库接进来", "Connect your library"),
            body = L10n.tf(
                "通过 Subsonic / OpenSubsonic 连接你自建的 Navidrome。音乐、账号与播放记录都只留在你的服务器和设备上。",
                "Lumen talks to your own Navidrome over Subsonic / OpenSubsonic. Music, accounts and history stay on your server and device.",
            ),
            bullets = listOf(
                L10n.tf("填写服务器地址与账号", "Enter the server address and account"),
                L10n.tf("连接成功后自动同步曲库", "The library syncs after connecting"),
            ),
        ),
        GuidePage(
            tag = L10n.tf("浏览", "BROWSE"),
            title = L10n.tf("专辑、艺人、搜索", "Albums, artists, search"),
            body = L10n.tf(
                "首页分专辑与艺人两栏，排序方式会记住。搜索直达专辑、艺人或单曲。",
                "Two tabs on the home screen, with a remembered sort order. Search jumps straight to an album, artist or track.",
            ),
            bullets = listOf(
                L10n.tf("左右滑动或点顶栏切换", "Swipe or tap the top bar to switch"),
                L10n.tf("排序与滚动位置下次还原", "Sort order and scroll position are restored"),
            ),
        ),
        GuidePage(
            tag = L10n.tf("播放", "PLAY"),
            title = L10n.tf("进度、歌词、遥控", "Seek, lyrics, controls"),
            body = L10n.tf(
                "拖动进度条跳转，歌词逐行跟随、点按定位；切歌淡入淡出可调，通知栏与锁屏都能遥控。",
                "Drag to seek; lyrics follow line by line and can be tapped. Crossfade is adjustable, and notification / lock-screen controls are always there.",
            ),
            bullets = listOf(
                L10n.tf("点歌词跳到对应时间", "Tap a lyric line to jump"),
                L10n.tf("切歌淡入淡出在设置里调", "Crossfade lives in Settings"),
            ),
        ),
        GuidePage(
            tag = L10n.tf("隐私", "PRIVACY"),
            title = L10n.tf("数据留在本机", "Data stays on device"),
            body = L10n.tf(
                "账号密码用 Android Keystore 加密，播放历史只保存在本机；不包含任何埋点、广告或行为分析 SDK。若你自建了 navidrome-stat，可在「统计」里接入聆听数据。",
                "Credentials are encrypted with the Android Keystore and play history stays local. No analytics, ads or tracking SDKs. If you self-host navidrome-stat, connect it under Stats.",
            ),
            bullets = listOf(
                L10n.tf("凭据加密，历史仅本机", "Encrypted credentials, local history"),
                L10n.tf("设置里可清除全部本机数据", "Clear all local data in Settings"),
            ),
        ),
    )

    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val haptics = rememberLumenHaptics()
    val page = pager.currentPage

    fun goPrevious() {
        if (page > 0) scope.launch { pager.animateScrollToPage(page - 1) }
    }

    fun goNext() {
        if (page == pages.lastIndex) onDone()
        else scope.launch { pager.animateScrollToPage(page + 1) }
    }

    BackHandler {
        if (page > 0) goPrevious() else onDone()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // 顶栏：产品名 + 跳过（一行，不做标题区）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "LUMEN",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    letterSpacing = 3.2.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        haptics.tap()
                        onDone()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        L10n.tf("跳过", "Skip"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                GuidePageContent(page = pages[index])
            }

            // 底部：细进度线 + 两个按钮
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)
                    .navigationBarsPadding(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((page + 1) / pages.size.toFloat())
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (page > 0) {
                        TextButton(
                            onClick = {
                                haptics.segment()
                                goPrevious()
                            },
                        ) {
                            Text(
                                L10n.tf("上一步", "Back"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "0" + (page + 1) + " / 0" + pages.size,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.width(20.dp))
                    Button(
                        onClick = {
                            // 每一步前进都是"提交"：用 Confirm，最后一步同样
                            haptics.confirm()
                            goNext()
                        },
                        shape = ButtonDefaults.shape,
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp),
                    ) {
                        Text(
                            if (page == pages.lastIndex) L10n.tf("开始使用", "Get started")
                            else L10n.tf("下一步", "Next"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidePageContent(page: GuidePage) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            page.tag,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            page.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 36.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            page.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 23.sp,
        )
        Spacer(Modifier.height(28.dp))
        page.bullets.forEach { bullet ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text(
                    "—",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    bullet,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
