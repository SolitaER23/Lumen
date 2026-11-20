package com.example.lumen.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val coverId: String,
    val songCount: Int,
    val year: Int,
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String,
    val disc: Int,
    val track: Int,
    val seconds: Int,
    val suffix: String,
)

/** OpenSubsonic 客户端：token + salt 认证，返回 JSON 用 org.json 解析。 */
class SubsonicClient(cfg: ServerConfig) {
    private val base = cfg.base.trimEnd('/')
    private val user = cfg.user
    private val password = cfg.password
    private val salt = Integer.toHexString((System.nanoTime() and 0xFFFFFFFF).toInt())
    private val token = md5(password + salt)

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun url(path: String, extra: Map<String, String> = emptyMap()): HttpUrl {
        val b = "$base/rest/$path".toHttpUrl().newBuilder()
            .addQueryParameter("u", user)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Lumen")
            .addQueryParameter("f", "json")
        extra.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    private fun get(path: String, extra: Map<String, String> = emptyMap()): JSONObject {
        val req = Request.Builder().url(url(path, extra)).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("empty body")
            val root = JSONObject(body).getJSONObject("subsonic-response")
            if (root.optString("status") != "ok") {
                val err = root.optJSONObject("error")
                error(err?.optString("message") ?: "Subsonic error")
            }
            return root
        }
    }

    fun ping(): Boolean = get("ping.view").optString("status") == "ok"

    fun albums(pageSize: Int = 500): List<Album> {
        val root = get("getAlbumList2.view", mapOf("type" to "alphabeticalByName", "size" to "$pageSize"))
        val arr = root.optJSONObject("albumList2")?.optJSONArray("album") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val a = arr.getJSONObject(i)
            Album(
                id = a.optString("id"),
                name = a.optString("name"),
                artist = a.optString("artist"),
                coverId = a.optString("coverArt"),
                songCount = a.optInt("songCount"),
                year = a.optInt("year"),
            )
        }
    }

    fun tracks(albumId: String): List<Track> {
        val root = get("getAlbum.view", mapOf("id" to albumId))
        val arr = root.optJSONObject("album")?.optJSONArray("song") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            Track(
                id = s.optString("id"),
                title = s.optString("title"),
                artist = s.optString("artist"),
                albumId = albumId,
                disc = s.optInt("discNumber", 1).coerceAtLeast(1),
                track = s.optInt("track", i + 1),
                seconds = s.optInt("duration"),
                suffix = s.optString("suffix"),
            )
        }.sortedWith(compareBy({ it.disc }, { it.track }))
    }

    fun streamUrl(id: String): String = url("stream.view", mapOf("id" to id)).toString()
    fun coverUrl(id: String, size: Int = 400): String =
        url("getCoverArt.view", mapOf("id" to id, "size" to "$size")).toString()

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}