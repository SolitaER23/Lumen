package app.lumen.player.util

import app.lumen.player.BuildConfig

/** Release 构建下静默的调试日志；错误日志仍由调用方直接使用 Log.e。 */
object Logx {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) android.util.Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) android.util.Log.i(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) android.util.Log.w(tag, msg)
    }
}
