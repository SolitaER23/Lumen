package app.lumen.player.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 内部音频标签，不面向用户展示。 */
data class AudioTag(val name: String, val score: Float)

/** 分析出的段落：约 12 秒一段，记录该段最突出的 AudioSet 标签。 */
data class AudioSegment(val startMs: Int, val endMs: Int, val tags: List<String>)

/** 一条心情分析结果（坐标是主数据，label 只是坐标的离散投影）。 */
data class MoodResult(
    val trackId: String,
    val valence: Float,
    val arousal: Float,
    val label: String,
    val tempo: Float = 0f,
    /** 拍点时间（毫秒，升序），用于背景随节拍闪烁。 */
    val beatsMs: IntArray = IntArray(0),
    /** 短时能量包络（0..1），每 envStepMs 一个点。 */
    val env: FloatArray = FloatArray(0),
    val envStepMs: Int = 100,
    /** YAMNet top-K AudioSet 标签，仅供内部检索/推荐使用。 */
    val tags: List<AudioTag> = emptyList(),
    /** YAMNet 521 维平均分，用于本地氛围电台相似度；旧数据为空。 */
    val feature: FloatArray = FloatArray(0),
    /** 段落级标签，用于全局背景与迷你播放条的当前声音类型。 */
    val segments: List<AudioSegment> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

object MoodStore {
    private const val DB = "moods.db"
    private const val TABLE = "moods"

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, DB, null, 8) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE (" +
                    "track_id TEXT PRIMARY KEY, valence REAL, arousal REAL, label TEXT, tempo REAL, " +
                    "beats TEXT, env TEXT, env_step INTEGER, updated INTEGER, tags TEXT, feature TEXT, segments TEXT)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // v6 只增加内部标签列，保留已有分析结果
            if (oldVersion < 6) {
                runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN tags TEXT") }
            }
            if (oldVersion < 7) {
                runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN feature TEXT") }
            }
            if (oldVersion < 8) {
                runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN segments TEXT") }
            }
        }

        override fun onOpen(db: SQLiteDatabase) {
            super.onOpen(db)
            val cols = LinkedHashSet<String>()
            runCatching {
                db.rawQuery("PRAGMA table_info($TABLE)", null).use { c ->
                    while (c.moveToNext()) cols.add(c.getString(1))
                }
            }
            app.lumen.player.util.Logx.i("LumenMood", "db v=${db.version} cols=$cols")
        }
    }

    private fun db(ctx: Context) = Helper(ctx).writableDatabase

    private fun encodeInts(a: IntArray): String =
        if (a.isEmpty()) "" else a.joinToString(",")

    private fun decodeInts(s: String?): IntArray =
        if (s.isNullOrBlank()) IntArray(0) else s.split(',').mapNotNull { it.toIntOrNull() }.toIntArray()

    private fun encodeFloats(a: FloatArray): String =
        if (a.isEmpty()) "" else a.joinToString(",") { "%.2f".format(it) }

    private fun decodeFloats(s: String?): FloatArray =
        if (s.isNullOrBlank()) FloatArray(0) else s.split(',').mapNotNull { it.toFloatOrNull() }.toFloatArray()

    private fun encodeTags(tags: List<AudioTag>): String {
        if (tags.isEmpty()) return ""
        val arr = JSONArray()
        tags.forEach { tag ->
            arr.put(JSONObject().put("n", tag.name).put("s", tag.score.toDouble()))
        }
        return arr.toString()
    }

    private fun encodeFeature(feature: FloatArray): String =
        if (feature.isEmpty()) "" else feature.joinToString(",") { "%.3f".format(it) }

    private fun decodeFeature(raw: String?): FloatArray =
        if (raw.isNullOrBlank()) FloatArray(0)
        else raw.split(',').mapNotNull { it.toFloatOrNull() }.toFloatArray()

    private fun encodeSegments(segments: List<AudioSegment>): String {
        if (segments.isEmpty()) return ""
        val arr = JSONArray()
        segments.forEach { seg ->
            arr.put(
                JSONObject()
                    .put("s", seg.startMs)
                    .put("e", seg.endMs)
                    .put("t", JSONArray(seg.tags)),
            )
        }
        return arr.toString()
    }

    private fun decodeSegments(raw: String?): List<AudioSegment> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val tags = o.optJSONArray("t") ?: JSONArray()
                AudioSegment(
                    startMs = o.optInt("s", 0),
                    endMs = o.optInt("e", 0),
                    tags = (0 until tags.length()).mapNotNull { j -> tags.optString(j).takeIf { it.isNotBlank() } },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun decodeTags(raw: String?): List<AudioTag> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                AudioTag(o.optString("n"), o.optDouble("s", 0.0).toFloat())
            }
        }.getOrDefault(emptyList())
    }

    fun put(ctx: Context, r: MoodResult) {
        val v = ContentValues().apply {
            put("track_id", r.trackId)
            put("valence", r.valence)
            put("arousal", r.arousal)
            put("label", r.label)
            put("tempo", r.tempo)
            put("beats", encodeInts(r.beatsMs))
            put("env", encodeFloats(r.env))
            put("env_step", r.envStepMs)
            put("updated", r.updatedAt)
            put("tags", encodeTags(r.tags))
            put("feature", encodeFeature(r.feature))
            put("segments", encodeSegments(r.segments))
        }
        runCatching { db(ctx).insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE) }
            .onFailure { android.util.Log.e("LumenMood", "mood put failed", it) }
    }

    fun get(ctx: Context, trackId: String): MoodResult? = runCatching {
        db(ctx).query(TABLE, null, "track_id=?", arrayOf(trackId), null, null, null).use { c ->
            if (c.moveToFirst()) {
                MoodResult(
                    trackId = c.getString(0),
                    valence = c.getFloat(1),
                    arousal = c.getFloat(2),
                    label = c.getString(3),
                    tempo = c.getFloat(4),
                    beatsMs = decodeInts(c.getString(5)),
                    env = decodeFloats(c.getString(6)),
                    envStepMs = if (c.isNull(7)) 100 else c.getInt(7),
                    tags = decodeTags(if (c.isNull(9)) null else c.getString(9)),
                    feature = decodeFeature(if (c.isNull(10)) null else c.getString(10)),
                    segments = decodeSegments(if (c.isNull(11)) null else c.getString(11)),
                    updatedAt = c.getLong(8),
                )
            } else null
        }
    }.onFailure { android.util.Log.e("LumenMood", "mood get failed", it) }.getOrNull()

    /** 电台只需要坐标、标签与 521 维特征，不加载 beats/env。 */
    fun radio(ctx: Context): Map<String, MoodResult> = runCatching {
        val out = LinkedHashMap<String, MoodResult>()
        db(ctx).rawQuery(
            "SELECT track_id,valence,arousal,label,tempo,tags,feature FROM $TABLE",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                out[id] = MoodResult(
                    trackId = id,
                    valence = c.getFloat(1),
                    arousal = c.getFloat(2),
                    label = c.getString(3),
                    tempo = c.getFloat(4),
                    tags = decodeTags(c.getString(5)),
                    feature = decodeFeature(c.getString(6)),
                )
            }
        }
        out
    }.getOrDefault(emptyMap())

    /** 读取全部分析结果（含 feature），供本地电台索引使用。 */
    fun all(ctx: Context): List<MoodResult> = runCatching {
        val out = ArrayList<MoodResult>()
        db(ctx).query(TABLE, null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    MoodResult(
                        trackId = c.getString(0),
                        valence = c.getFloat(1),
                        arousal = c.getFloat(2),
                        label = c.getString(3),
                        tempo = c.getFloat(4),
                        beatsMs = decodeInts(c.getString(5)),
                        env = decodeFloats(c.getString(6)),
                        envStepMs = if (c.isNull(7)) 100 else c.getInt(7),
                        tags = decodeTags(if (c.isNull(9)) null else c.getString(9)),
                        feature = decodeFeature(if (c.isNull(10)) null else c.getString(10)),
                        updatedAt = c.getLong(8),
                    ),
                )
            }
        }
        out
    }.getOrDefault(emptyList())

    fun count(ctx: Context): Int = runCatching {
        db(ctx).rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }.getOrDefault(0)

    /** 各心情标签的分布，用于分析页展示。 */
    fun distribution(ctx: Context): Map<String, Int> = runCatching {
        val out = LinkedHashMap<String, Int>()
        db(ctx).rawQuery("SELECT label, COUNT(*) FROM $TABLE GROUP BY label ORDER BY COUNT(*) DESC", null).use { c ->
            while (c.moveToNext()) out[c.getString(0) ?: "Unknown"] = c.getInt(1)
        }
        out
    }.getOrDefault(emptyMap())

    fun clear(ctx: Context) {
        runCatching { db(ctx).delete(TABLE, null, null) }
    }

    data class MoodDay(val date: String, val label: String?, val count: Int)

    /** 最近 N 天的分析量，label 为当天占比最高的情绪。 */
    fun recentDays(ctx: Context, days: Int = 7): List<MoodDay> = runCatching {
        val today = LocalDate.now()
        val start = today.minusDays((days - 1).toLong())
        val since = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val counts = LinkedHashMap<String, MutableMap<String, Int>>()
        db(ctx).rawQuery(
            "SELECT date(updated/1000,'unixepoch','localtime') AS d, label, COUNT(*) " +
                "FROM $TABLE WHERE updated>=? GROUP BY d, label",
            arrayOf(since.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val d = c.getString(0) ?: continue
                val label = c.getString(1) ?: "Unknown"
                counts.getOrPut(d) { LinkedHashMap() }[label] = c.getInt(2)
            }
        }
        val fmt = DateTimeFormatter.ofPattern("MM/dd")
        (0 until days).map { i ->
            val date = start.plusDays(i.toLong())
            val day = counts[date.toString()] ?: emptyMap()
            val dominant = day.maxByOrNull { it.value }?.key
            MoodDay(
                date = date.format(fmt),
                label = dominant,
                count = day.values.sum(),
            )
        }
    }.getOrDefault(emptyList())
}

/** 融合 YAMNet 分数 + DSP，得到 Russell 坐标、标签、tempo 与节拍包络。 */
object MoodMath {
    // YAMNet（521 类）的 mood 类索引，顺序：Happy, Sad, Tender, Exciting, Angry, Scary
    val YAMNET_IDX = intArrayOf(271, 272, 273, 274, 275, 276)

    private fun sigmoid(x: Float) = 1f / (1f + kotlin.math.exp(-x))

    /** 逐类重标定：AudioSet 的 mood 类原始分普遍偏低，用温度 + 偏置拉开。 */
    private fun calibrate(score: Float): Float = sigmoid((score - 0.10f) * 14f)

    private fun pick(scores: FloatArray?, idx: IntArray, name: String): Float {
        if (scores == null || idx[0] >= scores.size) return 0f
        return when (name) {
            "Happy" -> scores[idx[0]]
            "Sad" -> scores[idx[1]]
            "Tender" -> scores[idx[2]]
            "Exciting" -> scores[idx[3]]
            "Angry" -> scores[idx[4]]
            else -> scores[idx[5]]
        }
    }

    data class Dsp(
        val rms: Float,
        val centroidHz: Float,
        val onsetRate: Float,
        val tempoBpm: Float,
        val beatsMs: IntArray = IntArray(0),
        val env: FloatArray = FloatArray(0),
        val envStepMs: Int = 100,
    )

    /** 从 YAMNet 521 类平均分中取 top-K；仅供内部使用，不在 UI 展示。 */
    fun topTags(yamnet: FloatArray?, limit: Int = 8, minScore: Float = 0.02f): List<AudioTag> {
        if (yamnet == null) return emptyList()
        val n = minOf(yamnet.size, YAMNET_LABELS.size)
        return (0 until n)
            .map { AudioTag(YAMNET_LABELS[it], yamnet[it]) }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(limit)
    }

    fun fuse(yamnet: FloatArray?, dsp: Dsp, trackId: String): MoodResult {
        val names = listOf("Happy", "Sad", "Tender", "Exciting", "Angry", "Scary")
        val fused = names.associateWith { name ->
            calibrate(pick(yamnet, YAMNET_IDX, name))
        }
        val h = fused["Happy"] ?: 0f
        val s = fused["Sad"] ?: 0f
        val t = fused["Tender"] ?: 0f
        val e = fused["Exciting"] ?: 0f
        val a = fused["Angry"] ?: 0f
        val sc = fused["Scary"] ?: 0f

        val tempoNorm = ((dsp.tempoBpm - 60f) / 120f).coerceIn(0f, 1f)
        val rmsNorm = (dsp.rms * 3.2f).coerceIn(0f, 1f)
        val onsetNorm = (dsp.onsetRate / 8f).coerceIn(0f, 1f)
        val centroidNorm = (dsp.centroidHz / 4000f).coerceIn(0f, 1f)

        var v = 0.95f * h + 0.40f * t - 0.85f * s - 0.55f * a - 0.20f * sc + 0.18f
        var ar = 0.70f * e + 0.55f * a + 0.40f * sc + 0.55f * tempoNorm +
            0.40f * rmsNorm + 0.35f * onsetNorm + 0.20f * centroidNorm - 0.35f * t - 0.20f * s
        v = ((v + 1f) / 2f).coerceIn(0f, 1f)
        ar = ar.coerceIn(0f, 1f)

        val label = when {
            v >= 0.56f && ar >= 0.52f -> "Happy"
            v >= 0.56f && ar < 0.52f -> if (t > h) "Tender" else "Calm"
            v < 0.46f && ar >= 0.52f -> if (a > 0.10f) "Angry" else "Tense"
            else -> "Sad"
        }
        return MoodResult(
            trackId = trackId,
            valence = v,
            arousal = ar,
            label = label,
            tempo = dsp.tempoBpm,
            beatsMs = dsp.beatsMs,
            env = dsp.env,
            envStepMs = dsp.envStepMs,
        )
    }
}
