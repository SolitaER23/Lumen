package app.lumen.player.data

import android.content.Context
import app.lumen.player.playback.MoodAnalysisRunner

/**
 * 音色（乐器）搜索：用端侧分析得到的 AudioSet 标签匹配。
 *
 * 中文关键词映射到标签子串，例如「钢琴」→ piano（同时命中 Piano / Electric piano），
 * 「吉他」→ guitar（命中 Acoustic guitar / Electric guitar）。
 */
object TrackTagSearch {
    private val KEYWORDS: List<Pair<List<String>, List<String>>> = listOf(
        listOf("钢琴", "piano") to listOf("piano"),
        listOf("吉他", "guitar") to listOf("guitar"),
        listOf("贝斯", "bass") to listOf("bass"),
        listOf("鼓", "drum") to listOf("drum", "percussion"),
        listOf("小提琴", "violin") to listOf("violin", "fiddle"),
        listOf("大提琴", "cello") to listOf("cello"),
        listOf("弦乐", "strings", "string") to listOf("string", "orchestra"),
        listOf("萨克斯", "sax") to listOf("saxophone", "sax"),
        listOf("小号", "trumpet") to listOf("trumpet"),
        listOf("长笛", "flute") to listOf("flute"),
        listOf("电子", "electronic", "synth") to listOf("synthesizer", "electronic", "house music", "techno"),
        listOf("人声", "vocal", "唱") to listOf("singing", "vocal", "male singing", "female singing"),
        listOf("管风琴", "organ") to listOf("organ"),
        listOf("手风琴", "accordion") to listOf("accordion"),
        listOf("口琴", "harmonica") to listOf("harmonica"),
        listOf("古筝", "guzheng") to listOf("guzheng", "plucked string"),
        listOf("二胡", "erhu") to listOf("erhu", "bowed string"),
    )

    /** 把查询词解析成一组标签子串；无法识别时返回空。 */
    private fun labelsFor(query: String): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        KEYWORDS.forEach { (keys, labels) ->
            if (keys.any { q.contains(it.lowercase()) || it.lowercase().contains(q) }) out.addAll(labels)
        }
        return out.distinct()
    }

    /**
     * 返回命中的曲目（按标签命中数量降序），最多 [limit] 首。
     * 依赖本地分析结果与缓存曲目表；未分析的曲目不会出现在结果里。
     */
    fun search(ctx: Context, query: String, limit: Int = 40): List<Track> {
        val labels = labelsFor(query)
        if (labels.isEmpty()) return emptyList()
        val moods = runCatching { MoodStore.radio(ctx) }.getOrDefault(emptyMap())
        if (moods.isEmpty()) return emptyList()
        val tracks = runCatching { MoodAnalysisRunner.cachedTracks(ctx) }.getOrDefault(emptyList())
        if (tracks.isEmpty()) return emptyList()

        val score = HashMap<String, Int>()
        moods.forEach { (id, mood) ->
            var hits = 0
            mood.tags.take(12).forEach { tag ->
                val n = tag.name.lowercase()
                if (labels.any { n.contains(it) }) hits++
            }
            if (hits > 0) score[id] = hits
        }
        if (score.isEmpty()) return emptyList()
        return tracks.filter { score.containsKey(it.id) }
            .sortedByDescending { score[it.id] ?: 0 }
            .take(limit)
    }

    // ---------------- 心情搜索 ----------------

    private data class MoodQuery(val labels: Set<String>, val minArousal: Float?, val maxArousal: Float?)

    private fun moodQuery(query: String): MoodQuery? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        fun hit(vararg keys: String) = keys.any { q.contains(it) }
        return when {
            hit("安静", "平静", "放松", "轻音乐", "calm", "relax", "quiet") ->
                MoodQuery(setOf("Calm", "Tender"), null, 0.55f)
            hit("开心", "快乐", "愉快", "happy", "joy", "upbeat") ->
                MoodQuery(setOf("Happy"), null, null)
            hit("悲伤", "难过", "伤感", "忧郁", "sad", "melancholy") ->
                MoodQuery(setOf("Sad"), null, null)
            hit("激烈", "愤怒", "燥", "燃", "angry", "energetic", "intense") ->
                MoodQuery(setOf("Angry", "Tense"), 0.55f, null)
            hit("紧张", "悬疑", "tense") -> MoodQuery(setOf("Tense"), null, null)
            hit("温柔", "抒情", "tender", "gentle") -> MoodQuery(setOf("Tender"), null, null)
            else -> null
        }
    }

    /** 按心情关键词筛选已分析曲目（依赖本地分析结果）。 */
    fun searchMood(ctx: Context, query: String, limit: Int = 40): List<Track> {
        val mq = moodQuery(query) ?: return emptyList()
        val moods = runCatching { MoodStore.radio(ctx) }.getOrDefault(emptyMap())
        if (moods.isEmpty()) return emptyList()
        val tracks = runCatching { MoodAnalysisRunner.cachedTracks(ctx) }.getOrDefault(emptyList())
        if (tracks.isEmpty()) return emptyList()

        val keep = HashSet<String>()
        moods.forEach { (id, mood) ->
            val labelOk = mq.labels.isEmpty() || mq.labels.contains(mood.label)
            val a = mood.arousal
            val arousalOk = (mq.minArousal == null || a >= mq.minArousal) &&
                (mq.maxArousal == null || a <= mq.maxArousal)
            if (labelOk && arousalOk) keep.add(id)
        }
        if (keep.isEmpty()) return emptyList()
        return tracks.filter { keep.contains(it.id) }
            .sortedBy { moods[it.id]?.arousal ?: 1f }
            .take(limit)
    }

}
