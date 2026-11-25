package app.lumen.player.data

import app.lumen.player.util.L10n
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
                // 旧版本明文：迁移为 Keystore 密文后删除明文；加密不可用时先保留明文，下次再试
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

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * 保存统计配置；返回 false 表示 Token **没能加密落盘**。
     *
     * 旧实现在 Keystore 不可用时把 Token 明文写进 prefs，直接违反隐私条款
     * 「统计 Token 使用 Android Keystore 加密」的承诺。现在宁可保存失败（由 UI 提示），
     * 也不写明文。
     */
    fun save(ctx: Context, cfg: StatsConfig): Boolean {
        val token = cfg.token.trim()
        val enc = if (token.isBlank()) "" else {
            SettingsStore.encryptString(ctx, token)
                ?: return false
        }
        val editor = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("base", cfg.base.trim().trimEnd('/'))
        if (token.isBlank()) {
            editor.remove("token_enc").remove("token")
        } else {
            editor.putString("token_enc", enc).remove("token")
        }
        return editor.commit()
    }
}

/** 由 dashboard 数据推导出的洞察：活跃度、趋势、时段/星期分布。 */
data class StatsInsights(
    val activeDays: Int,
    val avgPerDay: Int,
    val peakDay: StatsDay?,
    /** 后半程相对前半程的变化百分比（短期趋势） */
    val trendPct: Int,
    /** 0=周日 … 6=周六 */
    val weekday: IntArray,
    /** 6 个时段：Ф0-4, 4-8, 8-12, 12-16, 16-20, 20-24 */
    val hourBuckets: IntArray,
    /** 24 小时逐时计数，用于热力条 */
    val hourCounts: IntArray,
    val uniqueArtists: Int,
    val uniqueAlbums: Int,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

internal fun parsePlayedAt(raw: String): java.time.LocalDateTime? {
    if (raw.isBlank()) return null
    val s = raw.trim().replace(" ", "T")
    runCatching { return java.time.Instant.parse(raw.trim()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }
    runCatching { return java.time.OffsetDateTime.parse(raw.trim()).toLocalDateTime() }
    runCatching { return java.time.LocalDateTime.parse(s.take(19)) }
    return null
}

fun StatsDashboard.insights(): StatsInsights {
    val active = days.filter { it.count > 0 }
    val peak = days.maxByOrNull { it.count }
    val total = days.sumOf { it.count }
    val half = days.size / 2
    val first = days.take(half).sumOf { it.count }
    val second = days.drop(half).sumOf { it.count }
    val trend = when {
        first <= 0 && second <= 0 -> 0
        first <= 0 -> 100
        else -> ((second - first) * 100.0 / first).toInt()
    }
    val weekday = IntArray(7)
    val hours = IntArray(6)
    val hourCounts = IntArray(24)
    history.mapNotNull { parsePlayedAt(it.playedAt) }.forEach { t ->
        // DayOfWeek: MONDAY=1 … SUNDAY=7 → 0=周日
        weekday[t.dayOfWeek.value % 7]++
        hours[(t.hour / 4).coerceIn(0, 5)]++
        hourCounts[t.hour.coerceIn(0, 23)]++
    }
    return StatsInsights(
        activeDays = active.size,
        avgPerDay = if (days.isEmpty()) 0 else (total.toFloat() / days.size).toInt(),
        peakDay = peak,
        trendPct = trend,
        weekday = weekday,
        hourBuckets = hours,
        hourCounts = hourCounts,
        uniqueArtists = artists.size,
        uniqueAlbums = albums.size,
    )
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
            throw StatsException(L10n.t("SSL 证书校验失败（自签证书请改用 http 或受信任证书）"))
        } catch (e: java.net.SocketTimeoutException) {
            throw StatsException(L10n.t("连接超时：服务器不可达或端口不对"))
        } catch (e: java.net.ConnectException) {
            throw StatsException(L10n.t("连接被拒绝：检查地址与端口"))
        } catch (e: java.net.UnknownHostException) {
            throw StatsException(L10n.t("域名解析失败：检查地址"))
        } catch (e: java.io.IOException) {
            throw StatsException(L10n.tf("无法连接服务器（连接被中断或服务未启动）：${e.message ?: ""}".trim(), "Cannot reach server (interrupted or not running): ${e.message ?: ""}".trim()))
        } catch (e: Exception) {
            throw StatsException(e.message ?: L10n.t("网络错误"))
        }
        resp.use {
            val code = resp.code
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                when (code) {
                    401, 403 -> throw StatsException(L10n.tf("认证失败（HTTP $code）：STATS_API_TOKEN 不正确或无权访问", "Authentication failed (HTTP $code): STATS_API_TOKEN is wrong or not authorized"))
                    404 -> throw StatsException(L10n.t("接口不存在（404）：地址可能缺少路径前缀，或服务版本过旧"))
                    else -> throw StatsException(L10n.tf("服务返回 HTTP $code", "Server returned HTTP $code"))
                }
            }
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw StatsException(L10n.t("返回的不是 JSON（可能是反向代理的错误页）"))
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
