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
}
