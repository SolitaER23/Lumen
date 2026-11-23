package app.lumen.player.data

import app.lumen.player.util.L10n
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 端侧心情分析：
 * 拉流 → MediaCodec 解码 → 16kHz 单声道 → YAMNet(tflite) + DSP
 * → 逐类重标定 → Russell 坐标 → 标签。CNN14/ONNX 已移除（体积与耗时都太大）。
 */
/** 一次分析需要的音频来源：转码流地址 + 曲目时长（用于切三段）。 */
data class AudioSource(val transcodeUrl: String, val durationSec: Int)

class MoodAnalyzer(private val ctx: Context) : java.io.Closeable {

    private data class YamnetResult(val average: FloatArray?, val segments: List<AudioSegment>)

    companion object {
        private const val SR = 16000
        private const val YAMNET_WINDOW = 15600 // 0.975s
    }

    private val http = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // 模型较重：一次分析多首时复用同一份解释器/Session
    private var yamnetInterpreter: Interpreter? = null

    /** 本次 analyze 是否因分段不可用而回退到整段（供上层统计冷缓存比例） */
    var lastFellBack: Boolean = false
        private set

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
            val sampled = if (capability.mode == SegmentMode.FULL) {
                fullSampled(trackId, source, temps, onPhase)
            } else {
                runCatching {
                    segmentSampled(trackId, source, capability, temps, onPhase)
                }.getOrElse { e ->
                    // 分段不可用（冷转码 / 代理缓存 / Range 被忽略）→ 本首退回整段，并清掉半成品
                    lastFellBack = true
                    app.lumen.player.util.Logx.w("LumenMood", "segment failed, fallback full: " + e.message)
                    temps.forEach { runCatching { it.delete() } }
                    temps.clear()
                    fullSampled(trackId, source, temps, onPhase)
                }
            }
            onPhase(L10n.t("分析节奏"), 0.58f)
            val dsp = dspFeatures(concat(sampled.pcm))
            onPhase(L10n.t("识别音色"), 0.62f)
            val yamnetResult = runYamnet(sampled) { onPhase(L10n.t("识别音色"), 0.62f + it * 0.36f) }
            onPhase(L10n.t("生成结果"), 0.99f)
            val yamnet = yamnetResult?.average
            val result = MoodMath.fuse(yamnet, dsp, trackId).copy(
                tags = MoodMath.topTags(yamnet),
                feature = yamnet?.copyOf() ?: FloatArray(0),
                segments = yamnetResult?.segments.orEmpty(),
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
        source: AudioSource,
        temps: MutableList<File>,
        onPhase: (String, Float) -> Unit,
    ): SampledAudio {
        onPhase(L10n.t("读取整段"), 0f)
        val f = File(ctx.cacheDir, "mood_" + trackId + ".mp3").also { temps.add(it) }
        download(source.transcodeUrl, f) { onPhase(L10n.t("读取整段"), it * 0.45f) }
        onPhase(L10n.t("解析音频"), 0.46f)
        return decodeSegments(f) ?: error(L10n.t("解码失败"))
    }

    private fun segmentSampled(
        trackId: String,
        source: AudioSource,
        capability: StreamCapability,
        temps: MutableList<File>,
        onPhase: (String, Float) -> Unit,
    ): SampledAudio {
        onPhase(L10n.t("读取片段"), 0f)
        val parts = fetchParts(trackId, source, capability, temps) { p ->
            onPhase(L10n.t("读取片段"), p * 0.45f)
        }
        onPhase(L10n.t("解析音频"), 0.46f)
        return decodeParts(parts) ?: error(L10n.t("解码失败"))
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
        val windows: List<IntRange> = if (durationMs <= 90_000) {
            listOf(0 until durationMs)
        } else {
            listOf(
                0 until 30_000,
                (durationMs / 2 - 15_000) until (durationMs / 2 + 15_000),
                (durationMs - 30_000) until durationMs,
            )
        }
        val out = ArrayList<Part>(windows.size)
        windows.forEachIndexed { i, w ->
            val file = File(ctx.cacheDir, "mood_" + trackId + "_" + i + ".part").also { temps.add(it) }
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
            out.add(Part(file, w.first))
            onProgress((i + 1).toFloat() / windows.size)
        }
        return out
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
        val pcm = ArrayList<FloatArray>(parts.size)
        val starts = ArrayList<Int>(parts.size)
        parts.forEach { p ->
            val data = decodeWhole(p.file) ?: return null
            if (data.isEmpty()) return null
            pcm.add(data)
            starts.add(p.startMs)
        }
        return SampledAudio(pcm, starts)
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

    private fun resample(src: FloatArray, n: Int, srcRate: Int): FloatArray {
        if (n == 0) return FloatArray(0)
        val ratio = SR.toDouble() / srcRate.toDouble()
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

    private fun decodeSegments(file: File): SampledAudio? {
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
        val durationUs = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
        val durationMs = if (durationUs > 0) (durationUs / 1000L).toInt() else 0
        val known = durationMs > 0

        val windows: List<IntRange> = when {
            !known -> emptyList()
            durationMs <= 60_000 -> listOf(0 until durationMs)
            else -> listOf(
                0 until 30_000,
                (durationMs / 2 - 15_000).coerceAtLeast(0) until (durationMs / 2 + 15_000).coerceAtMost(durationMs),
                (durationMs - 30_000).coerceAtLeast(0) until durationMs,
            )
        }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        // 时长已知时只缓存窗口内样本；未知时先整首收集，解码后再切
        val bufs = if (known) windows.map { Buf(30 * srcRate) } else emptyList()
        val all = if (known) null else Buf(1 shl 20)
        var idx = 0L
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val timeoutUs = 10_000L
        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(timeoutUs)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(buf, 0)
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
                val buf = codec.getOutputBuffer(outIdx)!!
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val shorts = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                while (shorts.hasRemaining()) {
                    var sum = 0f
                    var ch = 0
                    while (ch < channels && shorts.hasRemaining()) {
                        sum += shorts.get() / 32768f
                        ch++
                    }
                    if (ch > 0) {
                        val v = sum / ch
                        if (known) {
                            val ms = idx * 1000L / srcRate
                            for (w in windows.indices) {
                                if (ms in windows[w].first.toLong()..windows[w].last.toLong()) bufs[w].add(v)
                            }
                        } else {
                            all!!.add(v)
                        }
                        idx++
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        if (idx == 0L) return null

        val parts = ArrayList<FloatArray>(3)
        val starts = ArrayList<Int>(3)
        if (known) {
            windows.forEachIndexed { i, w ->
                parts.add(resample(bufs[i].array(), bufs[i].n, srcRate))
                starts.add(w.first)
            }
        } else {
            val totalMs = (idx * 1000L / srcRate).toInt()
            val fallback = if (totalMs <= 60_000) listOf(0 until totalMs) else listOf(
                0 until 30_000,
                (totalMs / 2 - 15_000).coerceAtLeast(0) until (totalMs / 2 + 15_000).coerceAtMost(totalMs),
                (totalMs - 30_000).coerceAtLeast(0) until totalMs,
            )
            val src = all!!.array()
            fallback.forEach { w ->
                val s0 = (w.first.toLong() * srcRate / 1000L).toInt().coerceIn(0, all.n)
                val s1 = (w.last.toLong() * srcRate / 1000L).toInt().coerceIn(s0, all.n)
                parts.add(resample(src, s1 - s0, srcRate))
                starts.add(w.first)
            }
        }
        return SampledAudio(parts, starts)
    }

    // ---------------- YAMNet (TFLite, CPU) ----------------

    private fun loadYamnet(): Interpreter {
        yamnetInterpreter?.let { return it }
        val dst = File(ctx.filesDir, "yamnet.tflite")
        val afd = ctx.assets.openFd("mood/yamnet.tflite")
        if (!dst.exists() || dst.length() != afd.declaredLength) {
            ctx.assets.open("mood/yamnet.tflite").use { ins ->
                dst.outputStream().use { os -> ins.copyTo(os, 128 * 1024) }
            }
        }
        val options = Interpreter.Options().apply {
            setNumThreads(2)
            setUseXNNPACK(false)
        }
        val interp = Interpreter(dst, options)
        yamnetInterpreter = interp
        app.lumen.player.util.Logx.i(
            "LumenMood",
            "YAMNet loaded: in=" + interp.getInputTensor(0).shape().toList() +
                " outs=" + (0 until interp.outputTensorCount).map { interp.getOutputTensor(it).shape().toList() },
        )
        return interp
    }

    private fun runYamnet(sampled: SampledAudio, onProgress: (Float) -> Unit): YamnetResult? {
        return runCatching {
            val interp = yamnetInterpreter ?: loadYamnet()
            val inputShape = interp.getInputTensor(0).shape()
            // YAMNet 输入是 1-D [15600]（不是 [1,15600]）
            val win = inputShape.firstOrNull { it > 0 } ?: YAMNET_WINDOW
            val classes = interp.getOutputTensor(0).shape().lastOrNull() ?: 521
            val input = FloatArray(win)
            val output = Array(1) { FloatArray(classes) }
            val sums = FloatArray(classes)
            val segmentMs = 12_000
            val segScores = LinkedHashMap<Int, HashMap<Int, Float>>()
            var frames = 0
            val perSegment = 8
            val budget = (sampled.pcm.size * perSegment).coerceAtLeast(1)
            sampled.pcm.forEachIndexed { si, pcm ->
                if (pcm.size >= win) {
                    val span = (pcm.size - win).coerceAtLeast(0)
                    val stride = if (perSegment <= 1) win else maxOf(win / 2, span / (perSegment - 1)).coerceAtLeast(1)
                    var pos = 0
                    var n = 0
                    while (n < perSegment && pos <= pcm.size - win) {
                        java.util.Arrays.fill(input, 0f)
                        System.arraycopy(pcm, pos, input, 0, win)
                        interp.run(input, output)
                        val frame = output[0]
                        for (c in 0 until classes) sums[c] += frame[c]

                        val startMs = sampled.startsMs[si] + (pos.toLong() * 1000L / SR).toInt()
                        val bucket = startMs / segmentMs
                        val bucketScores = segScores.getOrPut(bucket) { HashMap() }
                        (0 until classes)
                            .sortedByDescending { frame[it] }
                            .take(3)
                            .forEach { c -> bucketScores[c] = (bucketScores[c] ?: 0f) + frame[c] }

                        frames++
                        n++
                        pos += stride
                        onProgress((frames.toFloat() / budget).coerceIn(0f, 1f))
                    }
                }
            }
            app.lumen.player.util.Logx.i("LumenMood", "yamnet segments=${sampled.pcm.size} frames=$frames win=$win classes=$classes")
            val totalMs = sampled.pcm.sumOf { it.size } * 1000L / SR
            val segments = segScores.toSortedMap().map { (bucket, scores) ->
                AudioSegment(
                    startMs = bucket * segmentMs,
                    endMs = minOf((bucket + 1) * segmentMs, totalMs.toInt()),
                    tags = scores.entries
                        .sortedByDescending { it.value }
                        .take(4)
                        .mapNotNull { YAMNET_LABELS.getOrNull(it.key) },
                )
            }
            val average = if (frames == 0) null else FloatArray(classes) { sums[it] / frames }
            YamnetResult(average, segments)
        }.onFailure { android.util.Log.e("LumenMood", "yamnet exception", it) }.getOrNull()
    }

    // ---------------- DSP ----------------

    private fun dspFeatures(pcm: FloatArray): MoodMath.Dsp {
        if (pcm.isEmpty()) return MoodMath.Dsp(0f, 0f, 0f, 0f)
        var sumSq = 0.0
        for (s in pcm) sumSq += (s * s).toDouble()
        val rms = sqrt(sumSq / pcm.size).toFloat()

        val fftSize = 1024
        val hop = 512
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        var centroidSum = 0.0
        var centroidFrames = 0
        val energies = ArrayList<Float>()
        var pos = 0
        while (pos + fftSize <= pcm.size) {
            for (i in 0 until fftSize) {
                val w = 0.5f - 0.5f * cos(2.0 * Math.PI * i / (fftSize - 1)).toFloat()
                re[i] = pcm[pos + i] * w
                im[i] = 0f
            }
            fft(re, im)
            var magSum = 0.0
            var weighted = 0.0
            var energy = 0.0
            for (b in 1 until fftSize / 2) {
                val mag = hypot(re[b].toDouble(), im[b].toDouble())
                val freq = b.toDouble() * SR / fftSize
                magSum += mag
                weighted += mag * freq
                energy += mag * mag
            }
            if (magSum > 1e-6) {
                centroidSum += weighted / magSum
                centroidFrames++
            }
            energies.add(energy.toFloat())
            pos += hop
        }
        val centroid = if (centroidFrames > 0) (centroidSum / centroidFrames).toFloat() else 0f

        var fluxSum = 0f
        var onsets = 0
        val flux = FloatArray(energies.size)
        for (i in 1 until energies.size) {
            val d = energies[i] - energies[i - 1]
            flux[i] = if (d > 0) d else 0f
            fluxSum += flux[i]
        }
        val meanFlux = if (flux.size > 0) fluxSum / flux.size else 0f
        val durSec = pcm.size.toFloat() / SR
        for (i in 1 until flux.size) {
            if (flux[i] > meanFlux * 1.4f && flux[i] > flux[i - 1]) onsets++
        }
        val onsetRate = if (durSec > 0) onsets / durSec else 0f

        val fps = SR.toFloat() / hop
        var bestLag = 0
        var best = -1f
        val minLag = (fps * 60f / 200f).toInt().coerceAtLeast(1)
        val maxLag = (fps * 60f / 50f).toInt().coerceAtMost(flux.size / 2)
        for (lag in minLag..maxLag) {
            var s = 0f
            var n = 0
            for (i in 0 until flux.size - lag) {
                s += flux[i] * flux[i + lag]
                n++
            }
            if (n > 0) {
                val v = s / n
                if (v > best) {
                    best = v
                    bestLag = lag
                }
            }
        }
        val tempo = if (bestLag > 0) 60f * fps / bestLag else 0f

        // 能量包络（100ms 一点）与拍点（onset 通量局部峰），供背景精确同步
        val envStepMs = 100
        val frameMs = hop * 1000f / SR
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

        var meanFlux2 = 0f
        for (f in flux) meanFlux2 += f
        meanFlux2 /= flux.size.coerceAtLeast(1)
        var varFlux = 0f
        for (f in flux) varFlux += (f - meanFlux2) * (f - meanFlux2)
        val stdFlux = sqrt(varFlux / flux.size.coerceAtLeast(1))
        val beatThreshold = meanFlux2 + 1.5f * stdFlux
        val beats = ArrayList<Int>(1024)
        var lastBeatMs = -10_000
        for (i in 1 until flux.size - 1) {
            if (flux[i] > beatThreshold && flux[i] >= flux[i - 1] && flux[i] >= flux[i + 1]) {
                val ms = (i * frameMs).toInt()
                if (ms - lastBeatMs >= 260) {
                    beats.add(ms)
                    lastBeatMs = ms
                    if (beats.size >= 4000) break
                }
            }
        }
        return MoodMath.Dsp(
            rms = rms,
            centroidHz = centroid,
            onsetRate = onsetRate,
            tempoBpm = tempo.coerceIn(40f, 200f),
            beatsMs = beats.toIntArray(),
            env = env,
            envStepMs = envStepMs,
        )
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
        runCatching { yamnetInterpreter?.close() }
        yamnetInterpreter = null
    }
}