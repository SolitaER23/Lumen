package app.lumen.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 在线播放缓存键的纯逻辑。
 *
 * 真正写入磁盘的行为由 SimpleCache 承担；这里锁定两条不可回退的约束：
 * 缓存键按「账号 + 曲目 + 格式」隔离，且键里不能出现 Subsonic 凭据。
 */
class MediaCacheKeyTest {

    @Test
    fun streamKeyDistinguishesFormatAndBitrate() {
        val raw = MediaCache.streamKey("scope", "42", null, null)
        val mp3 = MediaCache.streamKey("scope", "42", "mp3", "320")
        val mp3NoRate = MediaCache.streamKey("scope", "42", "mp3", null)
        assertEquals("acct:scope|track:42#raw", raw)
        assertEquals("acct:scope|track:42#mp3@320", mp3)
        assertNotEquals(raw, mp3)
        assertNotEquals(mp3, mp3NoRate)
        // 空参数按 raw 处理，避免出现 "acct:scope|track:42#"
        assertEquals(raw, MediaCache.streamKey("scope", "42", " ", " "))
    }

    @Test
    fun uriFallbackKeyHidesCredentialsInUrl() {
        val url = "http://192.168.1.10:4533/rest/stream.view?u=alice&t=deadbeef&s=1234&id=42"
        val key = MediaCache.uriKey(url)
        assertTrue(key.startsWith("uri:"))
        assertFalse("键里不能出现账号或 token", key.contains("alice"))
        assertFalse(key.contains("deadbeef"))
        assertFalse(key.contains("192.168"))
        // 同一 URI 稳定，不同 URI 不撞
        assertEquals(key, MediaCache.uriKey(url))
        assertNotEquals(key, MediaCache.uriKey(url + "&format=mp3"))
    }
}
