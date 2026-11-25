package app.lumen.player.data

import android.content.Context
import app.lumen.player.util.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

enum class RadioVibe(
    private val zh: String,
    private val en: String,
    private val descZh: String,
    private val descEn: String,
    val valence: Float,
    val arousal: Float,
    val tempo: Float,
) {
    FOCUS("专注", "Focus", "稳定器乐，低干扰", "Steady instrumentals, low distraction", 0.55f, 0.35f, 90f),
    STUDY("学习", "Study", "平缓节拍，长时间陪伴", "Calm tempo for long sessions", 0.52f, 0.30f, 82f),
    WORK("工作", "Work", "清晰、有推进感", "Clear and gently driving", 0.55f, 0.40f, 100f),
    READING("阅读", "Reading", "安静、少人声", "Quiet, mostly instrumental", 0.50f, 0.28f, 74f),
    NIGHT("夜晚", "Night", "低速、低亮度的深夜", "Slow, dim late-night mood", 0.36f, 0.26f, 72f),
    SLEEP("睡前", "Sleep", "极低能量，舒缓收尾", "Very low energy, winding down", 0.30f, 0.14f, 60f),
    MORNING("清晨", "Morning", "明亮、轻盈地开始", "Bright and light to start the day", 0.66f, 0.46f, 102f),
    DUSK("黄昏", "Dusk", "温暖的落日余晖", "Warm end-of-day glow", 0.45f, 0.32f, 84f),
    RAINY("雨天", "Rainy", "潮湿、内省的慢节奏", "Damp, introspective slow tempo", 0.38f, 0.30f, 78f),
    CAFE("咖啡馆", "Café", "松弛的午后背景", "Relaxed afternoon backdrop", 0.55f, 0.38f, 94f),
    COMMUTE("通勤", "Commute", "路上平稳陪伴", "Steady company on the move", 0.52f, 0.50f, 112f),
    ROAD("公路", "Road trip", "开阔、持续向前", "Open, continuously forward", 0.60f, 0.55f, 118f),
    RUNNING("跑步", "Running", "高 BPM、强驱动", "High BPM, strong drive", 0.62f, 0.84f, 150f),
    WORKOUT("健身", "Workout", "高能量、硬朗", "High energy and punchy", 0.65f, 0.80f, 140f),
    ENERGY("活力", "Energy", "明亮、快速、有弹性", "Bright, fast and bouncy", 0.64f, 0.78f, 136f),
    PARTY("派对", "Party", "热烈、舞动", "Hot and danceable", 0.68f, 0.88f, 128f),
    RELAX("放松", "Relax", "柔和的呼吸感", "Soft, breathing pace", 0.50f, 0.24f, 70f),
    NOSTALGIA("怀旧", "Nostalgia", "旧时光的质感", "A texture from another time", 0.46f, 0.34f, 86f);

    fun label(): String = L10n.tf(zh, en)

    fun description(): String = L10n.tf(descZh, descEn)

    /**
     * 展示分组：只影响电台页的排版，不参与选曲。
     * 18 个预设平铺是 9 行双列卡片（约 1.5 屏），分组后一屏内可看全。
     */
    val group: RadioGroup
        get() = when (this) {
            FOCUS, STUDY, WORK, READING, NIGHT, SLEEP, MORNING, DUSK, CAFE -> RadioGroup.SCENE
            COMMUTE, ROAD, RUNNING, WORKOUT, ENERGY, PARTY -> RadioGroup.ACTIVITY
            RAINY, RELAX, NOSTALGIA -> RadioGroup.MOOD
        }
}

/** 电台预设的三类语境。 */
enum class RadioGroup(private val zh: String, private val en: String) {
    SCENE("场景", "Scene"),
    ACTIVITY("活动", "Activity"),
    MOOD("情绪", "Mood"),
    ;

    fun label(): String = L10n.tf(zh, en)

    /** 分组内的预设，保持枚举声明顺序（人工排过的高频优先）。 */
    fun vibes(): List<RadioVibe> = RadioVibe.entries.filter { it.group == this }
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

    /** trackId → 最近一次播放时间（毫秒）；用于新鲜度降权。 */
    private val lastPlayed = HashMap<String, Long>()

    /** 播放时调用，记录时间戳（进程内）。 */
    fun notePlayed(trackId: String) {
        lastPlayed[trackId] = System.currentTimeMillis()
    }

    /** 依据种子曲目坐标自动挑选最接近的氛围（省级用户一次点击）。 */
    fun autoVibe(valence: Float, arousal: Float, tempo: Float): RadioVibe {
        return RadioVibe.entries.minByOrNull { v ->
            val dv = (v.valence - valence) / 0.35f
            val da = (v.arousal - arousal) / 0.35f
            val dt = (v.tempo - tempo) / 60f
            dv * dv + da * da + dt * dt
        } ?: RadioVibe.NIGHT
    }

    /**
     * 选曲主流程。
     *
     * 必须跑在后台线程：它会 `SELECT` 整张 mood 表、解析每行 1024 维 CSV 特征，
     * 再对候选做 O(n·k) 的 1024 维点积（MMR 去同质化）。放主线程等于必然卡顿/ANR。
     */
    suspend fun build(
        ctx: Context,
        tracks: List<Track>,
        seedId: String?,
        vibe: RadioVibe,
        limit: Int = LIMIT,
        /** 0 = 完全贴近种子曲目，1 = 完全按氛围目标。 */
        strength: Float = 0.4f,
        /** true 时忽略传入的 vibe，按种子自动选择。 */
        autoPick: Boolean = false,
        /** 「换一批」时排除上一批已经出现过的曲目，保证换出来的真的是另一批。 */
        exclude: Set<String> = emptySet(),
    ): List<Track> = withContext(Dispatchers.Default) {
        buildOnCurrentThread(ctx, tracks, seedId, vibe, limit, strength, autoPick, exclude)
    }

    internal fun buildOnCurrentThread(
        ctx: Context,
        tracks: List<Track>,
        seedId: String?,
        vibe: RadioVibe,
        limit: Int = LIMIT,
        strength: Float = 0.4f,
        autoPick: Boolean = false,
        exclude: Set<String> = emptySet(),
    ): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        val moods = MoodStore.radio(ctx)
        val candidates = tracks.mapNotNull { track ->
            moods[track.id]?.let { track to it }
        }
        if (candidates.isEmpty()) return emptyList()

        val seed = candidates.firstOrNull { it.first.id == seedId }
            ?: candidates[Random.nextInt(candidates.size)]
        // 排除后剩下的候选太少时忽略排除（宁可与上一批有重复，也不要生成空电台）
        val pool = if (exclude.isEmpty()) {
            candidates
        } else {
            candidates.filterNot { it.first.id in exclude }.takeIf { it.size >= 5 } ?: candidates
        }
        val seedMood = seed.second
        val effectiveVibe = if (autoPick) autoVibe(seedMood.valence, seedMood.arousal, seedMood.tempo) else vibe
        // 该维度的全库均值（去偏用）；解析失败时为 null，退回原始余弦
        val mean = runCatching { MoodStore.meanFeature(ctx, seedMood.feature.size) }.getOrNull()
        val std = runCatching { MoodStore.featureStd(ctx, seedMood.feature.size) }.getOrNull()

        val scored = pool.map { (track, mood) ->
            val seedSim = similarity(seedMood, mood, mean, std)
            val vibeSim = 1f - moodDistance(mood, effectiveVibe)
            // 节奏连续性：与种子的 BPM 差越大扣分越多（±60 BPM 内线性衰减）
            val bpmGap = abs(mood.tempo - seedMood.tempo).coerceAtMost(60f)
            val tempoSim = 1f - bpmGap / 60f
            // 标签重合（Jaccard）：piano/electronic 这类音色层面的相似
            val tagSim = tagOverlap(seedMood.tags, mood.tags)
            // 新鲜度：最近播放过的曲子降权（24 小时内线性衰减到 0.5 倍）
            val last = lastPlayed[track.id] ?: 0L
            val ageH = if (last == 0L) 999f else ((System.currentTimeMillis() - last) / 3_600_000f)
            val fresh = (0.5f + 0.5f * (ageH / 24f).coerceAtMost(1f))
            val s = strength.coerceIn(0f, 1f)
            // 贴近度混音：s 越大越贴近氛围，越小越贴近种子本身的风格
            val mix = (1f - s) * seedSim + s * vibeSim
            val score = (0.70f * mix + 0.16f * tempoSim + 0.14f * tagSim) * fresh
            Triple(track, mood, score)
        }.sortedByDescending { it.third }

        // MMR 多样性：候选分 = score − λ·max(与已选曲目的嵌入相似度)
        val mmrLambda = 0.30f
        val picked = ArrayList<Track>()
        val pickedWithMood = ArrayList<Triple<Track, MoodResult, Float>>()
        val artistCount = HashMap<String, Int>()
        val albumCount = HashMap<String, Int>()

        val remaining = scored.toMutableList()
        while (picked.size < limit && remaining.isNotEmpty()) {
            var bestIdx = -1
            var bestVal = -1e9f
            for ((idx, item) in remaining.withIndex()) {
                val (track, mood, score) = item
                if (track.id == seed.first.id) continue
                val count = artistCount[track.artist] ?: 0
                if (count >= 2) continue
                val ac = albumCount[track.albumId] ?: 0
                if (ac >= 2) continue
                var maxSim = 0f
                for ((pt, pm, _) in pickedWithMood) {
                    val s2 = similarity(pm, mood, mean, std)
                    if (s2 > maxSim) maxSim = s2
                }
                val val2 = score - mmrLambda * maxSim
                if (val2 > bestVal) { bestVal = val2; bestIdx = idx }
            }
            if (bestIdx < 0) break
            val (track, mood, _) = remaining.removeAt(bestIdx)
            picked.add(track)
            pickedWithMood.add(Triple(track, mood, 0f))
            artistCount[track.artist] = (artistCount[track.artist] ?: 0) + 1
            albumCount[track.albumId] = (albumCount[track.albumId] ?: 0) + 1
        }
        return picked
    }


    /** 两组标签的 Jaccard 重合度（只比较近义的名字，避免大小写差异）。 */
    private fun tagOverlap(a: List<AudioTag>, b: List<AudioTag>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val sa = a.take(8).map { it.name.lowercase() }.toSet()
        val sb = b.take(8).map { it.name.lowercase() }.toSet()
        val inter = sa.intersect(sb).size
        val union = sa.union(sb).size
        return if (union == 0) 0f else inter.toFloat() / union
    }

    private fun similarity(a: MoodResult, b: MoodResult, mean: FloatArray? = null, std: FloatArray? = null): Float {
        val fa = a.feature
        val fb = b.feature
        // 只比较同维度向量：521（YAMNet）与 1024（CLAP）混算是无意义的
        if (fa.size >= 100 && fa.size == fb.size) {
            val n = minOf(fa.size, fb.size)
            var dot = 0.0
            var na = 0.0
            var nb = 0.0
            // 去偏：减去全库均值，消除公共分量后再算余弦
            val m = mean?.takeIf { it.size == fa.size }
            val sd = std?.takeIf { it.size == fa.size }
            for (i in 0 until n) {
                // z-score：先减均值再除以逐维标准差，等价于白化的对角近似
                val x = if (m != null) (fa[i] - m[i]) / (sd?.get(i) ?: 1f) else fa[i]
                val y = if (m != null) (fb[i] - m[i]) / (sd?.get(i) ?: 1f) else fb[i]
                dot += x * y
                na += x * x
                nb += y * y
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
