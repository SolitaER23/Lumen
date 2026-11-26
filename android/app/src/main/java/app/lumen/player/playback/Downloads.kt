package app.lumen.player.playback

import android.content.Context
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService as Media3DownloadService
import app.lumen.player.data.Album
import app.lumen.player.data.SettingsStore
import app.lumen.player.data.SubsonicClient
import app.lumen.player.data.Track
import app.lumen.player.util.Logx
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 一个下载分组：整张专辑，或单独一首歌。
 *
 * [key] 是 DownloadRequest.data（album:<id> / track:<id>），管理页按它分组与删除。
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
    /** 组内是否全部处于暂停（不再区分「排队」与「暂停」）。 */
    val paused: Boolean = false,
    /** 组内第一条失败的具体原因（已脱敏）；没有失败时为空串。 */
    val failure: String = "",
    /** 组内已知的内容总字节（服务端给了 Content-Length 才有）；0 表示未知。 */
    val bytesTotal: Long = 0L,
    /** 进度：有总字节时按字节算，否则退回「已完成曲目数 / 总数」。 */
    val progress: Float = 0f,
    /** 当前下载速度（字节/秒）；不在下载时为 0。 */
    val speedBytesPerSec: Long = 0L,
    /** 预计剩余秒数；无法估算时为 -1。 */
    val etaSeconds: Long = -1L,
)

/**
 * 离线下载：入队、快照、查询、删除。
 *
 * 状态是**事件驱动 + 内存快照**：
 *
 *  - [DownloadManager] 的回调只做「更新一个 Map 条目 + 打脏标记」，不做任何 IO；
 *  - 脏标记合并后由 IO 线程重算快照并推给 [snapshot]，UI 只订阅这个 StateFlow；
 *  - 单条事件的代价是 O(1) 次 span 查询（而不是原来每次事件全表重算），
 *    主线程不再碰 SQLite（审计 D8/D10）。
 *
 * 快照是进程级单例，离开下载页也继续更新，回来时进度是连续的。
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object Downloads {
    private const val TAG = "LumenDownloads"
    private const val PREFS = "lumen_downloads"
    private const val PREFIX_ALBUM = "album:"
    private const val PREFIX_TRACK = "track:"
    private const val TRACK_ID_SEPARATOR = "|"
    private const val MAINTENANCE_PREFS = "lumen_maintenance"
    private const val KEY_IDS_MIGRATED = "download_ids_grouped_v1"
    private const val KEY_URIS_SYNTHETIC = "download_uris_synthetic_v1"
    private const val KEY_WIFI_ONLY = "wifi_only"

    /**
     * 下载库里的 URI 是**合成地址**，不含任何凭据。
     *
     * Subsonic 的 u/t/s 是可重放凭据，一旦写进 DownloadRequest 就等于把账号访问权留在
     * databases/exoplayer_internal.db（审计 D2）。这里只存「要下载哪个请求、什么格式」，
     * 真正的地址在每次打开数据源时用当前账号实时签名（[resolveDownloadUrl]），
     * 顺带解决「改密码后旧请求永久 401」（审计 D3）。
     */
    private const val SYNTH_PREFIX = "lumen-dl://track/"

    /** 事件合并窗口：既保证进度够流畅，也避免一次批量入队触发 N 次快照重算。 */
    private const val PUBLISH_COALESCE_MS = 200L

    /** 单次快照最多重算多少条的字节数（超出部分沿用上次结果，等下次事件刷新）。 */
    private const val BYTES_REFRESH_BUDGET = 64

    /** 空间安全余量：刚好够也不下，避免下载过程把设备写满。 */
    private const val SPACE_HEADROOM_BYTES = 64L * 1024 * 1024

    /** 合成 URI 解析结果。 */
    internal data class SynthSpec(val requestId: String, val format: String?, val bitRate: Int?)

    /** 由宿主提供的「请求 id + 格式 → 真实流地址」解析器（设置后优先使用）。 */
    @Volatile
    private var urlResolver: ((String, String?, Int?) -> String)? = null

    @Volatile
    private var fallbackClient: SubsonicClient? = null

    @Volatile
    private var fallbackIdentity: String? = null

    /** 宿主（持有当前 SubsonicClient 的界面层）注册解析器；进程内只需要一个。 */
    fun setUrlResolver(resolver: (requestId: String, format: String?, bitRate: Int?) -> String) {
        urlResolver = resolver
    }

    /** 纯函数：请求 id + 格式 → 合成 URI（可单测）。 */
    internal fun syntheticUri(requestId: String, format: String?, bitRate: Int?): String {
        val token = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(requestId.toByteArray(Charsets.UTF_8))
        val query = ArrayList<String>(2)
        format?.takeIf { it.isNotBlank() }?.let { query.add("fmt=" + it) }
        bitRate?.takeIf { it > 0 }?.let { query.add("rate=" + it) }
        return if (query.isEmpty()) SYNTH_PREFIX + token else SYNTH_PREFIX + token + "?" + query.joinToString("&")
    }

    /** 纯函数：解析合成 URI；不是合成地址（例如升级前的真实 URL）时返回 null。 */
    internal fun parseSynthetic(raw: String): SynthSpec? {
        if (!raw.startsWith(SYNTH_PREFIX)) return null
        val rest = raw.substring(SYNTH_PREFIX.length)
        val token = rest.substringBefore('?')
        val query = if (rest.contains('?')) rest.substringAfter('?') else ""
        val requestId = runCatching {
            String(java.util.Base64.getUrlDecoder().decode(token), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        var format: String? = null
        var bitRate: Int? = null
        query.split('&').filter { it.isNotBlank() }.forEach { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            when (name) {
                "fmt" -> format = value.takeIf { it.isNotBlank() }
                "rate" -> bitRate = value.toIntOrNull()
            }
        }
        return SynthSpec(requestId, format, bitRate)
    }

    /**
     * 把下载库里的 URI 变成真正可请求的地址。
     *
     * 顺序：宿主注册的解析器（界面在线时的账号）→ 用持久化配置自建客户端（服务被系统
     * 单独拉起时）→ 失败则抛 IOException，让下载进入 FAILED 并显示可读原因。
     * 非合成地址（升级前的旧记录）原样返回。
     */
    internal fun resolveDownloadUrl(ctx: Context, raw: String): String {
        val spec = parseSynthetic(raw) ?: return raw
        urlResolver?.let { resolver ->
            runCatching { resolver(spec.requestId, spec.format, spec.bitRate) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        val client = fallbackClient(ctx)
            ?: throw java.io.IOException("账号未就绪，无法解析下载地址，请打开 App 后重试")
        return buildStreamUrl(client, spec.requestId, spec.format, spec.bitRate)
    }

    private fun buildStreamUrl(
        client: SubsonicClient,
        requestId: String,
        format: String?,
        bitRate: Int?,
    ): String {
        val base = client.streamUrl(trackIdOf(requestId))
        if (format == null && bitRate == null) return base
        return buildString {
            append(base)
            format?.let { append("&format=").append(it) }
            bitRate?.let { append("&maxBitRate=").append(it) }
        }
    }

    /** 服务被单独拉起（JobScheduler / 播放服务）时的兜底客户端，按账号身份缓存。 */
    private fun fallbackClient(ctx: Context): SubsonicClient? {
        val cfg = runCatching { SettingsStore.load(ctx) }.getOrNull() ?: return null
        if (cfg.base.isBlank() || cfg.user.isBlank()) return null
        val identity = runCatching { SettingsStore.identity(ctx) }.getOrNull().orEmpty()
        fallbackClient?.let { if (fallbackIdentity == identity) return it }
        return runCatching { SubsonicClient(cfg) }
            .onSuccess {
                fallbackClient = it
                fallbackIdentity = identity
            }
            .getOrNull()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val publishing = AtomicBoolean(false)

    @Volatile
    private var appCtx: Context? = null

    private val _snapshot = MutableStateFlow<List<AlbumDownload>>(emptyList())

    /** 下载分组快照；Compose 侧直接收集。 */
    val snapshot: StateFlow<List<AlbumDownload>> = _snapshot.asStateFlow()

    /** 请求 id → Download（内存镜像，事件与载入都写它）。 */
    private val entries = ConcurrentHashMap<String, Download>()

    /** 请求 id → 已缓存字节数（避免每次事件都查 span）。 */
    private val bytesByRequestId = ConcurrentHashMap<String, Long>()

    /** 等待重算字节的请求 id。 */
    private val dirty = ConcurrentHashMap.newKeySet<String>()

    /** 请求 id → 失败原因（来自 DownloadManager 回调的异常，已脱敏；进程内有效）。 */
    private val failureMessages = ConcurrentHashMap<String, String>()

    /** 已完成下载的「曲目 id → cache key」映射（同曲多分组时取第一个）。 */
    @Volatile
    private var completedKeyByTrack: Map<String, String> = emptyMap()

    private const val FAILED_NOTIFICATION_ID = 1010

    @Volatile
    private var lastFailedNotifyAt = 0L

    /** App 侧暂停的 stopReason（media3 只公开 STOP_REASON_NONE=0，其余由应用自定义）。 */
    private const val STOP_REASON_APP_PAUSE = 1

    /** 上一次采样（用于算瞬时速度）与平滑后的速度。 */
    private class RateSample(val bytes: Long, val atMs: Long)

    private val rateSamples = HashMap<String, RateSample>()
    private val smoothedSpeed = HashMap<String, Long>()
    private val publishLock = Any()

    private val _waitingForNetwork = MutableStateFlow(false)

    /** 是否因为「仅在非计费网络下载」而在等待网络（UI 用）。 */
    val waitingForNetwork: StateFlow<Boolean> = _waitingForNetwork.asStateFlow()

    /**
     * 预热：载入下载索引并把快照准备好。
     *
     * 在 Application 启动时调用一次即可（幂等）。放到 Application 是为了「进程被
     * DownloadService / PlaybackService 单独拉起」时也能拿到已完成下载集合，
     * 否则离线播放会因为拿不到 key 而回落网络。
     */
    fun warmUp(ctx: Context) {
        val app = ctx.applicationContext
        appCtx = app
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            runCatching { migrateRequestIds(app) }
                .onFailure { Logx.w(TAG, "迁移下载请求 id 失败: " + it.message) }
            runCatching { migrateRequestUris(app) }
                .onFailure { Logx.w(TAG, "迁移下载地址失败: " + it.message) }
            runCatching { attachListener(app) }
                .onFailure { Logx.w(TAG, "订阅下载状态失败: " + it.message) }
            applyRequirements(app)
            runCatching { loadAll(app) }
                .onFailure { Logx.w(TAG, "载入下载索引失败: " + it.message) }
            publish(app, full = true)
        }
    }

    private fun attachListener(ctx: Context) {
        val manager = MediaCache.downloadManager(ctx)
        runCatching { _waitingForNetwork.value = manager.isWaitingForRequirements }
        manager.addListener(
            object : DownloadManager.Listener {
                override fun onWaitingForRequirementsChanged(
                    downloadManager: DownloadManager,
                    waitingForRequirements: Boolean,
                ) {
                    _waitingForNetwork.value = waitingForRequirements
                }

                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?,
                ) {
                    onChanged(ctx, download, finalException)
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    val id = download.request.id
                    entries.remove(id)
                    bytesByRequestId.remove(id)
                    dirty.remove(id)
                    failureMessages.remove(id)
                    cleanMetaIfGone(ctx, download)
                    schedulePublish()
                }
            },
        )
    }

    private fun onChanged(ctx: Context, download: Download, finalException: Exception?) {
        val id = download.request.id
        entries[id] = download
        if (download.state == Download.STATE_FAILED) {
            if (finalException != null) {
                // 异常里带完整 URL（含 u/t/s），必须脱敏后才能进 UI / 日志
                val raw = finalException.message ?: finalException.javaClass.simpleName
                val safe = app.lumen.player.util.Redact.url(raw)
                failureMessages[id] = safe
                Logx.w(TAG, "下载失败 id=" + id + " : " + safe)
            }
            notifyFailedOnce(ctx)
        } else {
            failureMessages.remove(id)
        }
        dirty.add(id)
        schedulePublish()
    }

    /**
     * 整组删完后清掉它的元数据：旧实现只删文件不删 prefs，
     * 反复下载/删除会让 `name_/album_/t_` 键无限堆积（审计 D22）。
     */
    private fun cleanMetaIfGone(ctx: Context, removed: Download) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        val trackId = trackIdOf(removed.request.id)
        if (trackId.isNotBlank() && entries.values.none { trackIdOf(it.request.id) == trackId }) {
            edit.remove("t_" + trackId)
        }
        val groupKey = keyOf(removed)
        if (groupKey.isNotBlank() && entries.values.none { keyOf(it) == groupKey }) {
            edit.remove("name_" + groupKey)
            edit.remove("album_" + groupKey)
            edit.remove("artist_" + groupKey)
            edit.remove("albumname_" + groupKey)
        }
        edit.apply()
    }

    // ---------------- 网络策略 ----------------

    /** 是否只在非计费网络（Wi-Fi / 以太网）下载。 */
    fun wifiOnly(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WIFI_ONLY, false)

    /**
     * 切换「仅 Wi-Fi 下载」。
     *
     * 用 [DownloadManager.setRequirements] 表达，而不是在失败后重试：要求不满足时
     * media3 会把任务挂起为「等待网络」，网络恢复后自动继续（审计 D4）。
     */
    fun setWifiOnly(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WIFI_ONLY, enabled)
            .apply()
        applyRequirements(ctx)
    }

    /** 纯函数：把开关翻译成调度要求（可单测）。 */
    internal fun requirementsFor(wifiOnly: Boolean): androidx.media3.exoplayer.scheduler.Requirements =
        if (wifiOnly) {
            androidx.media3.exoplayer.scheduler.Requirements(
                androidx.media3.exoplayer.scheduler.Requirements.NETWORK_UNMETERED,
            )
        } else {
            androidx.media3.exoplayer.scheduler.Requirements(
                androidx.media3.exoplayer.scheduler.Requirements.NETWORK,
            )
        }

    private fun applyRequirements(ctx: Context) {
        runCatching { MediaCache.downloadManager(ctx).setRequirements(requirementsFor(wifiOnly(ctx))) }
            .onFailure { Logx.w(TAG, "设置下载网络要求失败: " + it.message) }
    }

    /**
     * 失败通知：同一批失败只发一条（15 s 节流），点开回到应用即可重试。
     *
     * 旧实现失败后只有管理页里一个「有失败」的标记，通知栏什么都没有（审计 D18）。
     */
    private fun notifyFailedOnce(ctx: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastFailedNotifyAt < 15_000L) return
        lastFailedNotifyAt = now
        runCatching {
            val failed = entries.values.count { it.state == Download.STATE_FAILED }
            if (failed <= 0) return
            val open = android.app.PendingIntent.getActivity(
                ctx,
                0,
                android.content.Intent(ctx, app.lumen.player.MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val helper = androidx.media3.exoplayer.offline.DownloadNotificationHelper(
                ctx,
                DownloadService.CHANNEL,
            )
            ctx.getSystemService(android.app.NotificationManager::class.java).notify(
                FAILED_NOTIFICATION_ID,
                helper.buildDownloadFailedNotification(
                    ctx,
                    android.R.drawable.stat_sys_warning,
                    open,
                    failed.toString() + " " + app.lumen.player.util.L10n.t("项下载失败，点此重试"),
                ),
            )
        }.onFailure { Logx.w(TAG, "发送失败通知失败: " + it.message) }
    }

    private fun schedulePublish() {
        if (!publishing.compareAndSet(false, true)) return
        val app = appCtx
        if (app == null) {
            publishing.set(false)
            return
        }
        scope.launch {
            runCatching {
                delay(PUBLISH_COALESCE_MS)
                // 先放开标志：发布期间到来的事件还能再排一次，保证「最后一次状态」一定上屏
                publishing.set(false)
                publish(app, full = false)
            }.onFailure {
                publishing.set(false)
                Logx.w(TAG, "刷新下载快照失败: " + it.message)
            }
        }
    }

    private fun loadAll(ctx: Context) {
        val manager = MediaCache.downloadManager(ctx)
        val cache = MediaCache.cache(ctx)
        val loaded = LinkedHashMap<String, Download>()
        runCatching {
            manager.downloadIndex.getDownloads().use { c ->
                while (c.moveToNext()) loaded[c.download.request.id] = c.download
            }
        }.onFailure { Logx.w(TAG, "读取下载索引失败: " + it.message) }

        entries.clear()
        entries.putAll(loaded)
        bytesByRequestId.clear()
        dirty.clear()
        failureMessages.clear()
        loaded.values.forEach { d -> bytesByRequestId[d.request.id] = bytesOf(cache, d) }
        publishPlaybackKeys(ctx)
    }

    /** 事件里更新一条的字节数（IO 线程，span 查询只查这一次）。 */
    private fun refreshBytes(ctx: Context, id: String) {
        val d = entries[id]
        if (d == null) {
            bytesByRequestId.remove(id)
            return
        }
        bytesByRequestId[id] = bytesOf(MediaCache.cache(ctx), d)
    }

    private fun publish(ctx: Context, full: Boolean) {
        // 发布可能被连续事件重入（发布前已放开 publishing 标志），这里串行化，
        // 保证速度采样与快照一致
        synchronized(publishLock) {
            val changed = if (full) entries.keys.toList() else dirty.toList()
            if (!full) dirty.removeAll(changed.toSet())
            var budget = BYTES_REFRESH_BUDGET
            changed.forEach { id ->
                if (budget-- <= 0) return@forEach
                runCatching { refreshBytes(ctx, id) }
            }
            _snapshot.value = buildSnapshot(ctx)
            publishPlaybackKeys(ctx)
        }
    }

    /**
     * 重建「曲目 id → 已完成下载的 cache key」映射。
     *
     * 同一首歌可能同时属于专辑组与单曲组，因此请求 id 带分组前缀；播放侧只关心
     * 「有没有一份已完成的副本」，取第一个即可（旧键也在这里被统一覆盖）。
     */
    private fun publishPlaybackKeys(ctx: Context) {
        val map = HashMap<String, String>()
        entries.values.forEach { d ->
            if (d.state != Download.STATE_COMPLETED) return@forEach
            val trackId = trackIdOf(d.request.id)
            if (trackId.isBlank()) return@forEach
            val key = d.request.customCacheKey ?: MediaCache.downloadKey(ctx, d.request.id)
            map.putIfAbsent(trackId, key)
        }
        completedKeyByTrack = map
        MediaCache.playbackDownloadKey = { trackId -> map[trackId] }
    }

    private fun buildSnapshot(ctx: Context): List<AlbumDownload> {
        val visible = entries.values.filter { it.state != Download.STATE_REMOVING }
        if (visible.isEmpty()) return emptyList()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return visible
            .groupBy { keyOf(it) }
            .map { (key, list) ->
                val done = list.count { it.state == Download.STATE_COMPLETED }
                val bytes = list.sumOf { bytesByRequestId[it.request.id] ?: 0L }
                val bytesTotal = list.sumOf { d -> d.contentLength.coerceAtLeast(0L) }
                val progress = when {
                    bytesTotal > 0L -> (bytes.toFloat() / bytesTotal).coerceIn(0f, 1f)
                    list.isNotEmpty() -> done.toFloat() / list.size
                    else -> 0f
                }
                val active = list.any { it.state == Download.STATE_DOWNLOADING }
                val speed = if (active) sampleSpeed(key, bytes) else {
                    rateSamples.remove(key)
                    smoothedSpeed.remove(key)
                    0L
                }
                val eta = if (speed > 0L && bytesTotal > bytes) (bytesTotal - bytes) / speed else -1L
                AlbumDownload(
                    key = key,
                    name = prefs.getString("name_" + key, null) ?: fallbackName(key),
                    total = list.size,
                    done = done,
                    bytes = bytes,
                    failed = list.any { it.state == Download.STATE_FAILED },
                    isSingle = key.startsWith(PREFIX_TRACK),
                    albumId = prefs.getString("album_" + key, null).orEmpty(),
                    artist = prefs.getString("artist_" + key, null).orEmpty(),
                    albumName = prefs.getString("albumname_" + key, null).orEmpty(),
                    paused = list.all { it.state == Download.STATE_STOPPED },
                    failure = list.firstNotNullOfOrNull { failureMessages[it.request.id] }.orEmpty(),
                    bytesTotal = bytesTotal,
                    progress = progress,
                    speedBytesPerSec = speed,
                    etaSeconds = eta,
                )
            }
            .sortedWith(compareByDescending<AlbumDownload> { !it.isSingle }.thenByDescending { it.name })
    }

    /**
     * 速度采样：瞬时值按 20 s 窗口用 EMA 平滑，避免进度条旁边数字乱跳。
     * 只在 IO 线程（publish）里调用。
     */
    private fun sampleSpeed(key: String, bytes: Long): Long {
        val now = android.os.SystemClock.elapsedRealtime()
        val prev = rateSamples[key]
        rateSamples[key] = RateSample(bytes, now)
        if (prev == null || now <= prev.atMs) return smoothedSpeed[key] ?: 0L
        val delta = (bytes - prev.bytes).coerceAtLeast(0L)
        val instant = delta * 1000L / (now - prev.atMs)
        val before = smoothedSpeed[key] ?: instant
        val smoothed = (before * 3 + instant) / 4
        smoothedSpeed[key] = smoothed
        return smoothed
    }

    private fun fallbackName(key: String): String = when {
        key.startsWith(PREFIX_TRACK) -> key.removePrefix(PREFIX_TRACK)
        key.startsWith(PREFIX_ALBUM) -> key.removePrefix(PREFIX_ALBUM)
        key.isBlank() -> TAG
        else -> key
    }

    /** 请求 id 里的曲目 id（批次 B 起形如 group|trackId，当前为纯 trackId）。 */
    internal fun trackIdOf(requestId: String): String = requestId.substringAfterLast(TRACK_ID_SEPARATOR)

    private fun keyOf(d: Download): String = String(d.request.data, Charsets.UTF_8)

    /** 单个下载占用的字节数（键顺序：自定义键 → 当前账号键 → 历史键 → URI）。 */
    private fun bytesOf(cache: SimpleCache, d: Download): Long {
        val key = d.request.customCacheKey
        if (key != null) {
            val bytes = runCatching { cache.getCachedSpans(key).sumOf { it.length } }.getOrDefault(0L)
            if (bytes > 0) return bytes
        }
        val uri = d.request.uri.toString()
        val bytes = runCatching { cache.getCachedSpans(uri).sumOf { it.length } }.getOrDefault(0L)
        return if (bytes > 0) bytes else 0L
    }

    /**
     * 0.91 及以前的下载请求 id 就是曲目 id；新格式带分组前缀。
     *
     * 不迁移的话，同一张专辑再次下载会出现「旧行 + 新行」两条记录，分组计数翻倍。
     * 这里用 WritableDownloadIndex.putDownload 原样改写 id，不动已下载的字节，
     * 因此不会触发重新下载；只在下载管理器空闲（冷启动且尚未恢复任务）时执行。
     */
    private fun migrateRequestIds(ctx: Context) {
        val prefs = ctx.getSharedPreferences(MAINTENANCE_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IDS_MIGRATED, false)) return
        val manager = MediaCache.downloadManager(ctx)
        if (!manager.isIdle) return
        val index = androidx.media3.exoplayer.offline.DefaultDownloadIndex(MediaCache.database(ctx))
        val rows = ArrayList<Download>()
        index.getDownloads().use { c ->
            while (c.moveToNext()) rows.add(c.download)
        }
        rows.forEach { d ->
            if (d.request.id.contains(TRACK_ID_SEPARATOR)) return@forEach
            val group = String(d.request.data, Charsets.UTF_8)
            if (group.isBlank()) return@forEach
            val newId = requestIdFor(group, d.request.id)
            if (newId == d.request.id) return@forEach
            val migrated = DownloadRequest.Builder(newId, d.request.uri)
                .setData(d.request.data)
                .setMimeType(d.request.mimeType)
                .setCustomCacheKey(d.request.customCacheKey)
                .build()
            index.putDownload(
                Download(
                    migrated,
                    d.state,
                    d.startTimeMs,
                    d.updateTimeMs,
                    d.contentLength,
                    d.stopReason,
                    d.failureReason,
                ),
            )
            index.removeDownload(d.request.id)
        }
        prefs.edit().putBoolean(KEY_IDS_MIGRATED, true).apply()
    }

    /**
     * 把升级前落在库里的真实 URL（含 u/t/s）改写为合成 URI。
     *
     * 只改 DownloadRequest，不动 state/字节；旧行仍然可以续传与播放。
     * 若下载管理器正忙则跳过，等下次冷启动再做（幂等标记随后才写）。
     */
    private fun migrateRequestUris(ctx: Context) {
        val prefs = ctx.getSharedPreferences(MAINTENANCE_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_URIS_SYNTHETIC, false)) return
        val manager = MediaCache.downloadManager(ctx)
        if (!manager.isIdle) return
        val index = androidx.media3.exoplayer.offline.DefaultDownloadIndex(MediaCache.database(ctx))
        val rows = ArrayList<Download>()
        index.getDownloads().use { c ->
            while (c.moveToNext()) rows.add(c.download)
        }
        rows.forEach { d ->
            val raw = d.request.uri.toString()
            if (parseSynthetic(raw) != null) return@forEach
            val uri = runCatching { android.net.Uri.parse(raw) }.getOrNull() ?: return@forEach
            val fmt = runCatching { uri.getQueryParameter("format") }.getOrNull()
            val rate = runCatching { uri.getQueryParameter("maxBitRate")?.toIntOrNull() }.getOrNull()
            val requestId = d.request.id
            val migrated = DownloadRequest.Builder(
                requestId,
                android.net.Uri.parse(syntheticUri(requestId, fmt, rate)),
            )
                .setData(d.request.data)
                .setMimeType(d.request.mimeType)
                .setCustomCacheKey(d.request.customCacheKey ?: MediaCache.downloadKey(ctx, requestId))
                .build()
            index.putDownload(
                Download(
                    migrated,
                    d.state,
                    d.startTimeMs,
                    d.updateTimeMs,
                    d.contentLength,
                    d.stopReason,
                    d.failureReason,
                ),
            )
        }
        prefs.edit().putBoolean(KEY_URIS_SYNTHETIC, true).apply()
    }

    // ---------------- 元数据 ----------------

    /** 清理下载元数据（名称/艺人/专辑）；下载内容由 MediaCache 单独清理。 */
    fun clearMeta(ctx: Context) {
        runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply() }
    }

    private fun saveName(ctx: Context, key: String, name: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name_" + key, name).apply()
    }

    private fun saveTrackTitle(ctx: Context, trackId: String, title: String) {
        runCatching {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("t_" + trackId, title).apply()
        }
    }

    /**
     * 下载的 MIME 必须按**实际字节**判断：streamUrl() 会把 m4a/ALAC 改写成
     * format=mp3 的转码流，此时后缀是 m4a 但内容是 mp3。写死 "audio/mpeg" 会让
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

    /**
     * 下载请求。
     *
     * [requestId] 必须**全局唯一**：media3 以 request.id 为唯一键，同一首歌出现在两张专辑
     * 或「专辑 + 单曲」时会互相顶替（审计 D7）。所以 id 用 "分组键|曲目 id"，
     * cache key 也随之带上分组，避免两个下载写同一批 span。
     */
    private fun request(
        ctx: Context,
        requestId: String,
        url: String,
        key: String,
        suffix: String,
    ): DownloadRequest {
        // 只把「格式决策」记进合成 URI：真实地址（含 u/t/s）在下载时实时签名
        val fmt = runCatching { android.net.Uri.parse(url).getQueryParameter("format") }.getOrNull()
        val rate = runCatching {
            android.net.Uri.parse(url).getQueryParameter("maxBitRate")?.toIntOrNull()
        }.getOrNull()
        return DownloadRequest.Builder(
            requestId,
            android.net.Uri.parse(syntheticUri(requestId, fmt, rate)),
        )
            .setData(key.toByteArray(Charsets.UTF_8))
            .setMimeType(mimeFor(url, suffix))
            // 独立 cache key（含账号作用域与分组）：既不与在线播放并发冲突，也能按后缀保留
            .setCustomCacheKey(MediaCache.downloadKey(ctx, requestId))
            .build()
    }

    /** 请求 id = 分组键|曲目 id（纯函数，便于单测）。 */
    internal fun requestIdFor(groupKey: String, trackId: String): String =
        if (groupKey.isBlank()) trackId else groupKey + TRACK_ID_SEPARATOR + trackId

    // ---------------- 入队 ----------------

    /** 空间预估结果（0 表示服务端未给出大小，无法判断）。 */
    data class SpaceCheck(val requiredBytes: Long, val freeBytes: Long, val allowed: Boolean)

    /**
     * 下载前的空间预估。
     *
     * 只统计服务端给了 size 的曲目（Subsonic 的 song.size）：未知大小的不参与判断，
     * 避免因为个别条目缺字段就把整张专辑拦掉；但只要已知部分已经超过可用空间就拦，
     * 因为大概率会写到磁盘满（审计 D11）。
     */
    fun checkSpace(ctx: Context, tracks: List<Track>): SpaceCheck {
        val required = tracks.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        val free = runCatching {
            android.os.StatFs(ctx.applicationContext.filesDir.path).availableBytes
        }.getOrDefault(Long.MAX_VALUE)
        if (required <= 0L) return SpaceCheck(0L, free, true)
        return SpaceCheck(required, free, free - SPACE_HEADROOM_BYTES >= required)
    }

    /** 整张专辑；返回 false 表示空间不足、未入队。 */
    fun enqueue(ctx: Context, album: Album, tracks: List<Track>, urlFor: (Track) -> String): Boolean {
        if (tracks.isEmpty()) return false
        if (!checkSpace(ctx, tracks).allowed) return false
        val key = PREFIX_ALBUM + album.id
        saveName(ctx, key, album.name)
        tracks.forEach { t ->
            saveTrackTitle(ctx, t.id, if (t.artist.isBlank()) t.title else t.title + " · " + t.artist)
            Media3DownloadService.sendAddDownload(
                ctx,
                DownloadService::class.java,
                request(ctx, requestIdFor(key, t.id), urlFor(t), key, t.suffix),
                true,
            )
        }
        return true
    }

    /** 单曲；返回 false 表示空间不足、未入队。 */
    fun enqueueTrack(ctx: Context, track: Track, urlFor: (Track) -> String): Boolean {
        if (!checkSpace(ctx, listOf(track)).allowed) return false
        val key = PREFIX_TRACK + track.id
        val name = if (track.artist.isBlank()) track.title else track.title + " · " + track.artist
        saveName(ctx, key, name)
        saveTrackTitle(ctx, track.id, name)
        // 记下专辑/艺人，管理页与「已下载」卡片可直接显示封面并播放
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("album_" + key, track.albumId)
            .putString("artist_" + key, track.artist)
            .putString("albumname_" + key, track.album)
            .apply()
        Media3DownloadService.sendAddDownload(
            ctx,
            DownloadService::class.java,
            request(ctx, requestIdFor(key, track.id), urlFor(track), key, track.suffix),
            true,
        )
        return true
    }

    // ---------------- 查询（全部内存，无 IO） ----------------

    /** 已下载完成的曲目 id（含专辑组里的单曲），用于列表打「已下载」标记。 */
    fun completedTrackIds(): Set<String> = completedKeyByTrack.keys

    data class DownloadTrack(
        val id: String,
        val title: String,
        val bytes: Long,
        val done: Boolean,
        val failed: Boolean,
        val paused: Boolean,
        /** 失败原因（已脱敏）；仅失败时非空。 */
        val failure: String = "",
    )

    /** 某个下载组（专辑或单曲）下的曲目明细，用于管理页展开。 */
    fun tracksOf(ctx: Context, groupKey: String): List<DownloadTrack> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return entries.values
            .asSequence()
            .filter { it.state != Download.STATE_REMOVING && keyOf(it) == groupKey }
            .map { d ->
                DownloadTrack(
                    id = d.request.id,
                    title = prefs.getString("t_" + trackIdOf(d.request.id), null)
                        ?: trackIdOf(d.request.id),
                    bytes = bytesByRequestId[d.request.id] ?: 0L,
                    done = d.state == Download.STATE_COMPLETED,
                    failed = d.state == Download.STATE_FAILED,
                    paused = d.state == Download.STATE_STOPPED,
                    failure = failureMessages[d.request.id].orEmpty(),
                )
            }
            .toList()
    }

    // ---------------- 删除 / 暂停 ----------------

    /** 删除一个分组（异步，不阻塞调用线程）。 */
    fun delete(ctx: Context, key: String) {
        val app = ctx.applicationContext
        scope.launch {
            runCatching {
                val ids = ArrayList<String>()
                MediaCache.downloadManager(app).downloadIndex.getDownloads().use { c ->
                    while (c.moveToNext()) {
                        val d = c.download
                        if (keyOf(d) == key && d.state != Download.STATE_REMOVING) ids.add(d.request.id)
                    }
                }
                ids.forEach { id ->
                    Media3DownloadService.sendRemoveDownload(app, DownloadService::class.java, id, true)
                }
            }.onFailure { Logx.w(TAG, "删除下载失败: " + it.message) }
        }
    }

    /** 全部删除（一次 Intent，避免逐个投递）。 */
    fun deleteAll(ctx: Context) {
        runCatching {
            Media3DownloadService.sendRemoveAllDownloads(ctx, DownloadService::class.java, false)
        }.onFailure { Logx.w(TAG, "全部删除下载失败: " + it.message) }
    }

    /** 单独删除一首（专辑组内也可单独删）；参数是请求 id。 */
    fun deleteTrack(ctx: Context, requestId: String) {
        runCatching {
            Media3DownloadService.sendRemoveDownload(ctx, DownloadService::class.java, requestId, true)
        }
    }

    /**
     * 立即清空下载（同步版本，供「清除本机数据」使用）。
     *
     * 与 [deleteAll] 的区别：这里直接操作 DownloadManager 并在返回前清空内存镜像，
     * 让随后的缓存清空不会再与下载写入线程抢同一批 span（审计 D16）。
     */
    suspend fun removeAllNow(ctx: Context) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val manager = runCatching { MediaCache.downloadManager(app) }.getOrNull()
        if (manager != null) {
            runCatching { manager.pauseDownloads() }
            val ids = ArrayList<String>()
            runCatching {
                manager.downloadIndex.getDownloads().use { c ->
                    while (c.moveToNext()) ids.add(c.download.request.id)
                }
            }
            ids.forEach { id -> runCatching { manager.removeDownload(id) } }
        }
        entries.clear()
        bytesByRequestId.clear()
        dirty.clear()
        completedKeyByTrack = emptyMap()
        MediaCache.playbackDownloadKey = { null }
        _snapshot.value = emptyList()
    }

    /**
     * 重试一个分组里失败的曲目。
     *
     * 关键点：**复用原 request**（同一个 id 与 cache key）重新入队——已下载的 span 会被
     * 续用，所以这是真续传，而不是从头再来（审计 D5）。不传 Foreground 会让服务在
     * 后台被限制启动，因此这里保持 true。
     */
    fun retry(ctx: Context, key: String) {
        retryWhere(ctx) { d -> keyOf(d) == key }
    }

    /** 重试单曲（参数是请求 id）。 */
    fun retryTrack(ctx: Context, requestId: String) {
        retryWhere(ctx) { d -> d.request.id == requestId }
    }

    private fun retryWhere(ctx: Context, predicate: (Download) -> Boolean) {
        val app = ctx.applicationContext
        scope.launch {
            runCatching {
                val targets = entries.values.filter { d ->
                    (d.state == Download.STATE_FAILED || d.state == Download.STATE_STOPPED) && predicate(d)
                }
                if (targets.isEmpty()) return@runCatching
                // 失败/暂停的请求重新入队，Media3 会复用已写入的 span 继续下载
                targets.forEach { d ->
                    failureMessages.remove(d.request.id)
                    Media3DownloadService.sendAddDownload(app, DownloadService::class.java, d.request, true)
                }
            }.onFailure { Logx.w(TAG, "重试下载失败: " + it.message) }
        }
    }

    /**
     * 组内暂停 / 继续。
     *
     * 用 downloadManager.setStopReason(id, ...) 精确控制单个请求：media3 只有
     * 「全局暂停」，而用户真正想暂停的往往是一张专辑（审计 C2/D13）。
     */
    fun pauseGroup(ctx: Context, key: String) = setGroupStopped(ctx, key, true)

    fun resumeGroup(ctx: Context, key: String) = setGroupStopped(ctx, key, false)

    private fun setGroupStopped(ctx: Context, key: String, stopped: Boolean) {
        val app = ctx.applicationContext
        scope.launch {
            runCatching {
                val manager = MediaCache.downloadManager(app)
                entries.values
                    .filter { keyOf(it) == key && it.state != Download.STATE_COMPLETED && it.state != Download.STATE_REMOVING }
                    .forEach { d ->
                        manager.setStopReason(
                            d.request.id,
                            if (stopped) STOP_REASON_APP_PAUSE else Download.STOP_REASON_NONE,
                        )
                    }
            }.onFailure { Logx.w(TAG, "分组暂停/继续失败: " + it.message) }
        }
    }

    fun pauseAll(ctx: Context) {
        runCatching { Media3DownloadService.sendPauseDownloads(ctx, DownloadService::class.java, true) }
    }

    fun resumeAll(ctx: Context) {
        runCatching { Media3DownloadService.sendResumeDownloads(ctx, DownloadService::class.java, true) }
    }
}
