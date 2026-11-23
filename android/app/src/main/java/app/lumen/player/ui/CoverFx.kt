package app.lumen.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lumoo 封面质感（首页 / 播放页专用）。
 *
 * 不再羽化封面像素，改用 Material 3 Expressive 的"实体卡片"语言：
 * 1. 圆角 + **彩色阴影**：用封面主色做 tinted shadow，让唱片从背景里"浮"起来，层次明确；
 * 2. **环境光晕**：卡片外一圈极淡的主色径向光，柔和过渡到页面背景，不切边；
 * 3. **顶部高光**：模拟受光面，给封面一点玻璃光泽；
 * 4. **底部渐隐**：底部 34% 渐变到页面底色，让卡片与页面自然衔接，同时保持上/左/右三边锐利；
 * 5. **边缘描边**：0.6dp 的明暗描边提升清晰度，避免深色封面糊在背景里。
 *
 * 颜色全部取实时主题（surface / primary），切歌、换主题、深浅色、AMOLED 都会跟着变。
 */
@Composable
fun LumooCover(
    modifier: Modifier = Modifier,
    albumId: String? = null,
    cornerRadius: Dp = 22.dp,
    elevation: Dp = 16.dp,
    scrimFraction: Float = 0.34f,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(cornerRadius)
    val glow = scheme.primary

    Box(
        modifier = modifier.drawBehind {
            // 环境光晕：比卡片略大，向外淡出，避免生硬边缘
            val r = size.maxDimension * 0.78f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to glow.copy(alpha = 0.22f),
                    0.55f to glow.copy(alpha = 0.10f),
                    1f to Color.Transparent,
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(2.dp)
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    ambientColor = glow.copy(alpha = 0.55f),
                    spotColor = glow.copy(alpha = 0.75f),
                )
                .clip(shape),
        ) {
            content()

            // 顶部受光
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.45f)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.16f),
                            1f to Color.Transparent,
                        ),
                    ),
            )

            // 底部融入页面底色
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(scrimFraction)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to scheme.surface.copy(alpha = 0.62f),
                        ),
                    ),
            )

            // 细描边：上亮下暗，提升清晰度
            Box(
                Modifier
                    .fillMaxSize()
                    .border(
                        width = 0.6.dp,
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.28f),
                            0.5f to Color.Transparent,
                            1f to scheme.surface.copy(alpha = 0.28f),
                        ),
                        shape = shape,
                    ),
            )
        }
    }
}
