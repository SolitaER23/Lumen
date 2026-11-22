package app.lumen.player.ui

import app.lumen.player.util.L10n
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.Account
import app.lumen.player.data.ServerConfig
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** 设置页：账号、外观、心情分析、存储、关于。 */
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

    LaunchedEffect(freed) {
        cacheBytes = withContext(Dispatchers.IO) {
            runCatching {
                val total = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                (total - downloadBytes).coerceAtLeast(0L)
            }.getOrDefault(0L)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(L10n.t("设置"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text(L10n.t("设置"), color = MaterialTheme.colorScheme.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Text(
                L10n.t("账号、外观与数据"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // 1. 账号与用户
        item {
            SettingSection(L10n.t("账号")) {
                CurrentAccountCard(cfg)
                ExpandRow(
                    label = L10n.t("切换用户"),
                    hint = if (accounts.size > 1) "${accounts.size} 个账号" else L10n.t("仅当前账号"),
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
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showAdd = true }
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(L10n.t("添加账号"), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onLogout)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(L10n.t("退出登录"), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { confirmWipe = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(L10n.t("清除本机数据"), color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            }
        }

        // 2. 外观
        item {
            SettingSection(L10n.t("外观")) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0f to L10n.t("标准"), 0.5f to L10n.t("中"), 1f to L10n.t("高")).forEach { (value, label) ->
                        val selected = kotlin.math.abs(contrast - value) < 0.01f
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
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
                    L10n.t("对比度越高，文字与背景越分明；越低，整体更柔和。默认「高」。"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    L10n.t("切歌淡入淡出"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0L to L10n.t("关闭"), 150L to L10n.t("短"), 260L to L10n.t("标准"), 420L to L10n.t("长")).forEach { (value, label) ->
                        val selected = fadeMs == value
                        Box(
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .clickable { onFadeMs(value) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Text(
                    L10n.t("切歌时旧曲淡出、新曲淡入，0 为直接切换。"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // 3. 心情分析
        item {
            SettingSection(L10n.t("心情分析")) {
                var moodDone by remember { mutableStateOf(0) }
                var moodTotal by remember { mutableStateOf(0) }
                var moodRunning by remember { mutableStateOf(false) }
                var moodCount by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        val (d, t, r) = app.lumen.player.playback.MoodWorker.progress(ctx)
                        moodDone = d
                        moodTotal = t
                        moodRunning = r
                        moodCount = withContext(Dispatchers.IO) {
                            app.lumen.player.data.MoodStore.count(ctx)
                        }
                        delay(1500)
                    }
                }
                val percent = if (moodTotal > 0) (moodDone * 100 / moodTotal) else 0
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (moodTotal == 0 && moodCount == 0) L10n.t("尚未分析")
                            else if (moodRunning) L10n.tf("正在分析 · $percent%", "Analyzing · $percent%")
                            else L10n.tf("已分析 $moodCount 首", "Analyzed $moodCount tracks"),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                        )
                        Text(
                            if (moodTotal > 0) L10n.tf("本库 $moodTotal 首 · 充电 + Wi-Fi 时后台进行", "$moodTotal tracks · runs in background while charging on Wi-Fi")
                            else L10n.t("在本机提取节奏与情绪，不上传文件"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    PillButton(L10n.t("管理")) { onOpenMood() }
                }
            }
        }

        // 4. 存储
        item {
            SettingSection(L10n.t("存储")) {
                SettingRow(
                    L10n.t("清理缓存"),
                    L10n.tf("${"%.1f".format(cacheBytes / 1024f / 1024f)} MB · 不含已下载", "${"%.1f".format(cacheBytes / 1024f / 1024f)} MB · excludes downloads"),
                ) {
                    IconButton(onClick = {
                        onClearCache()
                        freed = !freed
                    }) {
                        Icon(YoinSymbols.Delete, contentDescription = L10n.t("清理缓存"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (freed) {
                    Text(L10n.t("缓存已清理（下载不受影响）"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                SettingRow(L10n.t("离线下载"), L10n.tf("$downloadCount 首 · ${"%.1f".format(downloadBytes / 1024f / 1024f)} MB", "$downloadCount tracks · ${"%.1f".format(downloadBytes / 1024f / 1024f)} MB")) {
                    TextButton(onClick = onManageDownloads) {
                        Text(L10n.t("管理"), fontSize = 12.sp)
                    }
                }
            }
        }

        // 5. 关于
        item {
            SettingSection(L10n.tf("语言", "Language")) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "system" to L10n.tf("跟随系统", "System"),
                        "zh" to "中文",
                        "en" to "English",
                    ).forEach { (mode, label) ->
                        val selected = langMode == mode
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .clickable { onLangMode(mode) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            SettingSection(L10n.t("关于")) {
                SettingRow(L10n.t("版本"), versionName.ifBlank { "—" })
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenGuide)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        L10n.tf("使用引导", "Walkthrough"),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        L10n.tf("重新查看", "View again"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenLicenses)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(L10n.t("开源信息"), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(L10n.t("技术栈与许可"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("  ›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
                FeedbackBlock()
                AiCodedBadge(Modifier.padding(top = 14.dp))
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

    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            L10n.t("问题反馈"),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
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
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun AiCodedBadge(modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurface
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.5.dp, outline.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
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
private fun CurrentAccountCard(cfg: ServerConfig) {
    val initial = cfg.user.firstOrNull()?.uppercase() ?: "?"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(cfg.user, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                cfg.base.removePrefix("https://").removePrefix("http://"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(L10n.t("当前"), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ExpandRow(label: String, hint: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Text(
            title,
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
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), content = content)
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
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
        )
    }
}
