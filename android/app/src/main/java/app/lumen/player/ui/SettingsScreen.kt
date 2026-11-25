package app.lumen.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.graphicsLayer

/** 设置页：账号、外观、心情分析、存储、语言与关于。 */
@Composable
fun SettingsScreen(
    cfg: ServerConfig,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    downloadCount: Int,
    downloadBytes: Long,
    onManageDownloads: () -> Unit,
    onClearSongCache: suspend () -> Unit = {},
    onClearImageCache: suspend () -> Unit = {},
    onClearLyricsCache: suspend () -> Unit = {},
    onClearMoodAbnormal: suspend () -> Unit = {},
    onIgnoreFailedTracks: suspend () -> Int = { 0 },
    onRestoreIgnoredTracks: suspend () -> Int = { 0 },
    contrast: Float = 0.45f,
    onContrast: (Float) -> Unit = {},
    themeMode: String = "system",
    onThemeMode: (String) -> Unit = {},
    paletteStyle: String = "tonal",
    onPaletteStyle: (String) -> Unit = {},
    amoled: Boolean = false,
    onAmoled: (Boolean) -> Unit = {},
    moodTint: Boolean = true,
    onMoodTint: (Boolean) -> Unit = {},
    gradientTheme: Boolean = false,
    onGradientTheme: (Boolean) -> Unit = {},
    materialYou: Boolean = false,
    onMaterialYou: (Boolean) -> Unit = {},
    perPageColor: Boolean = false,
    onPerPageColor: (Boolean) -> Unit = {},
    pinned: Boolean = false,
    paletteStatus: String = "",
    onPinColor: () -> Unit = {},
    onUnpinColor: () -> Unit = {},
    fadeMs: Long = 260L,
    onFadeMs: (Long) -> Unit = {},
    smartFade: Boolean = true,
    onSmartFade: (Boolean) -> Unit = {},
    langMode: String = "system",
    onLangMode: (String) -> Unit = {},
    accounts: List<Account> = emptyList(),
    activeAccountId: String? = null,
    onSwitchAccount: (Account) -> Unit = {},
    onAddAccount: (String, String, String) -> Unit = { _, _, _ -> },
    onRemoveAccount: (Account) -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    versionName: String = "",
    onClearLocalData: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenChangelog: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var freed by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    var cacheSongs by remember { mutableStateOf(0L) }
    var cacheImages by remember { mutableStateOf(0L) }
    var cacheLyrics by remember { mutableStateOf(0L) }
    var moodAbnormal by remember { mutableIntStateOf(0) }
    var moodIgnored by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(freed) {
        withContext(Dispatchers.IO) {
            runCatching {
                cacheSongs = app.lumen.player.playback.MediaCache.streamingBytes(ctx)
                cacheImages = app.lumen.player.playback.MediaCache.imageBytes(ctx)
                cacheLyrics = app.lumen.player.data.LyricsCache.bytes(ctx)
                moodAbnormal = app.lumen.player.data.MoodStore.abnormalCount(ctx)
                moodIgnored = app.lumen.player.data.MoodStore.ignoredCount(ctx)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
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
            var showAppearance by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Surface(
                onClick = { showAppearance = true },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(L10n.t("打开外观设置"), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(L10n.t("主题、配色风格、对比度与封面取色"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                    Text("\u203A", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
                }
            }
            if (showAppearance) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showAppearance = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                    ),
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppearanceScreen(
                            state = AppearanceUiState(
                                themeMode = themeMode,
                                paletteStyle = paletteStyle,
                                contrast = contrast,
                                moodTint = moodTint,
                                gradientTheme = gradientTheme,
                                perPageColor = perPageColor,
                                materialYou = materialYou,
                                amoled = amoled,
                                pinned = pinned,
                                youTakeover = materialYou,
                                colorLocked = pinned,
                            ),
                            onBack = { showAppearance = false },
                            onThemeMode = onThemeMode,
                            onPaletteStyle = onPaletteStyle,
                            onContrast = onContrast,
                            onMoodTint = onMoodTint,
                            onGradientTheme = onGradientTheme,
                            onPerPageColor = onPerPageColor,
                            onMaterialYou = onMaterialYou,
                            onAmoled = onAmoled,
                            onPinColor = onPinColor,
                            onUnpinColor = onUnpinColor,
                        )
                    }
                }
            }
                }
            }

            // 03 播放（衔接相关设置从「外观」移出）
            item {
                ExpressiveSection("03", L10n.t("播放")) {
                    SettingLabel(L10n.t("切歌淡入淡出"), L10n.t("旧曲淡出，新曲淡入"))
                    SegmentedControl(
                        labels = listOf(L10n.t("关闭"), L10n.t("短"), L10n.t("标准"), L10n.t("长")),
                        selected = listOf(0L, 150L, 260L, 420L).indexOf(fadeMs).coerceAtLeast(0),
                        onSelect = { index -> onFadeMs(listOf(0L, 150L, 260L, 420L)[index]) },
                    )
                    SettingLabel(
                        L10n.tf("智能衔接", "Smart transition"),
                        L10n.tf("按当前曲目 BPM 调整淡入淡出", "Adjust crossfade to the current BPM"),
                        Modifier.padding(top = 18.dp),
                    )
                    SegmentedControl(
                        labels = listOf(L10n.t("关闭"), L10n.t("开启")),
                        selected = if (smartFade) 1 else 0,
                        onSelect = { index -> onSmartFade(index == 1) },
                    )
                    Text(
                        L10n.tf("开启后按曲目节拍选择一拍长度的淡入淡出；未分析 BPM 的曲目仍使用上面的时长。", "When enabled, the crossfade uses one beat of the current track; tracks without BPM data fall back to the duration above."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }


            // 04 存储：四类缓存分开管理，主按钮只清歌曲缓存
            item {
                ExpressiveSection("04", L10n.t("存储")) {
                    CacheRow(
                        title = L10n.t("歌曲缓存"),
                        size = cacheSongs,
                        hint = L10n.t("边下边播的流缓存，清理后下次播放重新缓冲"),
                        onClear = { scope.launch { onClearSongCache(); freed = !freed } },
                    )
                    CacheRow(
                        title = L10n.t("封面缓存"),
                        size = cacheImages,
                        hint = L10n.t("清理后封面需重新下载，不影响已下载歌曲"),
                        onClear = { scope.launch { onClearImageCache(); freed = !freed } },
                    )
                    CacheRow(
                        title = L10n.t("歌词缓存"),
                        size = cacheLyrics,
                        hint = L10n.t("已保存的歌词文本，清理后需重新获取"),
                        onClear = { scope.launch { onClearLyricsCache(); freed = !freed } },
                    )
                    StorageRow(
                        title = L10n.t("分析异常数据"),
                        value = L10n.tf("${moodAbnormal} 条", "${moodAbnormal} items"),
                        hint = L10n.t("清理后这些曲目会在下次启动时重试；想彻底不分析请用「忽略」"),
                        action = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    L10n.tf("忽略", "Ignore"),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .clickable {
                                            scope.launch {
                                                onIgnoreFailedTracks()
                                                freed = !freed
                                            }
                                        },
                                )
                                IconButton(onClick = {
                                    scope.launch {
                                        onClearMoodAbnormal()
                                        freed = !freed
                                    }
                                }) {
                                    Icon(
                                        YoinSymbols.Delete,
                                        contentDescription = L10n.t("清理异常数据"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                    if (moodIgnored > 0) {
                        StorageRow(
                            title = L10n.t("已忽略曲目"),
                            value = L10n.tf("${moodIgnored} 首", "${moodIgnored} tracks"),
                            hint = L10n.t("这些曲目不再参与分析，恢复后下次启动重试"),
                            action = {
                                Text(
                                    L10n.tf("恢复", "Restore"),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .clickable {
                                            scope.launch {
                                                onRestoreIgnoredTracks()
                                                freed = !freed
                                            }
                                        },
                                )
                            },
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

            // 06 关于：检查更新紧跟版本号；隐私条款并入关于；使用引导在问题反馈上方
            item {
                ExpressiveSection("06", L10n.t("关于")) {
                    AboutRow(
                        L10n.t("版本"),
                        "v${versionName.ifBlank { "—" }}",
                        onClick = null,
                        onLongClick = onOpenChangelog,
                    )
                    AboutRow(
                        L10n.tf("检查更新", "Check for updates"),
                        L10n.tf("夸克网盘 · 3GF3", "Quark · 3GF3"),
                        onClick = { ExternalLinks.open(ctx, ExternalLinks.UPDATE_URL) },
                        chevron = true,
                        onLongClick = {
                            val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("Lumen update code", ExternalLinks.UPDATE_CODE),
                            )
                            android.widget.Toast.makeText(
                                ctx,
                                L10n.t("提取码已复制"),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                    AboutRow(L10n.t("开源信息"), L10n.t("技术栈与许可"), onClick = onOpenLicenses, chevron = true)
                    AboutRow(L10n.tf("用户协议", "Terms of Service"), L10n.tf("查看", "View"), onClick = onOpenTerms, chevron = true)
                    AboutRow(L10n.tf("隐私条款", "Privacy Policy"), L10n.tf("查看", "View"), onClick = onOpenPrivacy, chevron = true)
                    AboutRow(L10n.tf("使用引导", "Walkthrough"), L10n.tf("重新查看", "View again"), onClick = onOpenGuide)
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
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
    val arrow by androidx.compose.animation.core.animateFloatAsState(
        if (expanded) 0f else -90f,
        androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 500f),
        label = "sec-arrow",
    )
    Column(
        Modifier.fillMaxWidth().padding(top = 26.dp)
            .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f)),
    ) {
        Row(
            Modifier.padding(start = 4.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Spacer(Modifier.weight(1f))
            Text(
                "\u25BE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.graphicsLayer { rotationZ = arrow },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
        ) {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), content = content)
            }
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

/** 开关行：左侧标题 + 说明，右侧 Switch。 */
@Composable
private fun ToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
    disabledReason: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                if (!enabled && disabledReason != null) disabledReason else hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
        )
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
private fun CacheRow(title: String, size: Long, hint: String, onClear: () -> Unit) {
    StorageRow(
        title = title,
        value = "%.1f".format(size / 1048576.0) + " MB",
        hint = hint,
        action = {
            IconButton(onClick = onClear) {
                Icon(
                    YoinSymbols.Delete,
                    contentDescription = L10n.t("清理"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun StorageRow(
    title: String,
    value: String,
    hint: String,
    action: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        action()
    }
}

@Composable
private fun AboutRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                when {
                    onClick != null && onLongClick != null -> Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    onLongClick != null -> Modifier.pointerInput(onLongClick) {
                        detectTapGestures(onLongPress = { onLongClick() })
                    }
                    else -> Modifier
                },
            )
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
            ExternalLinks.FEEDBACK_NAME,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clickable { ExternalLinks.open(ctx, ExternalLinks.BILIBILI_URL) }
                .padding(vertical = 4.dp),
        )
        Text(
            L10n.tf("点击前往 B 站主页", "Tap to open the Bilibili profile"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
        TextButton(
            onClick = {
                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                        "Lumen diagnostics",
                        app.lumen.player.data.Diagnostics.summary(ctx),
                    ),
                )
                android.widget.Toast.makeText(ctx, L10n.t("诊断信息已复制"), android.widget.Toast.LENGTH_SHORT).show()
            },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(L10n.tf("复制诊断信息", "Copy diagnostics"), fontSize = 11.sp)
        }
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