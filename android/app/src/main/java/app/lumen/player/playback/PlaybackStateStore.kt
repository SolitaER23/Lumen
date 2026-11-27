package app.lumen.player.playback

import android.content.Context
import app.lumen.player.data.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * 记住上一次的播放队列 / 曲目 / 进度，冷启动恢复（不自动播放）。
 *
 * 拆成两部分写：队列 JSON 只在「队列或索引变化」时序列化一次（上千首时是几十 KB），
 * 进度是一个 Long，3 秒写一次也只碰一个键。旧实现每 3 秒把整个队列重新序列化一遍，
 * 播放中会持续产生写放大。
 */
object PlaybackStateStore {
    private const val PREFS = "lumen_playback"
    private const val KEY_QUEUE = "state"
    private const val KEY_POSITION = "position"

    data class Restored(
        val tracks: List<Track>,
        val index: Int,
        val albumId: String?,
        val positionMs: Long,
    )

    /** 队列结构变化时调用（切专辑、切换曲目、随机重排）。 */
    fun saveQueue(ctx: Context, tracks: List<Track>, index: Int, albumId: String?) {
        if (tracks.isEmpty()) return
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
                },
            )
        }
        val obj = JSONObject().apply {
            put("tracks", arr)
            put("index", index)
            put("albumId", albumId ?: "")
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_QUEUE, obj.toString())
            .putLong(KEY_POSITION, 0L)
            .apply()
    }

    /** 只写进度：小键，3 秒一次也不会造成写放大。 */
    fun savePosition(ctx: Context, positionMs: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_QUEUE)
            .remove(KEY_POSITION)
            .apply()
    }

    fun load(ctx: Context): Restored? = runCatching {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = p.getString(KEY_QUEUE, null) ?: return null
        val obj = JSONObject(raw)
        val arr = obj.optJSONArray("tracks") ?: return null
        val tracks = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            tracks.add(
                Track(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    artist = o.optString("artist", ""),
                    albumId = o.optString("albumId", ""),
                    album = o.optString("album", ""),
                    disc = o.optInt("disc", 1),
                    track = o.optInt("track", 0),
                    seconds = o.optInt("seconds", 0),
                    suffix = o.optString("suffix", ""),
                ),
            )
        }
        if (tracks.isEmpty()) return null
        // 兼容旧版本：进度曾经写在同一个 JSON 的 position 字段里
        val position = p.getLong(KEY_POSITION, obj.optLong("position", 0L))
        Restored(
            tracks = tracks,
            index = obj.optInt("index", 0).coerceIn(0, tracks.lastIndex),
            albumId = obj.optString("albumId", "").ifBlank { null },
            positionMs = position.coerceAtLeast(0L),
        )
    }.getOrNull()
}
