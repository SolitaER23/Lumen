package app.lumen.player.playback

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lumen.player.data.Album
import app.lumen.player.data.MoodAnalyzer
import app.lumen.player.data.MoodStore
import app.lumen.player.data.SegmentMode
import app.lumen.player.data.SettingsStore
import app.lumen.player.data.StreamCapability
import app.lumen.player.data.StreamCapabilityStore
import app.lumen.player.data.StreamProber
import app.lumen.player.data.SubsonicClient
import app.lumen.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 全库心情分析的后台执行器。
 *
 * - 触发：曲库加载完成后调用 [start]，每次冷启动都会续跑未完成的曲目；
 * - 存活：绑定应用级协程作用域，切后台继续执行；用户从最近任务划掉进程即停止（符合预期）；
 * - 无感：不弹窗、不发通知，只有一个「正在分析」小图标，单曲约 1~2 秒；
 * - 失败：写入失败表并冷却重试，避免坏文件卡住整个队列。
 */
object MoodAnalysisRunner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    /** 当前阶段：下载 / 解码 / DSP / YAMNet / 融合 / 准备 */
    var phase by mutableStateOf("")
        private set
    /** 当前曲目名，用于气泡里的「正在处理」 */
    var currentTitle by mutableStateOf("")

    /** 正在播放的曲目 id（播放器上报），分析队列会把该曲排到最前。 */
    @Volatile
    var currentTrackId: String? = null
        private set

    fun notePlaying(trackId: String?) {
        currentTrackId = trackId
    }

    /** 当前服务器的取流方式（分段 / 整段），用于气泡状态条 */
    var modeLabel by mutableStateOf("")
        private set
    var modeDetail by mutableStateOf("")
        private set

    fun isActive(): Boolean = job?.isActive == true

    private const val PREF_FINE = "mood_fine_mode"

    /** 精细分析（5 窗）开关；切换后 model_id 变化会触发全库重算。 */
    fun isFineMode(ctx: Context): Boolean =
        ctx.getSharedPreferences("lumen_mood", Context.MODE_PRIVATE).getBoolean(PREF_FINE, false)

    fun setFineMode(ctx: Context, fine: Boolean) {
        ctx.getSharedPreferences("lumen_mood", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_FINE, fine).apply()
    }

    @Volatile
    private var paused = false

    fun isPaused(): Boolean = paused

    /** 用户主动暂停：循环在下一首之前检查并挂起（不退出协程，可原地恢复）。 */
    fun pause() { paused = true }

    fun resume() { paused = false }

    /**
     * 电量门控：低于阈值且未充电时暂停分析，避免把用户电量跑空。
     * 返回 null 表示可继续，否则返回暂停原因（用于界面提示）。
     */
    private fun pauseReason(ctx: Context): String? {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager

        // 1) 电量过低且未充电
        if (bm != null) {
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level in 0..14 && !bm.isCharging) return "电量过低（" + level + "%），已暂停分析"
        }

        // 2) 息屏且未充电：CLAP 推理会持续占用 CPU，息屏时暂停更省电
        if (pm != null && !pm.isInteractive && bm?.isCharging != true) {
            return "息屏且未充电，已暂停分析"
        }

        // 3) 过热降级：严重发热时停跑，避免持续高温
        if (pm != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val thermal = pm.currentThermalStatus
            if (thermal >= android.os.PowerManager.THERMAL_STATUS_SEVERE) {
                return "设备温度过高（thermal=" + thermal + "），已暂停分析"
            }
        }

        return null
    }


    /** 幂等启动：已在跑就忽略；[albums] 用于首次枚举曲目（结果会缓存）。 */
    fun start(ctx: Context, client: SubsonicClient, albums: List<Album>) {
        if (isActive()) return
        val appCtx = ctx.applicationContext
        job = scope.launch {
            running = true
            try {
                val tracks = loadTrackList(appCtx, client, albums)
                total = tracks.size
                done = MoodStore.count(appCtx).coerceAtMost(tracks.size)
                if (tracks.isEmpty()) return@launch
                val capKey = runCatching { SettingsStore.load(appCtx).base }.getOrDefault("")
                var capability = capabilityFor(appCtx, client, tracks.first(), capKey)
                modeLabel = capability.label
                modeDetail = capability.detail
                var segmentFailures = 0
                var fallbackEma = 0.0
                var samples = 0
                // 清理历史遗留的 YAMNet 模型副本（已不再使用，占 4 MB）
                runCatching { File(appCtx.filesDir, "yamnet.tflite").takeIf { it.exists() }?.delete() }
                var clapSmokeDone = false
                // 乱序分析：避免整张专辑先跑完、样本集中在某一风格；
                // 用固定种子打散，保证同一设备多次运行顺序一致（便于对比）
                val shuffledTracks = run {
            val base = tracks.shuffled(kotlin.random.Random(tracks.size * 31 + 7))
            // 正在播放的曲目优先分析（用户最可能马上想看到它的波形）
            val pid = currentTrackId
            if (pid != null) base.sortedBy { if (it.id == pid) 0 else 1 } else base
        }
                MoodAnalyzer(appCtx).use { analyzer ->
                    for (t in shuffledTracks) {
                        if (!isActive) return@launch
                        while (paused && isActive) kotlinx.coroutines.delay(400)
                        // 电量过低/息屏未充电/过热：等条件恢复再继续。
                        // 旧实现在这里 return@launch，等于「暂停」会永久结束本轮分析，
                        // 用户插上电源也不会自动续跑。
                        var blockReason = pauseReason(appCtx)
                        if (blockReason != null) {
                            app.lumen.player.util.Logx.i("LumenMood", "paused: " + blockReason)
                            while (blockReason != null && isActive) {
                                modeDetail = blockReason
                                kotlinx.coroutines.delay(10_000)
                                blockReason = pauseReason(appCtx)
                            }
                            if (!isActive) return@launch
                            modeDetail = capability.label
                        }
                        // 精细档只跳过已精细分析的曲目；标准档只要分析过就跳过
                        val requireFine = true
                        if (MoodStore.isAnalyzed(appCtx, t.id, requireFine)) { done++; continue }
                        if (!MoodStore.shouldAttempt(appCtx, t.id)) {
                            done++
                            continue
                        }
                        // 原始流：mp3/m4a/aac/ogg/opus 可直接解码，省掉服务端转码等待
                        val rawUrl = if (t.suffix.lowercase() in
                            setOf("mp3", "m4a", "aac", "ogg", "opus", "flac")
                        ) client.streamUrl(t.id) else null
                        val source = app.lumen.player.data.AudioSource(
                            transcodeUrl = transcodeUrl(client, t),
                            durationSec = t.seconds,
                            rawUrl = rawUrl,
                            // 只有帧式容器适合按字节切段；flac/wav 切在帧中间会解出损坏数据
                            windows = 5,
                            rawRangeSafe = t.suffix.lowercase() in setOf("mp3", "aac", "ogg", "opus"),
                        )
                        currentTitle = t.title
                        phase = "准备"
                        runCatching {
                            analyzer.analyze(t.id, source, capability) { name, _ -> phase = name }
                        }
                            .onSuccess {
                                // P1-4 冒烟：首曲之后测一次 CLAP embedding 的维度与耗时
                                if (!clapSmokeDone) {
                                    clapSmokeDone = true
                                    runCatching {
                                        val t0 = System.currentTimeMillis()
                                        app.lumen.player.data.ClapEmbedder(appCtx).use { emb ->
                                            val loadMs = System.currentTimeMillis() - t0
                                            val pcm = FloatArray(48000 * 120)
                                            val offs = emb.windowOffsets(pcm.size)
                                            val times = ArrayList<Long>()
                                            var last = FloatArray(0)
                                            for (o in offs) {
                                                val t = System.currentTimeMillis()
                                                last = emb.embedWindow(pcm, o)
                                                times.add(System.currentTimeMillis() - t)
                                            }
                                            val v = last
                                            val norm = kotlin.math.sqrt(v.fold(0f) { acc, x -> acc + x * x })
                                            app.lumen.player.util.Logx.i(
                                                "LumenClap",
                                                "CLAP_SMOKE dim=" + v.size + " load=" + loadMs +
                                                    "ms per=" + times + " norm=" + "%.3f".format(norm),
                                            )
                                        }
                                    }.onFailure {
                                        app.lumen.player.util.Logx.w("LumenClap", "CLAP_SMOKE failed: " + it.message)
                                    }
                                }
                                MoodStore.put(appCtx, it)
                                MoodStore.clearFailure(appCtx, t.id)
                                app.lumen.player.util.Logx.i("LumenMood", "ok ${t.id} ${it.label}")
                                // 冷转码（未缓存）时 Range 不可用，会逐首回退整段；
                                // 连续多首回退说明这套服务器基本只能整段读，切过去避免每次都白试一次
                                fallbackEma = fallbackEma * 0.8 + (if (analyzer.lastFellBack) 0.2 else 0.0)
                                samples++
                                if (samples >= 10 && fallbackEma >= 0.7 && capability.mode != SegmentMode.FULL) {
                                    capability = StreamCapability(
                                        mode = SegmentMode.FULL,
                                        label = "整段读取",
                                        detail = "冷转码不支持分段，已切整段",
                                    )
                                    modeLabel = capability.label
                                    modeDetail = capability.detail
                                    fallbackEma = 0.0
                                    samples = 0
                                }
                            }
                            .onFailure {
                                val attempts = MoodStore.recordFailure(appCtx, t.id)
                                app.lumen.player.util.Logx.w("LumenMood", "fail ${t.id} ($attempts): ${it.message}")
                                // 反复失败（≥5 次）的曲目自动退出队列，避免坏文件周期性回来
                                if (attempts >= 5) {
                                    MoodStore.ignore(appCtx, t.id)
                                    app.lumen.player.util.Logx.w("LumenMood", "ignored ${t.id} after $attempts failures")
                                }
                                // 连续多次分段失败 → 本次运行降级为整段（不写缓存，避免一次抖动把服务器永久判死）
                                segmentFailures++
                                if (capability.mode != SegmentMode.FULL && segmentFailures >= 5) {
                                    capability = StreamCapability(
                                        mode = SegmentMode.FULL,
                                        label = "整段读取",
                                        detail = "分段读取失败，本次已自动降级",
                                    )
                                    modeLabel = capability.label
                                    modeDetail = capability.detail
                                    segmentFailures = 0
                                }
                            }
                        done++
                        phase = "完成"
                        // 让出 CPU：避免连续满负荷分析与播放解码抢核心
                        delay(120)
                    }
                }
            } finally {
                running = false
                job = null
                phase = ""
                currentTitle = ""
            }
        }
    }

    /** 探测一次服务器能力并缓存；失败直接按「整段」处理，绝不让探测阻塞分析。 */
    private suspend fun capabilityFor(
        ctx: android.content.Context,
        client: SubsonicClient,
        sample: Track,
        key: String,
    ): StreamCapability {
        StreamCapabilityStore.get(ctx, key)?.let { return it }
        val cap = withContext(Dispatchers.IO) {
            runCatching {
                StreamProber().probe(transcodeUrl(client, sample), sample.seconds)
            }.getOrElse {
                StreamCapability(
                    mode = SegmentMode.FULL,
                    label = "整段读取",
                    detail = "探测失败，已退回整段",
                )
            }
        }
        StreamCapabilityStore.put(ctx, key, cap)
        return cap
    }

    private fun transcodeUrl(client: SubsonicClient, t: Track): String =
        client.streamUrl(t.id) + "&format=mp3&maxBitRate=96"

    fun stop() {
        job?.cancel()
        job = null
        running = false
    }

    // ---------------- 曲目清单（避免每次冷启动都重新拉 95 张专辑） ----------------

    private const val CACHE = "mood_tracklist.json"

    /**
     * 清理分析相关落盘数据：曲目清单缓存 + 被中断留下的临时音频片段。
     * 临时文件正常情况下由 analyze() 的 finally 删除，进程被杀时会残留。
     */
    fun clearCache(ctx: Context) {
        val app = ctx.applicationContext
        runCatching { File(app.cacheDir, CACHE).delete() }
        runCatching {
            app.cacheDir.listFiles { f -> f.name.startsWith("mood_") }?.forEach { it.delete() }
        }
    }

    /** 只读缓存曲目表（不联网），供音色搜索使用。 */
    fun cachedTracks(ctx: Context): List<Track> =
        runCatching { read(File(ctx.cacheDir, CACHE))?.second }.getOrNull() ?: emptyList()

    /**
     * 专辑集合签名。旧实现只比较专辑**数量**，增删各一张时数量不变、缓存永不刷新，
     * 曲目表会一直停留在旧内容。
     */
    private fun signatureOf(albums: List<Album>): String =
        albums.size.toString() + ":" + albums.joinToString(",") { it.id }.hashCode().toString()

    private fun loadTrackList(ctx: Context, client: SubsonicClient, albums: List<Album>): List<Track> {
        val file = File(ctx.cacheDir, CACHE)
        val sig = signatureOf(albums)
        val cached = runCatching { read(file) }.getOrNull()
        if (cached != null && cached.first == sig) return cached.second
        val all = ArrayList<Track>()
        albums.forEach { a ->
            runCatching { client.tracks(a.id) }.getOrDefault(emptyList()).forEach { all.add(it) }
        }
        if (all.isNotEmpty()) runCatching { write(file, sig, all) }
        return all
    }

    private fun read(file: File): Pair<String, List<Track>>? {
        if (!file.isFile) return null
        val o = JSONObject(file.readText())
        val arr = o.optJSONArray("tracks") ?: return null
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            out.add(
                Track(
                    id = t.getString("id"),
                    title = t.optString("title"),
                    artist = t.optString("artist", ""),
                    albumId = t.optString("albumId", ""),
                    album = t.optString("album", ""),
                    disc = t.optInt("disc", 1),
                    track = t.optInt("track", 0),
                    seconds = t.optInt("seconds", 0),
                    suffix = t.optString("suffix", ""),
                    starred = t.optBoolean("starred", false),
                ),
            )
        }
        val sig = o.optString("sig")
        if (sig.isBlank()) return null // 旧格式（只有专辑数量）：当作未命中，重拉一次
        return sig to out
    }

    private fun write(file: File, signature: String, tracks: List<Track>) {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id).put("title", t.title).put("artist", t.artist)
                    .put("albumId", t.albumId).put("album", t.album).put("disc", t.disc)
                    .put("track", t.track).put("seconds", t.seconds)
                    .put("suffix", t.suffix).put("starred", t.starred),
            )
        }
        file.writeText(JSONObject().put("sig", signature).put("tracks", arr).toString())
    }
}