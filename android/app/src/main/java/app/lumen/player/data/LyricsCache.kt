package app.lumen.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 歌词磁盘缓存：一首歌一个 JSON（行 + 时间轴）。
 * 与歌曲/封面/分析缓存分开，可单独清理，且不影响在线重新获取。
 */
object LyricsCache {
    private const val DIR = "lyrics"

    private fun dir(ctx: Context): File = File(ctx.applicationContext.cacheDir, DIR)

    private fun fileOf(ctx: Context, trackId: String): File =
        File(dir(ctx), trackId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    fun get(ctx: Context, trackId: String): List<LyricLine>? = runCatching {
        val f = fileOf(ctx, trackId)
        if (!f.isFile) return null
        val arr = JSONArray(f.readText())
        if (arr.length() == 0) return null
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LyricLine(o.optLong("t"), o.optString("s"))
        }
    }.getOrNull()

    fun put(ctx: Context, trackId: String, lines: List<LyricLine>) {
        runCatching {
            if (lines.isEmpty()) return
            val arr = JSONArray()
            lines.forEach { arr.put(JSONObject().put("t", it.startMs).put("s", it.text)) }
            dir(ctx).mkdirs()
            fileOf(ctx, trackId).writeText(arr.toString())
        }
    }

    fun bytes(ctx: Context): Long = runCatching {
        dir(ctx).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

    fun clear(ctx: Context) {
        runCatching { dir(ctx).deleteRecursively() }
    }
}
