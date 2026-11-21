package com.example.app.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Navidrome Stat 服务配置（地址 + 只读/管理 token）。 */
data class StatsConfig(val base: String, val token: String) {
    val complete: Boolean get() = base.startsWith("http") && token.isNotBlank()
}

object StatsStore {
    private const val FILE = "lumen_stats"

    fun load(ctx: Context): StatsConfig {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return StatsConfig(
            base = p.getString("base", "").orEmpty(),
            token = p.getString("token", "").orEmpty(),
        )
    }

    fun save(ctx: Context, cfg: StatsConfig) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("base", cfg.base.trim().trimEnd('/'))
            .putString("token", cfg.token.trim())
            .apply()
    }
}

/** 一眼能看懂的统计结果。 */
data class StatsDashboard(
    val plays: Int,
    val listenSeconds: Long,
    val tracks: Int,
    val clients: Int,
    val artists: List<StatsArtist>,
    val albums: List<StatsAlbum>,
    val history: List<StatsHistory>,
    val days: List<StatsDay>,
)

data class StatsArtist(val name: String, val count: Int)
data class StatsAlbum(val name: String, val artist: String, val count: Int)
data class StatsHistory(val title: String, val artist: String, val album: String, val playedAt: String)
data class StatsDay(val date: String, val count: Int)

/**
 * 对接 `StepaniaH/navidrome-stat`：`GET /api/stats/dashboard`，
 * 用 `Authorization: Bearer <token>` 认证。
 */
class StatsClient(private val cfg: StatsConfig) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun dashboard(days: Int): StatsDashboard {
        val url = cfg.base.trimEnd('/') +
            "/api/stats/dashboard?days=$days&timezone=Asia%2FShanghai"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${cfg.token}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("empty body")
            return parseDashboard(JSONObject(body))
        }
    }

    fun ping(): Boolean = runCatching { dashboard(0); true }.getOrDefault(false)
}

internal fun parseDashboard(root: JSONObject): StatsDashboard {
    val summary = root.optJSONObject("summary")
    val artists = root.optJSONArray("top_artists").objects().map {
        StatsArtist(name = it.optString("artist"), count = it.optInt("count"))
    }
    val albums = root.optJSONArray("top_albums").objects().map {
        StatsAlbum(
            name = it.optString("album"),
            artist = it.optString("artist"),
            count = it.optInt("count"),
        )
    }
    val history = root.optJSONArray("history").objects().map {
        StatsHistory(
            title = it.optString("title"),
            artist = it.optString("artist"),
            album = it.optString("album"),
            playedAt = it.optString("last_played_at"),
        )
    }
    val days = root.optJSONArray("daily").objects().map {
        StatsDay(date = it.optString("date"), count = it.optInt("count"))
    }
    return StatsDashboard(
        plays = summary?.optInt("total_plays") ?: 0,
        listenSeconds = summary?.optLong("total_listen_sec") ?: 0L,
        tracks = summary?.optInt("unique_tracks") ?: 0,
        clients = summary?.optInt("client_count") ?: 0,
        artists = artists,
        albums = albums,
        history = history,
        days = days,
    )
}

private fun org.json.JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optJSONObject(i) }
}
