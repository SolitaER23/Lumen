package app.lumen.player.data

import app.lumen.player.util.L10n
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 端侧心情分析：
 * 拉流 → MediaCodec 解码 → 16kHz 单声道 → YAMNet(tflite) + DSP
 * → 逐类重标定 → Russell 坐标 → 标签。CNN14/ONNX 已移除（体积与耗时都太大）。
 */
/** 一次分析需要的音频来源：转码流地址 + 曲目时长（用于切三段）。 */
data class AudioSource(
    val transcodeUrl: String,
    val durationSec: Int,
    val rawUrl: String? = null,
    /** 原始流是否可按字节 Range 切段（帧式容器才安全；flac 等会切坏帧头） */
    val rawRangeSafe: Boolean = false,
    /** 采样窗数：3=标准，5=精细（覆盖更多段落） */
    val windows: Int = 5,
)

class MoodAnalyzer(private val ctx: Context) : java.io.Closeable {

    companion object {
        private const val SR = 16000
    }

    private val http = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // 模型较重：一次分析多首时复用同一份解释器/Session
    private var clap: ClapEmbedder? = null
    private fun clapEmbedder(): ClapEmbedder = clap ?: ClapEmbedder.shared(ctx).also { clap = it }

    /** 三段并行预取：串行 fetch 是当前主要瓶颈 */
    private val fetchPool = java.util.concurrent.Executors.newFixedThreadPool(3)

    /** 本次 analyze 是否因分段不可用而回退到整段（供上层统计冷缓存比例） */
    var lastFellBack: Boolean = false
        private set

    /** 分阶段耗时（ms），供 P0-3 标定使用 */
    private var tFetchMs = 0L
    private var tDecodeMs = 0L
    private var tDspMs = 0L
    private var tYamnetMs = 0L

    fun analyze(
        trackId: String,
        source: AudioSource,
        capability: StreamCapability,
        onPhase: (String, Float) -> Unit = { _, _ -> },
    ): MoodResult {
        app.lumen.player.util.Logx.i("LumenMood", "start analysis " + trackId + " mode=" + capability.mode)
        lastFellBack = false
        val temps = ArrayList<File>()
        try {
            val rawUrl = source.rawUrl
            val sampled = if (rawUrl != null) {
                // 优先原始流 + Range 分段：只拉三窗，避免整首下载
                runCatching {
                    val len = if (source.rawRangeSafe) headContentLength(rawUrl) else -1L
                    if (len > 0 && source.durationSec > 0) {
                        rawSegmentSampled(trackId, rawUrl, len, source.durationSec, temps, source.windows, onPhase)
                    } else {
                        fullSampled(trackId, rawUrl, temps, onPhase)
                    }
                }.getOrElse { e ->
                    app.lumen.player.util.Logx.w("LumenMood", "raw failed, fallback transcode: " + e.message)
                    temps.forEach { runCatching { it.delete() } }
                    temps.clear()
                    if (capability.mode == SegmentMode.FULL) {
                        fullSampled(trackId, source.transcodeUrl, temps, onPhase)
                    } else {
                        segmentSampled(trackId, source, capability, temps, onPhase)
                    }
                }
            } else if (capability.mode == SegmentMode.FULL) {
                fullSampled(trackId, source.transcodeUrl, temps, onPhase)
            } else {
                runCatching {
                    segmentSampled(trackId, source, capability, temps, onPhase)
                }.getOrElse { e ->
                    // 分段不可用（冷转码 / 代理缓存 / Range 被忽略）→ 本首退回整段，并清掉半成品
                    lastFellBack = true
                    app.lumen.player.util.Logx.w("LumenMood", "segment failed, fallback full: " + e.message)
                    temps.forEach { runCatching { it.delete() } }
                    temps.clear()
                    fullSampled(trackId, source.transcodeUrl, temps, onPhase)
                }
            }
            onPhase(L10n.t("分析节奏"), 0.58f)
            val tDspStart = System.currentTimeMillis()
            // S2：解码输出为 48 kHz；DSP 沿用 16 kHz（3:1 抽取），CLAP 直接用 48 kHz
        val pcm16 = sampled.pcm.map { decimate3(it) }
        val dsp = dspFeatures(pcm16)
            tDspMs = System.currentTimeMillis() - tDspStart
            onPhase(L10n.t("识别音色"), 0.62f)
            val tYamStart = System.currentTimeMillis()
            // 用 CLAP 音乐模型替代 YAMNet：embedding(1024) 做相似度，clipwise(527) 做标签与情绪
            val embedder = clapEmbedder()
            // S3：三窗并行推理（ONNX Runtime 的 Session.run 线程安全）
            val embs = ArrayList<FloatArray>()
            val clips = ArrayList<FloatArray>()
            val segs = ArrayList<AudioSegment>()
            val winPool = java.util.concurrent.Executors.newFixedThreadPool(sampled.pcm.size.coerceIn(1, 3))
            try {
                val perWindow = sampled.pcm.mapIndexed { i, p48 ->
                    winPool.submit<Pair<FloatArray, FloatArray>>(java.util.concurrent.Callable {
                        val off = ((p48.size - ClapEmbedder.WINDOW) / 2).coerceAtLeast(0)
                        embedder.embedWindowPair(p48, off)
                    })
                }
                perWindow.forEachIndexed { i, f ->
                    val (e, c) = f.get()
                    embs.add(e)
                    clips.add(c)
                    val p48 = sampled.pcm[i]
                    segs.add(
                        AudioSegment(
                            startMs = sampled.startsMs.getOrElse(i) { 0 },
                            endMs = sampled.startsMs.getOrElse(i) { 0 } + (p48.size * 1000L / 48000).toInt(),
                            tags = MoodMath.topTags(c, limit = 4).map { it.name },
                        ),
                    )
                }
            } finally {
                winPool.shutdown()
            }
            tYamnetMs = System.currentTimeMillis() - tYamStart
            val feature = FloatArray(ClapEmbedder.DIM)
            embs.forEach { e -> for (k in feature.indices) feature[k] += e[k] }
            val en = embs.size.coerceAtLeast(1)
            for (k in feature.indices) feature[k] /= en
            val clipAvg = FloatArray(clips.firstOrNull()?.size ?: 0)
            clips.forEach { c -> for (k in clipAvg.indices) clipAvg[k] += c[k] }
            val cn = clips.size.coerceAtLeast(1)
            for (k in clipAvg.indices) clipAvg[k] /= cn
            val anchorProbs = runCatching { embedder.anchorProbs(feature) }.getOrNull()
            anchorProbs?.let { pr ->
                run {
                    val idx = pr.indices.sortedByDescending { pr[it] }.take(3)
                    app.lumen.player.util.Logx.i(
                        "LumenMood",
                        "ANCHOR " + idx.joinToString(" ") { MoodAnchors.names[it].split(" ")[0] + "=" + "%.2f".format(pr[it]) },
                    )
                }
            }
            onPhase(L10n.t("生成结果"), 0.99f)
            val clipOrNull: FloatArray? = if (clipAvg.isEmpty()) null else clipAvg
            // 逐窗情绪：对每个窗的 embedding 单独跑锚点投影（成本极小）
            val perWinV = embs.map { e ->
                val p = runCatching { embedder.anchorProbs(e) }.getOrNull()
                if (p == null) 0.5f to 0.5f
                else {
                    val r = MoodMath.fuseAnchors(p, dsp, trackId)
                    r.valence to r.arousal
                }
            }
            val anchorResult = anchorProbs?.let { MoodMath.fuseAnchors(it, dsp, trackId) }
            val result = MoodMath.fuse(clipOrNull, dsp, trackId).copy(
                tags = MoodMath.topTags(clipOrNull),
                feature = feature,
                segments = segs,
                windowsVec = embs,
                windowsBeats = splitBeatsByWindow(dsp.beatsMs, pcm16.map { it.size }),
                windowsTempo = if (dsp.windowTempos.isNotEmpty()) dsp.windowTempos else FloatArray(pcm16.size) { dsp.tempoBpm },
                windowsStartMs = sampled.startsMs.toIntArray(),
                windowsValence = perWinV.map { it.first }.toFloatArray(),
                windowsArousal = perWinV.map { it.second }.toFloatArray(),
            ).let { base -> anchorResult?.copy(
                    tags = base.tags,
                    feature = base.feature,
                    segments = base.segments,
                    windowsVec = base.windowsVec,
                    windowsBeats = base.windowsBeats,
                    windowsTempo = base.windowsTempo,
                    windowsStartMs = base.windowsStartMs,
                    windowsValence = base.windowsValence,
                    windowsArousal = base.windowsArousal,
                ) ?: base }
            app.lumen.player.util.Logx.i(
                "LumenMood",
                "timing fetch=" + tFetchMs + " decode=" + tDecodeMs + " dsp=" + tDspMs +
                    " clap=" + tYamnetMs + " total=" + (tFetchMs + tDecodeMs + tDspMs + tYamnetMs),
            )
            app.lumen.player.util.Logx.i(
                "LumenMood",
                "DSP_EXTRA lufs=" + "%.1f".format(dsp.lufs) + " dr=" + "%.1f".format(dsp.dynamicRange) +
                    " flat=" + "%.3f".format(dsp.flatness) + " rolloff=" + dsp.rolloffHz.toInt() +
                    " key=" + dsp.keyIndex + (if (dsp.keyMinor) "m" else "") +
                    " conf=" + "%.2f".format(dsp.keyConfidence),
            )
            app.lumen.player.util.Logx.i(
                "LumenMood",
                "mood " + trackId + " label=" + result.label + " beats=" + result.beatsMs.size +
                    " env=" + result.env.size + " tempo=" + "%.0f".format(result.tempo),
            )
            return result
        } finally {
            temps.forEach { runCatching { it.delete() } }
        }
    }

    /** HEAD 取原始文件大小，用于 Range 分段。返回 -1 表示不支持。 */
    private fun headContentLength(url: String): Long = runCatching {
        val req = okhttp3.Request.Builder().url(url).head().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return -1L
            // 明确不支持 Range 的服务器直接放弃
            if (resp.header("Accept-Ranges")?.equals("none", true) == true) return -1L
            resp.header("Content-Length")?.toLongOrNull() ?: -1L
        }
    }.getOrDefault(-1L)

    /** 对原始流做 Range 分段：只拉三段 30 秒，而不是整首。 */
    private fun rawSegmentSampled(
        trackId: String,
        url: String,
        totalBytes: Long,
        durationSec: Int,
        temps: MutableList<File>,
        windowCount: Int,
        onPhase: (String, Float) -> Unit,
    ): SampledAudio {
        val durationMs = durationSec.coerceAtLeast(1) * 1000
        val want = windowCount.coerceIn(1, 5)
        val windows: List<IntRange> = if (durationMs <= 90_000) {
            listOf(0 until durationMs)
        } else {
            (0 until want).map { i ->
                val center = if (want == 1) durationMs / 2 else (durationMs * i) / (want - 1)
                val start = (center - 15_000).coerceIn(0, (durationMs - 30_000).coerceAtLeast(0))
                start until (start + 30_000).coerceAtMost(durationMs)
            }.distinct()
        }
        app.lumen.player.util.Logx.i(
            "LumenMood",
            "RANGE_FETCH total=" + totalBytes + " dur=" + durationSec + "s windows=" + windows.size,
        )
        onPhase(L10n.t("读取片段"), 0.1f)
        val tFetch = System.currentTimeMillis()
        val parts = ArrayList<Part>(windows.size)
        windows.forEachIndexed { i, w ->
            val file = File(ctx.cacheDir, "mood_" + trackId + "_r" + i + ".part")
            synchronized(temps) { temps.add(file) }
            fetchRange(url, totalBytes, durationSec, w, file)
            parts.add(Part(file, w.first))
            onPhase(L10n.t("读取片段"), 0.1f + 0.35f * (i + 1) / windows.size)
        }
        tFetchMs = System.currentTimeMillis() - tFetch
        onPhase(L10n.t("解析音频"), 0.46f)
        val tDecode = System.currentTimeMillis()
        val decoded = decodeParts(parts) ?: error(L10n.t("解码失败"))
        tDecodeMs = System.currentTimeMillis() - tDecode
        return decoded
    }

    private fun download(url: String, out: File, onProgress: (Float) -> Unit) {
        val req = okhttp3.Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("empty body")
            val total = body.contentLength()
            out.outputStream().use { os ->
                body.byteStream().use { ins ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        os.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }

    private fun fullSampled(
        trackId: String,
        url: String,
        temps: MutableList<File>,
        onPhase: (String, Float) -> Unit,
    ): SampledAudio {
        onPhase(L10n.t("读取整段"), 0f)
        val f = File(ctx.cacheDir, "mood_" + trackId + ".mp3").also { temps.add(it) }
        val tFetch = System.currentTimeMillis()
        download(url, f) { onPhase(L10n.t("读取整段"), it * 0.45f) }
        tFetchMs = System.currentTimeMillis() - tFetch
        onPhase(L10n.t("解析音频"), 0.46f)
        val tDecode = System.currentTimeMillis()
        val decoded = decodeSegments(f) ?: error(L10n.t("解码失败"))
        tDecodeMs = System.currentTimeMillis() - tDecode
        return decoded
    }

    private fun segmentSampled(
        trackId: String,
        source: AudioSource,
        capability: StreamCapability,
        temps: MutableList<File>,
        onPhase: (String, Float) -> Unit,
    ): SampledAudio {
        onPhase(L10n.t("读取片段"), 0f)
        val tFetch = System.currentTimeMillis()
        val parts = fetchParts(trackId, source, capability, temps) { p ->
            onPhase(L10n.t("读取片段"), p * 0.45f)
        }
        tFetchMs = System.currentTimeMillis() - tFetch
        onPhase(L10n.t("解析音频"), 0.46f)
        val tDecode = System.currentTimeMillis()
        val decoded = decodeParts(parts) ?: error(L10n.t("解码失败"))
        tDecodeMs = System.currentTimeMillis() - tDecode
        return decoded
    }

    private data class Part(val file: File, val startMs: Int)

    /** 按策略取三段音频：timeOffset / Range 各取 30 秒；短曲只取一段。 */
    private fun fetchParts(
        trackId: String,
        source: AudioSource,
        capability: StreamCapability,
        temps: MutableList<File>,
        onProgress: (Float) -> Unit,
    ): List<Part> {
        val durationMs = source.durationSec.coerceAtLeast(1) * 1000
        // 统一 5 窗：按 0 / 25% / 50% / 75% / 100% 均匀取 30 秒片段
        val want = source.windows.coerceIn(1, 5)
        val windows: List<IntRange> = if (durationMs <= 90_000) {
            listOf(0 until durationMs)
        } else {
            (0 until want).map { i ->
                val center = if (want == 1) durationMs / 2 else (durationMs * i) / (want - 1)
                val start = (center - 15_000).coerceIn(0, (durationMs - 30_000).coerceAtLeast(0))
                start until (start + 30_000).coerceAtMost(durationMs)
            }.distinct()
        }
        val parts = arrayOfNulls<Part>(windows.size)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val futures = windows.mapIndexed { i, w ->
            fetchPool.submit {
                val file = File(ctx.cacheDir, "mood_" + trackId + "_" + i + ".part")
                synchronized(temps) { temps.add(file) }
                when (capability.mode) {
                    SegmentMode.TIME_OFFSET -> fetchTime(source.transcodeUrl, w.first / 1000, file)
                    SegmentMode.RANGE -> fetchRange(
                        url = source.transcodeUrl,
                        totalBytes = capability.totalBytes,
                        durationSec = source.durationSec,
                        window = w,
                        out = file,
                    )
                    SegmentMode.FULL -> Unit
                }
                parts[i] = Part(file, w.first)
                onProgress((done.incrementAndGet()).toFloat() / windows.size)
            }
        }
        futures.forEach { runCatching { it.get() }.getOrElse { e -> throw e } }
        return parts.filterNotNull()
    }

    /** timeOffset 分段：只读到 30 秒多一点就断开，不落整首。 */
    private fun fetchTime(url: String, offsetSec: Int, out: File) {
        val maxBytes = 32L * 13_000L
        val req = okhttp3.Request.Builder().url(url + "&timeOffset=" + offsetSec).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP " + resp.code)
            val body = resp.body ?: error("empty body")
            out.outputStream().use { os ->
                body.byteStream().use { ins ->
                    val buf = ByteArray(32 * 1024)
                    var read = 0L
                    while (read < maxBytes) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        val take = minOf(n.toLong(), maxBytes - read).toInt()
                        os.write(buf, 0, take)
                        read += take
                    }
                }
            }
        }
    }

    /** Range 分段：按总长度换算字节偏移，前后各留 1.5 秒余量。 */
    private fun fetchRange(url: String, totalBytes: Long, durationSec: Int, window: IntRange, out: File) {
        if (totalBytes <= 0) error("unknown content length")
        val dur = durationSec.coerceAtLeast(1)
        val rate = totalBytes.toDouble() / dur
        val start = ((window.first / 1000.0 - 1.5).coerceAtLeast(0.0) * rate).toLong().coerceIn(0L, totalBytes - 1)
        val end = ((window.last / 1000.0 + 1.5) * rate).toLong().coerceIn(start, totalBytes - 1)
        val req = okhttp3.Request.Builder().url(url).header("Range", "bytes=" + start + "-" + end).build()
        http.newCall(req).execute().use { resp ->
            if (resp.code != 206) error("HTTP " + resp.code)
            val body = resp.body ?: error("empty body")
            out.outputStream().use { os -> body.byteStream().use { it.copyTo(os, 32 * 1024) } }
        }
    }

    private fun decodeParts(parts: List<Part>): SampledAudio? {
        // S1：三段并行解码（MediaCodec 实例互相独立，可并发）
        val pool = java.util.concurrent.Executors.newFixedThreadPool(parts.size.coerceIn(1, 3))
        try {
            val futures = parts.map { p ->
                pool.submit<FloatArray?>(java.util.concurrent.Callable { decodeWhole(p.file) })
            }
            val pcm = ArrayList<FloatArray>(parts.size)
            val starts = ArrayList<Int>(parts.size)
            futures.forEachIndexed { i, f ->
                val data = runCatching { f.get() }.getOrNull() ?: return null
                if (data.isEmpty()) return null
                pcm.add(data)
                starts.add(parts[i].startMs)
            }
            return SampledAudio(pcm, starts)
        } finally {
            pool.shutdown()
        }
    }

    /** 解出整段 PCM（用于已切好的 30 秒小片段）。 */
    private fun decodeWhole(file: File): FloatArray? {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) return null
        extractor.selectTrack(trackIndex)
        val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        val buf = Buf(1 shl 18)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val timeoutUs = 10_000L
        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(timeoutUs)
                if (inIdx >= 0) {
                    val ib = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(ib, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, timeoutUs)
            if (outIdx >= 0) {
                val ob = codec.getOutputBuffer(outIdx)!!
                ob.position(info.offset)
                ob.limit(info.offset + info.size)
                val shorts = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                while (shorts.hasRemaining()) {
                    var sum = 0f
                    var ch = 0
                    while (ch < channels && shorts.hasRemaining()) {
                        sum += shorts.get() / 32768f
                        ch++
                    }
                    if (ch > 0) buf.add(sum / ch)
                }
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        if (buf.n == 0) return null
        return resample(buf.array(), buf.n, srcRate)
    }
    /** 三段采样：前 30s、中间 30s、最后 30s；短曲退化为整首。只保留这三段的 PCM。 */
    private data class SampledAudio(val pcm: List<FloatArray>, val startsMs: List<Int>)

    private class Buf(capacity: Int = 1 shl 16) {
        private var a = FloatArray(capacity.coerceAtLeast(1024))
        var n = 0
            private set

        fun add(v: Float) {
            if (n == a.size) a = a.copyOf(a.size * 2)
            a[n++] = v
        }

        fun array(): FloatArray = a
    }

    private fun concat(parts: List<FloatArray>): FloatArray {
        if (parts.isEmpty()) return FloatArray(0)
        if (parts.size == 1) return parts[0]
        val total = parts.sumOf { it.size }
        val out = FloatArray(total)
        var at = 0
        parts.forEach { p -> System.arraycopy(p, 0, out, at, p.size); at += p.size }
        return out
    }

    private fun resample(src: FloatArray, n: Int, srcRate: Int, dstRate: Int = SR): FloatArray {
        if (n == 0) return FloatArray(0)
        if (srcRate == dstRate) return src.copyOf(n)
        val ratio = dstRate.toDouble() / srcRate.toDouble()
        val dstLen = (n * ratio).toInt().coerceAtLeast(1)
        val dst = FloatArray(dstLen)
        for (i in 0 until dstLen) {
            val srcPos = i / ratio
            val i0 = srcPos.toInt().coerceIn(0, n - 1)
            val i1 = (i0 + 1).coerceAtMost(n - 1)
            val frac = (srcPos - i0).toFloat()
            dst[i] = src[i0] * (1f - frac) + src[i1] * frac
        }
        return dst
    }

    /** 读取容器时长（ms）；失败返回 0。 */
    private fun probeDurationMs(file: File): Int {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(file.absolutePath)
            var idx = -1
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) { idx = i; break }
            }
            if (idx < 0) 0 else {
                val us = runCatching { ex.getTrackFormat(idx).getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
                (us / 1000L).toInt()
            }
        } catch (_: Exception) {
            0
        } finally {
            runCatching { ex.release() }
        }
    }

    /**
     * 解码单个时间窗（startMs..endMs）。
     * 用 MediaExtractor.seekTo 只解目标区间，并按输出块批量转换，
     * 不再对整首逐样本做窗口归属判断。
     */
    private fun decodeWindow(file: File, startMs: Int, endMs: Int): FloatArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(file.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    trackIndex = i; format = f; break
                }
            }
            if (trackIndex < 0 || format == null) return null
            ex.selectTrack(trackIndex)
            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            ex.seekTo(startMs.toLong() * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val out = Buf(1 shl 16)
            val info = MediaCodec.BufferInfo()
            val shortArr = ShortArray(1 shl 15)
            var inputDone = false
            var outputDone = false
            val windowStartUs = startMs.toLong() * 1000L
            val windowEndUs = endMs.toLong() * 1000L
            var reachedEnd = false

            while (!outputDone && !reachedEnd) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(1_000L)
                    if (inIdx >= 0) {
                        val ib = codec.getInputBuffer(inIdx)!!
                        val size = ex.readSampleData(ib, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 1_000L)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val ob = codec.getOutputBuffer(outIdx)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val totalShorts = sb.remaining()
                        var done = 0
                        while (done < totalShorts) {
                            val n = minOf(shortArr.size, totalShorts - done)
                            sb.get(shortArr, 0, n)
                            // 该块第一个采样点的时间（us）
                            val blockStartUs = info.presentationTimeUs +
                                (done.toLong() * 1_000_000L / (srcRate.toLong() * channels))
                            val frames = n / channels
                            for (f in 0 until frames) {
                                val tUs = blockStartUs + (f.toLong() * 1_000_000L / srcRate)
                                if (tUs < windowStartUs) continue
                                if (tUs >= windowEndUs) { reachedEnd = true; break }
                                var sum = 0f
                                val base = f * channels
                                for (c in 0 until channels) sum += shortArr[base + c] / 32768f
                                out.add(sum / channels)
                            }
                            done += n
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            if (out.n == 0) return null
            // S2：直接解码到 48 kHz，供 CLAP 使用；DSP 需要 16 kHz 时由调用方抽取
            return resample(out.array(), out.n, srcRate, 48000)
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex.release() }
        }
    }

    /** 三段窗口解码：只解目标区间，短曲退化为整首。 */
    private fun decodeSegments(file: File, want: Int = 5): SampledAudio? {
        val durationMs = probeDurationMs(file)
        val windows: List<IntRange> = when {
            durationMs <= 0 -> listOf(0..Int.MAX_VALUE)
            durationMs <= 60_000 -> listOf(0 until durationMs)
            else -> (0 until want.coerceIn(1, 5)).map { i ->
                val center = if (want == 1) durationMs / 2 else (durationMs * i) / (want - 1)
                val start = (center - 15_000).coerceIn(0, (durationMs - 30_000).coerceAtLeast(0))
                start until (start + 30_000).coerceAtMost(durationMs)
            }.distinct()
        }
        val parts = ArrayList<FloatArray>(windows.size)
        val starts = ArrayList<Int>(windows.size)
        for (w in windows) {
            val end = if (w.last == Int.MAX_VALUE) Int.MAX_VALUE else w.last
            val pcm = decodeWindow(file, w.first, end) ?: return null
            if (pcm.isEmpty()) continue
            parts.add(pcm)
            starts.add(w.first)
        }
        return if (parts.isEmpty()) null else SampledAudio(parts, starts)
    }

    // ---------------- DSP ----------------

    /**
     * DSP 特征。
     * tempo 改为「逐窗估计 + 中位数 + 倍频校正」，拍点按 tempo 生成网格后吸附，
     * 避免拼接窗口边界造成的假 onset 与倍频跳动。
     */
    /** 48 kHz → 16 kHz 抽取（3:1）。先做 3 点平均抑制混叠，再降采样。 */
    private fun decimate3(src: FloatArray): FloatArray {
        val outLen = src.size / 3
        if (outLen <= 0) return src
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val b = i * 3
            out[i] = (src[b] + src[b + 1] + src[b + 2]) / 3f
        }
        return out
    }

    /** 把全曲拍点（毫秒）按窗边界切分，输出相对各窗起点的拍点。 */
    private fun splitBeatsByWindow(beatsMs: IntArray, sizes16k: List<Int>): List<IntArray> {
        if (beatsMs.isEmpty() || sizes16k.isEmpty()) return emptyList()
        val boundsMs = ArrayList<Int>(sizes16k.size + 1)
        var cum = 0L
        boundsMs.add(0)
        sizes16k.forEach { cum += it; boundsMs.add((cum * 1000L / 16000L).toInt()) }
        val out = ArrayList<IntArray>(sizes16k.size)
        for (i in 0 until sizes16k.size) {
            val lo = boundsMs[i]
            val hi = boundsMs[i + 1]
            out.add(beatsMs.filter { it in lo until hi }.map { it - lo }.toIntArray())
        }
        return out
    }

    private fun dspFeatures(parts: List<FloatArray>): MoodMath.Dsp {
        val pcm = concat(parts)
        if (pcm.isEmpty()) return MoodMath.Dsp(0f, 0f, 0f, 0f)

        var sumSq = 0.0
        for (s in pcm) sumSq += (s * s).toDouble()
        val rms = sqrt(sumSq / pcm.size).toFloat()

        val fftSize = 1024
        val hop = 512
        // 半音滤波器组：C2(65.4Hz) 起 5 个八度 = 60 个半音带（类 CQT chroma）
        val SEMITONES = 60
        val bandLo = IntArray(SEMITONES)
        val bandHi = IntArray(SEMITONES)
        val semitoneE = FloatArray(SEMITONES)
        run {
            val binHz = SR.toDouble() / fftSize
            for (b in 0 until SEMITONES) {
                val f = 65.406 * Math.pow(2.0, b / 12.0)
                val lo = ((f * Math.pow(2.0, -1.0 / 24.0)) / binHz).toInt().coerceIn(1, fftSize / 2 - 1)
                val hi = ((f * Math.pow(2.0, 1.0 / 24.0)) / binHz).toInt().coerceIn(lo, fftSize / 2 - 1)
                bandLo[b] = lo
                bandHi[b] = hi
            }
        }
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        var centroidSum = 0.0
        var centroidFrames = 0
        val energies = ArrayList<Float>()
        val chroma = FloatArray(12)
        val frameChroma = FloatArray(12)
        val magBuf = FloatArray(fftSize / 2)
        val frameSpec = FloatArray(fftSize / 2)
        var flatSum = 0.0
        var flatFrames = 0
        var rollSum = 0.0
        var rollFrames = 0
        var pos = 0
        var frameIdx = 0
        while (pos + fftSize <= pcm.size) {
            frameIdx++
            for (i in 0 until fftSize) {
                val w = 0.5f - 0.5f * cos(2.0 * Math.PI * i / (fftSize - 1)).toFloat()
                re[i] = pcm[pos + i] * w
                im[i] = 0f
            }
            fft(re, im)
            var magSum = 0.0
            var weighted = 0.0
            var energy = 0.0
            var logSum = 0.0
            var bins = 0
            var fidx = 0
            frameChroma.fill(0f)
            var peakCount = 0
            for (b in 1 until fftSize / 2) {
                val mag = hypot(re[b].toDouble(), im[b].toDouble())
                val freq = b.toDouble() * SR / fftSize
                magSum += mag
                weighted += mag * freq
                energy += mag * mag
                if (mag > 1e-9) { logSum += ln(mag); bins++ }
                // 音高类累积（chroma）：按 MIDI 音高折叠到 12 个半音类
                magBuf[b] = mag.toFloat()
                if (freq in 20.0..5000.0) frameSpec[fidx++] = mag.toFloat()
            }
            if (magSum > 1e-6) {
                centroidSum += weighted / magSum
                centroidFrames++
            }
            energies.add(energy.toFloat())
            // 谱平坦度：几何均值 / 算术均值（每 3 帧采样，统计量足够）
            val sampled = (frameIdx % 3) == 0
            if (sampled && bins > 0 && magSum > 1e-9) {
                val geo = exp(logSum / bins)
                val arith = magSum / bins
                if (arith > 0) { flatSum += geo / arith; flatFrames++ }
            }
            // 谱滚降 85%
            var accE = 0.0
            var totalE = 0.0
            for (k in 1 until fftSize / 2) totalE += frameSpec[k].toDouble() * frameSpec[k]
            if (sampled && totalE > 1e-12) {
                val target = totalE * 0.85
                for (k in 1 until fftSize / 2) {
                    accE += frameSpec[k].toDouble() * frameSpec[k]
                    if (accE >= target) { rollSum += k.toDouble() * SR / fftSize; rollFrames++; break }
                }
            }
            // HPCP：只对谱峰（局部极大且高于帧内最大值的 2%）折叠到 12 个音高类，
            // 避免宽带能量把 chroma 摊平
            if (frameIdx % 2 == 0) { // CQT 风格的半音滤波器组 chroma
                var frameMax = 0f
                for (k in 1 until fftSize / 2) if (magBuf[k] > frameMax) frameMax = magBuf[k]
                val thr = frameMax * 0.02f
                if (frameMax > 1e-6f) {
                    for (k in 2 until fftSize / 2 - 1) {
                        val m = magBuf[k]
                        if (m < thr || m <= magBuf[k - 1] || m < magBuf[k + 1]) continue
                        val freq = k.toDouble() * SR / fftSize
                        if (freq < 65.0 || freq > 2000.0) continue
                        val midi = 69.0 + 12.0 * ln(freq / 440.0) / ln(2.0)
                        val pc = ((midi.roundToInt() % 12) + 12) % 12
                        frameChroma[pc] += m * m
                        peakCount++
                    }
                }
                // 在半音频带上积分能量（等价于窄带 CQT），再减均值抑制宽带倾斜
                if (frameMax > 1e-6f) {
                    var sum = 0f
                    var cnt = 0
                    for (b in 0 until SEMITONES) {
                        var e = 0f
                        for (k in bandLo[b]..bandHi[b]) e += magBuf[k] * magBuf[k]
                        semitoneE[b] = sqrt(e)
                        sum += semitoneE[b]
                        cnt++
                    }
                    val mean = sum / cnt.coerceAtLeast(1)
                    for (b in 0 until SEMITONES) {
                        val v = semitoneE[b] - mean
                        if (v > 0f) chroma[b % 12] += v
                    }
                }
            }
            pos += hop
        }
        val centroid = if (centroidFrames > 0) (centroidSum / centroidFrames).toFloat() else 0f
        val flatness = if (flatFrames > 0) (flatSum / flatFrames).toFloat() else 0f
        val rolloff = if (rollFrames > 0) (rollSum / rollFrames).toFloat() else 0f

        // ---- 响度与动态范围（短时 400 ms 窗） ----
        val winLen = (SR * 0.4).toInt().coerceAtLeast(1)
        val shortTerm = ArrayList<Float>()
        var wPos = 0
        while (wPos + winLen <= pcm.size) {
            var ss = 0.0
            for (i in 0 until winLen) { val v = pcm[wPos + i]; ss += v.toDouble() * v }
            val ms = ss / winLen
            if (ms > 1e-10) shortTerm.add((10.0 * log10(ms)).toFloat())
            wPos += winLen / 2
        }
        val lufs = if (shortTerm.isEmpty()) -70f else -0.691f + shortTerm.average().toFloat()
        val drange = if (shortTerm.size >= 4) {
            val sorted = shortTerm.sorted()
            val p10 = sorted[(sorted.size * 0.10).toInt()]
            val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
            (p95 - p10).coerceAtLeast(0f)
        } else 0f

        // ---- 调性：chroma 与 24 个大小调模板相关 ----
        var keyIdx = -1
        var keyMinor = false
        var keyConf = 0f
        val chromaSum = chroma.sum()
        if (chromaSum > 1e-6) {
            for (i in 0 until 12) chroma[i] /= chromaSum
            val cn = sqrt(chroma.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-9f)
            for (i in 0 until 12) chroma[i] /= cn
            val major = normalize12(floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f))
            val minor = normalize12(floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f))
            var best = -1e9f
            var second = -1e9f
            for (r in 0 until 12) {
                var cm = 0f
                var cn = 0f
                for (i in 0 until 12) {
                    cm += chroma[(i + r) % 12] * major[i]
                    cn += chroma[(i + r) % 12] * minor[i]
                }
                if (cm > best) { second = best; best = cm; keyIdx = r; keyMinor = false }
                else if (cm > second) second = cm
                if (cn > best) { second = best; best = cn; keyIdx = r; keyMinor = true }
                else if (cn > second) second = cn
            }
            keyConf = if (best > 0) ((best - second) / best).coerceIn(0f, 1f) else 0f
        }

        var fluxSum = 0f
        val flux = FloatArray(energies.size)
        for (i in 1 until energies.size) {
            val d = energies[i] - energies[i - 1]
            flux[i] = if (d > 0) d else 0f
            fluxSum += flux[i]
        }
        val meanFlux = if (flux.size > 0) fluxSum / flux.size else 0f
        val durSec = pcm.size.toFloat() / SR
        var onsets = 0
        for (i in 1 until flux.size) {
            if (flux[i] > meanFlux * 1.4f && flux[i] > flux[i - 1]) onsets++
        }
        val onsetRate = if (durSec > 0) onsets / durSec else 0f

        val fps = SR.toFloat() / hop

        // ---- tempo：跨窗口合并自相关分数 + BPM 先验 + 抛物线细化 ----
        val ends = ArrayList<Int>()
        var acc = 0
        parts.forEach { acc += it.size; ends.add((acc / hop).coerceAtMost(flux.size)) }
        val slices = ArrayList<FloatArray>(3)
        var from = 0
        for (to in ends) {
            if (to - from >= 32) slices.add(flux.copyOfRange(from, to))
            from = to
        }
        val minLag = (fps * 60f / 200f).toInt().coerceAtLeast(1)
        val maxLag = (fps * 60f / 50f).toInt().coerceAtLeast(minLag + 1)
        val score = FloatArray(maxLag + 2)
        val sliceBpm = FloatArray(slices.size)
        var sliceIdx = 0
        for (w in slices) {
            val ac = FloatArray(maxLag + 2)
            var mx = 0f
            for (lag in minLag..maxLag) {
                var sum = 0f
                var n = 0
                for (i in 0 until w.size - lag) { sum += w[i] * w[i + lag]; n++ }
                if (n > 0) { ac[lag] = sum / n; if (ac[lag] > mx) mx = ac[lag] }
            }
            if (mx > 1e-9f) {
                for (lag in minLag..maxLag) score[lag] += ac[lag] / mx
                // 逐窗 BPM：取该片自相关的峰值 lag（未做跨窗合并）
                var best = -1f
                var bestL = 0
                for (lag in minLag..maxLag) {
                    if (ac[lag] > best) { best = ac[lag]; bestL = lag }
                }
                if (bestL > 0) sliceBpm[sliceIdx] = (60f * fps / bestL).coerceIn(40f, 200f)
            }
            sliceIdx++
        }
        // BPM 先验：70–150 满权，其余衰减，避免倍频与次谐波胜出
        for (lag in minLag..maxLag) {
            val bpm = 60f * fps / lag
            val w = when {
                bpm < 50f || bpm > 200f -> 0f
                // 实测：放宽到 60–185 反而让倍频错误从 15% 升到 22%（592 首），
                // 因此保留 70–150 满权、其余 0.6 的原始配置
                bpm in 70f..150f -> 1f
                else -> 0.6f
            }
            score[lag] *= w
        }
        var bestLag = 0
        var bestScore = -1f
        for (lag in minLag..maxLag) {
            if (score[lag] > bestScore) { bestScore = score[lag]; bestLag = lag }
        }
        val tempo = if (bestLag <= 0 || bestScore <= 0f) 0f else {
            // 抛物线插值细化（lag 分辨率在 31fps 下只有 4–7 BPM）
            val y0 = score.getOrElse(bestLag - 1) { 0f }
            val y1 = score[bestLag]
            val y2 = score.getOrElse(bestLag + 1) { 0f }
            val denom = (y0 - 2 * y1 + y2)
            val delta = if (kotlin.math.abs(denom) > 1e-6f) 0.5f * (y0 - y2) / denom else 0f
            val refined = bestLag + delta.coerceIn(-0.5f, 0.5f)
            60f * fps / refined.coerceAtLeast(1f)
        }

        // ---- 拍点：先检测峰值，再用 tempo 网格吸附 ----
        var meanFlux2 = 0f
        for (f in flux) meanFlux2 += f
        meanFlux2 /= flux.size.coerceAtLeast(1)
        var varFlux = 0f
        for (f in flux) varFlux += (f - meanFlux2) * (f - meanFlux2)
        val stdFlux = sqrt(varFlux / flux.size.coerceAtLeast(1))
        val beatThreshold = meanFlux2 + 1.5f * stdFlux
        val rawBeats = ArrayList<Int>(1024)
        var lastBeatMs = -10_000
        val frameMs = hop * 1000f / SR
        for (i in 1 until flux.size - 1) {
            if (flux[i] > beatThreshold && flux[i] >= flux[i - 1] && flux[i] >= flux[i + 1]) {
                val ms = (i * frameMs).toInt()
                if (ms - lastBeatMs >= 200) {
                    rawBeats.add(ms)
                    lastBeatMs = ms
                    if (rawBeats.size >= 4000) break
                }
            }
        }
        val beats = if (tempo > 40f && rawBeats.size >= 4) {
            val period = (60_000f / tempo).toInt().coerceAtLeast(200)
            // 相位 = 各检测点对周期的余数中位数
            val phases = rawBeats.map { it % period }.sorted()
            val phase = phases[phases.size / 2]
            val totalMs = pcm.size * 1000L / SR
            val grid = ArrayList<Int>(2048)
            var t = phase.toLong()
            while (t < totalMs && grid.size < 4000) { grid.add(t.toInt()); t += period }
            grid
        } else rawBeats

        // ---- 能量包络（100ms 一点） ----
        val envStepMs = 100
        val buckets = ((energies.size * frameMs) / envStepMs).toInt() + 1
        val envRaw = FloatArray(buckets)
        val envCnt = IntArray(buckets)
        for (i in energies.indices) {
            val b = (i * frameMs / envStepMs).toInt().coerceIn(0, buckets - 1)
            envRaw[b] += energies[i]
            envCnt[b]++
        }
        for (b in 0 until buckets) if (envCnt[b] > 0) envRaw[b] /= envCnt[b]
        val sortedEnv = envRaw.copyOf().also { it.sort() }
        val p95 = sortedEnv[(0.95f * (sortedEnv.size - 1)).toInt().coerceIn(0, sortedEnv.size - 1)]
            .coerceAtLeast(1e-6f)
        val env = FloatArray(buckets) { (envRaw[it] / p95).coerceIn(0f, 1f) }

        return MoodMath.Dsp(
            rms = rms,
            centroidHz = centroid,
            onsetRate = onsetRate,
            tempoBpm = tempo.coerceIn(40f, 200f),
            beatsMs = beats.toIntArray(),
            env = env,
            envStepMs = envStepMs,
            lufs = lufs,
            dynamicRange = drange,
            flatness = flatness,
            rolloffHz = rolloff,
            keyIndex = keyIdx,
            keyMinor = keyMinor,
            keyConfidence = keyConf,
            windowTempos = sliceBpm,
        )
    }

    /** 单窗自相关 tempo 估计；返回 null 表示置信不足。 */
    private fun estimateTempo(flux: FloatArray, fps: Float): Float? {
        if (flux.size < 32) return null
        var best = -1f
        var bestLag = 0
        val minLag = (fps * 60f / 200f).toInt().coerceAtLeast(1)
        val maxLag = (fps * 60f / 50f).toInt().coerceAtMost(flux.size / 2)
        if (maxLag <= minLag) return null
        for (lag in minLag..maxLag) {
            var s = 0f
            var n = 0
            for (i in 0 until flux.size - lag) { s += flux[i] * flux[i + lag]; n++ }
            if (n > 0) {
                val v = s / n
                if (v > best) { best = v; bestLag = lag }
            }
        }
        if (bestLag <= 0 || best <= 0f) return null
        return 60f * fps / bestLag
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    /**
     * 倍频校正（保守版）：只处理明显越界的倍频。
     * 不再把 <70 BPM 一律翻倍——慢歌本来就该是 60–70，强行翻倍会系统性破坏抒情曲。
     */
    private fun octaveCorrect(tempo: Float): Float {
        if (tempo <= 0f) return 0f
        var t = tempo
        while (t > 160f) t /= 2f
        while (t < 50f && t > 0f) t *= 2f
        return t.coerceIn(40f, 200f)
    }

    private inline fun Float.ifNaN(fallback: Float): Float = if (isNaN()) fallback else this

    /** 12 维向量 L2 归一化（用于调性模板余弦相似度）。 */
    private fun normalize12(v: FloatArray): FloatArray {
        var ss = 0f
        for (x in v) ss += x * x
        val n = sqrt(ss).coerceAtLeast(1e-9f)
        return FloatArray(12) { v[it] / n }
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    override fun close() {
        // 共享单例不在这里关闭：session 由进程持有，避免重复 createSession 触发原生 abort
        clap = null
        runCatching { fetchPool.shutdownNow() }
    }
}