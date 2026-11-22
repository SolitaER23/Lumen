package app.lumen.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.util.L10n
import kotlinx.coroutines.launch

private data class GuidePage(
    val badge: String,
    val title: String,
    val body: String,
    val bullets: List<String>,
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        GuidePage(
            badge = "01",
            title = L10n.tf("连接你的曲库", "Connect your library"),
            body = L10n.tf(
                "Lumen 通过 Subsonic / OpenSubsonic 协议连接你自建的 Navidrome。音乐、账号和播放记录都留在你自己的服务器与设备上。",
                "Lumen connects to your own Navidrome over Subsonic / OpenSubsonic. Your music, account and history stay on your server and device.",
            ),
            bullets = listOf(
                L10n.tf("填写服务器地址", "Enter your server address"),
                L10n.tf("输入用户名与密码", "Enter username and password"),
                L10n.tf("连接并同步曲库", "Connect and sync the library"),
            ),
        ),
        GuidePage(
            badge = "02",
            title = L10n.tf("找到想听的内容", "Find something to play"),
            body = L10n.tf(
                "首页按专辑、艺人、最近和收藏分区。搜索可以快速定位专辑或艺人，点开即可播放。",
                "Home is grouped into albums, artists, recent and favorites. Search finds albums or artists quickly, and tapping plays right away.",
            ),
            bullets = listOf(
                L10n.tf("切换专辑 / 艺人 / 最近 / 收藏", "Switch albums / artists / recent / favorites"),
                L10n.tf("点击专辑播放整张或单曲", "Play a whole album or a single track"),
                L10n.tf("搜索专辑与艺人", "Search albums and artists"),
            ),
        ),
        GuidePage(
            badge = "03",
            title = L10n.tf("播放与控制", "Playback and controls"),
            body = L10n.tf(
                "播放页支持拖动波浪进度条、逐行歌词、评分、收藏与下载；通知栏和锁屏也能控制播放。",
                "The player supports dragging the wave progress bar, synced lyrics, ratings, favorites and downloads. Notifications and the lock screen can control playback too.",
            ),
            bullets = listOf(
                L10n.tf("点专辑或单曲开始播放", "Tap an album or track to start"),
                L10n.tf("拖动进度条跳转，点击歌词行跟唱", "Drag the progress bar, tap a lyric line to jump"),
                L10n.tf("收藏、评分或下载当前曲目", "Favorite, rate or download the current track"),
            ),
        ),
        GuidePage(
            badge = "04",
            title = L10n.tf("让界面跟着音乐", "Make it feel like yours"),
            body = L10n.tf(
                "封面决定主色，端侧心情分析决定色温与呼吸感。切歌淡入淡出、多账号与语言都可以在设置里调整。",
                "Cover art sets the primary color, and on-device mood analysis sets the temperature and breathing. Crossfade, multiple accounts and language live in Settings.",
            ),
            bullets = listOf(
                L10n.tf("封面取色与动态背景", "Cover palette and animated background"),
                L10n.tf("切歌淡入淡出可调", "Adjustable crossfade on track change"),
                L10n.tf("账号、语言与数据管理", "Accounts, language and data controls"),
            ),
        ),
    )

    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = page == pages.lastIndex

    fun goPrevious() {
        if (page > 0) scope.launch { pager.animateScrollToPage(page - 1) }
    }

    fun goNext() {
        if (last) onDone() else scope.launch { pager.animateScrollToPage(page + 1) }
    }

    BackHandler {
        if (page > 0) goPrevious() else onDone()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Lumen",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDone) {
                    Text(
                        L10n.tf("跳过", "Skip"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                GuidePageContent(pages[index])
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { i ->
                    val selected = i == page
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .height(8.dp)
                            .width(if (selected) 22.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                            ),
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (page > 0) {
                    OutlinedButton(
                        onClick = { goPrevious() },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                    ) {
                        Text(L10n.tf("上一步", "Back"), fontSize = 14.sp)
                    }
                }
                Button(
                    onClick = { goNext() },
                    modifier = Modifier.weight(if (page > 0) 1.4f else 1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                ) {
                    Text(
                        if (last) L10n.tf("立即体验", "Get started") else L10n.tf("下一步", "Next"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
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
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp, max = 210.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        ),
                    ),
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    page.badge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp,
                )
                Text(
                    "LUMEN",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Text(
            page.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            page.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 10.dp),
        )

        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            page.bullets.forEachIndexed { index, bullet ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        bullet,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
