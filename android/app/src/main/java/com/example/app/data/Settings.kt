package com.example.app.data

import android.content.Context

/** 连接配置：服务器地址 / 用户名 / 密码。密码只存在应用私有目录。 */
data class ServerConfig(
    val base: String,
    val user: String,
    val password: String,
) {
    val complete: Boolean get() = base.startsWith("http") && user.isNotBlank() && password.isNotBlank()
}

object SettingsStore {
    private const val FILE = "lumen_settings"

    fun load(ctx: Context): ServerConfig {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return ServerConfig(
            base = p.getString("base", "") ?: "",
            user = p.getString("user", "") ?: "",
            password = p.getString("password", "") ?: "",
        )
    }

    fun save(ctx: Context, cfg: ServerConfig) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("base", cfg.base.trim().trimEnd('/'))
            .putString("user", cfg.user.trim())
            .putString("password", cfg.password)
            .apply()
    }
}