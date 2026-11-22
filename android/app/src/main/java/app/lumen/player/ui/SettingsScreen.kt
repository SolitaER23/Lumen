package app.lumen.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.Account
import app.lumen.player.data.ServerConfig
import app.lumen.player.util.L10n
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** 设置页：账号、外观、心情分析、存储、语言与关于。 */
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
    fadeMs: Long = 260L,
    onFadeMs: (Long) -> Unit = {},
    langMode: String = "system",
    onLangMode: (String) -> Unit = {},
    accounts: List<Account> = emptyList(),
    activeAccountId: String? = null,
    onSwitchAccount: (Account) -> Unit = {},
    onAddAccount: (String, String, String) -> Unit = { _, _, _ -> },
    onRemoveAccount: (Account) -> Unit = {},
    onOpenMood: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    versionName: String = "",
    onClearLocalData: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var cacheBytes by remember { mutableStateOf(0L) }
    var freed by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    var moodTotal by remember { mutableIntStateOf(0) }
    var moodRunning by remember { mutableStateOf(false) }
    var moodCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(freed) {
        cacheBytes = withContext(Dispatchers.IO) {
            runCatching {
                val total = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                (total - downloadBytes).coerceAtLeast(0L)
            }.getOrDefault(0L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val (_, t, r) = app.lumen.player.playback.MoodWorker.progress(ctx)
            moodTotal = t
            moodRunning = r
            moodCount = withContext(Dispatchers.IO) {
                app.lumen.player.data.MoodStore.count(ctx)
            }
            delay(1500)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(
                            "v${versionName.ifBlank { "—" }}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Text(
                    L10n.t("设置"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    L10n.t("账号、外观与数据"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // 01 账号
            item {
                ExpressiveSection("01", L10n.t("账号")) {
                    AccountHero(cfg)
                    ExpandRow(
                        label = L10n.t("切换用户"),
                        hint = if (accounts.size > 1) L10n.tf("${accounts.size} 个账号", "${accounts.size} accounts")
                        else L10n.t("仅当前账号"),
                        expanded = showAccounts,
                        onClick = { showAccounts = !showAccounts },
                    )
                    AnimatedVisibility(
                        visible = showAccounts,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(Modifier.padding(top = 4.dp)) {
                            accounts.forEach { account ->
                                AccountRow(
                                    account = account,
                                    active = account.id == activeAccountId,
                                    removable = accounts.size > 1,
                                    onSwitch = {
                                        onSwitchAccount(account)
                                        showAccounts = false
                                    },
                                    onRemove = { onRemoveAccount(account) },
                                )
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .clickable { showAdd = true }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(L10n.t("添加账号"), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                        ) { Text(L10n.t("退出登录"), fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = { confirmWipe = true },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                        ) {
                            Text(L10n.t("清除本机数据"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 02 外观
            item {
                ExpressiveSection("02", L10n.t("外观")) {
                    SettingLabel(L10n.t("对比度"), L10n.t("文字与背景的分离程度"))
                    SegmentedControl(
                        labels = listOf(L10n.t("标准"), L10n.t("中"), L10n.t("高")),
                        selected = if (contrast >= 0.75f) 2 else if (contrast >= 0.25f) 1 else 0,
                        onSelect = { index -> onContrast(listOf(0f, 0.5f, 1f)[index]) },
                    )
                    Text(
                        L10n.t("对比度越高，文字与背景越分明；越低，整体更柔和。默认「高」。"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    SettingLabel(L10n.t("切歌淡入淡出"), L10n.t("旧曲淡出，新曲淡入"), Modifier.padding(top = 18.dp))
                    SegmentedControl(
                        labels = listOf(L10n.t("关闭"), L10n.t("短"), L10n.t("标准"), L10n.t("长")),
                        selected = listOf(0L, 150L, 260L, 420L).indexOf(fadeMs).coerceAtLeast(0),
                        onSelect = { index -> onFadeMs(listOf(0L, 150L, 260L, 420L)[index]) },
                    )
                }
            }

            // 03 心情分析
            item {
                val percent = when {
                    moodTotal > 0 -> (moodCount * 100 / moodTotal).coerceIn(0, 100)
                    moodCount > 0 -> 100
                    else -> 0
                }
                ExpressiveSection("03", L10n.t("心情分析")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    L10n.tf("$moodCount 首", "$moodCount tracks"),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(8.dp))
                                MoodStatus(running = moodRunning, hasData = moodCount > 0)
                            }
                            Text(
                                if (moodTotal > 0) L10n.tf("本库 $moodTotal 首 · 覆盖 $percent%", "$moodTotal tracks · $percent% covered")
                                else L10n.t("在本机提取节奏与情绪，不上传文件"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        PillButton(L10n.t("管理"), primary = true) { onOpenMood() }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(percent / 100f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                                    ),
                                ),
                        )
                    }
                }
            }

            // 04 存储
            item {
                ExpressiveSection("04", L10n.t("存储")) {
                    StorageRow(
                        title = L10n.t("清理缓存"),
                        value = L10n.tf("${"%.1f".format(cacheBytes / 1024f / 1024f)} MB", "${"%.1f".format(cacheBytes / 1024f / 1024f)} MB"),
                        hint = L10n.tf("不含已下载", "excludes downloads"),
                        action = {
                            IconButton(onClick = {
                                onClearCache()
                                freed = !freed
                            }) {
                                Icon(
                                    YoinSymbols.Delete,
                                    contentDescription = L10n.t("清理缓存"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    if (freed) {
                        Text(
                            L10n.t("缓存已清理（下载不受影响）"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    StorageRow(
                        title = L10n.t("离线下载"),
                        value = L10n.tf("$downloadCount 首", "$downloadCount tracks"),
                        hint = "${"%.1f".format(downloadBytes / 1024f / 1024f)} MB",
                        action = {
                            PillButton(L10n.t("管理")) { onManageDownloads() }
                        },
                    )
                }
            }

            // 05 语言
            item {
                ExpressiveSection("05", L10n.tf("语言", "Language")) {
                    SegmentedControl(
                        labels = listOf(
                            L10n.tf("跟随系统", "System"),
                            "中文",
                            "English",
                        ),
                        selected = listOf("system", "zh", "en").indexOf(langMode).coerceAtLeast(0),
                        onSelect = { index -> onLangMode(listOf("system", "zh", "en")[index]) },
                    )
                }
            }

            // 06 关于
            item {
                ExpressiveSection("06", L10n.t("关于")) {
                    AboutRow(L10n.t("版本"), "v${versionName.ifBlank { "—" }}", onClick = null)
                    AboutRow(L10n.tf("使用引导", "Walkthrough"), L10n.tf("重新查看", "View again"), onClick = onOpenGuide)
                    AboutRow(L10n.t("开源信息"), L10n.t("技术栈与许可"), onClick = onOpenLicenses, chevron = true)
                    FeedbackBlock()
                    AiCodedBadge(Modifier.padding(top = 14.dp))
                }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(L10n.t("清除本机数据？")) },
            text = {
                Text(L10n.t("将删除本机保存的账号、播放历史、评分、心情数据、统计配置、离线下载与缓存，并回到登录页。该操作不可撤销。"))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    onClearLocalData()
                }) { Text(L10n.t("清除"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(L10n.t("取消")) } },
        )
    }

    if (showAdd) {
        AddAccountDialog(
            onDismiss = { showAdd = false },
            onConfirm = { base, user, pass ->
                showAdd = false
                onAddAccount(base, user, pass)
            },
        )
    }
}

@Composable
private fun ExpressiveSection(index: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(index, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), content = content)
        }
    }
}

@Composable
private fun SettingLabel(title: String, hint: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun SegmentedControl(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AccountHero(cfg: ServerConfig) {
    val initial = cfg.user.firstOrNull()?.uppercase() ?: "?"
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(cfg.user, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    cfg.base.removePrefix("https://").removePrefix("http://"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) {
                Text(
                    L10n.t("当前"),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun ExpandRow(label: String, hint: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text(
            if (expanded) "  ▾" else "  ▸",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun AccountRow(
    account: Account,
    active: Boolean,
    removable: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSwitch)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        )
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                account.label.ifBlank { account.user },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                account.host,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (removable && !active) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    YoinSymbols.Delete,
                    contentDescription = L10n.t("移除账号"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (active) {
            Text(L10n.t("当前"), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MoodStatus(running: Boolean, hasData: Boolean) {
    val color = when {
        running -> MaterialTheme.colorScheme.primary
        hasData -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(
            if (running) L10n.t("进行中") else if (hasData) L10n.tf("已保存", "Saved") else L10n.t("未开始"),
            color = color,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun StorageRow(
    title: String,
    value: String,
    hint: String,
    action: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp),
                )
            }
        }
        action()
    }
}

@Composable
private fun AboutRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (chevron) Text("  ›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun FeedbackBlock() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    val email = "moon.knurled.7j@icloud.com"
    var copied by remember { mutableStateOf(false) }

    fun copy() {
        clipboard.setText(AnnotatedString(email))
        copied = true
        android.widget.Toast.makeText(ctx, L10n.t("邮箱已复制"), android.widget.Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(14.dp),
    ) {
        Text(
            L10n.t("问题反馈"),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            email,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .pointerInput(email) {
                    detectTapGestures(onLongPress = { copy() })
                }
                .padding(vertical = 4.dp),
        )
        Text(
            if (copied) L10n.t("已复制到剪贴板") else L10n.t("长按邮箱复制"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun AiCodedBadge(modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurface
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.5.dp, outline.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                L10n.t("AI 编码"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Text(
                L10n.t("非真人生成"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var base by remember { mutableStateOf("http://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.t("添加账号")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text(L10n.t("服务器地址"), fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(L10n.t("用户名"), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(L10n.t("密码"), fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = base.startsWith("http") && user.isNotBlank() && pass.isNotBlank(),
                onClick = { onConfirm(base, user, pass) },
            ) { Text(L10n.t("添加并切换")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.t("取消")) } },
    )
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
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
