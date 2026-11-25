package app.lumen.player.data

import app.lumen.player.util.L10n

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    /** 5 个窗各自的 embedding（精细分析），用于段落级检索 */
    val windowsVec: List<FloatArray> = emptyList(),
    /** 每个采样窗的拍点（毫秒，相对该窗起点），用于按窗节拍动效 */
    val windowsBeats: List<IntArray> = emptyList(),
    /** 每个采样窗的 BPM（当前以全曲 tempo 近似填充） */
    val windowsTempo: FloatArray = FloatArray(0),
    /** 各采样窗在曲目中的起始毫秒（用于按播放位置选窗） */
    val windowsStartMs: IntArray = IntArray(0),
    /** 各窗的 valence / arousal（段落级配色用） */
    val windowsValence: FloatArray = FloatArray(0),
    val windowsArousal: FloatArray = FloatArray(0),
    /** 段落级标签，用于全局背景与迷你播放条的当前声音类型。 */
    val segments: List<AudioSegment> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

object MoodStore {
    /**
     * 当前特征模型标识；与行内 model_id 不一致即视为需要重算。
     *
     * 0.74.0 起权重从 fp32 换成 fp16（体积减半，APK 278→151 MB）。fp16 与 fp32 的
     * 输出在「频谱较稀疏」的曲目上会有可测差异（真实音频抽样：clipwise 余弦 0.91），
     * 因此**必须换 id**让旧结果作废重算 —— 否则同一曲库里一半是 fp32 特征、
     * 一半是 fp16 特征，相似度与电台排序会自相矛盾。
     */
    const val CURRENT_MODEL_ID = "clap-fp16-v1"

    private val meanCache = HashMap<Int, FloatArray>()

    /**
     * 计算指定维度的均值向量（相似度去偏用）。
     * CLAP 向量在所有曲目上共享一个很大的公共分量（实测余弦常年 0.97+），
     * 不减去均值时「相似度」几乎恒为常数，排序失去意义。
     */
    private val stdCache = HashMap<Int, FloatArray>()

    /** 逐维标准差（配合均值做 z-score 标准化）。 */
    fun featureStd(ctx: Context, dim: Int): FloatArray? {
        stdCache[dim]?.let { return it }
        val mean = meanFeature(ctx, dim) ?: return null
        val acc = DoubleArray(dim)
        var n = 0
        runCatching {
            db(ctx).rawQuery("SELECT feature FROM $TABLE", null).use { c ->
                while (c.moveToNext()) {
                    val raw = c.getString(0) ?: continue
                    if (raw.isEmpty()) continue
                    val parts = raw.split(',')
                    if (parts.size != dim) continue
                    for (i in 0 until dim) {
                        val d = (parts[i].toFloatOrNull() ?: 0f) - mean[i]
                        acc[i] += (d * d).toDouble()
                    }
                    n++
                }
            }
        }
        if (n == 0) return null
        val std = FloatArray(dim) { kotlin.math.sqrt((acc[it] / n).toDouble()).toFloat().coerceAtLeast(1e-4f) }
        stdCache[dim] = std
        return std
    }

    fun meanFeature(ctx: Context, dim: Int): FloatArray? {
        meanCache[dim]?.let { return it }
        val acc = FloatArray(dim)
        var n = 0
        runCatching {
            db(ctx).rawQuery("SELECT feature FROM $TABLE", null).use { c ->
                while (c.moveToNext()) {
                    val raw = c.getString(0) ?: continue
                    if (raw.isEmpty()) continue
                    val parts = raw.split(',')
                    if (parts.size != dim) continue
                    for (i in 0 until dim) {
                        parts[i].toFloatOrNull()?.let { acc[i] += it }
                    }
                    n++
                }
            }
        }
        if (n == 0) return null
        for (i in 0 until dim) acc[i] /= n
        meanCache[dim] = acc
        return acc
    }

    /** 当前生效的模型标识（随窗数档位变化）。 */
    var currentModelId: String = "clap-music-v2-fine"

    /**
     * 该曲是否已按目标档位分析过。
     * requireFine = true 时只认精细档（v2-fine）；false 时任意已分析结果都算完成，
     * 避免切换档位把整库判为过期。
     */
    fun isAnalyzed(ctx: Context, trackId: String, requireFine: Boolean): Boolean = runCatching {
        db(ctx).rawQuery("SELECT model_id FROM $TABLE WHERE track_id=?", arrayOf(trackId)).use { c ->
            if (!c.moveToFirst()) return@use false
            val id = c.getString(0) ?: ""
            if (requireFine) id == "clap-music-v2-fine" else id.isNotEmpty()
        }
    }.getOrDefault(false)

    /** 指定档位已覆盖的曲目数。 */
    fun countByModel(ctx: Context, modelId: String): Int = runCatching {
        db(ctx).rawQuery("SELECT COUNT(*) FROM $TABLE WHERE model_id=?", arrayOf(modelId)).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }.getOrDefault(0)

    /** 为已存在的库补齐缺失列（onCreate 遗漏时插入会失败）。 */
    fun ensureColumns(ctx: Context) {
        val cols = listOf("windows_vec", "windows_beats", "windows_tempo", "windows_start", "windows_va")
        runCatching {
            db(ctx).rawQuery("PRAGMA table_info($TABLE)", null).use { c ->
                val have = HashSet<String>()
                while (c.moveToNext()) have.add(c.getString(1))
                cols.filter { it !in have }.forEach { name ->
                    runCatching { db(ctx).execSQL("ALTER TABLE $TABLE ADD COLUMN $name TEXT") }
                }
            }
        }
    }

    /** 分析数据自检统计。 */
    data class MoodStats(
        val rows: Int,
        val withEnv: Int,
        val withStarts: Int,
        val startsDistribution: Map<Int, Int>,
        val models: List<Pair<String, Int>>,
    )

    fun stats(ctx: Context): MoodStats = runCatching {
        var rows = 0
        var env = 0
        val starts = HashMap<Int, Int>()
        val models = HashMap<String, Int>()
        db(ctx).rawQuery(
            "SELECT env, windows_start, model_id FROM $TABLE",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                rows++
                val e = c.getString(0)
                if (!e.isNullOrBlank() && e.length > 2) env++
                val s = c.getString(1)
                val n = if (s.isNullOrBlank()) 0 else s.split(",").size
                if (n > 0) starts[n] = (starts[n] ?: 0) + 1
                val model = c.getString(2) ?: "(null)"
                models[model] = (models[model] ?: 0) + 1
            }
        }
        MoodStats(
            rows = rows,
            withEnv = env,
            withStarts = starts.values.sum(),
            startsDistribution = starts,
            models = models.entries.sortedByDescending { it.value }.map { it.key to it.value },
        )
    }.getOrDefault(MoodStats(0, 0, 0, emptyMap(), emptyList()))

    /** 已分析曲目清单（按更新时间倒序），用于设置页展示。 */
    data class AnalyzedRow(
        val trackId: String,
        val label: String,
        val windows: Int,
        val hasEnv: Boolean,
        val hasVec: Boolean,
        val hasBeats: Boolean,
        val hasTempo: Boolean,
        val hasVa: Boolean,
    )

    fun analyzedRows(ctx: Context, limit: Int = 300): List<AnalyzedRow> = runCatching {
        val out = ArrayList<AnalyzedRow>()
        db(ctx).rawQuery(
            "SELECT track_id, label, windows_start, env, windows_vec, windows_beats, windows_tempo, windows_va FROM $TABLE ORDER BY updated DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val starts = c.getString(2)
                out.add(
                    AnalyzedRow(
                        trackId = c.getString(0) ?: "",
                        label = c.getString(1) ?: "",
                        windows = if (starts.isNullOrBlank()) 0 else starts.split(",").size,
                        hasEnv = !c.getString(3).isNullOrBlank(),
                        hasVec = !c.getString(4).isNullOrBlank(),
                        hasBeats = !c.getString(5).isNullOrBlank(),
                        hasTempo = !c.getString(6).isNullOrBlank(),
                        hasVa = !c.getString(7).isNullOrBlank(),
                    ),
                )
            }
        }
        out
    }.getOrDefault(emptyList())

    /** 清除所有非 5 窗的旧记录（窗边界数量 != 5），返回删除条数。 */
    fun clearNonFiveWindow(ctx: Context): Int = runCatching {
        val ids = ArrayList<String>()
        db(ctx).rawQuery("SELECT track_id, windows_start FROM $TABLE", null).use { c ->
            while (c.moveToNext()) {
                val s = c.getString(1)
                val n = if (s.isNullOrBlank()) 0 else s.split(",").size
                if (n != 5) ids.add(c.getString(0))
            }
        }
        ids.forEach { db(ctx).delete(TABLE, "track_id=?", arrayOf(it)) }
        synchronized(this) { meanCache.clear(); stdCache.clear() }
        revision += 1
        ids.size
    }.getOrDefault(0)

    /** 删除单曲分析结果，使其在下次分析时被重新处理。 */
    fun deleteResult(ctx: Context, trackId: String) {
        runCatching { db(ctx).delete(TABLE, "track_id=?", arrayOf(trackId)) }
        synchronized(this) { meanCache.clear(); stdCache.clear() }
        revision += 1
    }

    /** 该曲是否已是当前模型的结果。 */
    fun isCurrentModel(ctx: Context, trackId: String): Boolean = runCatching {
        db(ctx).rawQuery("SELECT model_id FROM $TABLE WHERE track_id=?", arrayOf(trackId)).use { c ->
            if (c.moveToFirst()) (c.getString(0) ?: "") == currentModelId else false
        }
    }.getOrDefault(false)

    private const val DB = "moods.db"
    private const val TABLE = "moods"

    private const val FAIL_TABLE = "mood_failures"

    private const val IGNORE_TABLE = "mood_ignored"

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, DB, null, 18) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE (" +
                    "track_id TEXT PRIMARY KEY, valence REAL, arousal REAL, label TEXT, tempo REAL, " +
                    "beats TEXT, env TEXT, env_step INTEGER, updated INTEGER, tags TEXT, feature TEXT, segments TEXT, model_id TEXT, " +
                    "windows_vec TEXT, windows_beats TEXT, windows_tempo TEXT, windows_start TEXT, windows_va TEXT)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $FAIL_TABLE (track_id TEXT PRIMARY KEY, attempts INTEGER, last INTEGER)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $IGNORE_TABLE (track_id TEXT PRIMARY KEY, at INTEGER)",
            )
        }

        /**
         * 迁移必须**按版本升序**执行：旧实现把 `< 13` 的 DELETE 放在 `< 12` 回填与
         * `< 11` 加列之前，导致从 v10 升级时 model_id 列还不存在、语句抛错被
         * runCatching 静默吞掉 —— 旧 YAMNet 结果永远清不掉。
         *
         * 失败不再完全静默：记日志（Logx.w 仅在 debug 输出），避免"看起来迁移成功"。
         */
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            fun step(label: String, sql: String) {
                runCatching { db.execSQL(sql) }.onFailure {
                    app.lumen.player.util.Logx.w(
                        "LumenMood",
                        "migration " + label + " (v" + oldVersion + "->" + newVersion + ") failed: " + it.message,
                    )
                }
            }

            // v6/v7/v8：补列，保留已有分析结果
            if (oldVersion < 6) step("v6.tags", "ALTER TABLE $TABLE ADD COLUMN tags TEXT")
            if (oldVersion < 7) step("v7.feature", "ALTER TABLE $TABLE ADD COLUMN feature TEXT")
            if (oldVersion < 8) step("v8.segments", "ALTER TABLE $TABLE ADD COLUMN segments TEXT")
            // v9/v10：失败/忽略表
            if (oldVersion < 9) {
                step(
                    "v9.failures",
                    "CREATE TABLE IF NOT EXISTS $FAIL_TABLE (track_id TEXT PRIMARY KEY, attempts INTEGER, last INTEGER)",
                )
            }
            if (oldVersion < 10) {
                step(
                    "v10.ignored",
                    "CREATE TABLE IF NOT EXISTS $IGNORE_TABLE (track_id TEXT PRIMARY KEY, at INTEGER)",
                )
            }
            // v11：加 model_id 列（必须在 v12 回填之前）
            if (oldVersion < 11) step("v11.model_id", "ALTER TABLE $TABLE ADD COLUMN model_id TEXT")
            // v12：按特征长度回填来源模型
            if (oldVersion < 12) {
                step(
                    "v12.backfill",
                    "UPDATE $TABLE SET model_id = CASE WHEN length(feature) > 3500 " +
                        "THEN 'clap-music-v1' ELSE 'yamnet-v1' END " +
                        "WHERE model_id IS NULL AND feature IS NOT NULL AND length(feature) > 0",
                )
            }
            // v13：旧 YAMNet 结果与 CLAP 特征不兼容，清除后等待重新分析
            if (oldVersion < 13) {
                step(
                    "v13.purge_yamnet",
                    "DELETE FROM $TABLE WHERE model_id IS NULL OR model_id != 'clap-music-v1'",
                )
            }
            // v14：fp32 权重换成 fp16，旧特征与新特征不可混用（见 CURRENT_MODEL_ID 注释）
            if (oldVersion < 18) {
                runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN windows_va TEXT") }
            }
            if (oldVersion < 17) {
                runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN windows_start TEXT") }
            }
            if (oldVersion < 16) {
                runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN windows_beats TEXT") }
                runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN windows_tempo TEXT") }
            }
            if (oldVersion < 15) {
                runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN windows_vec TEXT") }
            }
            if (oldVersion < 14) {
                step(
                    "v14.purge_fp32",
                    "DELETE FROM $TABLE WHERE model_id IS NULL OR model_id != 'clap-fp16-v1'",
                )
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

    /**
     * 进程级单例 helper。
     *
     * 旧实现是 `Helper(ctx).writableDatabase` —— **每次查询都新建一个 SQLiteOpenHelper**，
     * 从不 close：全库统计一次会创建几十个 helper / SQLiteDatabase 包装，
     * 每次都跑 onOpen 的 PRAGMA table_info，且长期占着文件句柄。
     */
    private var helper: Helper? = null

    private fun db(ctx: Context): SQLiteDatabase = synchronized(this) {
        val h = helper ?: Helper(ctx.applicationContext).also {
            // WAL：分析线程写入与 UI 读取并发时，避免 "database is locked"
            it.setWriteAheadLoggingEnabled(true)
            helper = it
        }
        h.writableDatabase
    }

    private fun encodeInts(a: IntArray): String =
        if (a.isEmpty()) "" else a.joinToString(",")

    private fun decodeInts(s: String?): IntArray =
        if (s.isNullOrBlank()) IntArray(0) else s.split(',').mapNotNull { it.toIntOrNull() }.toIntArray()

    /**
     * 浮点编码**必须**用 Locale.ROOT。
     *
     * 默认 Locale 下 `"%.2f".format()` 在德语/法语等区域会输出 `0,123`，
     * 而这里的分隔符正是逗号 —— 结果整行特征解析失败被静默丢弃，
     * 表现为「换个系统语言，氛围电台的相似度就整体退化」。
     */
    internal fun encodeFloats(a: FloatArray): String =
        if (a.isEmpty()) "" else a.joinToString(",") { String.format(java.util.Locale.ROOT, "%.2f", it) }

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

    internal fun encodeFeature(feature: FloatArray): String =
        if (feature.isEmpty()) "" else feature.joinToString(",") { String.format(java.util.Locale.ROOT, "%.3f", it) }

    private fun decodeWindowsBeats(raw: String?): List<IntArray> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("|").map { decodeInts(it) }
    }

    private fun decodeWindows(raw: String?): List<FloatArray> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("|").mapNotNull { seg ->
            decodeFeature(seg).takeIf { it.isNotEmpty() }
        }
    }

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

    /** 每次写入自增；Compose 观察它以在分析完成后立即刷新播放页数据。 */
    var revision by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun put(ctx: Context, r: MoodResult) {
        // 新增/覆盖一条结果后，全库均值与标准差不再准确 —— 不失效会让「氛围电台」
        // 一直用旧的中心点去偏，越听越偏。
        synchronized(this) {
            meanCache.clear()
            stdCache.clear()
        }
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
            if (r.windowsVec.isNotEmpty()) {
                put("windows_vec", r.windowsVec.joinToString("|") { arr -> encodeFeature(arr) })
            }
            if (r.windowsBeats.isNotEmpty()) {
                put("windows_beats", r.windowsBeats.joinToString("|") { encodeInts(it) })
            }
            if (r.windowsTempo.isNotEmpty()) {
                put("windows_tempo", r.windowsTempo.joinToString(","))
            }
            if (r.windowsStartMs.isNotEmpty()) {
                put("windows_start", encodeInts(r.windowsStartMs))
            }
            if (r.windowsValence.isNotEmpty() && r.windowsValence.size == r.windowsArousal.size) {
                put(
                    "windows_va",
                    r.windowsValence.indices.joinToString("|") { i ->
                        r.windowsValence[i].toString() + "," + r.windowsArousal[i].toString()
                    },
                )
            }
            put("model_id", if (r.feature.size >= 1024) CURRENT_MODEL_ID else "yamnet-v1")
        }
        val ok = runCatching {
            db(ctx).insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }.onFailure { android.util.Log.e("LumenMood", "mood put failed", it) }.isSuccess
        if (ok) revision += 1
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
                    windowsVec = decodeWindows(if (c.getColumnIndex("windows_vec") >= 0 && !c.isNull(c.getColumnIndex("windows_vec"))) c.getString(c.getColumnIndex("windows_vec")) else null),
                    windowsBeats = decodeWindowsBeats(if (c.getColumnIndex("windows_beats") >= 0 && !c.isNull(c.getColumnIndex("windows_beats"))) c.getString(c.getColumnIndex("windows_beats")) else null),
                    windowsTempo = decodeFloats(if (c.getColumnIndex("windows_tempo") >= 0 && !c.isNull(c.getColumnIndex("windows_tempo"))) c.getString(c.getColumnIndex("windows_tempo")) else null),
                    windowsStartMs = decodeInts(if (c.getColumnIndex("windows_start") >= 0 && !c.isNull(c.getColumnIndex("windows_start"))) c.getString(c.getColumnIndex("windows_start")) else null),
                    windowsValence = run {
                        val raw = if (c.getColumnIndex("windows_va") >= 0 && !c.isNull(c.getColumnIndex("windows_va"))) c.getString(c.getColumnIndex("windows_va")) else null
                        if (raw.isNullOrBlank()) FloatArray(0) else raw.split("|").mapNotNull { it.split(",").firstOrNull()?.toFloatOrNull() }.toFloatArray()
                    },
                    windowsArousal = run {
                        val raw = if (c.getColumnIndex("windows_va") >= 0 && !c.isNull(c.getColumnIndex("windows_va"))) c.getString(c.getColumnIndex("windows_va")) else null
                        if (raw.isNullOrBlank()) FloatArray(0) else raw.split("|").mapNotNull { it.split(",").getOrNull(1)?.toFloatOrNull() }.toFloatArray()
                    },
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

    // ---------- 失败 / 异常数据：只允许清理这一类，禁止清空全部分析结果 ----------

    /** 记录一次失败，返回累计失败次数。 */
    fun recordFailure(ctx: Context, trackId: String): Int = runCatching {
        val attempts = db(ctx).rawQuery("SELECT attempts FROM $FAIL_TABLE WHERE track_id=?", arrayOf(trackId)).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        val next = attempts + 1
        val v = ContentValues().apply {
            put("track_id", trackId)
            put("attempts", next)
            put("last", System.currentTimeMillis())
        }
        db(ctx).insertWithOnConflict(FAIL_TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE)
        next
    }.getOrDefault(1)

    fun clearFailure(ctx: Context, trackId: String) {
        runCatching { db(ctx).delete(FAIL_TABLE, "track_id=?", arrayOf(trackId)) }
    }

    /** 被「不再分析」标记的曲目：永久跳过。 */
    fun ignore(ctx: Context, trackId: String) {
        runCatching {
            val v = ContentValues().apply {
                put("track_id", trackId)
                put("at", System.currentTimeMillis())
            }
            db(ctx).insertWithOnConflict(IGNORE_TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun isIgnored(ctx: Context, trackId: String): Boolean = runCatching {
        db(ctx).rawQuery("SELECT 1 FROM $IGNORE_TABLE WHERE track_id=?", arrayOf(trackId)).use { it.moveToFirst() }
    }.getOrDefault(false)

    /** 把当前所有失败曲目加入忽略名单，并从失败表移除。返回忽略条数。 */
    fun ignoreFailures(ctx: Context): Int = runCatching {
        val ids = ArrayList<String>()
        db(ctx).rawQuery("SELECT track_id FROM $FAIL_TABLE", null).use { c ->
            while (c.moveToNext()) c.getString(0)?.let { ids.add(it) }
        }
        val now = System.currentTimeMillis()
        ids.forEach { id ->
            val v = ContentValues().apply {
                put("track_id", id)
                put("at", now)
            }
            db(ctx).insertWithOnConflict(IGNORE_TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }
        db(ctx).delete(FAIL_TABLE, null, null)
        ids.size
    }.getOrDefault(0)

    fun ignoredCount(ctx: Context): Int = runCatching {
        db(ctx).rawQuery("SELECT COUNT(*) FROM $IGNORE_TABLE", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }.getOrDefault(0)

    /** 恢复全部被忽略的曲目（下次启动会重新尝试）。返回恢复条数。 */
    fun clearIgnored(ctx: Context): Int = runCatching {
        db(ctx).delete(IGNORE_TABLE, null, null)
    }.getOrDefault(0)

    /** 同一首连续失败 3 次后冷却 6 小时，避免坏文件无限重试拖住整库队列。 */
    fun shouldAttempt(ctx: Context, trackId: String): Boolean = runCatching {
        if (isIgnored(ctx, trackId)) return@runCatching false
        db(ctx).rawQuery("SELECT attempts,last FROM $FAIL_TABLE WHERE track_id=?", arrayOf(trackId)).use { c ->
            if (!c.moveToFirst()) return@use true
            val attempts = c.getInt(0)
            val last = c.getLong(1)
            attempts < 3 || System.currentTimeMillis() - last > 6 * 3600_000L
        }
    }.getOrDefault(true)

    fun failureCount(ctx: Context): Int = runCatching {
        db(ctx).rawQuery("SELECT COUNT(*) FROM $FAIL_TABLE", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }.getOrDefault(0)

    /** 明显损坏的结果行：没有标签或坐标非有限值。 */
    fun brokenResultCount(ctx: Context): Int = runCatching {
        db(ctx).rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE label IS NULL OR label='' OR valence IS NULL OR arousal IS NULL " +
                "OR valence != valence OR arousal != arousal",
            null,
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }.getOrDefault(0)

    fun abnormalCount(ctx: Context): Int = failureCount(ctx) + brokenResultCount(ctx)

    /** 只清理「失败记录 + 损坏结果」，成功的结果一律保留。返回删除条数。 */
    fun clearAbnormal(ctx: Context): Int = runCatching {
        var removed = db(ctx).delete(FAIL_TABLE, null, null)
        removed += db(ctx).delete(
            TABLE,
            "label IS NULL OR label='' OR valence IS NULL OR arousal IS NULL OR valence != valence OR arousal != arousal",
            null,
        )
        removed
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

    /**
     * 整库删除（「清除本机数据」用）：moods + mood_failures + mood_ignored 一起消失。
     * 只清 moods 表会留下「哪些曲目分析过/失败过」的曲目 ID 清单，属于隐私残留。
     */
    fun clearAll(ctx: Context) {
        // 必须先关掉单例 helper：deleteDatabase 在还有连接打开时会失败并留下空库
        synchronized(this) {
            runCatching { helper?.close() }
            helper = null
        }
        runCatching { ctx.deleteDatabase(DB) }
        runCatching { ctx.deleteDatabase(DB + "-wal") }
        runCatching { ctx.deleteDatabase(DB + "-shm") }
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
    // CLAP（527 类）的 mood 类索引，顺序：Happy, Sad, Tender, Exciting, Angry, Scary
    // 注意：不是 YAMNet 的 271–276（那里是 Lullaby/Video game/Christmas/Dance/Wedding/Happy）
    val MOOD_IDX = intArrayOf(276, 278, 279, 280, 281, 282)

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
        /** 近似整体响度（LUFS 风格：-0.691 + 10·log10(均方)，未做 K 加权） */
        val lufs: Float = 0f,
        /** 动态范围：短时响度的 P95 − P10（dB） */
        val dynamicRange: Float = 0f,
        /** 谱平坦度（几何均值 / 算术均值），0=纯音，1=白噪 */
        val flatness: Float = 0f,
        /** 谱滚降：85% 能量所在频率 */
        val rolloffHz: Float = 0f,
        /** 主导音高类 0–11（C=0），-1 表示无法判定 */
        val keyIndex: Int = -1,
        val keyMinor: Boolean = false,
        /** 调性置信度 0–1 */
        val keyConfidence: Float = 0f,
        /** 每个采样窗各自的 BPM（按窗自相关峰值） */
        val windowTempos: FloatArray = FloatArray(0),
        val beatsMs: IntArray = IntArray(0),
        val env: FloatArray = FloatArray(0),
        val envStepMs: Int = 100,
    )

    /** 从 YAMNet 521 类平均分中取 top-K；仅供内部使用，不在 UI 展示。 */
    fun topTags(yamnet: FloatArray?, limit: Int = 8, minScore: Float = 0.02f): List<AudioTag> {
        if (yamnet == null) return emptyList()
        val n = minOf(yamnet.size, CLAP_LABELS.size)
        return (0 until n)
            .map { AudioTag(CLAP_LABELS[it], yamnet[it]) }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * 用 CLAP 文本锚点的概率直接给出情绪坐标。
     * 锚点顺序：happy, sad, calm, angry, tender, exciting, dark, nostalgic。
     */
    fun fuseAnchors(probs: FloatArray, dsp: Dsp, trackId: String): MoodResult {
        val pHappy = probs.getOrElse(0) { 0f }
        val pSad = probs.getOrElse(1) { 0f }
        val pCalm = probs.getOrElse(2) { 0f }
        val pAngry = probs.getOrElse(3) { 0f }
        val pTender = probs.getOrElse(4) { 0f }
        val pExciting = probs.getOrElse(5) { 0f }
        val pDark = probs.getOrElse(6) { 0f }
        val pNost = probs.getOrElse(7) { 0f }

        // 极性投影：正向（happy/tender/calm/exciting/nostalgic）− 负向（sad/angry/dark）
        val vRaw = pHappy * 1.0f + pTender * 0.55f + pCalm * 0.35f + pExciting * 0.15f +
            pNost * 0.25f - pSad * 0.85f - pAngry * 0.65f - pDark * 0.55f
        // 唤醒度：高强度（exciting/angry/dark）− 低强度（calm/tender/sad）
        val aRaw = pExciting * 1.0f + pAngry * 0.85f + pDark * 0.60f + pHappy * 0.35f +
            pNost * 0.10f - pCalm * 0.55f - pTender * 0.35f - pSad * 0.25f

        // 概率和恒为 1，vRaw ∈ [-1,1]、aRaw ∈ [-1,1]，映射到 0–1
        val v = (0.5f + vRaw * 0.95f).coerceIn(0f, 1f)
        var ar = (0.5f + aRaw * 1.05f).coerceIn(0f, 1f)
        // 用 DSP 的节奏/响度做小幅修正
        val tempoNorm = ((dsp.tempoBpm - 60f) / 120f).coerceIn(0f, 1f)
        ar = (ar * 0.85f + tempoNorm * 0.15f).coerceIn(0f, 1f)

        val label = when {
            v >= 0.56f && ar >= 0.52f -> L10n.t("Happy")
            v >= 0.56f -> L10n.t("Calm")
            v < 0.46f && ar >= 0.52f -> L10n.t("Angry")
            v < 0.46f -> L10n.t("Sad")
            ar >= 0.62f -> L10n.t("Tense")
            else -> L10n.t("Tender")
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

    fun fuse(yamnet: FloatArray?, dsp: Dsp, trackId: String): MoodResult {
        val names = listOf("Happy", "Sad", "Tender", "Exciting", "Angry", "Scary")
        val fused = names.associateWith { name ->
            calibrate(pick(yamnet, MOOD_IDX, name))
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

        // CLAP 的 clipwise 分数整体高于 YAMNet（情绪类常在 0.5–0.9），
        // 直接套用原系数会让 arousal 长期顶到 1.0（实测 mean 0.969 / sd 0.083），
        // 于是几乎全部落进 Angry 分支。这里把「分类分数项」整体降权，
        // 并用 tanh 做软压缩，让 arousal 展开到 0–1 的中段。
        // 用 softmax 把 6 个情绪类转成概率，再做极性投影。
        // 线性加权 + 硬缩放会把结果推到 0/1 两端（实测 p5=0、p50=0、p95=1），
        // softmax 天然有界且保留相对差异。
        val scores = floatArrayOf(h, s, t, e, a, sc)
        val mx = scores.max()
        val exps = FloatArray(6) { kotlin.math.exp((scores[it] - mx) * 6f) }
        var esum = 0f
        for (x in exps) esum += x
        val prob = FloatArray(6) { exps[it] / esum }
        val pHappy = prob[0]; val pSad = prob[1]; val pTender = prob[2]
        val pExciting = prob[3]; val pAngry = prob[4]; val pScary = prob[5]

        var vRaw = pHappy + 0.55f * pTender - 0.75f * pSad - 0.60f * pAngry - 0.25f * pScary
        var arRaw = pExciting + 0.85f * pAngry + 0.55f * pScary

        var v = (0.5f + vRaw * 1.1f).coerceIn(0f, 1f)
        var ar = (0.18f + arRaw * 0.75f + 0.30f * tempoNorm + 0.12f * rmsNorm).coerceIn(0f, 1f)

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
