package app.lumen.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载/缓存键的纯逻辑（批次 A 审计项 D1/D9/D12/D23）。
 *
 * 这里只测纯函数：真正「下载写入时不会被淘汰」的行为由 StreamOnlyCacheEvictor 承担，
 * 需要在仪器测试里用真机验证（见 docs/plans/download-audit.md 批次 A 验收）。
 */
class DownloadLogicTest {

    @Test
    fun downloadKeyIsRecognizedBySuffix() {
        val key = "acct:1a2b3c4d|42#lumen-dl"
        assertTrue(MediaCache.isDownloadKey(key))
        // 历史（0.74~0.90）下载键同样要被保留
        assertTrue(MediaCache.isDownloadKey("42#lumen-dl"))
        // 流缓存键不能被当成下载
        assertFalse(MediaCache.isDownloadKey("acct:1a2b3c4d|track:42#raw"))
        assertFalse(MediaCache.isDownloadKey("uri:deadbeef"))
    }

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

    @Test
    fun requestIdIsUniquePerGroup() {
        // 同一首歌出现在专辑组与单曲组时，请求 id 必须不同，否则 media3 按 id 合并会互相顶替
        val inAlbum = Downloads.requestIdFor("album:abc", "42")
        val asSingle = Downloads.requestIdFor("track:42", "42")
        assertEquals("album:abc|42", inAlbum)
        assertEquals("track:42|42", asSingle)
        assertNotEquals(inAlbum, asSingle)
        // 没有分组时退化为曲目 id（不应发生，但别产生 "|42" 这种脏 id）
        assertEquals("42", Downloads.requestIdFor("", "42"))
    }

    @Test
    fun wifiOnlyMapsToUnmeteredRequirement() {
        val anyNetwork = Downloads.requirementsFor(false)
        val wifiOnly = Downloads.requirementsFor(true)
        val network = androidx.media3.exoplayer.scheduler.Requirements.NETWORK
        val unmetered = androidx.media3.exoplayer.scheduler.Requirements.NETWORK_UNMETERED
        assertEquals(network, anyNetwork.requirements)
        // media3 会把「非计费」自动展开为「有网 + 非计费」（3），断言这一点而非裸常量
        assertEquals(network or unmetered, wifiOnly.requirements)
        assertTrue(wifiOnly.isNetworkRequired)
        assertTrue(wifiOnly.isUnmeteredNetworkRequired)
        assertFalse(anyNetwork.isUnmeteredNetworkRequired)
    }

    @Test
    fun syntheticUriRoundTripsWithoutCredentials() {
        val uri = Downloads.syntheticUri("album:abc|42", "mp3", 320)
        assertTrue(uri.startsWith("lumen-dl://"))
        // 合成地址里不能出现任何凭据（镜像审计 D2：凭据不落盘）
        assertFalse(uri.contains("u="))
        assertFalse(uri.contains("&t="))
        assertFalse(uri.contains("&s="))
        assertFalse(uri.contains("http"))
        val spec = Downloads.parseSynthetic(uri)
        assertEquals("album:abc|42", spec?.requestId)
        assertEquals("mp3", spec?.format)
        assertEquals(320, spec?.bitRate)
    }

    @Test
    fun syntheticUriWithoutFormatStaysParseable() {
        val spec = Downloads.parseSynthetic(Downloads.syntheticUri("track:7|7", null, null))
        assertEquals("track:7|7", spec?.requestId)
        assertNull(spec?.format)
        assertNull(spec?.bitRate)
    }

    @Test
    fun realUrlIsNotTreatedAsSynthetic() {
        val real = "http://192.168.1.10:4533/rest/stream.view?u=alice&t=deadbeef&s=1&id=42"
        assertNull(Downloads.parseSynthetic(real))
    }

    @Test
    fun trackIdIsExtractedFromRequestId() {
        // 当前（批次 A）请求 id 就是曲目 id
        assertEquals("42", Downloads.trackIdOf("42"))
        // 批次 B 起 id 会带上分组前缀，解析仍要拿到曲目 id
        assertEquals("42", Downloads.trackIdOf("album:abc|42"))
        assertEquals("42", Downloads.trackIdOf("track:42|42"))
    }
}
