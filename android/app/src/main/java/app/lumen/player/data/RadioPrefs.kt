package app.lumen.player.data

import android.content.Context

/**
 * 氛围电台的界面偏好：上次选的场景 / 选曲倾向 / 是否跟随当前歌曲。
 *
 * 为什么不登记进 `LocalData.wipe`：这三项与外观偏好同类，是"界面怎么显示"，
 * 不是个人听歌数据；换账号后延续同一份偏好更符合预期（与 Appearance 的处理一致）。
 */
object RadioPrefs {
    private const val FILE = "lumen_radio"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun vibe(ctx: Context): RadioVibe =
        RadioVibe.entries.getOrNull(prefs(ctx).getInt("vibe", RadioVibe.NIGHT.ordinal))
            ?: RadioVibe.NIGHT

    fun setVibe(ctx: Context, vibe: RadioVibe) {
        prefs(ctx).edit().putInt("vibe", vibe.ordinal).apply()
    }

    /** 0 = 完全贴近种子曲目，1 = 完全按场景目标。 */
    fun strength(ctx: Context): Float =
        prefs(ctx).getFloat("strength", 0.4f).coerceIn(0f, 1f)

    fun setStrength(ctx: Context, value: Float) {
        prefs(ctx).edit().putFloat("strength", value.coerceIn(0f, 1f)).apply()
    }

    /** true = 按当前曲目自动匹配场景；false = 使用用户手选的场景。 */
    fun autoPick(ctx: Context): Boolean = prefs(ctx).getBoolean("auto_pick", true)

    fun setAutoPick(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("auto_pick", value).apply()
    }
}
