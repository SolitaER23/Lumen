package app.lumen.player.data

import app.lumen.player.util.L10n
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@kotlinx.serialization.Serializable
data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val coverId: String,
    val songCount: Int,
    val year: Int,
)

/** 一行歌词：startMs 为 0 表示无时间轴（纯文本歌词） */
data class LyricLine(val startMs: Long, val text: String)

data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val coverId: String,
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String,
    val album: String = "",
    val disc: Int = 1,
    val track: Int = 0,
    val seconds: Int = 0,
    val suffix: String = "",
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
            album = s.optString("album"),
            disc = s.optInt("discNumber", 1).coerceAtLeast(1),
            track = s.optInt("track", i + 1),
            seconds = s.optInt("duration"),
            suffix = s.optString("suffix"),
        )
    }.sortedWith(compareBy({ it.disc }, { it.track }))
}

/** `getArtists` 的 `subsonic-response` 根节点 → 艺人列表（按名字排序） */
internal fun parseArtists(root: JSONObject): List<Artist> {
    val indexes = root.optJSONObject("artists")?.optJSONArray("index") ?: return emptyList()
    val out = mutableListOf<Artist>()
    for (i in 0 until indexes.length()) {
        val arr = indexes.getJSONObject(i).optJSONArray("artist") ?: continue
        for (j in 0 until arr.length()) {
            val a = arr.getJSONObject(j)
            val name = a.optString("name")
            if (name.isBlank()) continue
            out += Artist(
                id = a.optString("id"),
                name = name,
                albumCount = a.optInt("albumCount"),
                coverId = a.optString("coverArt").ifBlank { a.optString("id") },
            )
        }
    }
    return out.sortedBy { it.name.lowercase() }
}

/** `getArtist` → 该艺人的专辑（同时带回艺人信息） */
internal fun parseArtistDetail(root: JSONObject): Pair<Artist, List<Album>>? {
    val a = root.optJSONObject("artist") ?: return null
    val name = a.optString("name")
    val artist = Artist(
        id = a.optString("id"),
        name = name,
        albumCount = a.optInt("albumCount"),
        coverId = a.optString("coverArt").ifBlank { a.optString("id") },
    )
    val arr = a.optJSONArray("album") ?: return artist to emptyList()
    val albums = (0 until arr.length()).map { i ->
        val al = arr.getJSONObject(i)
        Album(
            id = al.optString("id"),
            name = al.optString("name"),
            artist = al.optString("artist").ifBlank { name },
            coverId = al.optString("coverArt"),
            songCount = al.optInt("songCount"),
            year = al.optInt("year"),
        )
    }.sortedByDescending { it.year }
    return artist to albums
}

/** `search3` → (艺人, 专辑) */
internal fun parseSearch(root: JSONObject): Pair<List<Artist>, List<Album>> {
    val r = root.optJSONObject("searchResult3") ?: return emptyList<Artist>() to emptyList()
    val artists = r.optJSONArray("artist")?.let { arr ->
        (0 until arr.length()).map { i ->
            val a = arr.getJSONObject(i)
            Artist(
                id = a.optString("id"),
                name = a.optString("name"),
                albumCount = a.optInt("albumCount"),
                coverId = a.optString("coverArt").ifBlank { a.optString("id") },
            )
        }
    } ?: emptyList()
    val albums = r.optJSONArray("album")?.let { arr ->
        (0 until arr.length()).map { i ->
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
    } ?: emptyList()
    return artists to albums
}
/** search3 全量解析：艺人 + 专辑 + 曲目。 */
internal fun parseSearchFull(root: JSONObject): Triple<List<Artist>, List<Album>, List<Track>> {
    val (artists, albums) = parseSearch(root)
    val songs = ArrayList<Track>()
    root.optJSONObject("searchResult3")?.optJSONArray("song")?.let { arr ->
        for (k in 0 until arr.length()) {
            val o = arr.optJSONObject(k) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            songs.add(
                Track(
                    id = id,
                    title = o.optString("title", ""),
                    artist = o.optString("artist", ""),
                    albumId = o.optString("albumId", ""),
                    album = o.optString("album", ""),
                    disc = o.optInt("discNumber", 1).coerceAtLeast(1),
                    track = o.optInt("track", 0),
                    seconds = o.optInt("duration", 0),
                    suffix = o.optString("suffix", ""),
                ),
            )
        }
    }
    return Triple(artists, albums, songs)
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

/** Retrofit 接口：所有 Subsonic 方法都是 `rest/<method>` + query 参数。 */
private interface SubsonicApi {
    @retrofit2.http.GET("rest/{method}")
    fun call(
        @retrofit2.http.Path("method") method: String,
        @retrofit2.http.QueryMap params: Map<String, String>,
    ): retrofit2.Call<JsonObject>
}

/** OpenSubsonic 客户端：token + salt 认证。 */
class SubsonicClient(cfg: ServerConfig, saltOverride: String? = null) {
    private val base = cfg.base.trimEnd('/')

    init {
        // 策略必须在数据层兜底：登录页只是"第一道门"
        NetworkGate.check(base)
    }
    private val user = cfg.user
    private val password = cfg.password
    // 注意：构造参数不能与属性同名，否则属性初始化时会被参数（null）遮蔽，token 会算成 md5(pw + "null")
    private val salt = saltOverride ?: Integer.toHexString((System.nanoTime() and 0xFFFFFFFF).toInt())
    private val token = md5Hex(password + salt)


    private val http = app.lumen.player.util.Http.client

    // Retrofit + Gson 负责请求/解析，Gson 的 JsonObject 再转成 org.json 供现有解析器复用
    private val api: SubsonicApi by lazy {
        Retrofit.Builder()
            .baseUrl("$base/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SubsonicApi::class.java)
    }

    private fun get(path: String, extra: Map<String, String> = emptyMap()): JSONObject {
        val params = HashMap<String, String>(extra)
        params["u"] = user
        params["t"] = token
        params["s"] = salt
        params["v"] = "1.16.1"
        params["c"] = "Lumen"
        params["f"] = "json"
        val response = try {
            api.call(path, params).execute()
        } catch (e: Exception) {
            error(e.message ?: L10n.t("网络错误"))
        }
        if (!response.isSuccessful) error("HTTP ${response.code()}")
        val json = response.body() ?: error("empty body")
        return parseRoot(json.toString())
    }

    fun ping(): Boolean = get("ping.view").optString("status") == "ok"

    /** 分页拉取全部专辑（服务端单页上限通常为 500）。 */
    fun albums(pageSize: Int = 500): List<Album> {
        val out = ArrayList<Album>()
        var offset = 0
        while (offset < 20000) {
            val page = parseAlbumList(
                get(
                    "getAlbumList2.view",
                    mapOf(
                        "type" to "alphabeticalByName",
                        "size" to "$pageSize",
                        "offset" to "$offset",
                    ),
                ),
            )
            out += page
            if (page.size < pageSize) break
            offset += pageSize
        }
        return out
    }

    fun tracks(albumId: String): List<Track> =
        parseAlbumDetail(get("getAlbum.view", mapOf("id" to albumId)), albumId)

    fun streamUrl(id: String): String = restUrl(base, "stream.view", user, token, salt, extra = mapOf("id" to id)).toString()

    fun artists(): List<Artist> = parseArtists(get("getArtists.view"))

    fun artistDetail(id: String): Pair<Artist, List<Album>>? =
        parseArtistDetail(get("getArtist.view", mapOf("id" to id)))

    fun search(query: String): Triple<List<Artist>, List<Album>, List<Track>> =
        parseSearchFull(
            get(
                "search3.view",
                mapOf("query" to query, "artistCount" to "20", "albumCount" to "30", "songCount" to "60"),
            ),
        )

    /** 播放上报：submission=false 为 Now Playing，true 才计入播放次数（Navidrome 统计依赖它）。 */
    fun scrobble(trackId: String, submission: Boolean) {
        get(
            "scrobble.view",
            mapOf(
                "id" to trackId,
                "submission" to submission.toString(),
                "time" to System.currentTimeMillis().toString(),
            ),
        )
    }

    /** 歌词：优先 OpenSubsonic 的 getLyricsBySongId（带时间轴），退回旧的 getLyrics 纯文本 */
    fun lyrics(trackId: String, title: String, artist: String): List<LyricLine>? {
        val byId = runCatching { get("getLyricsBySongId.view", mapOf("id" to trackId)) }.getOrNull()
        byId?.optJSONObject("lyricsList")?.optJSONArray("structuredLyrics")?.let { arr ->
            for (i in 0 until arr.length()) {
                val lines = arr.getJSONObject(i).optJSONArray("line") ?: continue
                val out = mutableListOf<LyricLine>()
                for (j in 0 until lines.length()) {
                    val o = lines.getJSONObject(j)
                    val v = o.optString("value")
                    if (v.isNotBlank()) out.add(LyricLine(o.optLong("start"), v))
                }
                if (out.isNotEmpty()) return out
            }
        }
        val legacy = runCatching {
            get("getLyrics.view", mapOf("artist" to artist, "title" to title))
        }.getOrNull() ?: return null
        val value = legacy.optJSONObject("lyrics")?.optString("value").orEmpty()
        if (value.isBlank()) return null
        return value.split("\n").filter { it.isNotBlank() }.map { LyricLine(0L, it.trim()) }
    }

    fun coverUrl(id: String, size: Int = 400): String =
        restUrl(base, "getCoverArt.view", user, token, salt, extra = mapOf("id" to id, "size" to "$size")).toString()
}
