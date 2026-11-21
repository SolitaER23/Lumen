package com.example.app.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * NeoDB 集成：OAuth2 登录 + 专辑匹配 + 评分/评论拉取。
 *
 * 说明：
 * - 实例地址可配置（默认 https://neodb.social），便于自建实例；
 * - 登录采用 OAuth2 授权码 + PKCE（若实例要求 client_secret，可在设置里填）；
 * - 所有请求带 Bearer token，401 时用 refresh_token 自动续期一次；
 * - API 字段做了容错解析（不同版本字段名可能不同）。
 */
object Neodb {
    private const val PREFS = "lumen_neodb"
    private const val REDIRECT = "lumen://neodb"

    data class Config(
        val instance: String = "https://neodb.social",
        val clientId: String = "",
        val clientSecret: String = "",
    )

    data class Item(
        val uuid: String,
        val title: String,
        val artist: String,
        val ratingAvg: Float,
        val ratingCount: Int,
        val coverUrl: String?,
    )

    data class Review(
        val user: String,
        val rating: Float,
        val text: String,
        val created: String,
    )

    sealed class UiState {
        data object NotLoggedIn : UiState()
        data object Loading : UiState()
        data object Empty : UiState()
        data class Ready(val item: Item, val reviews: List<Review>) : UiState()
        data class Error(val message: String) : UiState()
    }

    var config by mutableStateOf(Config())
        private set
    var loggedIn by mutableStateOf(false)
        private set
    var username by mutableStateOf("")
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val p = prefs(ctx)
        config = Config(
            instance = p.getString("instance", "https://neodb.social")!!.trimEnd('/'),
            clientId = p.getString("client_id", "") ?: "",
            clientSecret = p.getString("client_secret", "") ?: "",
        )
        loggedIn = !p.getString("access_token", null).isNullOrBlank()
        username = p.getString("username", "") ?: ""
    }

    fun saveConfig(ctx: Context, cfg: Config) {
        config = cfg.copy(
            instance = cfg.instance.trim().trimEnd('/').ifBlank { "https://neodb.social" },
        )
        prefs(ctx).edit()
            .putString("instance", config.instance)
            .putString("client_id", cfg.clientId.trim())
            .putString("client_secret", cfg.clientSecret.trim())
            .apply()
    }

    private fun token(ctx: Context, key: String): String? =
        prefs(ctx).getString(key, null)?.takeIf { it.isNotBlank() }

    // ---------------- OAuth ----------------

    private fun pkcePair(): Pair<String, String> {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val verifier = android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        val challenge = android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        return verifier to challenge
    }

    /** 授权页 URL；调用方用浏览器打开。 */
    fun authorizeUrl(ctx: Context): String? {
        if (config.clientId.isBlank()) return null
        val (verifier, challenge) = pkcePair()
        prefs(ctx).edit().putString("pkce_verifier", verifier).apply()
        return Uri.parse("${config.instance}/oauth/authorize").buildUpon()
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "read")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    /** 处理回调：lumen://neodb?code=... 换 token 并拉取用户信息。 */
    fun completeLogin(ctx: Context, uri: Uri, onDone: (Boolean, String?) -> Unit) {
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            onDone(false, uri.getQueryParameter("error") ?: "未获得授权码")
            return
        }
        Thread {
            val result = runCatching {
                exchange(ctx, code)
                fetchMe(ctx)
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (result.isSuccess) {
                    loggedIn = true
                    lastError = null
                    onDone(true, null)
                } else {
                    lastError = result.exceptionOrNull()?.message
                    onDone(false, lastError)
                }
            }
        }.start()
    }

    private fun exchange(ctx: Context, code: String) {
        val verifier = prefs(ctx).getString("pkce_verifier", "") ?: ""
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT)
            .add("client_id", config.clientId)
            .apply { if (config.clientSecret.isNotBlank()) add("client_secret", config.clientSecret) }
            .apply { if (verifier.isNotBlank()) add("code_verifier", verifier) }
            .build()
        val req = Request.Builder().url("${config.instance}/oauth/token").post(body).build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("登录失败（HTTP ${resp.code}）${raw.take(120)}")
            val json = JSONObject(raw)
            val at = json.optString("access_token")
            if (at.isBlank()) error("登录失败：响应里没有 access_token")
            prefs(ctx).edit()
                .putString("access_token", at)
                .putString("refresh_token", json.optString("refresh_token"))
                .apply()
        }
    }

    private fun fetchMe(ctx: Context) {
        val json = get(ctx, "/api/me")
        username = json.optString("display_name").ifBlank { json.optString("username") }
        prefs(ctx).edit().putString("username", username).apply()
    }

    fun logout(ctx: Context) {
        prefs(ctx).edit()
            .remove("access_token").remove("refresh_token").remove("username").remove("pkce_verifier")
            .apply()
        loggedIn = false
        username = ""
    }

    // ---------------- HTTP ----------------

    private fun get(ctx: Context, path: String, params: Map<String, String> = emptyMap()): JSONObject {
        val url = Uri.parse(config.instance + path).buildUpon()
            .apply { params.forEach { (k, v) -> appendQueryParameter(k, v) } }
            .build().toString()
        val req = Request.Builder().url(url)
            .apply { token(ctx, "access_token")?.let { header("Authorization", "Bearer $it") } }
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.code == 401) {
                // 尝试 refresh 一次
                if (refresh(ctx)) return get(ctx, path, params)
                error("需要重新登录 NeoDB")
            }
            if (!resp.isSuccessful) error("NeoDB 请求失败（HTTP ${resp.code}）")
            return JSONObject(raw)
        }
    }

    private fun refresh(ctx: Context): Boolean {
        val rt = token(ctx, "refresh_token") ?: return false
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", rt)
            .add("client_id", config.clientId)
            .apply { if (config.clientSecret.isNotBlank()) add("client_secret", config.clientSecret) }
            .build()
        return runCatching {
            val req = Request.Builder().url("${config.instance}/oauth/token").post(body).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val json = JSONObject(resp.body?.string().orEmpty())
                val at = json.optString("access_token")
                if (at.isBlank()) return@use false
                prefs(ctx).edit().putString("access_token", at)
                    .putString("refresh_token", json.optString("refresh_token"))
                    .apply()
                true
            }
        }.getOrDefault(false)
    }

    // ---------------- 数据 ----------------

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[(（\\[].*?[)）\\]]"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun similarity(a: String, b: String): Double {
        val x = norm(a); val y = norm(b)
        if (x.isBlank() || y.isBlank()) return 0.0
        if (x == y) return 1.0
        if (x.contains(y) || y.contains(x)) return 0.85
        val common = x.toSet().intersect(y.toSet()).size
        return common.toDouble() / maxOf(x.length, y.length)
    }

    private fun parseItem(o: JSONObject): Item? {
        val uuid = o.optString("id").ifBlank { o.optString("uuid") }
        val title = o.optString("title").ifBlank { o.optString("display_title") }
        if (uuid.isBlank() || title.isBlank()) return null
        val artist = o.optString("artist").ifBlank {
            o.optJSONArray("artist")?.let { arr ->
                (0 until arr.length()).joinToString(", ") { arr.optString(it) }
            } ?: ""
        }
        var rating = o.optDouble("rating", 0.0).toFloat()
        if (rating > 5f) rating /= 2f
        return Item(
            uuid = uuid,
            title = title,
            artist = artist,
            ratingAvg = rating,
            ratingCount = o.optInt("rating_count", 0),
            coverUrl = o.optString("cover_image_url").takeIf { it.isNotBlank() },
        )
    }

    private fun cacheKey(albumId: String) = "item_$albumId"

    private fun cachedUuid(ctx: Context, albumId: String): String? =
        prefs(ctx).getString(cacheKey(albumId), null)?.takeIf { it.isNotBlank() }

    /** 搜专辑并挑选最匹配的条目；命中后缓存 uuid。 */
    fun lookup(ctx: Context, albumId: String, album: String, artist: String): Item? {
        cachedUuid(ctx, albumId)?.let { uuid ->
            runCatching { return parseItem(get(ctx, "/api/catalog/$uuid")) ?: error("空条目") }
        }
        val json = get(ctx, "/api/search", mapOf("query" to "$album $artist", "category" to "music"))
        val arr: JSONArray = json.optJSONArray("data") ?: json.optJSONArray("items") ?: return null
        var best: Item? = null
        var bestScore = 0.0
        for (i in 0 until arr.length()) {
            val item = parseItem(arr.optJSONObject(i) ?: continue) ?: continue
            val score = similarity(item.title, album) * 0.7 + similarity(item.artist, artist) * 0.3
            if (score > bestScore) {
                bestScore = score
                best = item
            }
        }
        val chosen = best?.takeIf { bestScore >= 0.55 } ?: return null
        prefs(ctx).edit().putString(cacheKey(albumId), chosen.uuid).apply()
        return chosen
    }

    fun reviews(ctx: Context, uuid: String): List<Review> {
        // NeoDB 不同版本路径略有差异，依次尝试
        val json = runCatching { get(ctx, "/api/review/", mapOf("item" to uuid, "page" to "1")) }
            .getOrElse { get(ctx, "/api/review", mapOf("item" to uuid, "page" to "1")) }
        val arr: JSONArray = json.optJSONArray("data") ?: json.optJSONArray("reviews") ?: JSONArray()
        val out = ArrayList<Review>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val body = o.optString("comment").ifBlank { o.optString("body") }
                .ifBlank { o.optString("content") }
            if (body.isBlank()) continue
            var rating = o.optDouble("rating", 0.0).toFloat()
            if (rating > 5f) rating /= 2f
            val userObj = o.optJSONObject("user")
            val user = userObj?.optString("display_name")?.ifBlank { userObj.optString("username") }
                ?: o.optString("user_name")
            out.add(
                Review(
                    user = user.ifBlank { "NeoDB 用户" },
                    rating = rating,
                    text = body,
                    created = o.optString("created_time").ifBlank { o.optString("created") },
                ),
            )
        }
        return out
    }

    /** 播放页调用：返回可渲染状态。 */
    fun loadForPlayer(ctx: Context, albumId: String, album: String, artist: String): UiState {
        if (!loggedIn) return UiState.NotLoggedIn
        return try {
            val item = lookup(ctx, albumId, album, artist) ?: return UiState.Empty
            UiState.Ready(item, reviews(ctx, item.uuid))
        } catch (e: Exception) {
            UiState.Error(e.message ?: "NeoDB 加载失败")
        }
    }
}
