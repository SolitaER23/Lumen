package app.lumen.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalPageScope = staticCompositionLocalOf<AnimatedContentScope?> { null }

/**
 * 共享元素宿主。
 *
 * **为什么要在页面之外再包一层**：迷你条是常驻浮层（不是 AnimatedContent 的页面），
 * 它必须在同一个 SharedTransitionLayout 里，才能和播放页的封面互相 morph。
 * 页面切换由 [SharedPageSwitcher] 负责，两者共享同一个作用域。
 */
@Composable
fun SharedElementHost(
    modifier: Modifier = Modifier,
    content: @Composable SharedTransitionScope.() -> Unit,
) {
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalSharedScope provides this) {
            content()
        }
    }
}

/** 页面切换：只做 AnimatedContent 与页面级 scope；共享作用域由 [SharedElementHost] 提供。 */
@Composable
fun SharedPageSwitcher(
    target: Any,
    transitionSpec: AnimatedContentTransitionScope<Any>.() -> ContentTransform,
    content: @Composable AnimatedContentScope.(Any) -> Unit,
) {
    AnimatedContent(
        targetState = target,
        transitionSpec = transitionSpec,
        label = "page",
    ) { key ->
        CompositionLocalProvider(LocalPageScope provides this) {
            content(key)
        }
    }
}

/**
 * 封面飞行的运动曲线：tween + 强减速，不用 spring。
 *
 * 默认的 DefaultBoundsTransform 是 spring（StiffnessMediumLow），会轻微过冲再回弹，
 * 视觉上就是"落地后又跳一下"。这里换成单调收敛的曲线，保证只前进、不回头。
 */
@Composable
private fun rememberCoverBoundsTransform(): BoundsTransform {
    val spatial = androidx.compose.material3.MaterialTheme
        .motionScheme.defaultSpatialSpec<Rect>()
    return remember(spatial) { BoundsTransform { _, _ -> spatial } }
}

/**
 * 让封面参与共享元素转场。
 *
 * @param key 两端必须完全一致（迷你条 / 播放页都用 `np-<albumId>`）
 * @param scope 元素所在的 AnimatedVisibilityScope：页面内传 null 表示用当前页面作用域；
 *              页面之外的浮层（迷你条）必须显式传它自己的 scope。
 */
@Composable
fun Modifier.coverSharedElement(key: String, scope: AnimatedVisibilityScope?): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val visibilityScope = scope ?: LocalPageScope.current ?: return this
    val boundsTransform = rememberCoverBoundsTransform()
    return with(shared) {
        this@coverSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = boundsTransform,
        )
    }
}

/** 页面内（列表 / 详情 / 播放页）的封面入口。 */
@Composable
fun Modifier.albumSharedElement(key: String): Modifier =
    coverSharedElement(key, scope = null)
