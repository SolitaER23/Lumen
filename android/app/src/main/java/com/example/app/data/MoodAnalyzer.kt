package com.example.app.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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
 * 拉流 → MediaCodec 解码 → 16kHz 单声道 → YAMNet(tflite) + CNN14(int8 QDQ onnx) + DSP
 * → 逐类重标定 → Russell 坐标 → 标签。
 */
class MoodAnalyzer(private val ctx: Context) : java.io.Closeable {

    companion object {
        private const val SR = 16000
        private const val YAMNET_WINDOW = 15600 // 0.975s
        private const val CNN14_WINDOW = 160000 // 10s @16k
    }

    private val http = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // 模型较重：一次分析多首时复用同一份解释器/Session
    private var yamnetInterpreter: Interpreter? = null
    private var ortEnv: OrtEnvironment? = null
    private var cnnSession: OrtSession? = null
    private var cnnScoresName: String? = null

    fun analyze(trackId: String, url: String, onPhase: (String, Float) -> Unit = { _, _ -> }): MoodResult {
        com.example.app.util.Logx.i("LumenMood", "start analysis $trackId")
        val temp = File(ctx.cacheDir, "mood_$trackId.mp3")
        try {
            onPhase("下载", 0f)
            download(url, temp) { onPhase("下载", it * 0.35f) }
            onPhase("解码", 0.36f)
            val pcm = decodeAndResample(temp) ?: error("解码失败")
            onPhase("DSP", 0.45f)
            val dsp = dspFeatures(pcm)
            onPhase("YAMNet", 0.50f)
            val yamnet = runYamnet(pcm) { onPhase("YAMNet", 0.50f + it * 0.15f) }
            onPhase("CNN14", 0.66f)
            val cnn14 = runCnn14(pcm) { onPhase("CNN14", 0.66f + it * 0.33f) }
            onPhase("融合", 0.99f)
            val result = MoodMath.fuse(yamnet, cnn14, dsp, trackId)
            com.example.app.util.Logx.i(
                "LumenMood",
                "mood $trackId label=${result.label} beats=${result.beatsMs.size} env=${result.env.size} tempo=${"%.0f".format(result.tempo)}",
            )
            return result
        } finally {
            runCatching { temp.delete() }
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

    private fun decodeAndResample(file: File): FloatArray? {
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
        val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        var samples = FloatArray(1 shl 20)
        var count = 0
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
                        if (count >= samples.size) samples = samples.copyOf(samples.size * 2)
                        samples[count++] = sum / ch
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        if (count == 0) return null

        val ratio = SR.toDouble() / srcRate.toDouble()
        val dstLen = (count * ratio).toInt().coerceAtLeast(1)
        val dst = FloatArray(dstLen)
        for (i in 0 until dstLen) {
            val srcPos = i / ratio
            val i0 = srcPos.toInt().coerceIn(0, count - 1)
            val i1 = (i0 + 1).coerceAtMost(count - 1)
            val frac = (srcPos - i0).toFloat()
            dst[i] = samples[i0] * (1f - frac) + samples[i1] * frac
        }
        return dst
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
        com.example.app.util.Logx.i(
            "LumenMood",
            "YAMNet loaded: in=" + interp.getInputTensor(0).shape().toList() +
                " outs=" + (0 until interp.outputTensorCount).map { interp.getOutputTensor(it).shape().toList() },
        )
        return interp
    }

    private fun runYamnet(pcm: FloatArray, onProgress: (Float) -> Unit): FloatArray? {
        return runCatching {
            val interp = yamnetInterpreter ?: loadYamnet()
            val inputShape = interp.getInputTensor(0).shape()
            // YAMNet 输入是 1-D [15600]（不是 [1,15600]）
            val win = inputShape.firstOrNull { it > 0 } ?: YAMNET_WINDOW
            val input = FloatArray(win)
            val classes = interp.getOutputTensor(0).shape().lastOrNull() ?: 521
            val output = Array(1) { FloatArray(classes) }
            val sums = FloatArray(classes)
            var frames = 0
            var pos = 0
            while (pos < pcm.size && frames < 600) {
                java.util.Arrays.fill(input, 0f)
                val n = minOf(win, pcm.size - pos)
                System.arraycopy(pcm, pos, input, 0, n)
                interp.run(input, output)
                val frame = output[0]
                for (c in 0 until classes) sums[c] += frame[c]
                frames++
                pos += win
                onProgress((pos.toFloat() / pcm.size).coerceIn(0f, 1f))
            }
            com.example.app.util.Logx.i("LumenMood", "yamnet frames=$frames win=$win classes=$classes")
            if (frames == 0) null else FloatArray(classes) { sums[it] / frames }
        }.onFailure { android.util.Log.e("LumenMood", "yamnet exception", it) }.getOrNull()
    }

    private fun zerosFor(shape: IntArray, from: Int = 0): Any {
        val dim = shape.getOrElse(from) { 0 }.coerceAtLeast(0)
        return if (from >= shape.size - 1) FloatArray(dim) else Array(dim) { zerosFor(shape, from + 1) }
    }

    // ---------------- CNN14 (ONNX QDQ) ----------------

    private fun ensureCnn14File(): File {
        val dst = File(ctx.filesDir, "cnn14_int8.onnx")
        val afd = ctx.assets.openFd("mood/cnn14_int8.onnx")
        if (dst.exists() && dst.length() == afd.declaredLength) return dst
        ctx.assets.open("mood/cnn14_int8.onnx").use { ins ->
            dst.outputStream().use { os -> ins.copyTo(os, 256 * 1024) }
        }
        return dst
    }

    private fun runCnn14(pcm: FloatArray, onProgress: (Float) -> Unit): FloatArray? {
        return runCatching {
            val model = ensureCnn14File()
            val env = ortEnv ?: OrtEnvironment.getEnvironment().also { ortEnv = it }
            val session = cnnSession ?: run {
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                env.createSession(model.absolutePath, opts).also { cnnSession = it }
            }
            val inputName = session.inputNames.first()
            val scoresName = cnnScoresName ?: session.outputNames.toList()
                .first { it.contains("score", ignoreCase = true) }
                .also { cnnScoresName = it }
            val win = CNN14_WINDOW
            val input = FloatArray(win)
            val scores = FloatArray(527)
            var frames = 0
            var pos = 0
            while (pos < pcm.size && frames < 60) {
                java.util.Arrays.fill(input, 0f)
                val n = minOf(win, pcm.size - pos)
                System.arraycopy(pcm, pos, input, 0, n)
                OnnxTensor.createTensor(
                    env,
                    java.nio.FloatBuffer.wrap(input),
                    longArrayOf(1, win.toLong()),
                ).use { t ->
                    session.run(mapOf(inputName to t)).use { res ->
                        @Suppress("UNCHECKED_CAST")
                        val arr = (res.get(scoresName).get() as OnnxTensor).floatBuffer
                        for (c in 0 until 527) scores[c] += arr.get(c)
                    }
                }
                frames++
                pos += win
                onProgress((pos.toFloat() / pcm.size).coerceIn(0f, 1f))
            }
            if (frames == 0) null else FloatArray(527) { scores[it] / frames }
        }.onFailure { android.util.Log.e("LumenMood", "cnn14 exception", it) }.getOrNull()
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
        runCatching { cnnSession?.close() }
        cnnSession = null
    }
}
