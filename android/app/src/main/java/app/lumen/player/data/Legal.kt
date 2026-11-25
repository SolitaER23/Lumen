package app.lumen.player.data

import android.content.Context
import app.lumen.player.BuildConfig

/**
 * 用户协议/隐私条款的接受状态。
 *
 * 版本号来自构建期注入的 [BuildConfig.LEGAL_VERSION]（值取自
 * `assets/legal/privacy.json` 的 `version` 字段）。之前是手写的常量 1，
 * 与文本版本完全脱钩——改了条款也不会重新征求同意。
 */
object Legal {
    private const val PREFS = "lumen_legal"
    private const val KEY_ACCEPTED = "accepted_version_str"

    /** 旧版本（Int）记录，仅用于迁移。 */
    private const val KEY_LEGACY_INT = "accepted_version"

    /** 当前随包的条款版本，例如 `2026-11-25`。 */
    fun currentVersion(): String = BuildConfig.LEGAL_VERSION

    fun accepted(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString(KEY_ACCEPTED, null)?.let { return it == BuildConfig.LEGAL_VERSION }
        // 旧版本只存了 Int(1)：视为已接受当前文本，迁移成字符串版本，避免老用户被强制重读
        if (p.getInt(KEY_LEGACY_INT, 0) >= 1) {
            p.edit()
                .putString(KEY_ACCEPTED, BuildConfig.LEGAL_VERSION)
                .remove(KEY_LEGACY_INT)
                .apply()
            return true
        }
        return false
    }

    fun accept(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACCEPTED, BuildConfig.LEGAL_VERSION)
            .remove(KEY_LEGACY_INT)
            .apply()
    }

    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_ACCEPTED)
            .remove(KEY_LEGACY_INT)
            .apply()
    }
}
