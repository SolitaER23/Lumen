package app.lumen.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyTest {

    @Test
    fun privateRangesAreAllowedOverCleartext() {
        val hosts = listOf(
            "192.168.1.10", "10.0.0.5", "172.16.3.9", "172.31.255.1", "127.0.0.1",
            "169.254.2.3", "100.101.102.103", "localhost", "nas", "navidrome.local",
        )
        hosts.forEach {
            assertTrue("应判定为私网: " + it, NetworkPolicy.isPrivateHost(it))
            assertTrue(
                "私网 http 应放行: " + it,
                NetworkPolicy.evaluate("http://" + it + ":4533", allowPublicCleartext = false) ==
                    CleartextVerdict.OK,
            )
        }
    }

    @Test
    fun publicHostsNeedExplicitOptIn() {
        val hosts = listOf("music.example.com", "8.8.8.8", "172.32.0.1", "192.169.0.1", "1.1.1.1")
        hosts.forEach {
            assertFalse("不应判定为私网: " + it, NetworkPolicy.isPrivateHost(it))
            assertTrue(
                "公网 http 应要求确认: " + it,
                NetworkPolicy.evaluate("http://" + it, allowPublicCleartext = false) ==
                    CleartextVerdict.PUBLIC_CLEARTEXT,
            )
            assertTrue(
                "勾选后应放行: " + it,
                NetworkPolicy.evaluate("http://" + it, allowPublicCleartext = true) ==
                    CleartextVerdict.OK,
            )
        }
    }

    @Test
    fun httpsAndIpv6AreHandled() {
        assertTrue(
            NetworkPolicy.evaluate("https://music.example.com", false) == CleartextVerdict.OK,
        )
        assertTrue(NetworkPolicy.isPrivateHost("::1"))
        assertTrue(NetworkPolicy.isPrivateHost("fe80::1"))
        assertTrue(NetworkPolicy.isPrivateHost("fd12:3456::1"))
        assertFalse(NetworkPolicy.isPrivateHost("2001:4860:4860::8888"))
    }

    @Test
    fun invalidAddressesAreRejected() {
        listOf("", "http://", "ftp://host", "192.168.1.10", "httpfoo://host").forEach {
            assertTrue(
                "应判定非法: " + it,
                NetworkPolicy.evaluate(it, allowPublicCleartext = true) == CleartextVerdict.INVALID,
            )
        }
    }
}
