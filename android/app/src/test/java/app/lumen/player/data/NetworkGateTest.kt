package app.lumen.player.data

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 数据层闸门（对应审计 P1-4）：即便绕过登录页，公网明文也必须在构造客户端时被拒绝。
 */
class NetworkGateTest {

    @After
    fun reset() {
        NetworkGate.setAllowForTest(false)
    }

    @Test
    fun publicCleartextIsRejectedByDefault() {
        NetworkGate.setAllowForTest(false)
        try {
            NetworkGate.check("http://music.example.com:4533")
            fail("公网明文应被拒绝")
        } catch (e: InsecureBaseException) {
            assertTrue(e.verdict == CleartextVerdict.PUBLIC_CLEARTEXT)
        }
    }

    @Test
    fun publicCleartextPassesAfterOptIn() {
        NetworkGate.setAllowForTest(true)
        NetworkGate.check("http://music.example.com:4533")
    }

    @Test
    fun privateCleartextAlwaysPasses() {
        NetworkGate.setAllowForTest(false)
        NetworkGate.check("http://192.168.1.10:4533")
        NetworkGate.check("http://10.0.0.5:4533")
        NetworkGate.check("http://nas:4533")
    }

    @Test
    fun httpsAlwaysPasses() {
        NetworkGate.setAllowForTest(false)
        NetworkGate.check("https://music.example.com")
    }

    @Test
    fun invalidAddressIsRejected() {
        NetworkGate.setAllowForTest(true)
        try {
            NetworkGate.check("ftp://host")
            fail("非法 scheme 应被拒绝")
        } catch (e: InsecureBaseException) {
            assertTrue(e.verdict == CleartextVerdict.INVALID)
        }
    }
}
