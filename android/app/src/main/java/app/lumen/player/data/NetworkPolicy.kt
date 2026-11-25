package app.lumen.player.data

import android.content.Context
import java.net.URI

/**
 * 明文 HTTP 策略。
 *
 * 背景：Subsonic 的 `u/t/s` 是可重放的凭据（`t = md5(password + s)`），一旦走明文
 * HTTP，同网段任何人都能抓包并**长期**冒充该账号。自托管用户绝大多数在局域网内用
 * http，所以不能一刀切禁用；折中方案是：
 *
 *  - 私网 / 回环 / 链路本地 / `*.local` / 单标签主机名 → 允许 http（局域网自托管常态）；
 *  - 公网地址（域名或公网 IP）+ http → 默认阻止，需要用户在登录页显式勾选「不推荐」开关；
 *  - https 一律允许。
 *
 * 说明：Android 的 network security config 只支持按域名/主机名放行，**不支持 CIDR 网段**，
 * 因此无法用清单配置表达「仅局域网允许明文」，只能在应用层判定。
 */
enum class CleartextVerdict {
    /** 地址合法，且不需要额外确认。 */
    OK,

    /** 公网地址使用明文 HTTP：需要用户显式确认。 */
    PUBLIC_CLEARTEXT,

    /** 地址不合法（缺少 scheme/host）。 */
    INVALID,
}

object NetworkPolicy {

    fun evaluate(base: String, allowPublicCleartext: Boolean): CleartextVerdict {
        // 用 java.net.URI 而不是 android.net.Uri：这里要保持纯 JVM 可测（Uri 在单测里是 stub）
        val uri = runCatching { URI(base.trim()) }.getOrNull() ?: return CleartextVerdict.INVALID
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase().orEmpty()
        if (scheme != "http" && scheme != "https") return CleartextVerdict.INVALID
        if (host.isBlank()) return CleartextVerdict.INVALID
        if (scheme == "https") return CleartextVerdict.OK
        return when {
            isPrivateHost(host) -> CleartextVerdict.OK
            allowPublicCleartext -> CleartextVerdict.OK
            else -> CleartextVerdict.PUBLIC_CLEARTEXT
        }
    }

    /**
     * 判定主机名是否属于「局域网/本机」范畴。
     * 这里刻意用**白名单式**判定：只有明确属于私网范围才放行。
     */
    fun isPrivateHost(rawHost: String): Boolean {
        val host = rawHost.trim().trimStart('[').trimEnd(']').lowercase()
        if (host.isEmpty()) return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan")) return true
        // 单标签主机名（如 "nas"、"navidrome"）无法解析到公网，按局域网处理
        if (!host.contains('.') && !host.contains(':')) return true

        ipv4(host)?.let { (a, b) ->
            return when {
                a == 10 -> true                       // 10.0.0.0/8
                a == 127 -> true                      // 回环
                a == 172 && b in 16..31 -> true        // 172.16.0.0/12
                a == 192 && b == 168 -> true           // 192.168.0.0/16
                a == 169 && b == 254 -> true           // 链路本地
                a == 100 && b in 64..127 -> true       // CGNAT（Tailscale 等组网常用）
                else -> false
            }
        }
        // IPv6：回环 / 链路本地 / ULA
        if (host == "::1") return true
        if (host.startsWith("fe80:")) return true
        if (host.startsWith("fc") || host.startsWith("fd")) return true
        return false
    }

    private fun ipv4(host: String): Pair<Int, Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val nums = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
        return nums[0] to nums[1]
    }
}

/** 「允许对公网地址使用明文 HTTP」这个风险开关的持久化。 */
object NetworkSettings {
    private const val FILE = "lumen_network"
    private const val KEY_ALLOW_PUBLIC_CLEARTEXT = "allow_public_cleartext"

    fun allowPublicCleartext(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALLOW_PUBLIC_CLEARTEXT, false)

    fun setAllowPublicCleartext(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ALLOW_PUBLIC_CLEARTEXT, value)
            .apply()
    }
}
