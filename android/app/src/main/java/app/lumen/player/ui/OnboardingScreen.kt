package app.lumen.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.util.L10n
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class GuidePage(
    val tag: String,
    val title: String,
    val body: String,
    val bullets: List<String>,
)

/** 有机圆弧形，避免卡片太规整。 */
private class HeroBlob : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.02f)
            cubicTo(w * 0.44f, -h * 0.05f, w * 0.90f, h * 0.02f, w * 0.97f, h * 0.32f)
            cubicTo(w * 1.03f, h * 0.60f, w * 0.86f, h * 0.94f, w * 0.55f, h * 0.99f)
            cubicTo(w * 0.24f, h * 1.04f, w * 0.02f, h * 0.80f, w * 0.02f, h * 0.50f)
            cubicTo(w * 0.02f, h * 0.28f, w * 0.00f, h * 0.08f, w * 0.12f, h * 0.02f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        GuidePage(
            tag = L10n.tf("连接", "CONNECT"),
            title = L10n.tf("连接你的\n私有曲库", "Your library,\nyour rules"),
            body = L10n.tf(
                "Lumen 通过 Subsonic / OpenSubsonic 连接你自建的 Navidrome。音乐、账号与播放记录都只留在你的服务器和设备上。",
                "Lumen connects to your own Navidrome over Subsonic / OpenSubsonic. Music, accounts and history stay on your server and device.",
            ),
            bullets = listOf(
                L10n.tf("填写服务器地址", "Enter the server address"),
                L10n.t("输入用户名与密码"),
                L10n.tf("连接并同步曲库", "Connect and sync the library"),
            ),
        ),
        GuidePage(
            tag = L10n.tf("发现", "EXPLORE"),
            title = L10n.tf("把音乐\n找出来", "Find your\nnext track"),
            body = L10n.tf(
                "专辑、艺人、最近、收藏四个分区；搜索直接定位专辑和艺人。整张播放或单曲播放都由你决定。",
                "Albums, artists, recent and favorites are one swipe away. Search jumps straight to an album or artist, then play the whole thing or a single track.",
            ),
            bullets = listOf(
                L10n.tf("四栏切换，快速浏览", "Four tabs, one glance"),
                L10n.tf("点击专辑播放整张或单曲", "Play a whole album or one track"),
                L10n.tf("搜索专辑与艺人", "Search albums and artists"),
            ),
        ),
        GuidePage(
            tag = L10n.tf("播放", "PLAY"),
            title = L10n.tf("播放，\n不止播放", "More than\njust play"),
            body = L10n.tf(
                "波浪进度条可以拖动跳转，歌词逐行跟随；评分、收藏、下载与通知栏控制都在手边。",
                "Drag the wave to seek, follow synced lyrics line by line, and reach rating, favorites, downloads and notification controls without leaving the player.",
            ),
            bullets = listOf(
                L10n.tf("拖动进度条，点击歌词跳转", "Drag to seek, tap lyrics to jump"),
                L10n.tf("收藏、评分或下载当前曲目", "Favorite, rate or download a track"),
                L10n.tf("通知栏 / 锁屏遥控", "Notification and lock-screen controls"),
            ),
        ),
        GuidePage(
            tag = L10n.tf("个性化", "MAKE IT YOURS"),
            title = L10n.tf("让界面\n跟着音乐", "Let the UI\nmove with it"),
            body = L10n.tf(
                "封面决定主色，端侧心情分析决定色温与呼吸。切歌淡入淡出、多账号和语言都能在设置里调整。",
                "Cover art sets the primary color; on-device mood analysis sets the temperature and breathing. Crossfade, accounts and language live in Settings.",
            ),
            bullets = listOf(
                L10n.tf("封面取色与动态背景", "Cover colors and animated background"),
                L10n.tf("切歌淡入淡出可调", "Adjustable crossfade"),
                L10n.tf("账号、语言与数据管理", "Accounts, language and data"),
            ),
        ),
    )

    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage

    val accents = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary,
    )
    val accent by animateColorAsState(accents[page % accents.size], tween(420), label = "accent")

    fun goPrevious() {
        if (page > 0) scope.launch { pager.animateScrollToPage(page - 1) }
    }

    fun goNext() {
        if (page == pages.lastIndex) onDone()
        else scope.launch { pager.animateScrollToPage(page + 1) }
    }

    BackHandler {
        if (page > 0) goPrevious() else onDone()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            ExpressiveBackground(accent)

            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                OnboardingHeader(accent = accent, onSkip = onDone)

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val heroHeight = (maxHeight * 0.44f).coerceIn(170.dp, 280.dp)
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 12.dp,
                    ) { index ->
                        val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction)
                            .coerceIn(-2f, 2f)
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val a = abs(offset).coerceIn(0f, 1f)
                                    alpha = 1f - 0.35f * a
                                    scaleX = 0.90f + 0.10f * (1f - a)
                                    scaleY = 0.90f + 0.10f * (1f - a)
                                    translationY = 26.dp.toPx() * a
                                },
                        ) {
                            GuidePageContent(
                                page = pages[index],
                                index = index,
                                accent = accents[index % accents.size],
                                heroHeight = heroHeight,
                            )
                        }
                    }
                }

                OnboardingFooter(
                    page = page,
                    count = pages.size,
                    accent = accent,
                    onBack = { goPrevious() },
                    onNext = { goNext() },
                )
            }
        }
    }
}

@Composable
private fun OnboardingHeader(accent: Color, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "LUMEN",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                L10n.tf("首次使用指南", "GETTING STARTED"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            TextButton(onClick = onSkip, contentPadding = PaddingValues(horizontal = 16.dp)) {
                Text(
                    L10n.tf("跳过", "Skip"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun GuidePageContent(
    page: GuidePage,
    index: Int,
    accent: Color,
    heroHeight: Dp,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            HeroPanel(
                index = index,
                accent = accent,
                height = heroHeight,
            )
        }

        Surface(
            Modifier
                .offset(y = (-20).dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.18f),
                    ) {
                        Text(
                            "0${index + 1}",
                            color = accent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        page.tag,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Text(
                    page.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    page.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 9.dp),
                )

                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    page.bullets.forEachIndexed { i, bullet ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${i + 1}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                bullet,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun HeroPanel(index: Int, accent: Color, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .shadow(28.dp, HeroBlob(), clip = false)
            .clip(HeroBlob())
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accent,
                        accent.copy(alpha = 0.72f),
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1600f),
                ),
            ),
    ) {
        HeroArt(index = index, accent = accent)

        Row(
            Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                "0${index + 1} / 0${4}",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

@Composable
private fun HeroArt(index: Int, accent: Color) {
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val white = Color.White
        when (index) {
            0 -> {
                val cx = w * 0.68f
                val cy = h * 0.54f
                drawCircle(white.copy(alpha = 0.12f), radius = w * 0.30f, center = Offset(cx, cy))
                drawCircle(white.copy(alpha = 0.18f), radius = w * 0.20f, center = Offset(cx, cy))
                drawCircle(white.copy(alpha = 0.28f), radius = w * 0.11f, center = Offset(cx, cy))
                drawCircle(white, radius = 9.dp.toPx(), center = Offset(cx, cy))
                for (i in 0 until 8) {
                    val a = (i / 8f) * 2f * PI.toFloat()
                    val rr = w * 0.23f
                    drawCircle(
                        white.copy(alpha = if (i % 2 == 0) 0.75f else 0.42f),
                        radius = if (i % 2 == 0) 5.dp.toPx() else 3.dp.toPx(),
                        center = Offset(cx + cos(a) * rr, cy + sin(a) * rr),
                    )
                }
                drawLine(
                    white.copy(alpha = 0.35f),
                    Offset(w * 0.12f, h * 0.80f),
                    Offset(cx, cy),
                    strokeWidth = 2.dp.toPx(),
                )
                drawRoundRect(
                    white.copy(alpha = 0.55f),
                    topLeft = Offset(w * 0.10f, h * 0.70f),
                    size = Size(w * 0.16f, h * 0.10f),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }

            1 -> {
                for (i in 0 until 3) {
                    rotate(degrees = (i - 1) * 10f, pivot = Offset(w * 0.50f, h * 0.52f)) {
                        drawRoundRect(
                            white.copy(alpha = 0.16f + i * 0.08f),
                            topLeft = Offset(w * (0.20f + i * 0.14f), h * 0.24f + i * 8.dp.toPx()),
                            size = Size(w * 0.30f, h * 0.48f),
                            cornerRadius = CornerRadius(20.dp.toPx()),
                        )
                    }
                }
                drawCircle(tertiary.copy(alpha = 0.95f), radius = w * 0.13f, center = Offset(w * 0.72f, h * 0.33f))
                drawCircle(
                    white.copy(alpha = 0.9f),
                    radius = w * 0.13f,
                    center = Offset(w * 0.72f, h * 0.33f),
                    style = Stroke(width = 4.dp.toPx()),
                )
                drawLine(
                    white.copy(alpha = 0.9f),
                    Offset(w * 0.81f, h * 0.42f),
                    Offset(w * 0.88f, h * 0.51f),
                    strokeWidth = 6.dp.toPx(),
                )
            }

            2 -> {
                drawCircle(
                    white.copy(alpha = 0.16f),
                    radius = w * 0.26f,
                    center = Offset(w * 0.50f, h * 0.48f),
                )
                drawCircle(
                    white.copy(alpha = 0.9f),
                    radius = w * 0.20f,
                    center = Offset(w * 0.50f, h * 0.48f),
                )
                val tri = Path().apply {
                    moveTo(w * 0.46f, h * 0.38f)
                    lineTo(w * 0.46f, h * 0.58f)
                    lineTo(w * 0.62f, h * 0.48f)
                    close()
                }
                drawPath(tri, accent)
                for (i in 0 until 22) {
                    val x = w * 0.06f + i * (w * 0.88f / 21f)
                    val amp = 10.dp.toPx() + (i % 5) * 5.dp.toPx()
                    val top = h * 0.80f - amp
                    drawLine(
                        white.copy(alpha = 0.55f),
                        Offset(x, h * 0.80f),
                        Offset(x, top),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
            }

            else -> {
                drawCircle(white.copy(alpha = 0.16f), radius = w * 0.31f, center = Offset(w * 0.38f, h * 0.46f))
                drawCircle(
                    white.copy(alpha = 0.55f),
                    radius = w * 0.31f,
                    center = Offset(w * 0.38f, h * 0.46f),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawCircle(tertiary.copy(alpha = 0.72f), radius = w * 0.23f, center = Offset(w * 0.63f, h * 0.57f))
                drawCircle(
                    white.copy(alpha = 0.7f),
                    radius = w * 0.23f,
                    center = Offset(w * 0.63f, h * 0.57f),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawCircle(white.copy(alpha = 0.30f), radius = w * 0.17f, center = Offset(w * 0.53f, h * 0.41f))
                sparkle(Offset(w * 0.24f, h * 0.24f), w * 0.07f, white)
                sparkle(Offset(w * 0.78f, h * 0.28f), w * 0.05f, white.copy(alpha = 0.8f))
                sparkle(Offset(w * 0.70f, h * 0.76f), w * 0.04f, white.copy(alpha = 0.6f))
            }
        }
    }
}

private fun DrawScope.sparkle(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticBezierTo(
            center.x + radius * 0.18f,
            center.y - radius * 0.18f,
            center.x + radius,
            center.y,
        )
        quadraticBezierTo(
            center.x + radius * 0.18f,
            center.y + radius * 0.18f,
            center.x,
            center.y + radius,
        )
        quadraticBezierTo(
            center.x - radius * 0.18f,
            center.y + radius * 0.18f,
            center.x - radius,
            center.y,
        )
        quadraticBezierTo(
            center.x - radius * 0.18f,
            center.y - radius * 0.18f,
            center.x,
            center.y - radius,
        )
        close()
    }
    drawPath(path, color)
}

@Composable
private fun ExpressiveBackground(accent: Color) {
    val surface = MaterialTheme.colorScheme.surface
    val tertiary = MaterialTheme.colorScheme.tertiary
    val transition = rememberInfiniteTransition(label = "guide-bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(surface)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(w * (0.15f + 0.12f * t), h * (0.18f + 0.05f * t)),
                radius = w * 0.85f,
            ),
            radius = w * 0.85f,
            center = Offset(w * (0.15f + 0.12f * t), h * (0.18f + 0.05f * t)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tertiary.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(w * (0.90f - 0.10f * t), h * (0.75f - 0.08f * t)),
                radius = w * 0.70f,
            ),
            radius = w * 0.70f,
            center = Offset(w * (0.90f - 0.10f * t), h * (0.75f - 0.08f * t)),
        )
    }
}

@Composable
private fun OnboardingFooter(
    page: Int,
    count: Int,
    accent: Color,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(count) { i ->
                val active = i == page
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) accent
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                        ),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page > 0) {
                OutlinedButton(
                    onClick = onBack,
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(54.dp),
                ) {
                    Text("←", fontSize = 20.sp)
                }
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    if (page == count - 1) L10n.tf("立即体验", "Get started") else L10n.tf("下一步", "Next"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text("→", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
