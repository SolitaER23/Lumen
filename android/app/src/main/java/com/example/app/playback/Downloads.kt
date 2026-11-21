package com.example.app.playback

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService as Media3DownloadService
import com.example.app.data.Album
import com.example.app.data.Track

/**
 * 一个下载分组：整张专辑，或单独一首歌。
 *
 * [key] 是 `DownloadRequest.data`（`album:<id>` / `track:<id>`），管理页按它分组与删除。
 */
data class AlbumDownload(
    val key: String,
    val name: String,
    val total: Int,
    val done: Int,
    val bytes: Long,
    val failed: Boolean,
    val isSingle: Boolean = false,
    val albumId: String = "",
    val artist: String = "",
    val albumName: String = "",
)

/**
 * 离线下载的薄封装：入队、查询、删除，全部走 [DownloadService] 与 [MediaCache]。
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object Downloads {
    private const val PREFS = "lumen_downloads"
    private const val PREFIX_ALBUM = "album:"
    private const val PREFIX_TRACK = "track:"

    private fun keyOf(d: Download): String =
        d.request.data?.let { String(it, Charsets.UTF_8) } ?: ""

    private fun saveName(ctx: Context, key: String, name: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name_$key", name).apply()
    }

    private fun request(id: String, url: String, key: String): DownloadRequest =
        DownloadRequest.Builder(id, android.net.Uri.parse(url))
            .setData(key.toByteArray(Charsets.UTF_8))
            .setMimeType("audio/mpeg")
            .build()

    /** 整张专辑 */
    fun enqueue(ctx: Context, album: Album, tracks: List<Track>, urlFor: (Track) -> String) {
        if (tracks.isEmpty()) return
        val key = PREFIX_ALBUM + album.id
        saveName(ctx, key, album.name)
        tracks.forEach { t ->
            Media3DownloadService.sendAddDownload(ctx, DownloadService::class.java, request(t.id, urlFor(t), key), true)
        }
    }

    /** 单曲 */
    fun enqueueTrack(ctx: Context, track: Track, urlFor: (Track) -> String) {
        val key = PREFIX_TRACK + track.id
        saveName(ctx, key, if (track.artist.isBlank()) track.title else "${track.title} · ${track.artist}")
        // 记下专辑/艺人，管理页与「已下载」卡片可直接显示封面并播放
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("album_$key", track.albumId)
            .putString("artist_$key", track.artist)
            .putString("albumname_$key", track.album)
            .apply()
        Media3DownloadService.sendAddDownload(
            ctx,
            DownloadService::class.java,
            request(track.id, urlFor(track), key),
            true,
        )
    }

    /** 某张专辑的下载状态（专辑页用） */
    fun status(ctx: Context, albumId: String): AlbumDownload? =
        summaries(ctx).firstOrNull { it.key == PREFIX_ALBUM + albumId }

    /** 某一首是否已下载（播放页用） */
    fun isTrackDownloaded(ctx: Context, trackId: String): Boolean =
        summaries(ctx).any { it.key == PREFIX_TRACK + trackId && it.total > 0 && it.done == it.total }

    fun summaries(ctx: Context): List<AlbumDownload> {
        val downloads = runCatching {
            MediaCache.downloadManager(ctx).downloadIndex.getDownloads().use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.download) }
            }
        }.getOrDefault(emptyList())
        if (downloads.isEmpty()) return emptyList()

        val cache = MediaCache.cache(ctx)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return downloads.groupBy { keyOf(it) }
            .map { (key, list) ->
                AlbumDownload(
                    key = key,
                    name = prefs.getString("name_$key", null) ?: key,
                    total = list.size,
                    done = list.count { it.state == Download.STATE_COMPLETED },
                    bytes = list.sumOf { d ->
                        runCatching {
                            cache.getCachedSpans(d.request.uri.toString()).sumOf { it.length }
                        }.getOrDefault(0L)
                    },
                    failed = list.any { it.state == Download.STATE_FAILED },
                    isSingle = key.startsWith(PREFIX_TRACK),
                    albumId = prefs.getString("album_$key", null).orEmpty(),
                    artist = prefs.getString("artist_$key", null).orEmpty(),
                    albumName = prefs.getString("albumname_$key", null).orEmpty(),
                )
            }
            .sortedWith(compareByDescending<AlbumDownload> { !it.isSingle }.thenByDescending { it.name })
    }

    fun delete(ctx: Context, key: String) {
        runCatching {
            MediaCache.downloadManager(ctx).downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    val d = cursor.download
                    if (keyOf(d) == key && d.state != Download.STATE_REMOVING) {
                        Media3DownloadService.sendRemoveDownload(ctx, DownloadService::class.java, d.request.id, true)
                    }
                }
            }
        }
    }

    fun totalBytes(ctx: Context): Long = summaries(ctx).sumOf { it.bytes }

    fun totalCount(ctx: Context): Int = summaries(ctx).sumOf { it.total }
}
