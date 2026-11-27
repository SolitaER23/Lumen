package app.lumen.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalPageScope = staticCompositionLocalOf<AnimatedContentScope?> { null }

/** 封面共享元素 key：首页卡片 / 专辑页 hero / 播放页封面三处必须完全一致。 */
private const val ALBUM_KEY_PREFIX = "lumen-cover-"
private const val MINI_KEY_PREFIX = "lumen-mini-"

fun albumCoverKey(albumId: String): String = ALBUM_KEY_PREFIX + albumId

/** 迷你条封面的共享 key。与播放页配对时用（从迷你条展开播放页）。 */
fun miniBarCoverKey(albumId: String): String = MINI_KEY_PREFIX + albumId

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
 * 容器/封面的空间动效曲线：直接用 M3 Expressive 的 spatial spring。
 *
 * 不用默认的 `DefaultBoundsTransform`（也是 spring，但规格和 Expressive 主题不一致），
 * 这样"封面飞行"和按钮、开关、页面转场用的是同一套物理，手感统一。
 */
@Composable
private fun rememberCoverBoundsTransform(): BoundsTransform {
    val spatial: FiniteAnimationSpec<Rect> = MaterialTheme.motionScheme.slowSpatialSpec()
    return remember(spatial) { BoundsTransform { _, _ -> spatial } }
}

/**
 * 封面在转场 overlay 里绘制，而 overlay 位于整页之上——既盖过固定顶栏，也盖过底部迷你播放条。
 *
 * - 来源卡片被滚到固定顶栏下面时，布局边界会伸进顶栏/状态栏：入场封面会从状态栏里钻出来；
 * - 来源卡片贴着迷你条时，封面下缘有一截在迷你条后面：飞行的封面会盖住迷你条。
 *
 * 这个裁剪把 overlay 里的封面限制在两条常驻栏之间的可见区域：
 * 入场时从栏后滑出，返回时滑回栏后被自然盖住。
 */
private class BetweenBarsClip(
    private val topPx: () -> Float,
    private val bottomPx: () -> Float,
) : SharedTransitionScope.OverlayClip {
    private val path = Path()
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path? {
        val top = topPx()
        val bottom = bottomPx()
        if (top <= 0f && bottom <= 0f) return null
        val clipTop = if (top > 0f) maxOf(bounds.top, top) else bounds.top
        val clipBottom = if (bottom > 0f) minOf(bounds.bottom, bottom) else bounds.bottom
        if (clipTop <= bounds.top + 0.5f && clipBottom >= bounds.bottom - 0.5f) return null
        path.rewind()
        if (clipBottom <= clipTop) {
            path.addRect(Rect.Zero)
        } else {
            path.addRect(Rect(left = bounds.left, top = clipTop, right = bounds.right, bottom = clipBottom))
        }
        return path
    }
}

@Composable
private fun rememberBelowTopBarClip(topOnly: Boolean = false): SharedTransitionScope.OverlayClip {
    val density = LocalDensity.current
    // 首页顶栏 = 状态栏内边距 + 56dp（HomeTopBar 的高度）
    val topPx = with(density) {
        WindowInsets.statusBars.getTop(density).toFloat() + LumenSizes.homeTopBar.toPx()
    }
    // 迷你条上缘 = 窗口高度 - 手势导航条 - 条下方间距 - 条本体高度
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val barPx = with(density) {
        (LumenSizes.miniBarHeight + LumenSizes.miniBarBottomGap).toPx()
    }
    val windowInfo = LocalWindowInfo.current
    val containerHeightPx = windowInfo.containerSize.height.toFloat()
    val bottomPx = if (topOnly) 0f else (containerHeightPx - navBottomPx - barPx).coerceAtLeast(0f)
    return remember(topPx, bottomPx) { BetweenBarsClip({ topPx }, { bottomPx }) }
}

/**
 * 让封面参与共享元素转场。
 *
 * @param key 两端必须完全一致（见 [albumCoverKey] / [miniBarCoverKey]）
 * @param scope 元素所在的 AnimatedVisibilityScope：页面内传 null 表示用当前页面作用域；
 *              页面之外的浮层（迷你条）必须显式传它自己的 scope。
 */
@Composable
fun Modifier.coverSharedElement(key: String, scope: AnimatedVisibilityScope?): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val visibilityScope = scope ?: LocalPageScope.current ?: return this
    val boundsTransform = rememberCoverBoundsTransform()
    // 迷你条自己的封面在条内（下缘之下），不参与底部裁剪；页面封面才需要被两条栏夹住
    val overlayClip = rememberBelowTopBarClip(topOnly = key.startsWith(MINI_KEY_PREFIX))
    return with(shared) {
        this@coverSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = boundsTransform,
            clipInOverlayDuringTransition = overlayClip,
        )
    }
}

/** 页面内（列表 / 详情 / 播放页）的封面入口。 */
@Composable
fun Modifier.albumSharedElement(key: String): Modifier =
    coverSharedElement(key, scope = null)
