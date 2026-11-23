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
) {
    val complete: Boolean get() = base.trim().startsWith("http", ignoreCase = true) && user.isNotBlank() && password.isNotBlank()
}

/** 外观偏好：MCU 对比度（0=标准，0.5=中，1=高）。 */
object Appearance {
    private const val FILE = "lumen_settings"
    private const val KEY_CONTRAST = "mcu_contrast"

    /** 对比度：0 = 标准 / 0.45 = 增强 / 0.7 = 最高（旧值 1.0 收敛到 0.7）。 */
    fun contrast(ctx: Context): Float {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val v = p.getFloat(KEY_CONTRAST, 0.45f)
        if (v > 0.7f) {
            p.edit().putFloat(KEY_CONTRAST, 0.7f).apply()
            return 0.7f
        }
        return v
    }

    fun setContrast(ctx: Context, value: Float) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_CONTRAST, value.coerceIn(0f, 0.7f)).apply()
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

    fun moodTint(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("mood_tint", true)

    fun setMoodTint(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("mood_tint", v).apply()
    }

    fun gradientTheme(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("gradient_theme", false)

    fun setGradientTheme(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("gradient_theme", v).apply()
    }

    fun perPageColor(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("per_page_color", false)

    fun setPerPageColor(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("per_page_color", v).apply()
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

    /** 智能衔接：按当前曲目 BPM 调整淡入淡出时长。 */
    fun smartFade(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("smart_fade", true)

    fun setSmartFade(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("smart_fade", value).apply()
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
