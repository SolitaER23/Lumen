package app.lumen.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.util.L10n
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 外观设置独立子页。
 *
 * 从设置主页拆出，避免单页过长。所有状态由外部持有并回写，
 * 本页只负责呈现与转译交互，不直接读写存储。
 */
data class AppearanceUiState(
    val themeMode: String,
    val paletteStyle: String,
    val contrast: Float,
    val gradientTheme: Boolean,
    val perPageColor: Boolean,
    val materialYou: Boolean,
    val amoled: Boolean,
    val pinned: Boolean,
    val youTakeover: Boolean,
    val colorLocked: Boolean,
)

@Composable
fun AppearanceScreen(
    state: AppearanceUiState,
    onBack: () -> Unit,
    onThemeMode: (String) -> Unit,
    onPaletteStyle: (String) -> Unit,
    onContrast: (Float) -> Unit,
    onGradientTheme: (Boolean) -> Unit,
    onPerPageColor: (Boolean) -> Unit,
    onMaterialYou: (Boolean) -> Unit,
    onAmoled: (Boolean) -> Unit,
    onPinColor: () -> Unit,
    onUnpinColor: () -> Unit,
) {
    val coverSwitchEnabled = !state.youTakeover && !state.colorLocked
    val disableReason: String? = when {
        state.youTakeover -> L10n.t("Material You 已接管配色")
        state.colorLocked -> L10n.t("配色已锁定，先解锁再调整")
        else -> null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.width(40.dp).padding(0.dp),
            ) {
                Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                L10n.t("外观"),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            L10n.t("主题、配色与封面取色"),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 52.dp, bottom = 8.dp),
        )

        AppearanceSection("01", L10n.t("主题")) {
            SegmentedRow(
                title = L10n.t("深浅模式"),
                hint = L10n.t("跟随系统 / 强制深色 / 强制浅色"),
                labels = listOf(L10n.t("跟随系统"), L10n.t("深色"), L10n.t("浅色")),
                values = listOf("system", "dark", "light"),
                selected = state.themeMode,
                onSelect = onThemeMode,
            )
            SegmentedRow(
                title = L10n.t("配色风格"),
                hint = L10n.t("从封面取色后按此风格展开"),
                labels = listOf(L10n.t("色相"), L10n.t("活力"), L10n.t("表现"), L10n.t("中性")),
                values = listOf("tonal", "vibrant", "expressive", "neutral"),
                selected = state.paletteStyle,
                onSelect = onPaletteStyle,
            )
            FloatSegmentedRow(
                title = L10n.t("对比度"),
                hint = L10n.t("文字与背景的分离程度"),
                labels = listOf(L10n.t("标准"), L10n.t("增强"), L10n.t("最高")),
                values = listOf(0.0f, 0.45f, 0.8f),
                selected = state.contrast,
                onSelect = onContrast,
            )
        }

        AppearanceSection("02", L10n.t("封面取色")) {
            ToggleRow(
                L10n.t("封面渐变配色"),
                L10n.t("从封面提取主色，生成主色、辅色与点缀色"),
                state.gradientTheme,
                coverSwitchEnabled,
                onChange = onGradientTheme,
                disabledReason = disableReason,
            )
            ToggleRow(
                L10n.t("每页独立取色"),
                L10n.t("进入专辑等页面时改用该页封面配色"),
                state.perPageColor,
                coverSwitchEnabled,
                onChange = onPerPageColor,
                disabledReason = disableReason,
            )
            ToggleRow(
                L10n.t("Material You"),
                L10n.t("Android 12+ 用系统壁纸配色（接管封面取色）"),
                state.materialYou,
                !state.colorLocked,
                onChange = onMaterialYou,
            )
            ToggleRow(
                L10n.t("AMOLED 纯黑"),
                L10n.t("深色模式下背景压到纯黑，更省电"),
                state.amoled,
                state.themeMode != "light",
                onChange = onAmoled,
            )
            var fxOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(AmbientFx.enabled) }
            val fxCtx = androidx.compose.ui.platform.LocalContext.current
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        L10n.t("氛围动效"),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        L10n.t("背景光团呼吸与漂浮粒子；关闭后完全停止渲染"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
                Switch(
                    checked = fxOn,
                    onCheckedChange = {
                        fxOn = it
                        AmbientFx.set(fxCtx, it)
                    },
                )
            }

            Text(
                when {
                    state.youTakeover -> L10n.t("Material You 生效中：封面相关开关与专辑锁色已禁用。")
                    state.colorLocked -> L10n.t("已锁定专辑配色：渐变/每页取色暂不可用，解锁后恢复。")
                    else -> L10n.t("以上开关按需组合；互相冲突时会自动禁用。")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        AppearanceSection("03", L10n.t("专辑锁色")) {
            Text(
                L10n.t("固定当前配色，不随切歌变化"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Row(Modifier.fillMaxWidth().alpha(if (state.youTakeover) 0.38f else 1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(L10n.t("跟随封面"), !state.pinned && !state.youTakeover, Modifier.weight(1f)) { onUnpinColor() }
                PillButton(L10n.t("锁定当前"), state.pinned && !state.youTakeover, Modifier.weight(1f)) { onPinColor() }
            }
        }

        Spacer(Modifier.padding(bottom = 40.dp))
    }
}

@Composable
private fun AppearanceSection(index: String, title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape)
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
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) { content() }
        }
    }
}

@Composable
private fun SegmentedRow(
    title: String,
    hint: String,
    labels: List<String>,
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label ->
                PillButton(label, selected == values[i], Modifier.weight(1f)) { onSelect(values[i]) }
            }
        }
    }
}

@Composable
private fun FloatSegmentedRow(
    title: String,
    hint: String,
    labels: List<String>,
    values: List<Float>,
    selected: Float,
    onSelect: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label ->
                val active = kotlin.math.abs(selected - values[i]) < 0.01f
                PillButton(label, active, Modifier.weight(1f)) { onSelect(values[i]) }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

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
        Modifier.fillMaxWidth().padding(top = 10.dp).alpha(if (enabled) 1f else 0.38f),
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
        Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
    }
}
