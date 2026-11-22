package app.lumen.player.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicTest {

    @Test
    fun md5MatchesKnownVector() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5Hex("abc"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5Hex(""))
    }

    @Test
    fun restUrlCarriesAuthAndExtraParams() {
        val url = restUrl(
            base = "http://192.0.2.10:4533/",
            path = "stream.view",
            user = "demo",
            token = "deadbeef",
            salt = "cafe",
            extra = mapOf("id" to "abc", "format" to "mp3"),
        ).toString()
        assertTrue(url.startsWith("http://192.0.2.10:4533/rest/stream.view?"))
        assertTrue(url.contains("u=demo"))
        assertTrue(url.contains("t=deadbeef"))
        assertTrue(url.contains("s=cafe"))
        assertTrue(url.contains("v=1.16.1"))
        assertTrue(url.contains("c=Lumen"))
        assertTrue(url.contains("f=json"))
        assertTrue(url.contains("id=abc"))
        assertTrue(url.contains("format=mp3"))
    }

    @Test
    fun baseTrailingSlashDoesNotDoubleSlash() {
        val url = restUrl("http://host:4533///", "ping.view", "u", "t", "s").toString()
        assertTrue(url.startsWith("http://host:4533/rest/ping.view?"))
    }

    @Test
    fun parseAlbumListReadsFields() {
        val root = JSONObject(
            """
            {"albumList2":{"album":[
              {"id":"a1","name":"Album One","artist":"Artist A","coverArt":"c1","songCount":12,"year":2024},
              {"id":"a2","name":"Album Two","artist":"Artist B","coverArt":"c2","songCount":8,"year":1999}
            ]}}
            """.trimIndent(),
        )
        val albums = parseAlbumList(root)
        assertEquals(2, albums.size)
        assertEquals("Album One", albums[0].name)
        assertEquals("Artist A", albums[0].artist)
        assertEquals("c1", albums[0].coverId)
        assertEquals(12, albums[0].songCount)
        assertEquals(2024, albums[0].year)
    }

    @Test
    fun parseAlbumListHandlesEmpty() {
        assertTrue(parseAlbumList(JSONObject("{}")).isEmpty())
        assertTrue(parseAlbumList(JSONObject("""{"albumList2":{}}""")).isEmpty())
    }

    @Test
    fun parseAlbumDetailSortsByDiscThenTrack() {
        val root = JSONObject(
            """
            {"album":{"song":[
              {"id":"s3","title":"D2T1","discNumber":2,"track":1,"duration":61},
              {"id":"s2","title":"D1T2","discNumber":1,"track":2,"duration":62},
              {"id":"s1","title":"D1T1","discNumber":1,"track":1,"duration":63}
            ]}}
            """.trimIndent(),
        )
        val tracks = parseAlbumDetail(root, "album1")
        assertEquals(listOf("s1", "s2", "s3"), tracks.map { it.id })
        assertEquals("album1", tracks[0].albumId)
        assertEquals(1, tracks[0].disc)
        assertEquals(63, tracks[0].seconds)
    }

    @Test
    fun parseAlbumDetailDefaultsDiscToOne() {
        val root = JSONObject(
            """{"album":{"song":[{"id":"x","title":"T","track":5,"duration":10}]}}""",
        )
        val tracks = parseAlbumDetail(root, "a")
        assertEquals(1, tracks[0].disc)
        assertEquals(5, tracks[0].track)
    }

    @Test
    fun checkStatusThrowsWithServerMessage() {
        val err = runCatching {
            checkStatus(JSONObject("""{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}"""))
        }.exceptionOrNull()
        assertNotNull(err)
        assertEquals("Wrong username or password", err!!.message)
    }

    @Test
    fun checkStatusAcceptsOk() {
        checkStatus(JSONObject("""{"status":"ok","version":"1.16.1"}"""))
    }

    @Test
    fun parseRootUnwrapsAndValidates() {
        val ok = parseRoot("""{"subsonic-response":{"status":"ok","albumList2":{}}}""")
        assertEquals("ok", ok.optString("status"))
        val bad = runCatching { parseRoot("""{"subsonic-response":{"status":"failed"}}""") }.exceptionOrNull()
        assertNotNull(bad)
    }
}
