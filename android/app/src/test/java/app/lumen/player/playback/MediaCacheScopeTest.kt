package app.lumen.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 缓存键的账号隔离（对应审计 P1-3）。
 *
 * 键必须是「账号作用域 + 曲目 id + 格式」：不同服务器的同 id 曲目不能互相命中，
 * 否则多账号用户会听到另一台服务器的音频（错歌 + 跨账号数据串用）。
 */
class MediaCacheScopeTest {

    @Test
    fun sameAccountProducesSameScope() {
        val a = MediaCache.scopeOf("http://192.168.1.10:4533|alice")
        val b = MediaCache.scopeOf("http://192.168.1.10:4533|alice")
        assertEquals(a, b)
        assertEquals(8, a.length)
    }

    @Test
    fun differentServerOrUserProducesDifferentScope() {
        val base = MediaCache.scopeOf("http://192.168.1.10:4533|alice")
        assertNotEquals("不同服务器必须不同", base, MediaCache.scopeOf("http://192.168.1.20:4533|alice"))
        assertNotEquals("不同用户必须不同", base, MediaCache.scopeOf("http://192.168.1.10:4533|bob"))
    }

    @Test
    fun scopeDoesNotLeakHostOrUser() {
        val scope = MediaCache.scopeOf("http://192.168.1.10:4533|alice")
        assertEquals(false, scope.contains("192.168"))
        assertEquals(false, scope.contains("alice"))
    }
}
