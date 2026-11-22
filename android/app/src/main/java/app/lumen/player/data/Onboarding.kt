package app.lumen.player.data

import android.content.Context

/** 首次启动引导的完成状态。 */
object Onboarding {
    private const val PREFS = "lumen_onboarding"
    private const val KEY_DONE = "done"

    fun completed(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun complete(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    }

    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
