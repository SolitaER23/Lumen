package app.lumen.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 本机播放历史：按单曲记录最近播放，供「最近」页以歌曲形式展示。 */
object PlayHistory {
    private const val FILE = "lumen_history"
    private const val KEY = "tracks"
    private const val LIMIT = 60

    fun record(ctx: Context, track: Track) {
        if (track.id.isBlank()) return
        val list = load(ctx).filterNot { it.id == track.id }
        val next = (listOf(track) + list).take(LIMIT)
        val arr = JSONArray()
        next.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("albumId", t.albumId)
                    .put("album", t.album)
                    .put("disc", t.disc)
                    .put("track", t.track)
                    .put("seconds", t.seconds)
                    .put("suffix", t.suffix)
                    .put("starred", t.starred),
            )
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun load(ctx: Context): List<Track> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            out += Track(
                id = id,
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
        return out
    }
}
