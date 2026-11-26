package app.lumen.player.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局氛围动效开关（背景光团呼吸）。
 *
 * 关闭时 ExpressiveBackdrop 不再创建无限动画，只绘制一帧静态背景，
 * 完全停止相关渲染，避免空转耗电。
 */
object AmbientFx {
    private const val PREFS = "lumen_ui_fx"
    private const val KEY = "ambient_fx"

    /** true = 开启动效；Compose 读取该状态，切换后立即重组。 */
    var enabled by mutableStateOf(true)
        private set

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = p.getBoolean(KEY, true)
    }

    fun set(ctx: Context, value: Boolean) {
        enabled = value
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, value).apply()
    }

    /**
     * 仅测试用：不落盘地关闭氛围动效。
     *
     * Compose 的 idling resource 会把「无限动画」视为永远忙碌，UI 测试因此无法进入 idle。
     * 测试在 @BeforeClass 里调用本方法，把 4 处无限动画（背景/波浪/引导/艺人滚动）全部
     * 换成静态值；生产路径不受影响。
     */
    internal fun setForTest(value: Boolean) {
        enabled = value
    }

    /**
     * 仅测试用：关闭"首页按两次返回退出"。
     *
     * UI 测试会多次调用返回键；一旦两次落入 2 秒窗口，Activity 会直接 finish，
     * Compose 规则随即报 "No compose hierarchies found"。
     */
    @Volatile
    var suppressExitOnBackForTest: Boolean = false
        private set

    internal fun setSuppressExitOnBackForTest(value: Boolean) {
        suppressExitOnBackForTest = value
    }
}
