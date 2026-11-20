package com.example.app.data

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

// ---------------------------------------------------------------------------
// 纯函数：URL 构造与响应解析（可在 JVM 单元测试里直接验证）
// ---------------------------------------------------------------------------

internal fun md5Hex(s: String): String {
    val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
    return d.joinToString("") { "%02x".format(it) }
}

internal fun restUrl(
    base: String,
    path: String,
    user: String,
    token: String,
    salt: String,
    version: String = "1.16.1",
    client: String = "Lumen",
    extra: Map<String, String> = emptyMap(),
): HttpUrl {
    val b = "${base.trimEnd('/')}/rest/$path".toHttpUrl().newBuilder()
        .addQueryParameter("u", user)
        .addQueryParameter("t", token)
        .addQueryParameter("s", salt)
        .addQueryParameter("v", version)
        .addQueryParameter("c", client)
        .addQueryParameter("f", "json")
    extra.forEach { (k, v) -> b.addQueryParameter(k, v) }
    return b.build()
}

/** `getAlbumList2` 的 `subsonic-response` 根节点 → 专辑列表 */
internal fun parseAlbumList(root: JSONObject): List<Album> {
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

/** `getAlbum` 的 `subsonic-response` 根节点 → 曲目（按碟号、轨号排序） */
internal fun parseAlbumDetail(root: JSONObject, albumId: String): List<Track> {
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

/** 服务端返回 `status=ok` 才算成功，否则抛出带服务端消息的异常 */
internal fun checkStatus(root: JSONObject) {
    if (root.optString("status") != "ok") {
        val err = root.optJSONObject("error")
        error(err?.optString("message")?.takeIf { it.isNotBlank() } ?: "Subsonic error")
    }
}

/** 把响应 body 解成 `subsonic-response` 根节点 */
internal fun parseRoot(body: String): JSONObject {
    val root = JSONObject(body).getJSONObject("subsonic-response")
    checkStatus(root)
    return root
}

/** OpenSubsonic 客户端：token + salt 认证。 */
class SubsonicClient(cfg: ServerConfig, saltOverride: String? = null) {
    private val base = cfg.base.trimEnd('/')
    private val user = cfg.user
    private val password = cfg.password
    // 注意：构造参数不能与属性同名，否则属性初始化时会被参数（null）遮蔽，token 会算成 md5(pw + "null")
    private val salt = saltOverride ?: Integer.toHexString((System.nanoTime() and 0xFFFFFFFF).toInt())
    private val token = md5Hex(password + salt)


    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun get(path: String, extra: Map<String, String> = emptyMap()): JSONObject {
        val req = Request.Builder().url(restUrl(base, path, user, token, salt, extra = extra)).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("empty body")
            return parseRoot(body)
        }
    }

    fun ping(): Boolean = get("ping.view").optString("status") == "ok"

    fun albums(pageSize: Int = 500): List<Album> =
        parseAlbumList(get("getAlbumList2.view", mapOf("type" to "alphabeticalByName", "size" to "$pageSize")))

    fun tracks(albumId: String): List<Track> =
        parseAlbumDetail(get("getAlbum.view", mapOf("id" to albumId)), albumId)

    fun streamUrl(id: String): String = restUrl(base, "stream.view", user, token, salt, extra = mapOf("id" to id)).toString()

    fun coverUrl(id: String, size: Int = 400): String =
        restUrl(base, "getCoverArt.view", user, token, salt, extra = mapOf("id" to id, "size" to "$size")).toString()
}
