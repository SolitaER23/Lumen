package app.lumen.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.BoundsTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalPageScope = staticCompositionLocalOf<AnimatedContentScope?> { null }

/**
 * 页面宿主：SharedTransitionLayout + AnimatedContent。
 * 所有页面都在同一个共享元素作用域里，封面可以真正 morph，
 * 而不是用矩形缩放/位移"模拟"容器变换。
 */
@Composable
fun SharedPageHost(
    target: Any,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<Any>.() -> ContentTransform,
    content: @Composable AnimatedContentScope.(Any) -> Unit,
) {
    SharedTransitionLayout(modifier = modifier) {
        val shared = this
        AnimatedContent(
            targetState = target,
            transitionSpec = transitionSpec,
            label = "page",
            modifier = Modifier,
        ) { key ->
            CompositionLocalProvider(
                LocalSharedScope provides shared,
                LocalPageScope provides this,
            ) {
                content(key)
            }
        }
    }
}

/**
 * 封面飞行的运动曲线：tween + 强减速，不用 spring。
 *
 * 默认的 DefaultBoundsTransform 是 spring（StiffnessMediumLow），会轻微过冲再回弹，
 * 视觉上就是"落地后又跳一下"。这里换成单调收敛的曲线，保证只前进、不回头。
 */

/** 给元素挂共享 key（不在页面宿主内时是空操作）。 */
@Composable
fun Modifier.albumSharedElement(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val page = LocalPageScope.current ?: return this
    // MD3 Expressive：空间动效取官方 motionScheme 的默认空间规格（克制、无回弹）
    val spatial = androidx.compose.material3.MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.geometry.Rect>()
    val boundsTransform = androidx.compose.runtime.remember(spatial) {
        BoundsTransform { _, _ -> spatial }
    }
    return with(shared) {
        this@albumSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = page,
            boundsTransform = boundsTransform,
        )
    }
}
