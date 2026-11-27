package app.lumen.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 专辑曲目磁盘缓存（stale-while-revalidate）。
 *
 * 打开专辑时先用磁盘里的上一份结果立刻渲染（0 网络等待），
 * 同时在后台刷新并覆写，保证内容最终一致。
 * 弱网/服务端抖动时，用户看到的是上一次的正确列表而不是空白页。
 */
object AlbumTracksCache {
    private const val TTL_MS = 7L * 24 * 3600 * 1000

    private fun dir(ctx: Context): File =
        File(ctx.cacheDir, "album_tracks").apply { if (!exists()) mkdirs() }

    private fun file(ctx: Context, albumId: String): File =
        File(dir(ctx), albumId.hashCode().toUInt().toString(16) + ".json")

    fun load(ctx: Context, albumId: String): List<Track>? = runCatching {
        val f = file(ctx, albumId)
        if (!f.isFile) return null
        if (System.currentTimeMillis() - f.lastModified() > TTL_MS) return null
        val arr = JSONArray(f.readText())
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Track(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    albumId = o.optString("albumId", albumId),
                    album = o.optString("album"),
                    disc = o.optInt("disc", 1),
                    track = o.optInt("track"),
                    seconds = o.optInt("seconds"),
                    suffix = o.optString("suffix"),
                ),
            )
        }
        out.ifEmpty { null }
    }.getOrNull()

    fun save(ctx: Context, albumId: String, tracks: List<Track>) {
        runCatching {
            if (tracks.isEmpty()) return
            val arr = JSONArray()
            tracks.forEach { t ->
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
                        .put("suffix", t.suffix),
                )
            }
            val f = file(ctx, albumId)
            f.writeText(arr.toString())
            f.setLastModified(System.currentTimeMillis())
        }
    }

    fun clear(ctx: Context) {
        runCatching { dir(ctx).deleteRecursively() }
    }
}
