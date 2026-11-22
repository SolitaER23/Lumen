package app.lumen.player.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/** 专辑列表本地缓存：冷启动先用上次内容填充（kotlinx.serialization）。 */
object AlbumCache {
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(ctx: Context) = File(ctx.filesDir, "albums.json")

    fun load(ctx: Context): List<Album> = runCatching {
        val f = file(ctx)
        if (!f.exists()) emptyList() else json.decodeFromString<List<Album>>(f.readText())
    }.getOrDefault(emptyList())

    fun save(ctx: Context, albums: List<Album>) {
        runCatching { file(ctx).writeText(json.encodeToString(albums)) }
    }
}