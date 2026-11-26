package app.lumen.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.StatsClient
import app.lumen.player.data.StatsConfig
import app.lumen.player.data.StatsDashboard
import app.lumen.player.data.StatsStore
import app.lumen.player.data.insights
import app.lumen.player.data.parsePlayedAt
import app.lumen.player.util.L10n
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 聆听统计（数据源：自建 navidrome-stat /api/stats/dashboard）。
 *
 * 信息层级：
 * 1. 关键指标（播放/时长/曲目/客户端）—— 一眼看总量与短趋势
 * 2. 活跃趋势 —— 每日播放柱状 + 日均/峰值/活跃天数
 * 3. 分类汇总 —— 星期分布 / 时段分布
 * 4. 排行榜 —— 艺人、专辑（带占比条）
 * 5. 汇总指标 —— 曲目数与总时长
 * 6. 最近播放
 */
@Composable
fun StatsScreen(
    initial: StatsConfig,
    onBack: () -> Unit,
) {
    // 页面内返回按钮 / 系统返回统一走同一入口
    androidx.activity.compose.BackHandler { onBack() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val reportPalette = app.lumen.player.data.StatsReport.Palette(
        backgroundTop = cs.surface.toArgbInt(),
        backgroundBottom = cs.surfaceContainerLow.toArgbInt(),
        card = cs.surfaceContainer.toArgbInt(),
        track = cs.surfaceContainerHighest.toArgbInt(),
        textPrimary = cs.onSurface.toArgbInt(),
        textSecondary = cs.onSurfaceVariant.toArgbInt(),
        accent = cs.primary.toArgbInt(),
    )
    val accentColor = MaterialTheme.colorScheme.primary
    val accentArgb = android.graphics.Color.rgb(
        (accentColor.red * 255).toInt(),
        (accentColor.green * 255).toInt(),
        (accentColor.blue * 255).toInt(),
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
    var prevSeconds by remember { androidx.compose.runtime.mutableLongStateOf(-1L) }

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

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                L10n.tf("聆听统计", "Listening stats"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (cfg.complete) {
                Text(
                    L10n.tf("分享长图", "Share image"),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable {
                            val d = data ?: return@clickable
                            val ins = d.insights()
                            val accent = accentArgb
                            val range = when {
                                days <= 0 -> L10n.tf("全部时间", "All time")
                                days == 365 -> L10n.tf("近 1 年", "Last year")
                                else -> L10n.tf("近 $days 天", "Last $days days")
                            }
                            val labels = app.lumen.player.data.StatsReport.ReportLabels(
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
                                        val bmp = app.lumen.player.data.StatsReport.render(
                                            d = d,
                                            ins = ins,
                                            rangeLabel = range,
                                            palette = reportPalette,
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
                        }                )
                IconButton(onClick = { reload++ }) {
                    Icon(YoinSymbols.Refresh, contentDescription = L10n.t("刷新"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editing = true }) {
                    Icon(YoinSymbols.Settings, contentDescription = L10n.t("配置"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (editing) {
            ConfigEditor(
                initial = cfg,
                onCancel = { if (cfg.complete) editing = false },
                onSave = {
                    // 存不下（Keystore 不可用）时保持弹窗打开并提示，绝不退回明文
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
            return@Column
        }

        when {
            loading && data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && data == null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { reload++ }) { Text(L10n.t("重试")) }
            }
            else -> {
                val d = data
                if (d == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }
                val ins = d.insights()
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        RangeChips(days) { days = it }
                    }
                    item {
                        KpiGrid(d)
                    }
                    item {
                        SectionCard(L10n.tf("活跃趋势", "Activity trend")) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        L10n.tf("日均 ${ins.avgPerDay} 次", "${ins.avgPerDay} / day"),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        L10n.tf(
                                            "活跃 ${ins.activeDays} 天 · 峰值 ${ins.peakDay?.count ?: 0} 次",
                                            "${ins.activeDays} active days · peak ${ins.peakDay?.count ?: 0}",
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                    )
                                    if (prevPlays >= 0) {
                                        val delta = if (prevPlays == 0) 0
                                        else ((d.plays - prevPlays) * 100.0 / prevPlays).toInt()
                                        Text(
                                            L10n.tf(
                                                "上一周期 $prevPlays 次 · ${formatDuration(prevSeconds)}（${if (delta >= 0) "+" else ""}$delta%）",
                                                "Previous $prevPlays plays · ${formatDuration(prevSeconds)} (${if (delta >= 0) "+" else ""}$delta%)",
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                                TrendBadge(ins.trendPct)
                            }
                            Spacer(Modifier.height(12.dp))
                            DailyChart(d.days)
                        }
                    }
                    item {
                        SectionCard(L10n.tf("分类汇总", "Breakdown")) {
                            Text(
                                L10n.tf("按星期", "By weekday"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            DistributionBars(
                                labels = listOf(
                                    L10n.tf("日", "Sun"), L10n.tf("一", "Mon"), L10n.tf("二", "Tue"),
                                    L10n.tf("三", "Wed"), L10n.tf("四", "Thu"), L10n.tf("五", "Fri"),
                                    L10n.tf("六", "Sat"),
                                ),
                                values = ins.weekday.toList(),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                L10n.tf("24 小时分布", "24-hour distribution"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            HourHeatStrip(ins.hourCounts)
                        }
                    }
                    item {
                        SectionCard(L10n.tf("排行榜", "Leaderboard")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(L10n.tf("艺人", "Artists"), L10n.tf("专辑", "Albums")).forEachIndexed { i, label ->
                                    val active = board == i
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(
                                                if (active) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            )
                                            .clickable { board = i }
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            label,
                                            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (board == 0) {
                                RankList(
                                    title = L10n.tf("常听艺人", "Top artists"),
                                    rows = d.artists.take(6).map { it.name to it.count },
                                )
                            } else {
                                RankList(
                                    title = L10n.tf("常听专辑", "Top albums"),
                                    rows = d.albums.take(6).map { (it.name + " · " + it.artist) to it.count },
                                )
                            }
                        }
                    }
                    item {
                        SectionCard(L10n.tf("曲库概览", "Library overview")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MiniStat(
                                    L10n.tf("统计曲目", "Tracks"),
                                    "${d.tracks}",
                                    Modifier.weight(1f),
                                )
                                MiniStat(
                                    L10n.tf("时长", "Time"),
                                    formatDuration(d.listenSeconds),
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (d.history.isNotEmpty()) {
                        item {
                            Text(
                                L10n.tf("最近播放", "Recently played"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(d.history.take(12), key = { it.title + it.playedAt }) { h ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        h.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        h.artist,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    shortTime(h.playedAt),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeChips(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(7, 30, 90, 365, 0)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { d ->
            val active = d == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                    )
                    .clickable { onSelect(d) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    when (d) {
                        0 -> L10n.tf("全部", "All")
                        365 -> L10n.tf("1 年", "1y")
                        else -> L10n.tf("$d 天", "$d d")
                    },
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun KpiGrid(d: StatsDashboard) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(L10n.tf("播放次数", "Plays"), "${d.plays}", Modifier.weight(1f))
            KpiCard(L10n.tf("收听时长", "Listening"), formatDuration(d.listenSeconds), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(L10n.tf("独立曲目", "Unique tracks"), "${d.tracks}", Modifier.weight(1f))
            KpiCard(L10n.tf("客户端", "Clients"), "${d.clients}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun TrendBadge(pct: Int) {
    val up = pct >= 0
    val color = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            if (up) "↑ $pct%" else "↓ ${-pct}%",
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DailyChart(days: List<app.lumen.player.data.StatsDay>) {
    val max = (days.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        if (days.isEmpty()) return@Canvas
        val gap = size.width * 0.02f / days.size
        val bw = (size.width - gap * (days.size - 1)) / days.size
        days.forEachIndexed { i, day ->
            val h = size.height * (day.count / max.toFloat())
            val x = i * (bw + gap)
            drawRoundRect(
                color = track,
                topLeft = Offset(x, 0f),
                size = Size(bw, size.height),
                cornerRadius = CornerRadius(bw / 2, bw / 2),
            )
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, size.height - h),
                size = Size(bw, h.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(bw / 2, bw / 2),
            )
        }
    }
}

/** 24 小时热力条：每格一小时，透明度表示播放量。 */
@Composable
private fun HourHeatStrip(counts: IntArray) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val base = MaterialTheme.colorScheme.primary
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (h in 0 until 24) {
                val v = counts.getOrElse(h) { 0 }
                val alpha = (0.08f + 0.92f * (v / max.toFloat())).coerceIn(0.08f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(base.copy(alpha = alpha)),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "23").forEach {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun DistributionBars(labels: List<String>, values: List<Int>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val v = values.getOrElse(i) { 0 }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.width(34.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((v / max.toFloat()).coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("$v", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun RankList(title: String, rows: List<Pair<String, Int>>) {
    if (rows.isEmpty()) return
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        rows.forEachIndexed { i, (name, count) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${i + 1}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.width(18.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((count / max.toFloat()).coerceIn(0f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ConfigEditor(
    initial: StatsConfig,
    onCancel: () -> Unit,
    onSave: (StatsConfig) -> Unit,
) {
    var base by remember { mutableStateOf(initial.base.ifBlank { "http://" }) }
    var token by remember { mutableStateOf(initial.token) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            L10n.tf("连接 navidrome-stat 服务", "Connect to navidrome-stat"),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            L10n.tf(
                "填入自建 navidrome-stat 的地址与 STATS_API_TOKEN。数据只在本机与你的服务之间传输。",
                "Enter your self-hosted navidrome-stat URL and STATS_API_TOKEN. Data only travels between this device and your server.",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp,
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
            onClick = { onSave(StatsConfig(base.trim(), token.trim())) },
            enabled = base.trim().startsWith("http") && token.isNotBlank(),
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text(L10n.t("保存并加载")) }
        if (initial.complete) {
            Button(
                onClick = onCancel,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text(L10n.t("取消")) }
        }
    }
}

private fun formatDuration(sec: Long): String {
    if (sec <= 0) return "0"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun shortTime(raw: String): String {
    val t = parsePlayedAt(raw) ?: return ""
    return "%02d-%02d %02d:%02d".format(t.monthValue, t.dayOfMonth, t.hour, t.minute)
}


/** Compose Color → ARGB Int（导出长图用）。 */
private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
