@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.lumen.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lumen.player.data.StatsClient
import app.lumen.player.data.StatsConfig
import app.lumen.player.data.StatsDashboard
import app.lumen.player.data.StatsHistory
import app.lumen.player.data.StatsInsights
import app.lumen.player.data.StatsReport
import app.lumen.player.data.StatsStore
import app.lumen.player.data.insights
import app.lumen.player.data.parsePlayedAt
import app.lumen.player.util.L10n
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 聆听统计（数据源：自建 navidrome-stat /api/stats/dashboard）。
 *
 * 信息架构（从"一眼看懂"到"按需深入"）：
 * 1. 范围选择：7 天 / 30 天 / 90 天 / 1 年 / 全部；
 * 2. 主指标卡：总播放次数、收听时长、环比趋势，以及日均 / 活跃天数 / 峰值；
 * 3. 曲库指标：独立曲目、客户端（与播放量同级的两个"规模"数字）；
 * 4. 每日播放：合并长区间后的柱状图 + 区间与上期对比；
 * 5. 聆听习惯：按星期 / 24 小时分布（来自最近播放样本）；
 * 6. 常听排行：艺人 / 专辑切换，带占比条；
 * 7. 最近播放：按时间倒序的样本记录。
 *
 * 顶栏右侧的分享 / 刷新 / 设置是三个同规格的 M3 Expressive 图标按钮：
 * 同样的 48dp 触控区、24dp Yoin 图标、onSurfaceVariant 颜色与按压形状 morph。
 */
@Composable
fun StatsScreen(
    initial: StatsConfig,
    onBack: () -> Unit,
) {
    androidx.activity.compose.BackHandler { onBack() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val reportPalette = StatsReport.Palette(
        backgroundTop = cs.surface.toArgb(),
        backgroundBottom = cs.surfaceContainerLow.toArgb(),
        card = cs.surfaceContainer.toArgb(),
        track = cs.surfaceContainerHighest.toArgb(),
        textPrimary = cs.onSurface.toArgb(),
        textSecondary = cs.onSurfaceVariant.toArgb(),
        accent = cs.primary.toArgb(),
    )
    var cfg by remember { mutableStateOf(initial) }
    var editing by remember { mutableStateOf(!initial.complete) }
    var days by remember { mutableIntStateOf(30) }
    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<StatsDashboard?>(null) }
    var board by remember { mutableIntStateOf(0) } // 0=艺人 1=专辑
    // 上一周期对比：额外拉一次 2 倍区间，两者相减得到前一个窗口
    var prevPlays by remember { mutableIntStateOf(-1) }
    var prevSeconds by remember { mutableLongStateOf(-1L) }
    // 洞察只随数据变化重算，避免每次重组都遍历 history
    val ins = remember(data) { data?.insights() }

    LaunchedEffect(cfg, days, reload) {
        if (!cfg.complete) return@LaunchedEffect
        loading = true
        error = null
        try {
            val client = StatsClient(cfg)
            // 0 表示"全部"：用一个足够大的窗口（约 10 年），服务端按天聚合时等价于全量
            val requestDays = if (days <= 0) 3650 else days
            val current = withContext(Dispatchers.IO) { client.dashboard(requestDays) }
            data = current
            val larger = withContext(Dispatchers.IO) {
                runCatching { client.dashboard(requestDays * 2) }.getOrNull()
            }
            // 只有服务端真的返回了更长的区间（总量变大）时，相减才有意义；
            // 若服务端把区间裁剪到已有数据（两次总量相同），则隐藏上一周期，避免显示 0 次
            if (larger != null && larger.plays > current.plays) {
                prevPlays = larger.plays - current.plays
                prevSeconds = (larger.listenSeconds - current.listenSeconds).coerceAtLeast(0L)
            } else {
                prevPlays = -1
                prevSeconds = -1L
            }
        } catch (e: Exception) {
            error = e.message ?: L10n.t("请求失败")
        }
        loading = false
    }

    /** 把当前面板导出为长图并拉起系统分享（保留原功能，口径与导出内容不变）。 */
    fun shareReport() {
        val d = data ?: return
        val analysis = ins ?: d.insights()
        shareStatsImage(
            ctx = ctx,
            scope = scope,
            d = d,
            ins = analysis,
            days = days,
            palette = reportPalette,
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            LumenTopBar(
                title = L10n.tf("聆听统计", "Listening stats"),
                onBack = onBack,
                actions = {
                    if (cfg.complete && !editing) {
                        StatsTopAction(
                            icon = YoinSymbols.Share,
                            label = L10n.tf("分享长图", "Share image"),
                            enabled = data != null,
                            onClick = { shareReport() },
                        )
                        StatsTopAction(
                            icon = YoinSymbols.Refresh,
                            label = L10n.t("刷新"),
                            onClick = { reload++ },
                        )
                        StatsTopAction(
                            icon = YoinSymbols.Settings,
                            label = L10n.t("配置"),
                            onClick = { editing = true },
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            // 已有数据时刷新：保留旧内容，用顶部细进度条提示正在拉取
            if (loading && data != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when {
                editing -> ConfigEditor(
                    initial = cfg,
                    onCancel = { if (cfg.complete) editing = false },
                    onSave = {
                        // 存不下（Keystore 不可用）时保持表单并提示，绝不退回明文
                        if (StatsStore.save(ctx, it)) {
                            cfg = it
                            editing = false
                            reload++
                        } else {
                            android.widget.Toast.makeText(
                                ctx,
                                L10n.tf(
                                    "无法安全保存统计 Token（系统密钥库不可用）",
                                    "Could not securely store the stats token (keystore unavailable)",
                                ),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                )
                loading && data == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LumenLoading()
                }
                error != null && data == null -> Box(
                    Modifier.fillMaxSize().padding(LumenSizes.l),
                    contentAlignment = Alignment.Center,
                ) {
                    LumenErrorState(
                        message = error!!,
                        retryLabel = L10n.t("重试"),
                        onRetry = { reload++ },
                    )
                }
                data == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LumenLoading()
                }
                else -> StatsContent(
                    d = data!!,
                    ins = ins ?: data!!.insights(),
                    days = days,
                    onDays = { days = it },
                    board = board,
                    onBoard = { board = it },
                    prevPlays = prevPlays,
                    prevSeconds = prevSeconds,
                )
            }
        }
    }
}

/** 顶栏动作：与右侧刷新/设置完全同规格（48dp / 24dp 图标 / 同色 / 同形状 morph）。 */
@Composable
private fun StatsTopAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = rememberLumenHaptics()
    IconButton(
        onClick = {
            haptics.tap()
            onClick()
        },
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun StatsContent(
    d: StatsDashboard,
    ins: StatsInsights,
    days: Int,
    onDays: (Int) -> Unit,
    board: Int,
    onBoard: (Int) -> Unit,
    prevPlays: Int,
    prevSeconds: Long,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = LumenSizes.screenH,
            end = LumenSizes.screenH,
            top = LumenSizes.xs,
            bottom = LumenSizes.contentBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(LumenSizes.m),
    ) {
        item { RangeSelector(days, onDays) }
        item { HeroCard(d, ins, prevPlays) }
        item { KpiRow(d) }
        item { ActivityCard(d, ins, days, prevPlays, prevSeconds) }
        item { HabitsCard(ins) }
        item { RankingCard(d, board, onBoard) }
        if (d.history.isNotEmpty()) {
            item { RecentPlaysCard(d.history) }
        }
    }
}

@Composable
private fun RangeSelector(days: Int, onSelect: (Int) -> Unit) {
    val options = listOf(7, 30, 90, 365, 0)
    val labels = listOf(
        L10n.tf("7天", "7d"),
        L10n.tf("30天", "30d"),
        L10n.tf("90天", "90d"),
        L10n.tf("1年", "1y"),
        L10n.tf("全部", "All"),
    )
    LumenSegmentedTabs(
        labels = labels,
        selected = options.indexOf(days).coerceAtLeast(0),
        onSelect = { onSelect(options[it]) },
        colors = statsSegmentedColors(),
    )
}

/** 本页所有分段控件统一用主色选中态，和主指标卡 / 排行徽标同一套配色。 */
@Composable
private fun statsSegmentedColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    inactiveContainerColor = Color.Transparent,
    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** 主指标：总播放是唯一的大字，其余规模信息降一级，避免"四个数字一样大"的平铺。 */
@Composable
private fun HeroCard(d: StatsDashboard, ins: StatsInsights, prevPlays: Int) {
    val cs = MaterialTheme.colorScheme
    val onHero = cs.onPrimaryContainer
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(LumenSizes.l)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        L10n.tf("总播放", "Total plays"),
                        style = MaterialTheme.typography.labelLarge,
                        color = onHero.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.height(LumenSizes.xxs))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            d.plays.toString(),
                            style = MaterialTheme.typography.displaySmallEmphasized,
                            color = onHero,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(LumenSizes.xs))
                        Text(
                            L10n.tf("次", "plays"),
                            style = MaterialTheme.typography.titleMedium,
                            color = onHero.copy(alpha = 0.75f),
                            modifier = Modifier.padding(bottom = LumenSizes.xs),
                        )
                    }
                }
                HeroTrendPill(d = d, ins = ins, prevPlays = prevPlays)
            }
            Spacer(Modifier.height(LumenSizes.xs))
            Text(
                L10n.tf(
                    "收听时长 " + formatDuration(d.listenSeconds),
                    formatDuration(d.listenSeconds) + " listened",
                ),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = onHero,
            )
            Spacer(Modifier.height(LumenSizes.m))
            HorizontalDivider(color = onHero.copy(alpha = 0.18f))
            Spacer(Modifier.height(LumenSizes.s))
            Row(horizontalArrangement = Arrangement.spacedBy(LumenSizes.m)) {
                HeroStat(
                    label = L10n.tf("日均", "Daily"),
                    value = L10n.tf(ins.avgPerDay.toString() + " 次", ins.avgPerDay.toString()),
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    label = L10n.tf("活跃天数", "Active days"),
                    value = L10n.tf(ins.activeDays.toString() + " 天", ins.activeDays.toString()),
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    label = L10n.tf("峰值单日", "Peak day"),
                    value = L10n.tf(
                        (ins.peakDay?.count ?: 0).toString() + " 次",
                        (ins.peakDay?.count ?: 0).toString(),
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = onHero,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = onHero.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

/** 环比（上一周期）/ 短期趋势（区间前半 vs 后半）都收敛成一个胶囊，主卡只出现一处趋势。 */
@Composable
private fun HeroTrendPill(d: StatsDashboard, ins: StatsInsights, prevPlays: Int) {
    val hasPrev = prevPlays >= 0
    val pct = if (hasPrev) {
        if (prevPlays == 0) 0 else ((d.plays - prevPlays) * 100.0 / prevPlays).toInt()
    } else {
        ins.trendPct
    }
    // 没有上一周期时，trendPct 是「区间后半程 vs 前半程」，标签写清楚，避免被误读成环比
    val label = if (hasPrev) L10n.tf("较上期", "vs prev.") else L10n.tf("后半程", "2nd half")
    val arrow = if (pct >= 0) "↑" else "↓"
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        shape = CircleShape,
        color = onHero.copy(alpha = 0.12f),
        contentColor = onHero,
    ) {
        Text(
            arrow + " " + abs(pct) + "% " + label,
            style = MaterialTheme.typography.labelMediumEmphasized,
            modifier = Modifier.padding(horizontal = LumenSizes.s, vertical = LumenSizes.xxs),
        )
    }
}

/** 曲库规模与设备数：不与时间口径混排，避免主卡数字过载。 */
@Composable
private fun KpiRow(d: StatsDashboard) {
    Row(horizontalArrangement = Arrangement.spacedBy(LumenSizes.s)) {
        KpiCard(
            icon = YoinSymbols.MusicNote,
            label = L10n.tf("独立曲目", "Unique tracks"),
            value = d.tracks.toString(),
            modifier = Modifier.weight(1f),
        )
        KpiCard(
            icon = YoinSymbols.Devices,
            label = L10n.tf("客户端", "Clients"),
            value = d.clients.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KpiCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainer,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(LumenSizes.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(cs.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(LumenSizes.xs))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(LumenSizes.s))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = cs.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** 通用卡片：统一的 surfaceContainer / large 形状 / 16dp 内边距。 */
@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(LumenSizes.m)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = cs.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(LumenSizes.xxs))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(LumenSizes.s))
            content()
        }
    }
}

@Composable
private fun ActivityCard(
    d: StatsDashboard,
    ins: StatsInsights,
    days: Int,
    prevPlays: Int,
    prevSeconds: Long,
) {
    val cs = MaterialTheme.colorScheme
    val peak = ins.peakDay?.count ?: 0
    val series = remember(d.days) { dailyBarSeries(d.days) }
    SectionCard(
        title = L10n.tf("每日播放", "Daily plays"),
        subtitle = L10n.tf(
            rangeLabel(days) + " · 峰值 " + peak + " 次",
            rangeLabel(days) + " · peak " + peak,
        ),
    ) {
        if (d.days.isEmpty()) {
            Text(
                L10n.tf("暂无每日数据", "No daily data yet"),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        } else {
            DailyChart(series)
            Spacer(Modifier.height(LumenSizes.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    shortDate(d.days.firstOrNull()?.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
                if (series.daysPerBar > 1) {
                    Text(
                        L10n.tf(
                            "每根 ≈ " + series.daysPerBar + " 天",
                            "≈ " + series.daysPerBar + " d/bar",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                Text(
                    shortDate(d.days.lastOrNull()?.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        if (prevPlays >= 0) {
            Spacer(Modifier.height(LumenSizes.s))
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(LumenSizes.s))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    L10n.tf("上一周期", "Previous period"),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    L10n.tf(
                        prevPlays.toString() + " 次 · " + formatDuration(prevSeconds),
                        prevPlays.toString() + " plays · " + formatDuration(prevSeconds),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HabitsCard(ins: StatsInsights) {
    val cs = MaterialTheme.colorScheme
    val hasSample = ins.hourCounts.sum() > 0
    val peakHour = ins.hourCounts.indices.maxByOrNull { ins.hourCounts[it] } ?: 0
    SectionCard(
        title = L10n.tf("聆听习惯", "Listening habits"),
        subtitle = L10n.tf("按最近播放样本统计", "Based on recent plays"),
    ) {
        if (!hasSample) {
            Text(
                L10n.tf("还没有足够的播放记录", "Not enough history yet"),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        } else {
            Text(
                L10n.tf("按星期", "By weekday"),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(LumenSizes.xs))
            WeekdayBars(ins.weekday)
            Spacer(Modifier.height(LumenSizes.m))
            Text(
                L10n.tf("24 小时分布", "24-hour distribution"),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(LumenSizes.xs))
            HourHeatStrip(ins.hourCounts, peakHour)
        }
    }
}

@Composable
private fun RankingCard(
    d: StatsDashboard,
    board: Int,
    onBoard: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val rows = if (board == 0) {
        d.artists.take(6).map { it.name to it.count }
    } else {
        d.albums.take(6).map { (it.name + " · " + it.artist) to it.count }
    }
    SectionCard(
        title = L10n.tf("常听排行", "Top played"),
        subtitle = L10n.tf("按播放次数排序", "Ordered by plays"),
    ) {
        LumenSegmentedTabs(
            labels = listOf(L10n.tf("艺人", "Artists"), L10n.tf("专辑", "Albums")),
            selected = board,
            onSelect = onBoard,
            colors = statsSegmentedColors(),
        )
        Spacer(Modifier.height(LumenSizes.s))
        if (rows.isEmpty()) {
            Text(
                L10n.tf("暂无排行数据", "No ranking data yet"),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        } else {
            val max = rows.maxOf { it.second }.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(LumenSizes.xs)) {
                rows.forEachIndexed { i, row ->
                    RankRow(index = i, name = row.first, count = row.second, max = max)
                }
            }
        }
    }
}

@Composable
private fun RankRow(index: Int, name: String, count: Int, max: Int) {
    val cs = MaterialTheme.colorScheme
    val top = index < 3
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = if (top) cs.primaryContainer else cs.surfaceContainerHigh,
            contentColor = if (top) cs.onPrimaryContainer else cs.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    (index + 1).toString(),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                )
            }
        }
        Spacer(Modifier.width(LumenSizes.s))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(LumenSizes.xxs))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(cs.onSurface.copy(alpha = 0.07f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((count / max.toFloat()).coerceIn(0f, 1f))
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(cs.primary.copy(alpha = 0.8f)),
                )
            }
        }
        Spacer(Modifier.width(LumenSizes.s))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentPlaysCard(history: List<StatsHistory>) {
    val cs = MaterialTheme.colorScheme
    val shown = history.take(10)
    SectionCard(
        title = L10n.tf("最近播放", "Recently played"),
        subtitle = L10n.tf(
            "最近 " + shown.size + " 条",
            "Latest " + shown.size,
        ),
    ) {
        shown.forEachIndexed { i, h ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = LumenSizes.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = cs.surfaceContainerHigh,
                    contentColor = cs.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            YoinSymbols.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(LumenSizes.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        h.title,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        h.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(LumenSizes.s))
                Text(
                    shortTime(h.playedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
            if (i != shown.lastIndex) {
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

/** 每日柱状：合并后的柱子 + 底部基线；峰值柱用更实的渐变强调。 */
@Composable
private fun DailyChart(series: DailyBarSeries) {
    val cs = MaterialTheme.colorScheme
    val max = (series.counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val primary = cs.primary
    val track = cs.onSurface.copy(alpha = 0.06f)
    val baseline = cs.outlineVariant.copy(alpha = 0.45f)
    // drawWithCache：柱宽、圆角、每条柱子的渐变都只跟「尺寸 + 数据」有关。
    // 旧实现每次绘制都给每根柱子新建一个 Brush（最多 56 个/帧），滚动时会持续制造垃圾。
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .drawWithCache {
                val n = series.counts.size
                val gap = if (n > 1) (size.width / n * 0.28f).coerceAtMost(6.dp.toPx()) else 0f
                val barWidth = if (n > 0) ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f) else 0f
                val corner = minOf(barWidth / 2f, 4.dp.toPx())
                val radius = CornerRadius(corner, corner)
                val heights = series.counts.map { count ->
                    (size.height * (count / max.toFloat())).coerceAtLeast(2f)
                }
                val brushes = series.counts.mapIndexed { i, count ->
                    Brush.verticalGradient(
                        colors = listOf(primary, primary.copy(alpha = if (count == max) 0.8f else 0.5f)),
                        startY = size.height - heights[i],
                        endY = size.height,
                    )
                }
                onDrawBehind {
                    if (n == 0) return@onDrawBehind
                    series.counts.forEachIndexed { i, count ->
                        val x = i * (barWidth + gap)
                        drawRoundRect(
                            color = track,
                            topLeft = Offset(x, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = radius,
                        )
                        if (count > 0) {
                            drawRoundRect(
                                brush = brushes[i],
                                topLeft = Offset(x, size.height - heights[i]),
                                size = Size(barWidth, heights[i]),
                                cornerRadius = radius,
                            )
                        }
                    }
                    drawLine(
                        color = baseline,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            },
    )
}

@Composable
private fun WeekdayBars(values: IntArray) {
    val cs = MaterialTheme.colorScheme
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val labels = listOf(
        L10n.tf("日", "Sun"), L10n.tf("一", "Mon"), L10n.tf("二", "Tue"),
        L10n.tf("三", "Wed"), L10n.tf("四", "Thu"), L10n.tf("五", "Fri"),
        L10n.tf("六", "Sat"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(LumenSizes.xs)) {
        labels.forEachIndexed { i, label ->
            val v = values.getOrElse(i) { 0 }
            val active = v > 0 && v == max
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(cs.onSurface.copy(alpha = 0.06f)),
                ) {
                    if (v > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth((v / max.toFloat()).coerceIn(0f, 1f))
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(if (active) cs.primary else cs.primary.copy(alpha = 0.55f)),
                        )
                    }
                }
                Spacer(Modifier.width(LumenSizes.s))
                Text(
                    v.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) cs.onSurface else cs.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(28.dp),
                )
            }
        }
    }
}

@Composable
private fun HourHeatStrip(counts: IntArray, peakHour: Int) {
    val cs = MaterialTheme.colorScheme
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (h in 0 until 24) {
                val v = counts.getOrElse(h) { 0 }
                val alpha = if (v <= 0) 0.07f else (0.25f + 0.75f * (v / max.toFloat()))
                Box(
                    Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(cs.primary.copy(alpha = alpha.coerceIn(0.07f, 1f))),
                )
            }
        }
        Spacer(Modifier.height(LumenSizes.xxs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "23").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(LumenSizes.xs))
        Text(
            L10n.tf(
                "最常在 %02d:00 收听".format(peakHour),
                "Peak hour %02d:00".format(peakHour),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfigEditor(
    initial: StatsConfig,
    onCancel: () -> Unit,
    onSave: (StatsConfig) -> Unit,
) {
    val haptics = rememberLumenHaptics()
    var base by remember { mutableStateOf(initial.base.ifBlank { "http://" }) }
    var token by remember { mutableStateOf(initial.token) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = LumenSizes.screenH, vertical = LumenSizes.s),
        verticalArrangement = Arrangement.spacedBy(LumenSizes.m),
    ) {
        Text(
            L10n.tf("连接 navidrome-stat 服务", "Connect to navidrome-stat"),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            L10n.tf(
                "填入自建 navidrome-stat 的地址与 STATS_API_TOKEN。数据只在本机与你的服务之间传输。",
                "Enter your self-hosted navidrome-stat URL and STATS_API_TOKEN. Data only travels between this device and your server.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = base,
            onValueChange = { base = it },
            label = { Text(L10n.t("服务地址")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("STATS_API_TOKEN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                haptics.confirm()
                onSave(StatsConfig(base.trim(), token.trim()))
            },
            enabled = base.trim().startsWith("http") && token.isNotBlank(),
            shape = ButtonDefaults.shape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                L10n.t("保存并连接"),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
        if (initial.complete) {
            // 取消是次级动作：用描边按钮，避免和主色/次色两个大色块抢注意力
            OutlinedButton(
                onClick = {
                    haptics.tap()
                    onCancel()
                },
                shape = ButtonDefaults.shape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    L10n.t("取消"),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
        Spacer(Modifier.height(LumenSizes.contentBottom))
    }
}

/** 导出长图并拉起系统分享（原「分享长图」的全部行为）。 */
private fun shareStatsImage(
    ctx: android.content.Context,
    scope: CoroutineScope,
    d: StatsDashboard,
    ins: StatsInsights,
    days: Int,
    palette: StatsReport.Palette,
) {
    val range = rangeLabel(days)
    val labels = StatsReport.ReportLabels(
        plays = L10n.tf("播放次数", "Plays"),
        duration = L10n.tf("收听时长", "Listening time"),
        tracks = L10n.tf("独立曲目", "Unique tracks"),
        clients = L10n.tf("客户端", "Clients"),
        activity = L10n.tf("活跃趋势", "Activity trend"),
        avgLine = L10n.tf("日均 %d 次 · 活跃 %d 天 · 峰值 %d 次", "%d per day · %d active days · peak %d"),
        trendLine = L10n.tf("短期趋势 %+d%%", "Short-term trend %+d%%"),
        byWeekday = L10n.tf("按星期", "By weekday"),
        weekdays = listOf(
            L10n.tf("周日", "Sun"), L10n.tf("周一", "Mon"), L10n.tf("周二", "Tue"),
            L10n.tf("周三", "Wed"), L10n.tf("周四", "Thu"), L10n.tf("周五", "Fri"),
            L10n.tf("周六", "Sat"),
        ),
        byHour = L10n.tf("24 小时分布", "24-hour distribution"),
        topArtists = L10n.tf("常听艺人", "Top artists"),
        topAlbums = L10n.tf("常听专辑", "Top albums"),
        recent = L10n.tf("最近播放", "Recently played"),
        footer = "Lumen · navidrome-stat",
    )
    scope.launch {
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                val bmp = StatsReport.render(
                    d = d,
                    ins = ins,
                    rangeLabel = range,
                    palette = palette,
                    title = L10n.tf("聆听统计", "Listening stats"),
                    labels = labels,
                )
                val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
                val file = java.io.File(dir, "lumen-stats.png")
                java.io.FileOutputStream(file).use {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                // 只写应用私有缓存，经 FileProvider 分享：
                // 不再复制到公共相册（Pictures/Lumen）——收听画像不应进系统媒体库
                android.net.Uri.parse(
                    androidx.core.content.FileProvider.getUriForFile(
                        ctx,
                        ctx.packageName + ".fileprovider",
                        file,
                    ).toString(),
                )
            }.getOrNull()
        }
        if (saved != null) {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, saved)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                ctx.startActivity(
                    android.content.Intent.createChooser(intent, L10n.tf("分享统计", "Share stats")),
                )
            }
        }
    }
}

private fun rangeLabel(days: Int): String = when {
    days <= 0 -> L10n.tf("全部时间", "All time")
    days == 365 -> L10n.tf("近 1 年", "Last year")
    else -> L10n.tf("近 " + days + " 天", "Last " + days + " days")
}

private fun formatDuration(sec: Long): String {
    if (sec <= 0) return "0"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) h.toString() + "h " + m + "m" else m.toString() + "m"
}

private fun shortDate(raw: String?): String {
    val t = raw?.trim().orEmpty()
    return if (t.length >= 10) t.substring(5, 10) else t
}

private fun shortTime(raw: String): String {
    val t = parsePlayedAt(raw) ?: return ""
    return "%02d-%02d %02d:%02d".format(t.monthValue, t.dayOfMonth, t.hour, t.minute)
}
