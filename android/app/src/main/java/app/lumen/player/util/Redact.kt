package app.lumen.player.util

/**
 * 日志脱敏。
 *
 * Subsonic 的认证三件套（`u` 用户名、`t` token、`s` salt）是**可重放的凭据**，
 * 一旦整条 URL 进 logcat / 崩溃报告 / 用户粘贴的日志，等于泄露账号访问权。
 * 所有对外输出的 URL 必须先过这里。
 */
object Redact {
    private val SECRET_PARAMS = setOf("t", "s", "u", "pwd", "token", "password", "auth")

    fun url(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val q = raw.indexOf('?')
        if (q < 0) return raw
        val base = raw.substring(0, q + 1)
        val query = raw.substring(q + 1)
        val rebuilt = query.split('&').joinToString("&") { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) pair
            else {
                val name = pair.substring(0, eq)
                if (name.lowercase() in SECRET_PARAMS) name + "=***" else pair
            }
        }
        return base + rebuilt
    }
}
