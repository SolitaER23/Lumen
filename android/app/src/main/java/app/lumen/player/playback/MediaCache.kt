package app.lumen.player.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import app.lumen.player.data.SettingsStore
import java.io.File
import java.util.concurrent.Executors

/**
 * 全进程共享的媒体缓存 + 下载管理器。
 *
 * 播放与离线下载必须用同一个 [SimpleCache] 实例（同一目录二次打开会抛
 * "SimpleCache is locked"）。因此「清理缓存」不能删目录，而是按 key 清理——
 * 下载内容以 [DOWNLOAD_KEY_MARK] 结尾的 key 存放，按后缀判据保留即可。
 *
 * 淘汰策略见 [StreamOnlyCacheEvictor]：下载 key 永不淘汰（审计 D1）。
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object MediaCache {
    /** 下载内容专用的 key 后缀：既是「不可淘汰」标记，也是「清理缓存」的保留判据。 */
    internal const val DOWNLOAD_KEY_MARK = "#lumen-dl"

    /**
     * 流缓存上限。
     *
     * 0.91 之前是「流 + 下载共 1 GB」；下载独占 [DOWNLOAD_KEY_MARK] 后不再计入，
     * 512 MB 足够覆盖「边听边缓存」，同时把低存储设备的占用控制在可预期范围。
     */
    private const val STREAM_MAX_BYTES = 512L * 1024 * 1024

    private const val MAINTENANCE_PREFS = "lumen_maintenance"
    private const val KEY_MOVED_MEDIA_DIR = "media_dir_moved_v1"

    @Volatile
    private var cache: SimpleCache? = null

    @Volatile
    private var evictor: StreamOnlyCacheEvictor? = null

    @Volatile
    private var database: StandaloneDatabaseProvider? = null

    @Volatile
    private var manager: DownloadManager? = null

    /**
     * 媒体缓存目录。
     *
     * 0.91 及以前放在 cacheDir/media：系统在低存储时会自动裁剪 cacheDir，用户在系统设置里
     * 点「清除缓存」也会连离线下载一起删掉——而下载是用户资产，不能由系统代为清理。
     * 因此迁到 filesDir/media（同一分区，rename 原子）；流缓存超量改由
     * [StreamOnlyCacheEvictor] 自己控制，不再依赖系统回收。
     *
     * 迁移失败（例如磁盘不足）时回退旧目录并保留标记，下次启动重试；不会丢数据。
     */
    internal fun mediaDir(ctx: Context): File {
        val app = ctx.applicationContext
        val target = File(app.filesDir, "media")
        val legacy = File(app.cacheDir, "media")
        if (!legacy.exists()) return target
        val prefs = app.getSharedPreferences(MAINTENANCE_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MOVED_MEDIA_DIR, false)) return if (target.exists()) target else legacy

        // 上一次迁移部分成功（新目录已存在）：把旧目录里剩下的文件补齐过去，避免两套索引
        if (target.exists()) {
            runCatching {
                legacy.walkTopDown().filter { it.isFile }.forEach { f ->
                    val dest = File(target, f.relativeTo(legacy).path)
                    if (!dest.exists()) {
                        dest.parentFile?.mkdirs()
                        f.copyTo(dest, overwrite = false)
                    }
                }
                legacy.deleteRecursively()
            }
            if (!legacy.exists()) {
                prefs.edit().putBoolean(KEY_MOVED_MEDIA_DIR, true).apply()
            }
            return target
        }

        val moved = runCatching { legacy.renameTo(target) }.getOrDefault(false)
        if (!moved) return legacy
        prefs.edit().putBoolean(KEY_MOVED_MEDIA_DIR, true).apply()
        return target
    }

    fun cache(ctx: Context): SimpleCache = synchronized(this) {
        cache ?: run {
            val ev = evictor ?: StreamOnlyCacheEvictor(STREAM_MAX_BYTES).also { evictor = it }
            SimpleCache(mediaDir(ctx), ev, database(ctx)).also { cache = it }
        }
    }

    fun database(ctx: Context): StandaloneDatabaseProvider = synchronized(this) {
        database ?: StandaloneDatabaseProvider(ctx.applicationContext).also { database = it }
    }

    /**
     * 账号作用域指纹。
     *
     * 缓存键里**必须**带账号身份：不同 Navidrome 上的曲目 id 完全可能相同，
     * 只用 "track:<id>" 会让 A 服务器的音频命中 B 服务器的缓存（错歌 + 跨账号串数据）。
     * 这里只读 base/user（不解密密码），并对结果做一次短哈希，避免把服务器地址写进键名。
     */
    internal fun accountScope(ctx: Context): String = scopeOf(SettingsStore.identity(ctx))

    /** 纯函数形式，便于单测。 */
    internal fun scopeOf(identity: String): String = sha256Hex("lumen-cache|" + identity).take(8)

    /** 已下载内容的独立 cache key（账号隔离），避免与在线播放的同一 URI 并发读写互相卡住。 */
    fun downloadKey(ctx: Context, trackId: String): String =
        "acct:" + accountScope(ctx) + "|" + trackId + DOWNLOAD_KEY_MARK

    private fun trackIdOf(uri: android.net.Uri): String? =
        runCatching { uri.getQueryParameter("id") }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * 播放侧读缓存时的「已完成下载」查找：曲目 id → cache key。
     *
     * 由 [Downloads] 在载入索引与收到 DownloadManager 事件时维护。只认已完成状态：
     * 进行中的下载若被播放侧当成缓存键，会与下载器同时写同一个 key（审计 D12），
     * 这正是 0.22.1 「边播边下停在 0/1」的成因。
     */
    @Volatile
    internal var playbackDownloadKey: (String) -> String? = { null }

    fun cacheDataSourceFactory(ctx: Context): CacheDataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
        return CacheDataSource.Factory()
            .setCache(cache(ctx))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx.applicationContext, http))
            .setCacheKeyFactory(object : CacheKeyFactory {
                override fun buildCacheKey(spec: androidx.media3.datasource.DataSpec): String =
                    cacheKeyFor(ctx, spec.uri, playbackDownloadKey)
            })
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * 流缓存的键：**不能**直接用 URI——Subsonic 的 u/t/s 参数是可重放凭据，
     * 写进 SimpleCache 索引库等于把账号访问权留在磁盘上。
     *
     * 有 id（音频流）时：已完成下载优先（离线可播），否则用
     * "acct:<scope>|track:<id>#<format>[@bitrate]"（同一首歌的原始流与转码流字节不同，
     * 必须区分）。没有 id 的罕见请求退回 URI 的 SHA-256 摘要。
     */
    internal fun cacheKeyFor(
        ctx: Context,
        uri: android.net.Uri,
        playbackDownloadKey: (String) -> String?,
    ): String {
        val id = trackIdOf(uri) ?: return uriKey(uri.toString())
        // 已完成的下载优先（含 0.74~0.90 的旧键，映射由 Downloads 维护）
        playbackDownloadKey(id)?.let { return it }
        return streamKey(
            accountScope(ctx),
            id,
            uri.getQueryParameter("format"),
            uri.getQueryParameter("maxBitRate"),
        )
    }

    /** 纯函数：流缓存键（可单测）。 */
    internal fun streamKey(scope: String, trackId: String, format: String?, bitrate: String?): String {
        val f = format?.takeIf { it.isNotBlank() } ?: "raw"
        val b = bitrate?.takeIf { it.isNotBlank() }
        return "acct:" + scope + "|track:" + trackId + "#" + f + if (b != null) "@" + b else ""
    }

    /** 纯函数：无曲目 id 时的回落键（对 URI 取摘要，避免把凭据写进索引库）。 */
    internal fun uriKey(rawUri: String): String = "uri:" + sha256Hex(rawUri)

    /** 判断缓存 key 是否属于下载内容（不可被淘汰，清理缓存时保留）。 */
    internal fun isDownloadKey(key: String): Boolean = key.endsWith(DOWNLOAD_KEY_MARK)

    private fun sha256Hex(raw: String): String = runCatching {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        d.joinToString("") { b -> "%02x".format(java.util.Locale.ROOT, b) }.take(32)
    }.getOrDefault(Integer.toHexString(raw.hashCode()))

    /**
     * 只清在线播放产生的流缓存，下载内容（[DOWNLOAD_KEY_MARK] 结尾）保留。
     *
     * 旧实现要求调用方传「已下载 key 白名单」，而白名单只覆盖
     * COMPLETED / DOWNLOADING / QUEUED 三种状态：用户「先全部暂停、再清理缓存」会误删
     * 暂停中下载的 span（审计 D9）。现在下载与流缓存的边界是 key 后缀，与状态无关。
     */
    fun clearStreamCache(ctx: Context) {
        val c = cache(ctx)
        val keys = runCatching { c.keys.toList() }.getOrDefault(emptyList())
        keys.forEach { key ->
            if (!key.endsWith(DOWNLOAD_KEY_MARK)) runCatching { c.removeResource(key) }
        }
    }

    /** 清空全部缓存（含下载）。仅「清除本机数据 / 注销」这类全量动作使用。 */
    fun clearAllCache(ctx: Context) {
        val c = cache(ctx)
        val keys = runCatching { c.keys.toList() }.getOrDefault(emptyList())
        keys.forEach { key -> runCatching { c.removeResource(key) } }
    }

    fun clearImageCache(ctx: Context) {
        runCatching { File(ctx.applicationContext.cacheDir, "image_cache").deleteRecursively() }
    }

    /** 歌曲流缓存占用（**不含**下载）；口径与设置页「歌曲缓存」一致。 */
    fun streamingBytes(ctx: Context): Long {
        val c = cache(ctx)
        val total = runCatching { c.cacheSpace }.getOrDefault(0L)
        val downloads = evictor?.pinnedBytes ?: 0L
        return (total - downloads).coerceAtLeast(0L)
    }

    /** 封面缓存占用。 */
    fun imageBytes(ctx: Context): Long = runCatching {
        File(ctx.applicationContext.cacheDir, "image_cache")
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }.getOrDefault(0L)

    fun downloadManager(ctx: Context): DownloadManager = synchronized(this) {
        manager ?: run {
            val app = ctx.applicationContext
            // 下载器超时比播放宽松：自建服务器的转码流首字节可能很慢，8 s 默认值过于激进
            val upstream = DefaultDataSource.Factory(
                app,
                DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(30_000),
            )
            DownloadManager(
                app,
                database(ctx),
                cache(ctx),
                // 下载库里只有合成 URI（不含 u/t/s），真正的地址在这里按当前账号实时签名：
                // ① 凭据不落盘（审计 D2）；② 改密码/换账号后旧队列仍能续传（审计 D3）
                ResolvingDataSource.Factory(upstream) { spec ->
                    val raw = spec.uri.toString()
                    val resolved = Downloads.resolveDownloadUrl(app, raw)
                    if (resolved == raw) spec else spec.withUri(android.net.Uri.parse(resolved))
                },
                Executors.newFixedThreadPool(2),
            ).also { manager = it }
        }
    }
}

/**
 * 只对「流缓存」做 LRU 的淘汰器：以 [MediaCache.DOWNLOAD_KEY_MARK] 结尾的 key 永不淘汰。
 *
 * 为什么不能用 LeastRecentlyUsedCacheEvictor：下载与流缓存共用一个 SimpleCache 时，
 * 下载 span 写完后不再被读取，恰好是 LRU 里最先被淘汰的对象；于是「已下载」会在用户
 * 不知情的情况下变成 0 字节、离线播放失效（审计 D1）。这里按 key 记账，下载 key 直接
 * 不进淘汰候选集，只统计占用。
 *
 * 记账口径：evictableBytes 只累计非下载 span；下载 span 累计到 [pinnedBytes]。
 */
internal class StreamOnlyCacheEvictor(private val maxBytes: Long) : CacheEvictor {
    /** key → 该 key 当前缓存的字节数；LinkedHashMap 的访问顺序即 LRU 顺序。 */
    private val lru = LinkedHashMap<String, Long>()
    private val lock = Any()

    /** 下载内容占用字节（不计入 [maxBytes]，供设置页展示）。 */
    @Volatile
    var pinnedBytes: Long = 0L
        private set

    private var evictableBytes: Long = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) = Unit

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        if (!span.isCached) return
        if (isDownload(span.key)) {
            synchronized(lock) { pinnedBytes += span.length }
            return
        }
        synchronized(lock) {
            val total = (lru.remove(span.key) ?: 0L) + span.length
            lru[span.key] = total
            evictableBytes += span.length
        }
        evictIfNeeded(cache)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        synchronized(lock) {
            if (isDownload(span.key)) {
                pinnedBytes = (pinnedBytes - span.length).coerceAtLeast(0L)
                return
            }
            val total = lru[span.key] ?: return
            if (total <= span.length) {
                lru.remove(span.key)
                evictableBytes = (evictableBytes - total).coerceAtLeast(0L)
            } else {
                lru[span.key] = total - span.length
                evictableBytes = (evictableBytes - span.length).coerceAtLeast(0L)
            }
        }
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        if (isDownload(newSpan.key)) return
        synchronized(lock) {
            val total = lru.remove(newSpan.key) ?: return
            lru[newSpan.key] = total
        }
    }

    private fun evictIfNeeded(cache: Cache) {
        // 单次最多淘汰 64 个 key：避免一次批量写入触发长循环，也避免长时间占用缓存锁
        repeat(64) {
            val victim = synchronized(lock) {
                if (evictableBytes <= maxBytes) return
                lru.entries.firstOrNull { it.value > 0L }?.key ?: return
            }
            runCatching { cache.removeResource(victim) }
            synchronized(lock) {
                val left = lru.remove(victim) ?: 0L
                evictableBytes = (evictableBytes - left).coerceAtLeast(0L)
            }
        }
    }

    private fun isDownload(key: String): Boolean = MediaCache.isDownloadKey(key)
}
