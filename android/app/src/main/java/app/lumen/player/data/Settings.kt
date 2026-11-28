package app.lumen.player.data

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
    /**
     * 已保存的密码解不开（Keystore 被重置、密文损坏）。
     * UI 应提示「请重新输入密码」，而不是把账号当空密码静默处理。
     */
    val passwordUnreadable: Boolean = false,
) {
    /**
     * 配置是否完整可用。注意不要用 `startsWith("http")`——它会把 `httpfoo://` 也放进来。
     * 明文 HTTP 的地址策略由 [NetworkPolicy] 在登录页把关。
     */
    val complete: Boolean
        get() {
            val uri = runCatching { java.net.URI(base.trim()) }.getOrNull() ?: return false
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            if (uri.host.isNullOrBlank()) return false
            return user.isNotBlank() && password.isNotBlank()
        }
}

/**
 * 外观偏好：MCU 对比度（0 = 标准，0.5 = 中等，1 = 最高）。
 *
 * 与登录凭据分开存放（历史版本共用 lumen_settings，导致「退出登录」会连带把
 * 主题/对比度/淡入淡出一起清空）。旧文件保留不删，便于回滚。
 */
object Appearance {
    private const val FILE = "lumen_appearance"
    private const val LEGACY_FILE = "lumen_settings"
    private const val KEY_CONTRAST = "mcu_contrast"
    private const val KEY_MIGRATED = "migrated_from_legacy"

    /** 从旧文件迁移过来的外观键（与写入端保持一致）。 */
    private val APPEARANCE_KEYS = setOf(
        KEY_CONTRAST, "theme_mode", "palette_style", "gradient_theme",
        "material_you", "amoled", "pinned_album", "pinned_seeds",
        "fade_ms",
    )

    /** 幂等迁移：把旧 lumen_settings 里的外观键搬到 lumen_appearance。 */
    fun migrate(ctx: Context) {
        val target = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (target.getBoolean(KEY_MIGRATED, false)) return
        val legacy = ctx.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
        val editor = target.edit()
        legacy.all.forEach { (key, value) ->
            if (key !in APPEARANCE_KEYS) return@forEach
            when (value) {
                is Float -> editor.putFloat(key, value)
                is Long -> editor.putLong(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.putBoolean(KEY_MIGRATED, true).commit()
    }

    /**
     * 对比度：Google 的三档刻度（0 / 0.5 / 1）。
     *
     * 历史版本存过 0.45、0.7 这类自造档位，甚至滑杆留下的任意浮点：
     * 读取时按最近档位归一（>0.6 视为最高，>0.2 视为中等），并把归一后的值写回，
     * 保证设置页永远有一个档位是选中状态。
     */
    fun contrast(ctx: Context): Float {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val raw = p.getFloat(KEY_CONTRAST, 0f)
        val level = when {
            raw <= 0.2f -> 0f
            raw < 0.6f -> 0.5f
            else -> 1f
        }
        if (level != raw) p.edit().putFloat(KEY_CONTRAST, level).apply()
        return level
    }

    fun setContrast(ctx: Context, value: Float) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_CONTRAST, value.coerceIn(0f, 1f)).apply()
    }

    // ---------- 主题 / 配色偏好 ----------

    fun themeMode(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("theme_mode", "system") ?: "system"

    fun setThemeMode(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("theme_mode", v).apply()
    }

    fun paletteStyle(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("palette_style", "tonal") ?: "tonal"

    fun setPaletteStyle(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("palette_style", v).apply()
    }

    fun gradientTheme(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("gradient_theme", false)

    fun setGradientTheme(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("gradient_theme", v).apply()
    }

    fun materialYou(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("material_you", false)

    fun setMaterialYou(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("material_you", v).apply()
    }

    fun amoled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("amoled", false)

    fun setAmoled(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("amoled", v).apply()
    }

    /** 按专辑锁色：存的是专辑 id + 该专辑的种子，与播放中的曲目解耦。 */
    fun pinnedAlbum(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("pinned_album", null)

    fun pinnedSeeds(ctx: Context): List<Int>? {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("pinned_seeds", null) ?: return null
        return runCatching {
            val arr = org.json.JSONArray(raw)
            List(arr.length()) { arr.getInt(it) }
        }.getOrNull()
    }

    fun setPinned(ctx: Context, albumId: String, seeds: List<Int>) {
        val arr = org.json.JSONArray()
        seeds.take(3).forEach { arr.put(it) }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("pinned_album", albumId)
            .putString("pinned_seeds", arr.toString())
            .apply()
    }

    fun clearPinned(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .remove("pinned_album")
            .remove("pinned_seeds")
            .apply()
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

    /**
     * 只读身份字段（服务器地址 + 用户名），**不解密密码**。
     * 用于给媒体缓存做账号命名空间：每次读都做一次 Keystore 解密代价太高。
     */
    fun identity(ctx: Context, file: String = FILE): String {
        val p = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)
        val base = p.getString("base", "").orEmpty().trim().lowercase()
        val user = p.getString("user", "").orEmpty().trim().lowercase()
        return base + "|" + user
    }

    fun load(ctx: Context, file: String = FILE): ServerConfig {
        val p = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)
        var unreadable = false
        val enc = p.getString(PREF_PASSWORD_ENC, null)
        val password = when {
            enc != null -> try {
                decrypt(ctx, enc)
            } catch (e: CryptoException) {
                // 关键：**不要**删掉密文。Keystore 可能只是暂时不可用（换锁屏方式、
                // 恢复备份后首次解锁），删了就永久丢失凭据。保留并让 UI 提示重输。
                unreadable = true
                app.lumen.player.util.Logx.w("LumenCrypto", "解密失败（保留密文）: " + e.message)
                ""
            }
            // 旧版本明文：迁移成密文并删掉明文；加密不可用时保留明文，下次再试
            p.contains(PREF_PASSWORD_LEGACY) -> {
                val plain = p.getString(PREF_PASSWORD_LEGACY, "").orEmpty()
                if (plain.isNotEmpty()) {
                    try {
                        p.edit()
                            .putString(PREF_PASSWORD_ENC, encrypt(ctx, plain))
                            .remove(PREF_PASSWORD_LEGACY)
                            .commit()
                    } catch (e: CryptoException) {
                        app.lumen.player.util.Logx.w("LumenCrypto", "明文迁移加密失败（保留明文）: " + e.message)
                    }
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
            passwordUnreadable = unreadable,
        )
    }

    /**
     * 保存连接配置。
     *
     * 返回 false 表示密码**没能安全落盘**（Keystore 不可用）。此时不会写入任何字段、
     * 也不会删掉旧密文 —— 旧实现是 `putString(key, null)`，等于把密码静默删掉，
     * 用户只会看到「账号不完整」，完全不知道发生了什么。
     */
    fun save(ctx: Context, cfg: ServerConfig, file: String = FILE): Boolean {
        val enc = try {
            encrypt(ctx, cfg.password)
        } catch (e: CryptoException) {
            app.lumen.player.util.Logx.w("LumenCrypto", "保存密码失败: " + e.message)
            return false
        }
        return ctx.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            .putString("base", cfg.base.trim().trimEnd('/'))
            .putString("user", cfg.user.trim())
            .putString(PREF_PASSWORD_ENC, enc)
            .remove(PREF_PASSWORD_LEGACY)
            .commit()
    }

    /**
     * 认证 salt：安装后固定，保证同一首曲目的 URL 跨重启稳定。
     *
     * 用 SecureRandom 而不是 `Random(nanoTime)`：subsonic 的 `t = md5(password + salt)`，
     * 可预测的 salt 会让 token 更容易被离线枚举。
     */
    fun salt(ctx: Context): String {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        p.getString("salt", null)?.let { return it }
        val rnd = java.security.SecureRandom()
        val s = String.format(java.util.Locale.ROOT, "%08x", rnd.nextInt())
        p.edit().putString("salt", s).apply()
        return s
    }

    /** 清空全部连接配置（含 salt）；外观偏好已迁到独立文件，不受影响。 */
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * 只清凭据（登出用）：保留 salt（流缓存键稳定，避免登出后全量重下）与账号索引。
     * 「清除本机数据」请走 [clear] / `LocalData.wipe`。
     */
    fun clearCredentials(ctx: Context, keepSalt: Boolean = true) {
        val e = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .remove("base")
            .remove("user")
            .remove(PREF_PASSWORD_ENC)
            .remove(PREF_PASSWORD_LEGACY)
        if (!keepSalt) e.remove("salt")
        e.apply()
    }

    /** 供其他模块复用 Keystore 加密（如统计 Token）；失败返回 null，调用方不得回退明文。 */
    fun encryptString(ctx: Context, plain: String): String? =
        runCatching { encrypt(ctx, plain) }.getOrNull()

    fun decryptString(ctx: Context, enc: String): String? =
        runCatching { decrypt(ctx, enc) }.getOrNull()

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

    /** 密文格式：`v1:` + Base64(IV(12B) + GCM 密文)，带版本前缀便于以后换算法。 */
    private const val ENC_PREFIX = "v1:"

    private fun encrypt(ctx: Context, plain: String): String {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val ct = cipher.doFinal(plain.toByteArray())
            return ENC_PREFIX + Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw CryptoException("Keystore 加密失败: " + e.message, e)
        }
    }

    private fun decrypt(ctx: Context, blob: String): String {
        try {
            val body = if (blob.startsWith(ENC_PREFIX)) blob.substring(ENC_PREFIX.length) else blob
            val bytes = Base64.decode(body, Base64.NO_WRAP)
            if (bytes.size <= 12) throw CryptoException("密文长度异常: " + bytes.size)
            val iv = bytes.copyOfRange(0, 12)
            val ct = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(ct))
        } catch (e: CryptoException) {
            throw e
        } catch (e: Exception) {
            throw CryptoException("Keystore 解密失败: " + e.message, e)
        }
    }
}

/** Keystore 不可用 / 密文损坏。调用方必须显式处理，不能把失败当成「空密码」。 */
class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
