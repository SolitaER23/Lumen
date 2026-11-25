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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.MoodStore
import androidx.compose.foundation.layout.statusBarsPadding

/**
 * 「已分析曲目」独立页面：列出每首已分析歌曲，并把分析出的项目翻成中文。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AnalyzedTracksScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf<List<MoodStore.AnalyzedRow>>(emptyList()) }
    var titles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var summary by remember { mutableStateOf<MoodStore.MoodStats?>(null) }
    LaunchedEffect(Unit) {
        rows = MoodStore.analyzedRows(ctx, 500)
        summary = MoodStore.stats(ctx)
        titles = runCatching {
            app.lumen.player.playback.MoodAnalysisRunner.cachedTracks(ctx)
                .associate { it.id to (it.title + " · " + it.artist) }
        }.getOrDefault(emptyMap())
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    // 与首页一致的 Expressive 背景（铺满并置于底层）
    ExpressiveBackdrop(Modifier.matchParentSize())
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, top = 14.dp, end = 18.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "←",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClick = onBack).padding(10.dp),
            )
            Text(
                "已分析曲目",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        summary?.let { s ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "共 " + s.rows + " 首，其中 " + (s.startsDistribution[5] ?: 0) + " 首已达到 5 段精细分析",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "能量包络 " + s.withEnv + " 首 · 5 段窗口 " + (s.startsDistribution[5] ?: 0) + " 首",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有分析数据",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows) { r ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                titles[r.trackId] ?: r.trackId,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Chip(r.windows.toString() + " 段采样", true)
                                if (r.label.isNotEmpty()) Chip(r.label, false)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                buildString {
                                    append("能量轮廓："); append(if (r.hasEnv) "有" else "无")
                                    append("   ·   音色向量："); append(if (r.hasVec) "有" else "无")
                                    append("   ·   拍点："); append(if (r.hasBeats) "有" else "无")
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                buildString {
                                    append("逐段速度："); append(if (r.hasTempo) "有" else "无")
                                    append("   ·   分段情绪："); append(if (r.hasVa) "有" else "无")
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun Chip(text: String, primary: Boolean) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (primary) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            fontSize = 10.sp,
            color = if (primary) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
