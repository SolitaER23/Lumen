package app.lumen.player.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.lumen.player.util.L10n

/**
 * 外观设置子页。
 *
 * 结构与 Google 官方 Material 3 设置页一致：
 * - `Scaffold` + `TopAppBar`（返回键在顶栏，不再手画一行"返回箭头 + 标题"）；
 * - 每条设置 = `ListItem`（标题 titleSmall / 说明 bodySmall）/ `Switch` / `SegmentedButton`；
 * - 分组 = `surfaceContainerLow` 的 `Surface`，分隔用 `HorizontalDivider`；
 * - 间距走 4dp 网格，形状/字阶/颜色一律取 MaterialTheme token，不写字面色值或字号。
 *
 * 所有状态由外部持有并回写，本页只负责呈现与转译交互，不直接读写存储。
 */
data class AppearanceUiState(
    val themeMode: String,
    val paletteStyle: String,
    val contrast: Float,
    val gradientTheme: Boolean,
    val materialYou: Boolean,
    val amoled: Boolean,
    val pinned: Boolean,
    val youTakeover: Boolean,
    val colorLocked: Boolean,
    /** 当前配色的来源说明（封面取色 / 系统配色 / 已锁定 …），由调用方给出。 */
    val paletteStatus: String = "",
    /** 当前是否处于深色（含跟随系统解析结果），用于禁用只对深色生效的开关。 */
    val darkActive: Boolean = false,
)

@Composable
fun AppearanceScreen(
    state: AppearanceUiState,
    onBack: () -> Unit,
    onThemeMode: (String) -> Unit,
    onPaletteStyle: (String) -> Unit,
    onContrast: (Float) -> Unit,
    onGradientTheme: (Boolean) -> Unit,
    onMaterialYou: (Boolean) -> Unit,
    onAmoled: (Boolean) -> Unit,
    onPinColor: () -> Unit,
    onUnpinColor: () -> Unit,
) {
    // 本页承载在全屏 Dialog 里，而 Dialog 是**独立窗口**：状态栏/导航栏图标的明暗
    // 必须给这个窗口单独设置，否则浅色外观页上会出现"白底白字"的系统栏。
    val dialogView = LocalView.current
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    LaunchedEffect(lightSurface) {
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(dialogWindow, dialogView).apply {
            isAppearanceLightStatusBars = lightSurface
            isAppearanceLightNavigationBars = lightSurface
        }
    }

    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // 动态取色接管后，「封面渐变」既不会生效也不该让用户以为改了会起作用
    val coverSwitchesEnabled = !state.youTakeover && !state.colorLocked
    val coverSwitchReason: String? = when {
        state.youTakeover -> L10n.tf("系统动态取色已接管配色", "Dynamic color is in charge")
        state.colorLocked -> L10n.tf("配色已锁定，先解锁再调整", "Palette locked — unlock to change")
        else -> null
    }
    val amoledEnabled = state.darkActive

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { LumenTopBar(title = L10n.tf("外观", "Appearance"), onBack = onBack) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    L10n.tf("主题、配色与封面取色", "Theme, color and cover tint"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }

            // 当前配色：把角色直接摊开给用户看，改档位时能立刻看到结果
            item {
                PalettePreview(
                    status = state.paletteStatus,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            // 01 主题
            item {
                AppearanceGroup(index = "01", title = L10n.tf("主题", "Theme")) {
                    SegmentedSetting(
                        title = L10n.tf("深浅模式", "Light or dark"),
                        hint = L10n.tf("跟随系统 / 强制深色 / 强制浅色", "System, dark, or light"),
                        labels = ThemeMode.entries.map { themeModeLabel(it) },
                        selected = ThemeMode.of(state.themeMode).ordinal,
                        onSelect = { onThemeMode(ThemeMode.entries[it].key) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SegmentedSetting(
                        title = L10n.tf("配色风格", "Palette style"),
                        hint = L10n.tf("从封面取色后按此风格展开", "How the cover seed expands into roles"),
                        labels = PaletteStyle.entries.map { if (L10n.lang == "en") it.labelEn else it.label },
                        selected = PaletteStyle.of(state.paletteStyle).ordinal,
                        onSelect = { onPaletteStyle(PaletteStyle.entries[it].key) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SegmentedSetting(
                        title = L10n.tf("对比度", "Contrast"),
                        hint = L10n.tf("前景与背景的分离程度，越高越清晰", "Separation between content and background"),
                        labels = ContrastLevel.entries.map { if (L10n.lang == "en") it.labelEn else it.label },
                        selected = ContrastLevel.nearest(state.contrast).ordinal,
                        onSelect = { onContrast(ContrastLevel.entries[it].level) },
                    )
                }
            }

            // 02 取色
            item {
                AppearanceGroup(index = "02", title = L10n.tf("取色", "Color source")) {
                    ToggleSetting(
                        title = L10n.tf("Material You", "Material You"),
                        hint = if (dynamicColorAvailable) {
                            L10n.tf("用系统壁纸配色，覆盖封面取色", "Use the wallpaper palette instead of the cover")
                        } else {
                            L10n.tf("需要 Android 12 及以上", "Requires Android 12 or newer")
                        },
                        checked = state.materialYou,
                        enabled = dynamicColorAvailable && !state.colorLocked,
                        onChange = onMaterialYou,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ToggleSetting(
                        title = L10n.tf("封面渐变配色", "Cover gradient"),
                        hint = coverSwitchReason
                            ?: L10n.tf("从封面提取主色、辅色与点缀色", "Derive primary, secondary and accent from the cover"),
                        checked = state.gradientTheme,
                        enabled = coverSwitchesEnabled,
                        onChange = onGradientTheme,
                    )
                }
            }

            // 03 显示
            item {
                AppearanceGroup(index = "03", title = L10n.tf("显示", "Display")) {
                    ToggleSetting(
                        title = L10n.tf("AMOLED 纯黑", "AMOLED black"),
                        hint = if (amoledEnabled) {
                            L10n.tf("深色模式下画布压到纯黑，卡片仍保留层级", "Pure-black canvas in dark mode; cards keep their levels")
                        } else {
                            L10n.tf("仅深色模式生效", "Dark mode only")
                        },
                        checked = state.amoled,
                        enabled = amoledEnabled,
                        onChange = onAmoled,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    var fxOn by remember { mutableStateOf(AmbientFx.enabled) }
                    val fxContext = LocalContext.current
                    ToggleSetting(
                        title = L10n.tf("氛围动效", "Ambient motion"),
                        hint = L10n.tf("背景光团缓慢漂移与呼吸；关闭后完全静止以省电", "Slow drifting glow; off keeps it still and saves power"),
                        checked = fxOn,
                        onChange = {
                            fxOn = it
                            AmbientFx.set(fxContext, it)
                        },
                    )
                }
            }

            // 04 专辑锁色
            item {
                AppearanceGroup(index = "04", title = L10n.tf("专辑锁色", "Album palette lock")) {
                    Text(
                        L10n.tf("固定当前配色，不随切歌变化", "Keep the current palette when tracks change"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    )
                    // 动态取色接管时锁色不会有任何效果：一起变灰并拒绝点击，
                    // 避免"看起来禁用、点下去却改了状态"。
                    Box(
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .alpha(if (state.youTakeover) 0.38f else 1f)
                            .then(if (state.youTakeover) Modifier.semantics { disabled() } else Modifier),
                    ) {
                        LumenSegmentedTabs(
                            labels = listOf(
                                L10n.tf("跟随封面", "Follow cover"),
                                L10n.tf("锁定当前", "Lock current"),
                            ),
                            selected = if (state.pinned) 1 else 0,
                            onSelect = {
                                if (!state.youTakeover) {
                                    if (it == 1) onPinColor() else onUnpinColor()
                                }
                            },
                        )
                    }
                }
            }

            // 状态说明：只用一句话讲清"为什么某个开关是灰的"
            if (state.youTakeover || state.colorLocked) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                state.youTakeover -> L10n.tf(
                                    "Material You 生效中：封面相关开关与专辑锁色暂不可用。",
                                    "Material You is on: cover switches and the palette lock are unavailable.",
                                )
                                else -> L10n.tf(
                                    "已锁定专辑配色：渐变暂不可用，解锁后恢复。",
                                    "Palette locked: gradient is unavailable until you unlock it.",
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> L10n.tf("跟随系统", "System")
    ThemeMode.DARK -> L10n.tf("深色", "Dark")
    ThemeMode.LIGHT -> L10n.tf("浅色", "Light")
}

/**
 * 当前配色的角色预览：主色 / 辅色 / 第三色 + 三个容器色。
 * 色块本身来自 ColorScheme，改风格或对比度时立刻能看出差别。
 */
@Composable
private fun PalettePreview(status: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val swatches: List<Pair<Color, String>> = listOf(
        cs.primary to L10n.tf("主色", "Primary"),
        cs.secondary to L10n.tf("辅色", "Secondary"),
        cs.tertiary to L10n.tf("第三色", "Tertiary"),
        cs.primaryContainer to L10n.tf("主色容器", "Primary container"),
        cs.secondaryContainer to L10n.tf("辅色容器", "Secondary container"),
        cs.tertiaryContainer to L10n.tf("第三色容器", "Tertiary container"),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                swatches.forEach { (color, label) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(color)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.small,
                            )
                            .semantics {
                                // 色块本身不可读，把角色名念出来，TalkBack 才不是一片空白
                                contentDescription = label
                            },
                    )
                }
            }
            if (status.isNotBlank()) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** 分组容器：编号 + 标题 + 一块 surfaceContainerLow 的卡片。 */
@Composable
private fun AppearanceGroup(
    index: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    index,
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

/** 标题 + 说明 + 分段按钮（M3 官方 SegmentedButton，独占一行，避免挤在 ListItem 尾部）。 */
@Composable
private fun SegmentedSetting(
    title: String,
    hint: String,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        LumenSegmentedTabs(
            labels = labels,
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * 开关设置：官方 ListItem + Switch。
 *
 * 整行可点（`toggleable` + Role.Switch），触控目标大于 48dp；Switch 自身不重复处理点击，
 * 避免一次点击触发两次回调。禁用时说明文字直接变成原因，用户不用猜。
 */
@Composable
private fun ToggleSetting(
    title: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onChange,
        )
    ListItem(
        content = {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = rowModifier,
    )
}
