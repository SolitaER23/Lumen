package app.lumen.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** 封面切换：新旧封面交叉溶解 + 轻微景深缩放，播放页与迷你条共用。 */
@Composable
internal fun AnimatedCover(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    /** 用于查四边采样色；为空时回退到主题主色 */
    albumId: String? = null,
    /** 共享元素 key：播放页传 `np-<albumId>`，与迷你条封面配对 */
    sharedKey: String? = null,
    /** 弱网降级：先展示这张低清图，高清到达后自动覆盖 */
    lowResUrl: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val edges = albumId?.let { app.lumen.player.data.CoverEdgeStore.get(it) }
    var current by remember { mutableStateOf(url) }
    var previous by remember { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(url) {
        if (url.isNotBlank() && url != current) {
            previous = current
            current = url
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            )
            previous = null
        }
    }

    LumooCover(
        modifier = if (sharedKey != null) modifier.albumSharedElement(sharedKey) else modifier,
        albumId = albumId,
        cornerRadius = cornerRadius,
        elevation = 20.dp,
        scrimFraction = 0.30f,
    ) {
        // 低清占位：通常已在列表缓存里，弱网下也能立刻看到画面
        if (!lowResUrl.isNullOrBlank() && lowResUrl != current) {
            AsyncImage(
                model = lowResUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        previous?.let { old ->
            AsyncImage(
                model = old,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (1f - progress.value).coerceIn(0f, 1f)
                        val s = 1f + 0.06f * progress.value
                        scaleX = s
                        scaleY = s
                    },
            )
        }
        AsyncImage(
            model = current,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = progress.value.coerceIn(0f, 1f)
                    val s = 0.94f + 0.06f * progress.value
                    scaleX = s
                    scaleY = s
                },
        )
    }
}
