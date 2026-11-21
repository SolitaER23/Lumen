package com.example.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

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
    val updatedAt: Long = System.currentTimeMillis(),
)

object MoodStore {
    private const val DB = "moods.db"
    private const val TABLE = "moods"

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, DB, null, 5) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE (" +
                    "track_id TEXT PRIMARY KEY, valence REAL, arousal REAL, label TEXT, tempo REAL, " +
                    "beats TEXT, env TEXT, env_step INTEGER, updated INTEGER)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // 历史 schema 可能缺列（早期升级路径不一致），直接重建；心情结果可重新分析
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }

        override fun onOpen(db: SQLiteDatabase) {
            super.onOpen(db)
            val cols = LinkedHashSet<String>()
            runCatching {
                db.rawQuery("PRAGMA table_info($TABLE)", null).use { c ->
                    while (c.moveToNext()) cols.add(c.getString(1))
                }
            }
            android.util.Log.i("LumenMood", "db v=${db.version} cols=$cols")
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
                    updatedAt = c.getLong(8),
                )
            } else null
        }
    }.onFailure { android.util.Log.e("LumenMood", "mood get failed", it) }.getOrNull()

    fun count(ctx: Context): Int = runCatching {
        db(ctx).rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }.getOrDefault(0)

    fun clear(ctx: Context) {
        runCatching { db(ctx).delete(TABLE, null, null) }
    }
}

/** 融合两个模型的分数 + DSP，得到 Russell 坐标、标签、tempo 与节拍包络。 */
object MoodMath {
    // YAMNet(521 类) 与 CNN14(527 类) 的 mood 类索引，顺序统一：
    // Happy, Sad, Tender, Exciting, Angry, Scary
    val YAMNET_IDX = intArrayOf(271, 272, 273, 274, 275, 276)
    val CNN14_IDX = intArrayOf(276, 278, 279, 280, 281, 282)

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

    fun fuse(yamnet: FloatArray?, cnn14: FloatArray?, dsp: Dsp, trackId: String): MoodResult {
        val names = listOf("Happy", "Sad", "Tender", "Exciting", "Angry", "Scary")
        val fused = names.associateWith { name ->
            val y = calibrate(pick(yamnet, YAMNET_IDX, name))
            val c = calibrate(pick(cnn14, CNN14_IDX, name))
            if (yamnet != null && cnn14 != null) (y + c) / 2f else maxOf(y, c)
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
