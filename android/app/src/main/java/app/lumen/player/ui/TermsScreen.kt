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
    // 文案来自 assets/legal/terms.json（中英双语，按当前语言选取）。
    // 读 assets + 解析 JSON 是阻塞 I/O：放到 IO 线程，避免首次进入时在组合期卡主线程。
    // null = 加载中；空列表 = 真失败（两者必须区分，否则加载中会误报"协议缺失"）。
    val blocksState by androidx.compose.runtime.produceState<List<TermBlock>?>(
        initialValue = null,
        L10n.lang,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            app.lumen.player.data.LegalDocs.load(ctx, "terms").map {
                TermBlock(it.title, it.paragraphs + it.bullets)
            }
        }
    }
    val blocks = blocksState

    BackHandler { onBack() }
    val haptics = rememberLumenHaptics()

    LazyColumn(
        state = listState,
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
                // 版本日期直接取条款包的 version，避免页面与文本各写一份而漂移
                "Lumen · " + app.lumen.player.data.Legal.currentVersion(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                L10n.tf(
                    "官方在线版本尚未上线：目前无法上线，也不计划上线；以本页文本为准。",
                    "No online copy is hosted: it cannot be published now and none is planned. This screen is authoritative.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (blocks != null && blocks.isEmpty()) {
            // 条款资源缺失/损坏：必须显式告知，否则用户会卡在「读到底部才能同意」的死角
            item {
                Text(
                    L10n.tf(
                        "用户协议内容加载失败（assets/legal/terms.json 缺失或损坏）。请重新安装应用；此状态下无法完成同意。",
                        "Failed to load the Terms (assets/legal/terms.json missing or corrupted). Reinstall the app; consent cannot be completed.",
                    ),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )
            }
        }

        blocks?.forEachIndexed { index, block ->
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
                                        haptics.navigate()
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
