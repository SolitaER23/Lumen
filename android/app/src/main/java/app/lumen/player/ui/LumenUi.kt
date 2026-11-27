package app.lumen.player.ui

import app.lumen.player.util.L10n
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gpo.yoin.symbols.YoinSymbols

/**
 * M3 Expressive 设计令牌。
 *
 * 只放**尺寸与节奏**：颜色一律来自 `MaterialTheme.colorScheme`，形状来自
 * `MaterialTheme.shapes` / `ShapeDefaults`，字体来自 `MaterialTheme.typography`
 * （含 Emphasized 强调档）。这样换主题、换封面、切深浅色时所有界面一起变。
 */
object LumenSizes {
    /** 4dp 基准网格 */
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** 屏幕左右安全边距（M3：16dp） */
    val screenH = 16.dp

    /** 迷你播放条高度（含外边距后占位） */
    val miniBar = 72.dp

    /** 迷你播放条本体高度（不含导航栏内边距与下方间距）；共享元素裁剪按它算可见下边界 */
    val miniBarHeight = 64.dp

    /** 迷你播放条与手势导航条之间的间距 */
    val miniBarBottomGap = 6.dp

    /** 首页顶栏内容高度（不含状态栏内边距）；共享元素裁剪按它算可见上边界 */
    val homeTopBar = 56.dp

    /** 迷你条可见时，可滚动内容的底部留白：条 + 导航条 + 呼吸 */
    val contentBottom = 148.dp
}

/** 顶栏：M3 TopAppBar（Expressive 标题字阶 + 透明容器，让动态背景透上来）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        YoinSymbols.Back,
                        contentDescription = L10n.t("返回"),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        actions = actions,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

/** M3 Expressive 单项选择分段按钮：用作页面内等价的标签切换。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LumenSegmentedTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** false = 按传入宽度/内容排布（用于放进顶栏这种横向拥挤的位置） */
    fillWidth: Boolean = true,
    /** 需要与页面主色统一时传入（默认沿用 M3 的 secondaryContainer 选中色） */
    colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(),
) {
    SingleChoiceSegmentedButtonRow(modifier = if (fillWidth) modifier.fillMaxWidth() else modifier) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                colors = colors,
                icon = {},
                label = {
                    Text(
                        label,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}

/**
 * 封面：全应用唯一的封面绘制入口。
 *
 * - 圆角来自 M3 形状刻度（不写临时圆角）；
 * - 占位底色用 `surfaceContainerHighest`，弱网/冷启动不会闪白；
 * - `sharedKey` 让同一张封面在首页卡片 → 专辑页 hero → 播放页之间连续 morph。
 */
@Composable
fun AlbumArt(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    sharedKey: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    content: (@Composable () -> Unit)? = null,
) {
    val shared = if (sharedKey != null) Modifier.albumSharedElement(sharedKey) else Modifier
    Box(modifier.then(shared)) {
        Box(Modifier.matchParentSize().clip(shape)) {
            Surface(
                modifier = Modifier.matchParentSize(),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = shape,
            ) {}
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
            content?.invoke()
        }
    }
}

/** M3 Expressive 加载指示器（官方 LoadingIndicator，带形状 morph 动效）。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LumenLoading(modifier: Modifier = Modifier) {
    LoadingIndicator(
        modifier = modifier.size(40.dp),
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 分区标题：标题用 Emphasized 字阶，尾部计数用弱化色。 */
@Composable
fun LumenSectionHeader(
    title: String,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    /** 外层列表已有左右留白时传 0dp，避免叠加成双倍缩进 */
    horizontalPadding: androidx.compose.ui.unit.Dp = LumenSizes.screenH,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = LumenSizes.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (trailing != null) {
            Text(
                "  ·  $trailing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 错误态：M3 Card + 主按钮，文案由调用方给（已经本地化）。 */
@Composable
fun LumenErrorState(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(LumenSizes.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LumenSizes.m),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(LumenSizes.m),
            )
        }
        Button(
            onClick = onRetry,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = LumenSizes.l, vertical = LumenSizes.s),
        ) {
            Text(retryLabel, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** 空态：一句话 + 一句提示，避免"死路"。 */
@Composable
fun LumenEmptyState(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(LumenSizes.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LumenSizes.xs),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 小胶囊（用于播放模式、评分这类次级信息）。 */
@Composable
fun LumenPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = LumenSizes.s, vertical = LumenSizes.xxs),
        )
    }
}
