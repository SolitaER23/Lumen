package com.example.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 一个已保存的服务器账号（密码落盘时由 SettingsStore 用 Keystore 加密）。 */
data class Account(
    val id: String,
    val label: String,
    val base: String,
    val user: String,
    val password: String,
) {
    val host: String
        get() = base.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

/**
 * 多账号存储：
 * - 索引放在 lumen_accounts（只存 id 与展示名）；
 * - 每个账号的连接信息单独存一个 prefs 文件，密码仍走 Keystore 加密；
 * - 当前生效账号仍写在 SettingsStore 的默认文件里，其他代码不用改。
 */
object Accounts {
    private const val INDEX_FILE = "lumen_accounts"

    private fun index(ctx: Context) =
        ctx.getSharedPreferences(INDEX_FILE, Context.MODE_PRIVATE)

    private fun accountFile(id: String) = "lumen_account_$id"

    /** 首次启动或老版本升级：把当前连接迁移成一个账号。 */
    fun ensure(ctx: Context, current: ServerConfig): List<Account> {
        if (list(ctx).isEmpty() && current.complete) {
            val a = add(ctx, current.base, current.user, current.password)
            setActive(ctx, a.id)
        }
        return list(ctx)
    }

    fun list(ctx: Context): List<Account> {
        val raw = index(ctx).getString("list", null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Account>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank { continue }
            val cfg = SettingsStore.load(ctx, accountFile(id))
            out += Account(
                id = id,
                label = o.optString("label").ifBlank { cfg.user },
                base = cfg.base,
                user = cfg.user,
                password = cfg.password,
            )
        }
        return out
    }

    fun activeId(ctx: Context): String? = index(ctx).getString("active", null)

    fun active(ctx: Context): Account? = list(ctx).firstOrNull { it.id == activeId(ctx) }

    fun add(ctx: Context, base: String, user: String, password: String, label: String = ""): Account {
        val id = UUID.randomUUID().toString().take(8)
        val cfg = ServerConfig(base.trim().trimEnd('/'), user.trim(), password)
        SettingsStore.save(ctx, cfg, accountFile(id))
        val shown = label.ifBlank {
            val host = cfg.base.removePrefix("https://").removePrefix("http://").trimEnd('/')
            "${cfg.user}@$host"
        }
        val arr = runCatching { JSONArray(index(ctx).getString("list", "[]")) }.getOrDefault(JSONArray())
        arr.put(JSONObject().put("id", id).put("label", shown))
        index(ctx).edit().putString("list", arr.toString()).apply()
        return Account(id, shown, cfg.base, cfg.user, cfg.password)
    }

    /** 把账号设为当前：写回默认配置（其他代码读取的就是它），并返回 cfg。 */
    fun activate(ctx: Context, account: Account): ServerConfig {
        val cfg = SettingsStore.load(ctx, accountFile(account.id))
        val resolved = if (cfg.complete) cfg else ServerConfig(account.base, account.user, account.password)
        SettingsStore.save(ctx, resolved)
        setActive(ctx, account.id)
        return resolved
    }

    fun remove(ctx: Context, account: Account) {
        val arr = runCatching { JSONArray(index(ctx).getString("list", "[]")) }.getOrDefault(JSONArray())
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") != account.id) kept.put(o)
        }
        index(ctx).edit().putString("list", kept.toString()).apply()
        ctx.getSharedPreferences(accountFile(account.id), Context.MODE_PRIVATE).edit().clear().apply()
        if (activeId(ctx) == account.id) {
            val next = list(ctx).firstOrNull()
            if (next != null) {
                SettingsStore.save(ctx, ServerConfig(next.base, next.user, next.password))
                setActive(ctx, next.id)
            } else {
                index(ctx).edit().remove("active").apply()
            }
        }
    }

    /** 清除全部账号与本机凭据。 */
    fun clearAll(ctx: Context) {
        list(ctx).forEach { acc ->
            ctx.getSharedPreferences(accountFile(acc.id), Context.MODE_PRIVATE).edit().clear().apply()
        }
        index(ctx).edit().clear().apply()
    }

    private fun setActive(ctx: Context, id: String) {
        index(ctx).edit().putString("active", id).apply()
    }
}
