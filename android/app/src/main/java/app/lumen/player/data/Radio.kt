package app.lumen.player.data

import android.content.Context
import app.lumen.player.util.L10n
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

enum class RadioVibe(
    private val zh: String,
    private val en: String,
    val valence: Float,
    val arousal: Float,
    val tempo: Float,
) {
    FOCUS("专注", "Focus", 0.55f, 0.35f, 90f),
    NIGHT("夜晚", "Night", 0.36f, 0.26f, 72f),
    ENERGY("活力", "Energy", 0.62f, 0.78f, 138f),
    COMMUTE("通勤", "Commute", 0.52f, 0.50f, 110f);

    fun label(): String = L10n.tf(zh, en)
}

/**
 * 本地氛围电台：
 * - 曲库索引缓存在 filesDir，避免每次进入都重新请求专辑/曲目；
 * - 相似度优先使用 YAMNet 521 维平均分，旧数据回退到情绪坐标 + tempo；
 * - 所有计算在本机完成，不产生网络请求。
 */
object Radio {
    private const val CACHE = "radio_tracks.json"
    private const val LIMIT = 30

    suspend fun loadLibrary(
        ctx: Context,
        client: SubsonicClient,
        onProgress: (Float) -> Unit,
    ): List<Track> {
        readCache(ctx)?.takeIf { it.isNotEmpty() }?.let { return it }
        val albums = client.albums()
        val out = ArrayList<Track>()
        albums.forEachIndexed { index, album ->
            runCatching { client.tracks(album.id) }.getOrDefault(emptyList()).forEach { out.add(it) }
            onProgress((index + 1).toFloat() / albums.size.coerceAtLeast(1))
        }
        writeCache(ctx, out)
        return out
    }

    fun clearCache(ctx: Context) {
        runCatching { File(ctx.filesDir, CACHE).delete() }
    }

    fun build(
        ctx: Context,
        tracks: List<Track>,
        seedId: String?,
        vibe: RadioVibe,
        limit: Int = LIMIT,
    ): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        val moods = MoodStore.radio(ctx)
        val candidates = tracks.mapNotNull { track ->
            moods[track.id]?.let { track to it }
        }
        if (candidates.isEmpty()) return emptyList()

        val seed = candidates.firstOrNull { it.first.id == seedId }
            ?: candidates[Random.nextInt(candidates.size)]
        val seedMood = seed.second

        val scored = candidates.map { (track, mood) ->
            val seedSim = similarity(seedMood, mood)
            val vibeSim = 1f - moodDistance(mood, vibe)
            val score = 0.60f * seedSim + 0.40f * vibeSim
            Triple(track, mood, score)
        }.sortedByDescending { it.third }

        val picked = ArrayList<Track>(limit)
        val artistCount = HashMap<String, Int>()

        for ((track, _, _) in scored) {
            if (picked.size >= limit) break
            // 种子只用于相似度，不强制放在队首，避免切换氛围时重启当前曲目
            if (track.id == seed.first.id) continue
            val count = artistCount[track.artist] ?: 0
            if (count >= 2) continue
            picked.add(track)
            artistCount[track.artist] = count + 1
        }
        return picked
    }

    private fun similarity(a: MoodResult, b: MoodResult): Float {
        val fa = a.feature
        val fb = b.feature
        if (fa.size >= 100 && fb.size >= 100) {
            val n = minOf(fa.size, fb.size)
            var dot = 0.0
            var na = 0.0
            var nb = 0.0
            for (i in 0 until n) {
                dot += fa[i] * fb[i]
                na += fa[i] * fa[i]
                nb += fb[i] * fb[i]
            }
            if (na > 0.0 && nb > 0.0) return (dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb))).toFloat()
        }
        return 1f - moodDistance(a, b)
    }

    private fun moodDistance(a: MoodResult, b: MoodResult): Float {
        val da = abs(a.valence - b.valence) * 0.5f
        val db = abs(a.arousal - b.arousal) * 0.4f
        val ta = ((a.tempo - 60f) / 120f).coerceIn(0f, 1f)
        val tb = ((b.tempo - 60f) / 120f).coerceIn(0f, 1f)
        return (da + db + abs(ta - tb) * 0.1f).coerceIn(0f, 1f)
    }

    private fun moodDistance(m: MoodResult, vibe: RadioVibe): Float {
        val v = abs(m.valence - vibe.valence) * 0.5f
        val a = abs(m.arousal - vibe.arousal) * 0.4f
        val tempoNorm = ((vibe.tempo - 60f) / 120f).coerceIn(0f, 1f)
        val t = abs(((m.tempo - 60f) / 120f).coerceIn(0f, 1f) - tempoNorm) * 0.1f
        return (v + a + t).coerceIn(0f, 1f)
    }

    private fun readCache(ctx: Context): List<Track>? = runCatching {
        val f = File(ctx.filesDir, CACHE)
        if (!f.exists()) return null
        val arr = JSONArray(f.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Track(
                id = o.optString("id"),
                title = o.optString("title"),
                artist = o.optString("artist"),
                albumId = o.optString("albumId"),
                album = o.optString("album"),
                disc = o.optInt("disc", 1),
                track = o.optInt("track", 0),
                seconds = o.optInt("seconds", 0),
                suffix = o.optString("suffix"),
                starred = o.optBoolean("starred", false),
            )
        }
    }.getOrNull()

    private fun writeCache(ctx: Context, tracks: List<Track>) {
        runCatching {
            val arr = JSONArray()
            tracks.forEach { t ->
                arr.put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("albumId", t.albumId)
                        put("album", t.album)
                        put("disc", t.disc)
                        put("track", t.track)
                        put("seconds", t.seconds)
                        put("suffix", t.suffix)
                        put("starred", t.starred)
                    },
                )
            }
            File(ctx.filesDir, CACHE).writeText(arr.toString())
        }
    }
}
