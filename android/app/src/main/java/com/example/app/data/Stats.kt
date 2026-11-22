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
        val legacy = p.getString("token", "").orEmpty()
        val enc = p.getString("token_enc", null)
        val token = when {
            enc != null -> SettingsStore.decryptString(ctx, enc).orEmpty()
            legacy.isNotBlank() -> {
                // 旧版本明文：迁移为 Keystore 密文后删除明文
                val migrated = SettingsStore.encryptString(ctx, legacy)
                if (migrated != null) {
                    p.edit().putString("token_enc", migrated).remove("token").apply()
                }
                legacy
            }
            else -> ""
        }
        return StatsConfig(
            base = p.getString("base", "").orEmpty(),
            token = token,
        )
    }

    fun save(ctx: Context, cfg: StatsConfig) {
        val editor = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("base", cfg.base.trim().trimEnd('/'))
        val token = cfg.token.trim()
        val enc = if (token.isBlank()) null else SettingsStore.encryptString(ctx, token)
        if (enc != null) {
            editor.putString("token_enc", enc).remove("token").apply()
        } else {
            // Keystore 不可用时退回明文，避免丢配置
            editor.putString("token", token).remove("token_enc").apply()
        }
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
/** 带可读原因的统计请求异常。 */
class StatsException(message: String) : Exception(message)

class StatsClient(private val cfg: StatsConfig) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun dashboard(days: Int): StatsDashboard {
        var base = cfg.base.trim().trimEnd('/')
        // 允许用户直接粘贴 dashboard 地址
        base = base.removeSuffix("/api/stats/dashboard")
        val url = "$base/api/stats/dashboard?days=$days&timezone=Asia%2FShanghai"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${cfg.token}")
            .header("Accept", "application/json")
            .build()
        val resp = try {
            http.newCall(req).execute()
        } catch (e: javax.net.ssl.SSLException) {
            throw StatsException("SSL 证书校验失败（自签证书请改用 http 或受信任证书）")
        } catch (e: java.net.SocketTimeoutException) {
            throw StatsException("连接超时：服务器不可达或端口不对")
        } catch (e: java.net.ConnectException) {
            throw StatsException("连接被拒绝：检查地址与端口")
        } catch (e: java.net.UnknownHostException) {
            throw StatsException("域名解析失败：检查地址")
        } catch (e: java.io.IOException) {
            throw StatsException("无法连接服务器（连接被中断或服务未启动）：${e.message ?: ""}".trim())
        } catch (e: Exception) {
            throw StatsException(e.message ?: "网络错误")
        }
        resp.use {
            val code = resp.code
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                when (code) {
                    401, 403 -> throw StatsException("认证失败（HTTP $code）：STATS_API_TOKEN 不正确或无权访问")
                    404 -> throw StatsException("接口不存在（404）：地址可能缺少路径前缀，或服务版本过旧")
                    else -> throw StatsException("服务返回 HTTP $code")
                }
            }
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw StatsException("返回的不是 JSON（可能是反向代理的错误页）")
            return parseDashboard(json)
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
