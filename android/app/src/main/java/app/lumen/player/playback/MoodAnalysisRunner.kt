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
        private set
    /** 当前服务器的取流方式（分段 / 整段），用于气泡状态条 */
    var modeLabel by mutableStateOf("")
        private set
    var modeDetail by mutableStateOf("")
        private set

    fun isActive(): Boolean = job?.isActive == true

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
                MoodAnalyzer(appCtx).use { analyzer ->
                    for (t in tracks) {
                        if (!isActive) return@launch
                        if (MoodStore.get(appCtx, t.id) != null) continue
                        if (!MoodStore.shouldAttempt(appCtx, t.id)) {
                            done++
                            continue
                        }
                        val source = app.lumen.player.data.AudioSource(
                            transcodeUrl = transcodeUrl(client, t),
                            durationSec = t.seconds,
                        )
                        currentTitle = t.title
                        phase = "准备"
                        runCatching {
                            analyzer.analyze(t.id, source, capability) { name, _ -> phase = name }
                        }
                            .onSuccess {
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

    private fun loadTrackList(ctx: Context, client: SubsonicClient, albums: List<Album>): List<Track> {
        val file = File(ctx.cacheDir, CACHE)
        val cached = runCatching { read(file) }.getOrNull()
        if (cached != null && cached.first == albums.size) return cached.second
        val all = ArrayList<Track>()
        albums.forEach { a ->
            runCatching { client.tracks(a.id) }.getOrDefault(emptyList()).forEach { all.add(it) }
        }
        if (all.isNotEmpty()) runCatching { write(file, albums.size, all) }
        return all
    }

    private fun read(file: File): Pair<Int, List<Track>>? {
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
        return o.optInt("albums") to out
    }

    private fun write(file: File, albums: Int, tracks: List<Track>) {
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
        file.writeText(JSONObject().put("albums", albums).put("tracks", arr).toString())
    }
}