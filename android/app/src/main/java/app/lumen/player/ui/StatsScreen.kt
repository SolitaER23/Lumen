package app.lumen.player.ui

import app.lumen.player.util.L10n
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.StatsClient
import app.lumen.player.data.StatsConfig
import app.lumen.player.data.StatsDashboard
import app.lumen.player.data.StatsStore
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 聆听统计：数据来自自建的 `navidrome-stat` 服务
 * （`GET /api/stats/dashboard`，Bearer token 认证）。
 */
@Composable
fun StatsScreen(
    initial: StatsConfig,
    onBack: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var cfg by remember { mutableStateOf(initial) }
    var editing by remember { mutableStateOf(!initial.complete) }
    var days by remember { mutableIntStateOf(30) }
    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<StatsDashboard?>(null) }

    LaunchedEffect(cfg, days, reload) {
        if (!cfg.complete) return@LaunchedEffect
        loading = true
        error = null
        try {
            data = withContext(Dispatchers.IO) { StatsClient(cfg).dashboard(days) }
        } catch (e: Exception) {
            error = e.message ?: L10n.t("请求失败")
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(L10n.t("统计"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(
                L10n.t("刷新"),
                color = if (cfg.complete && !loading) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(enabled = cfg.complete && !loading) { reload++ }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            IconButton(onClick = { editing = !editing }) {
                Icon(YoinSymbols.Settings, contentDescription = L10n.t("设置"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (editing || !cfg.complete) {
            ConfigForm(
                cfg = cfg,
                onSave = { base, token ->
                    val next = StatsConfig(base.trim(), token.trim())
                    StatsStore.save(ctx, next)
                    cfg = next
                    editing = false
                },
            )
            return@Column
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Text(
                "navidrome-stat",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                cfg.base,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            PeriodRow(days) { days = it }
            Spacer(Modifier.height(8.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Column(
                    Modifier.fillMaxSize().padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { editing = true }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(L10n.t("修改连接"), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                }
                data != null -> StatsContent(data!!)
            }
        }
    }
}

@Composable
private fun ConfigForm(cfg: StatsConfig, onSave: (String, String) -> Unit) {
    var base by remember(cfg) { mutableStateOf(cfg.base.ifBlank { "http://" }) }
    var token by remember(cfg) { mutableStateOf(cfg.token) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            L10n.t("连接统计服务"),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            L10n.t("填写自建的 navidrome-stat 地址（例：http://192.0.2.10:39421）与部署时设置的 STATS_API_TOKEN。"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = base,
            onValueChange = { base = it },
            label = { Text(L10n.t("服务地址")) },
            placeholder = { Text("http://192.0.2.10:39421") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("STATS_API_TOKEN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        var testResult by remember { mutableStateOf<String?>(null) }
        var testing by remember { mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = base.startsWith("http") && token.isNotBlank()) {
                    onSave(base, token)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(L10n.t("保存并连接"), color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = !testing && base.startsWith("http") && token.isNotBlank()) {
                    testing = true
                    testResult = null
                    scope.launch {
                        testResult = try {
                            kotlinx.coroutines.withContext(Dispatchers.IO) {
                                StatsClient(StatsConfig(base.trim(), token.trim())).dashboard(0)
                            }
                            L10n.t("连接正常")
                        } catch (e: Exception) {
                            e.message ?: L10n.t("连接失败")
                        }
                        testing = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (testing) L10n.t("测试中…") else L10n.t("测试连接"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
            )
        }
        testResult?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (cfg.base.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                L10n.t("清除已保存的配置"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onSave("", "") },
            )
        }
    }
}

@Composable
private fun PeriodRow(days: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7 to L10n.t("7 天"), 30 to L10n.t("30 天"), 0 to L10n.t("全部")).forEach { (value, label) ->
            val active = days == value
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { onChange(value) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun StatsContent(d: StatsDashboard) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard(L10n.t("播放"), "${d.plays}", Modifier.weight(1f))
                SummaryCard(L10n.t("时长"), formatHours(d.listenSeconds), Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard(L10n.t("曲目"), "${d.tracks}", Modifier.weight(1f))
                SummaryCard(L10n.t("客户端"), "${d.clients}", Modifier.weight(1f))
            }
        }
        if (d.days.isNotEmpty()) {
            item { SectionLabel(L10n.t("每日播放")) }
            item { DailyChart(d.days) }
        }
        if (d.artists.isNotEmpty()) {
            item { SectionLabel(L10n.t("最常听艺人")) }
            items(d.artists.take(10)) { artist ->
                StatRow(title = artist.name, subtitle = null, trailing = L10n.tf("${artist.count} 次", "${artist.count} plays"))
            }
        }
        if (d.albums.isNotEmpty()) {
            item { SectionLabel(L10n.t("最常听专辑")) }
            items(d.albums.take(10)) { album ->
                StatRow(title = album.name, subtitle = album.artist, trailing = L10n.tf("${album.count} 次", "${album.count} plays"))
            }
        }
        if (d.history.isNotEmpty()) {
            item { SectionLabel(L10n.t("最近播放")) }
            items(d.history.take(20)) { h ->
                StatRow(
                    title = h.title,
                    subtitle = listOfNotNull(
                        h.artist.takeIf { it.isNotBlank() },
                        h.album.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    trailing = h.playedAt.take(16).replace('T', ' '),
                )
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun StatRow(title: String, subtitle: String?, trailing: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title.ifBlank { "—" },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun DailyChart(days: List<app.lumen.player.data.StatsDay>) {
    val shown = days.takeLast(14)
    val max = shown.maxOf { it.count }.coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val baseColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(vertical = 6.dp),
    ) {
        val n = shown.size.coerceAtLeast(1)
        val gap = size.width / n * 0.25f
        val barW = size.width / n - gap
        shown.forEachIndexed { i, d ->
            val h = size.height * (d.count.toFloat() / max.toFloat())
            val left = i * (barW + gap) + gap / 2
            drawRoundRect(
                color = baseColor,
                topLeft = Offset(left, 0f),
                size = androidx.compose.ui.geometry.Size(barW, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 3f),
            )
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, size.height - h),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 3f),
            )
        }
    }
}

private fun formatHours(seconds: Long): String {
    val h = seconds / 3600.0
    return if (h >= 1) "%.1f h".format(h) else "%d m".format(seconds / 60)
}


