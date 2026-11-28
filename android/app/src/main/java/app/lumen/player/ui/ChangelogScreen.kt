package app.lumen.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.util.L10n
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    // 页面内返回按钮 / 系统返回统一走同一入口
    androidx.activity.compose.BackHandler { onBack() }
    val ctx = LocalContext.current
    val haptics = rememberLumenHaptics()
    val text by produceState("", Unit) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ctx.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
    }
    // CHANGELOG 有 ~150KB：split / indexOfFirst / drop 每次重组都跑一遍很浪费，按文本缓存
    val lines = androidx.compose.runtime.remember(text) {
        val all = text.lines()
        val firstVersion = all.indexOfFirst { it.startsWith("## [") }
        if (firstVersion > 0) all.drop(firstVersion) else all
    }
    fun clean(value: String): String = value.replace("**", "").replace("`", "")

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        haptics.tap()
                        onBack()
                    },
                ) {
                    Icon(YoinSymbols.Back, contentDescription = L10n.t("返回"), tint = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text(
                        L10n.tf("更新日志", "Changelog"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                    )
                    Text(
                        L10n.t("版本记录"),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        items(count = lines.size, contentType = { "line" }) { index ->
            val line = lines[index]
            when {
                line.startsWith("# ") -> Text(
                    clean(line.removePrefix("# ").trim()),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )

                line.startsWith("## ") -> Text(
                    clean(line.removePrefix("## ").trim()),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )

                line.startsWith("### ") -> Text(
                    clean(line.removePrefix("### ").trim()),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )

                line.startsWith("- ") -> Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        clean(line.removePrefix("- ").trim()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }

                line.startsWith("```") || line.startsWith("|") || line.startsWith("---") -> Unit

                line.isBlank() -> Spacer(Modifier.padding(top = 4.dp))

                else -> Text(
                    clean(line),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }

        if (text.isBlank()) {
            item {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            L10n.tf("更新日志暂时不可用", "Changelog unavailable"),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
