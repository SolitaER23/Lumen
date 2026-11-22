package app.lumen.player.data

import android.content.Context

/** 用户协议/隐私条款的接受状态；条款版本变更时重新确认。 */
object Legal {
    const val VERSION = 1
    private const val PREFS = "lumen_legal"
    private const val KEY = "accepted_version"

    fun accepted(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0) >= VERSION

    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun accept(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY, VERSION)
            .apply()
    }
}
