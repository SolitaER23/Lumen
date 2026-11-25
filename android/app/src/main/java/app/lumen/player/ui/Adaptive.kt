package app.lumen.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/** 首页专辑网格的滚动位置：单栏与双栏各自有 saveable key，用它做跨模式续接。 */
object HomeScrollStore {
    var albumIndex: Int = 0
    /** 同一条目的像素偏移，返回时能精确回到原来的位置 */
    var albumOffset: Int = 0

    private const val PREFS = "lumen_home_scroll"
    private var loaded = false
    private var lastPersist = 0L

    /** 冷启动时把上次的滚动位置/头部高度读回来，首帧就是正确布局（否则会先按兜底值渲染再跳）。 */
    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        albumIndex = p.getInt("index", 0)
        albumOffset = p.getInt("offset", 0)
    }

    /** 滚动过程中节流写入，避免频繁落盘。 */
    fun persist(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersist < 400) return
        lastPersist = now
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("index", albumIndex)
            .putInt("offset", albumOffset)
            .apply()
    }
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * 折叠屏姿态（官方 WindowManager）：分离式铰链才参与布局。
 * - 竖直铰链（书本 / 双屏）：按铰链位置切分左右两栏；
 * - 水平铰链（桌面 / 帐篷）：详情内容避开铰链，分上下两块。
 */
data class FoldState(
    val vertical: Boolean,
    /** 铰链中心在窗口中的比例（0–1） */
    val fraction: Float,
    val hingeThicknessDp: Float,
)

/**
 * 官方自适应规范（Android / Material 3 WindowSizeClass）：
 * Compact <600dp 单栏；Medium 600–840dp 双栏 34/66；Expanded ≥840dp 双栏 42/58。
 */
data class AdaptiveLayout(
    val widthClass: WindowWidthSizeClass,
    val twoPane: Boolean,
    val leftWeight: Float,
    val fold: FoldState?,
)

@OptIn(androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberAdaptiveLayout(): AdaptiveLayout {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val sizeClass = if (activity == null) {
        WindowWidthSizeClass.Compact
    } else {
        calculateWindowSizeClass(activity).widthSizeClass
    }

    var fold by remember { mutableStateOf<FoldState?>(null) }
    if (activity != null) {
        androidx.compose.runtime.LaunchedEffect(activity) {
            val tracker = WindowInfoTracker.getOrCreate(activity)
            tracker.windowLayoutInfo(activity).collect { info ->
                val f = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull { it.isSeparating }
                fold = if (f == null) {
                    null
                } else {
                    val bounds = f.bounds
                    if (f.orientation == FoldingFeature.Orientation.VERTICAL) {
                        val w = activity.window.decorView.width.coerceAtLeast(1)
                        FoldState(true, (bounds.centerX() / w.toFloat()).coerceIn(0.25f, 0.75f), 12f)
                    } else {
                        val h = activity.window.decorView.height.coerceAtLeast(1)
                        FoldState(false, (bounds.centerY() / h.toFloat()).coerceIn(0.2f, 0.8f), 12f)
                    }
                }
            }
        }
    }

    // 官方建议：list-detail 用于 Expanded；Medium 仍以单栏为主（平板竖屏 700dp 左右时
    // 主列表只有 ~230dp，标题/标签会挤成一列，反而更差）
    val base = if (sizeClass == WindowWidthSizeClass.Expanded) 0.42f else 1f
    val leftWeight = if (fold?.vertical == true) fold!!.fraction else base
    return AdaptiveLayout(
        widthClass = sizeClass,
        twoPane = sizeClass == WindowWidthSizeClass.Expanded,
        leftWeight = leftWeight,
        fold = fold,
    )
}
