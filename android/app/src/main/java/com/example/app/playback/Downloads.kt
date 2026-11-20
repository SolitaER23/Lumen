package com.example.app.playback

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService as Media3DownloadService
import com.example.app.data.Album
import com.example.app.data.Track

/** 一张专辑的下载概要（供设置页/专辑页展示）。 */
data class AlbumDownload(
    val albumId: String,
    val name: String,
    val total: Int,
    val done: Int,
    val bytes: Long,
    val failed: Boolean,
)

/**
 * 离线下载的薄封装：入队、查询、删除，全部走 [com.example.app.playback.DownloadService] 与 [MediaCache]。
 *
 * `DownloadRequest.data` 存专辑 id（UTF-8 字节），用来把曲目归到专辑下；
 * 专辑名存在 prefs 里，这样离线时设置页也能显示名字。
 */
object Downloads {
    private const val PREFS = "lumen_downloads"

    private fun albumIdOf(d: Download): String =
        d.request.data?.let { String(it, Charsets.UTF_8) } ?: ""

    fun enqueue(ctx: Context, album: Album, tracks: List<Track>, urlFor: (Track) -> String) {
        if (tracks.isEmpty()) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name_${album.id}", album.name).apply()
        tracks.forEach { t ->
            val request = DownloadRequest.Builder(t.id, android.net.Uri.parse(urlFor(t)))
                .setData(album.id.toByteArray(Charsets.UTF_8))
                .setMimeType("audio/mpeg")
                .build()
            Media3DownloadService.sendAddDownload(ctx, DownloadService::class.java, request, true)
        }
    }

    fun status(ctx: Context, albumId: String): AlbumDownload? =
        summaries(ctx).firstOrNull { it.albumId == albumId }

    fun summaries(ctx: Context): List<AlbumDownload> {
        val downloads = runCatching {
            MediaCache.downloadManager(ctx).downloadIndex.getDownloads().use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.download) }
            }
        }.getOrDefault(emptyList())
        if (downloads.isEmpty()) return emptyList()

        val cache = MediaCache.cache(ctx)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return downloads.groupBy { albumIdOf(it) }
            .map { (albumId, list) ->
                AlbumDownload(
                    albumId = albumId,
                    name = prefs.getString("name_$albumId", null) ?: albumId,
                    total = list.size,
                    done = list.count { it.state == Download.STATE_COMPLETED },
                    bytes = list.sumOf { d ->
                        runCatching {
                            cache.getCachedSpans(d.request.uri.toString()).sumOf { it.length }
                        }.getOrDefault(0L)
                    },
                    failed = list.any { it.state == Download.STATE_FAILED },
                )
            }
            .sortedByDescending { it.name }
    }

    fun delete(ctx: Context, albumId: String) {
        runCatching {
            MediaCache.downloadManager(ctx).downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    val d = cursor.download
                    if (albumIdOf(d) == albumId && d.state != Download.STATE_REMOVING) {
                        Media3DownloadService.sendRemoveDownload(
                            ctx,
                            DownloadService::class.java,
                            d.request.id,
                            true,
                        )
                    }
                }
            }
        }
    }

    fun totalBytes(ctx: Context): Long = summaries(ctx).sumOf { it.bytes }

    fun totalCount(ctx: Context): Int = summaries(ctx).sumOf { it.total }
}
