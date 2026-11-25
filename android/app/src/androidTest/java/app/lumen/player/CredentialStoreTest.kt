package app.lumen.player

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lumen.player.data.ServerConfig
import app.lumen.player.data.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 凭据存储语义（对应审查 P1-2）。
 *
 * 关键约束：
 *  1) 密码只能以 `v1:` 前缀的 Keystore 密文落盘，不得出现明文键；
 *  2) 密文解不开时**保留密文**并返回 `passwordUnreadable`，让 UI 提示重输；
 *     旧实现会把键写成 null（等价于删除），用户会莫名其妙丢掉密码；
 *  3) 旧版本明文密码首次读取时自动迁移成密文。
 */
@RunWith(AndroidJUnit4::class)
class CredentialStoreTest {

    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = "lumen_test_creds"
    private fun prefs() = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)

    @After
    fun cleanup() {
        prefs().edit().clear().commit()
    }

    @Test
    fun savesEncryptedRoundTrips() {
        val cfg = ServerConfig("http://192.168.1.5:4533", "alice", "s3cret")
        assertTrue("保存应成功", SettingsStore.save(ctx, cfg, file))

        val loaded = SettingsStore.load(ctx, file)
        assertEquals("alice", loaded.user)
        assertEquals("http://192.168.1.5:4533", loaded.base)
        assertEquals("s3cret", loaded.password)
        assertFalse(loaded.passwordUnreadable)

        val raw = prefs().getString("password_enc", null)
        assertTrue("密文应带版本前缀", raw != null && raw.startsWith("v1:"))
        assertFalse("不得残留明文密码键", prefs().contains("password"))
    }

    @Test
    fun corruptedCipherIsKeptAndFlagged() {
        prefs().edit()
            .putString("base", "http://192.0.2.1:4533")
            .putString("user", "bob")
            .putString("password_enc", "v1:AAAA") // 长度不足 12 字节，必然解密失败
            .commit()

        val loaded = SettingsStore.load(ctx, file)
        assertTrue("应标记为不可读", loaded.passwordUnreadable)
        assertEquals("", loaded.password)
        assertEquals(
            "密文必须保留（Keystore 可能只是暂时不可用）",
            "v1:AAAA",
            prefs().getString("password_enc", null),
        )
    }

    @Test
    fun legacyPlaintextMigratesToCiphertext() {
        prefs().edit()
            .putString("base", "http://192.0.2.2:4533")
            .putString("user", "carol")
            .putString("password", "legacy-pw")
            .commit()

        val first = SettingsStore.load(ctx, file)
        assertEquals("legacy-pw", first.password)
        assertFalse("明文键应被迁移掉", prefs().contains("password"))
        assertTrue(prefs().getString("password_enc", "").orEmpty().startsWith("v1:"))

        val second = SettingsStore.load(ctx, file)
        assertEquals("迁移后应可重复读取", "legacy-pw", second.password)
    }
}
