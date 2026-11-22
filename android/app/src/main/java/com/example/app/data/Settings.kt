package com.example.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 连接配置：服务器地址 / 用户名 / 密码。密码用 Android Keystore 的 AES-GCM 加密后落盘。 */
data class ServerConfig(
    val base: String,
    val user: String,
    val password: String,
) {
    val complete: Boolean get() = base.startsWith("http") && user.isNotBlank() && password.isNotBlank()
}

/** 外观偏好：MCU 对比度（0=标准，0.5=中，1=高）。 */
object Appearance {
    private const val FILE = "lumen_settings"
    private const val KEY_CONTRAST = "mcu_contrast"

    fun contrast(ctx: Context): Float {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        // 本次升级默认切到「高」对比度（只迁移一次）
        if (!p.getBoolean("mcu_contrast_high_default", false)) {
            p.edit().putFloat(KEY_CONTRAST, 1f).putBoolean("mcu_contrast_high_default", true).apply()
            return 1f
        }
        return p.getFloat(KEY_CONTRAST, 1f)
    }

    fun setContrast(ctx: Context, value: Float) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_CONTRAST, value.coerceIn(0f, 1f)).apply()
    }

    /** 切歌音量渐变时长（毫秒，0 = 关闭）。 */
    fun fadeMs(ctx: Context): Long =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong("fade_ms", 260L)

    fun setFadeMs(ctx: Context, value: Long) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong("fade_ms", value.coerceIn(0L, 2000L)).apply()
    }
}

object SettingsStore {
    private const val FILE = "lumen_settings"
    private const val KEY_ALIAS = "lumen_settings_key"
    private const val PREF_PASSWORD_ENC = "password_enc"
    private const val PREF_PASSWORD_LEGACY = "password"

    fun load(ctx: Context, file: String = FILE): ServerConfig {
        val p = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)
        val enc = p.getString(PREF_PASSWORD_ENC, null)
        val password = when {
            enc != null -> decrypt(ctx, enc).orEmpty()
            // 旧版本明文：迁移成密文并删掉明文
            p.contains(PREF_PASSWORD_LEGACY) -> {
                val plain = p.getString(PREF_PASSWORD_LEGACY, "").orEmpty()
                if (plain.isNotEmpty()) {
                    p.edit()
                        .putString(PREF_PASSWORD_ENC, encrypt(ctx, plain))
                        .remove(PREF_PASSWORD_LEGACY)
                        .apply()
                } else {
                    p.edit().remove(PREF_PASSWORD_LEGACY).apply()
                }
                plain
            }
            else -> ""
        }
        return ServerConfig(
            base = p.getString("base", "") ?: "",
            user = p.getString("user", "") ?: "",
            password = password,
        )
    }

    fun save(ctx: Context, cfg: ServerConfig, file: String = FILE) {
        ctx.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            .putString("base", cfg.base.trim().trimEnd('/'))
            .putString("user", cfg.user.trim())
            .putString(PREF_PASSWORD_ENC, encrypt(ctx, cfg.password))
            .remove(PREF_PASSWORD_LEGACY)
            .apply()
    }

    /** 认证 salt：安装后固定，保证同一首曲目的 URL（= 缓存键）跨重启稳定。 */
    fun salt(ctx: Context): String {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        p.getString("salt", null)?.let { return it }
        val s = "%08x".format(java.util.Random(System.nanoTime()).nextInt())
        p.edit().putString("salt", s).apply()
        return s
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** 供其他模块复用 Keystore 加密（如统计 Token）。 */
    fun encryptString(ctx: Context, plain: String): String? = encrypt(ctx, plain)

    fun decryptString(ctx: Context, enc: String): String? = decrypt(ctx, enc)

    // ---- Keystore AES-GCM ----

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return kg.generateKey()
    }

    private fun encrypt(ctx: Context, plain: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = cipher.doFinal(plain.toByteArray())
        Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(ctx: Context, blob: String): String? = runCatching {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val ct = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ct))
    }.getOrNull()
}
