package app.lumen.player.playback

import app.lumen.player.util.L10n
import android.content.Context
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService as Media3DownloadService
import app.lumen.player.data.Album
import app.lumen.player.data.Track

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

    // ---------------- 事件驱动的状态通知 ----------------
    //
    // 旧实现由各页面按 1s / 1.5s / 2s 轮询 summaries()，而 summaries() 对每个下载都要
    // 查一次 SimpleCache 的 span（SQLite）。1000 首下载 = 每秒上千次查询。
    // 现在改为监听 DownloadManager：进度回调节流到 500ms，天然只在下载时触发。

    private val observers = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    private var observing = false

    /** 订阅下载状态变化（回调在主线程）。与 [unobserve] 成对使用。 */
    fun observe(ctx: Context, onChange: () -> Unit) {
        observers.add(onChange)
        ensureObserver(ctx)
    }

    fun unobserve(onChange: () -> Unit) {
        observers.remove(onChange)
    }

    private fun ensureObserver(ctx: Context) {
        if (observing) return
        synchronized(this) {
            if (observing) return
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var lastNotify = 0L
            fun publish() {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastNotify < 500L) return
                lastNotify = now
                observers.forEach { cb -> runCatching { cb() } }
            }
            runCatching {
                MediaCache.downloadManager(ctx).addListener(
                    object : DownloadManager.Listener {
                        override fun onDownloadChanged(
                            downloadManager: DownloadManager,
                            download: Download,
                            finalException: Exception?,
                        ) {
                            handler.post { publish() }
                        }

                        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                            handler.post { publish() }
                        }
                    },
                )
                observing = true
            }.onFailure {
                app.lumen.player.util.Logx.w("LumenDownloads", "订阅下载状态失败: " + it.message)
            }
        }
    }

    /** 单个下载占用的字节数：优先用自定义 cache key，取不到再回退到旧 URI key。 */
    private fun cachedBytesFor(cache: SimpleCache, id: String, uri: android.net.Uri): Long {
        val byKey = runCatching { cache.getCachedSpans(MediaCache.downloadKey(id)).sumOf { it.length } }
            .getOrDefault(0L)
        if (byKey > 0) return byKey
        return runCatching { cache.getCachedSpans(uri.toString()).sumOf { it.length } }.getOrDefault(0L)
    }

    /** 清理下载元数据（名称/艺人/专辑）；下载内容由 MediaCache 单独清理。 */
    fun clearMeta(ctx: Context) {
        runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply() }
    }

    private fun saveName(ctx: Context, key: String, name: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name_$key", name).apply()
    }

    private fun saveTrackTitle(ctx: Context, trackId: String, title: String) {
        runCatching {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("t_$trackId", title).apply()
        }
    }

    /**
     * 下载的 MIME 必须按**实际字节**判断：`streamUrl()` 会把 m4a/ALAC 改写成
     * `format=mp3` 的转码流，此时后缀是 m4a 但内容是 mp3。写死 "audio/mpeg" 会让
     * flac/opus 的离线播放被平台按错误容器处理。
     */
    private fun mimeFor(url: String, suffix: String): String {
        val fmt = runCatching { android.net.Uri.parse(url).getQueryParameter("format") }.getOrNull()
        return when ((fmt ?: suffix).orEmpty().lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "audio/ogg"
            "m4a", "aac", "mp4" -> "audio/mp4"
            "wav" -> "audio/wav"
            else -> "audio/*"
        }
    }

    private fun request(id: String, url: String, key: String, suffix: String): DownloadRequest =
        DownloadRequest.Builder(id, android.net.Uri.parse(url))
            .setData(key.toByteArray(Charsets.UTF_8))
            .setMimeType(mimeFor(url, suffix))
            // 独立 cache key：既不与在线播放同一 URI 并发冲突，也方便“清缓存”时保留
            .setCustomCacheKey(MediaCache.downloadKey(id))
            .build()

    /** 整张专辑 */
    fun enqueue(ctx: Context, album: Album, tracks: List<Track>, urlFor: (Track) -> String) {
        if (tracks.isEmpty()) return
        val key = PREFIX_ALBUM + album.id
        saveName(ctx, key, album.name)
        tracks.forEach { t ->
            saveTrackTitle(ctx, t.id, if (t.artist.isBlank()) t.title else "${t.title} · ${t.artist}")
            Media3DownloadService.sendAddDownload(
                ctx,
                DownloadService::class.java,
                request(t.id, urlFor(t), key, t.suffix),
                true,
            )
        }
    }

    /** 单曲 */
    fun enqueueTrack(ctx: Context, track: Track, urlFor: (Track) -> String) {
        val key = PREFIX_TRACK + track.id
        saveName(ctx, key, if (track.artist.isBlank()) track.title else "${track.title} · ${track.artist}")
        saveTrackTitle(ctx, track.id, if (track.artist.isBlank()) track.title else "${track.title} · ${track.artist}")
        // 记下专辑/艺人，管理页与「已下载」卡片可直接显示封面并播放
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("album_$key", track.albumId)
            .putString("artist_$key", track.artist)
            .putString("albumname_$key", track.album)
            .apply()
        Media3DownloadService.sendAddDownload(
            ctx,
            DownloadService::class.java,
            request(track.id, urlFor(track), key, track.suffix),
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
                    bytes = list.sumOf { d -> cachedBytesFor(cache, d.request.id, d.request.uri) },
                    failed = list.any { it.state == Download.STATE_FAILED },
                    isSingle = key.startsWith(PREFIX_TRACK),
                    albumId = prefs.getString("album_$key", null).orEmpty(),
                    artist = prefs.getString("artist_$key", null).orEmpty(),
                    albumName = prefs.getString("albumname_$key", null).orEmpty(),
                )
            }
            .sortedWith(compareByDescending<AlbumDownload> { !it.isSingle }.thenByDescending { it.name })
    }

    /** 已下载曲目的 cache key（自定义下载 key），用于「清理缓存」时保留。 */
    fun downloadedUris(ctx: Context): Set<String> = runCatching {
        MediaCache.downloadManager(ctx).downloadIndex.getDownloads().use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    val d = cursor.download
                    if (d.state == Download.STATE_COMPLETED || d.state == Download.STATE_DOWNLOADING ||
                        d.state == Download.STATE_QUEUED
                    ) {
                        add(MediaCache.downloadKey(d.request.id))
                        add(d.request.uri.toString())
                    }
                }
            }
        }
    }.getOrDefault(emptySet())

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

    /** 已下载完成的曲目 id（含专辑组里的单曲），用于列表打「已下载」标记。 */
    fun completedTrackIds(ctx: Context): Set<String> = runCatching {
        MediaCache.downloadManager(ctx).downloadIndex.getDownloads().use { c ->
            buildSet {
                while (c.moveToNext()) {
                    val d = c.download
                    if (d.state == Download.STATE_COMPLETED) add(d.request.id)
                }
            }
        }
    }.getOrDefault(emptySet())

    data class DownloadTrack(
        val id: String,
        val title: String,
        val bytes: Long,
        val done: Boolean,
        val failed: Boolean,
        val paused: Boolean,
    )

    /** 某个下载组（专辑或单曲）下的曲目明细，用于管理页展开。 */
    fun tracksOf(ctx: Context, groupKey: String): List<DownloadTrack> = runCatching {
        val cache = MediaCache.cache(ctx)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val out = ArrayList<DownloadTrack>()
        MediaCache.downloadManager(ctx).downloadIndex.getDownloads().use { c ->
            while (c.moveToNext()) {
                val d = c.download
                if (keyOf(d) != groupKey) continue
                val size = cachedBytesFor(cache, d.request.id, d.request.uri)
                out.add(
                    DownloadTrack(
                        id = d.request.id,
                        title = prefs.getString("t_${d.request.id}", null) ?: d.request.id,
                        bytes = size,
                        done = d.state == Download.STATE_COMPLETED,
                        failed = d.state == Download.STATE_FAILED,
                        paused = d.state == Download.STATE_STOPPED,
                    ),
                )
            }
        }
        out
    }.getOrDefault(emptyList())

    /** 单独删除一首（专辑组内也可单独删）。 */
    fun deleteTrack(ctx: Context, trackId: String) {
        runCatching {
            Media3DownloadService.sendRemoveDownload(ctx, DownloadService::class.java, trackId, true)
        }
    }

    fun pauseAll(ctx: Context) {
        runCatching { Media3DownloadService.sendPauseDownloads(ctx, DownloadService::class.java, true) }
    }

    fun resumeAll(ctx: Context) {
        runCatching { Media3DownloadService.sendResumeDownloads(ctx, DownloadService::class.java, true) }
    }

    fun totalBytes(ctx: Context): Long = summaries(ctx).sumOf { it.bytes }

    fun totalCount(ctx: Context): Int = summaries(ctx).sumOf { it.total }
}