package com.example.app.playback

import android.content.Context
import com.example.app.data.Track
import org.json.JSONArray
import org.json.JSONObject

/** 记住上一次的播放队列 / 曲目 / 进度，冷启动恢复（不自动播放）。 */
object PlaybackStateStore {
    private const val PREFS = "lumen_playback"
    private const val KEY = "state"

    data class Restored(
        val tracks: List<Track>,
        val index: Int,
        val albumId: String?,
        val positionMs: Long,
    )

    fun save(ctx: Context, tracks: List<Track>, index: Int, albumId: String?, positionMs: Long) {
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
                    put("starred", t.starred)
                },
            )
        }
        val obj = JSONObject().apply {
            put("tracks", arr)
            put("index", index)
            put("albumId", albumId ?: "")
            put("position", positionMs)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, obj.toString()).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun load(ctx: Context): Restored? = runCatching {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
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
                    starred = o.optBoolean("starred", false),
                ),
            )
        }
        if (tracks.isEmpty()) return null
        Restored(
            tracks = tracks,
            index = obj.optInt("index", 0).coerceIn(0, tracks.lastIndex),
            albumId = obj.optString("albumId", "").ifBlank { null },
            positionMs = obj.optLong("position", 0L).coerceAtLeast(0L),
        )
    }.getOrNull()
}
