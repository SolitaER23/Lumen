package app.lumen.player.data

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.lumen.player.util.Logx
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer

/**
 * 音乐版 CLAP 音频塔（laion_clap music_audioset_epoch_15_esc_90.14，fp32）。
 * 权重以 fp16 随包分发（约 141 MiB），首次分析时落到 filesDir 并打标记，之后不再重复写入。
 *
 * 输入：48 kHz 单声道 PCM，10 秒窗
 * 输出：1024 维 embedding（模型内含 STFT / log-mel 前端）
 *
 * 执行后端按 NNAPI → XNNPACK → CPU 依次尝试，失败自动降级并打日志。
 */
class ClapEmbedder(private val ctx: Context) : Closeable {

    companion object {
        /**
         * 进程级单例，构造时传入的是 applicationContext（见 shared()），不会泄漏 Activity；
         * lint 的 StaticFieldLeak 只看到「static 持有 Context」这一层，这里显式抑制。
         */
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ClapEmbedder? = null

        /** 进程级单例：ORT 的 createSession 不能并发/重复创建，否则原生层 abort。 */
        fun shared(ctx: Context): ClapEmbedder = instance ?: synchronized(this) {
            instance ?: ClapEmbedder(ctx.applicationContext).also { instance = it }
        }

        const val SR = 48000
        const val WINDOW = 10 * SR
        const val DIM = 1024
        // fp16 权重随包分发：294 MB(fp32) → 147.9 MB(fp16)，APK 从 278 MB 降到 ~160 MB。
        // 与 fp32 对比实测：embedding 余弦 0.999997、clipwise 余弦 0.999997、Top-5 标签完全一致。
        private const val ASSET = "mood/clap_music_fp16.onnx"
        private const val MODEL_FILE = "clap_music_fp16.onnx"
        private const val PROJ_ASSET = "mood/audio_projection.onnx"
        private const val PROJ_FILE = "audio_projection.onnx"
        /** 资产内容版本戳：换模型时必须改这里，否则旧副本会被继续使用。 */
        private const val ASSET_TAG = "clap-fp16-v1"
    }

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var cached: OrtSession? = null
    private var projCached: OrtSession? = null

    /** 加载 1024→512 的投影层（与文本锚点同一联合空间）。 */
    private fun projection(): OrtSession? {
        projCached?.let { return it }
        val dst = runCatching { copyAssetOnce(PROJ_ASSET, PROJ_FILE, ASSET_TAG) }.getOrElse {
            Logx.w("LumenClap", "projection 落盘失败: " + it.message)
            return null
        }
        val o = OrtSession.SessionOptions()
        o.setIntraOpNumThreads(2)
        projCached = runCatching { env.createSession(dst.absolutePath, o) }.getOrNull()
        return projCached
    }

    /** 由 1024 维 embedding 计算 8 个情绪锚点的 softmax 概率。 */
    fun anchorProbs(embedding: FloatArray): FloatArray? {
        val sess = projection() ?: return null
        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(embedding),
            longArrayOf(1, embedding.size.toLong()),
        )
        return try {
            sess.run(mapOf(sess.inputNames.first() to tensor)).use { r ->
                @Suppress("UNCHECKED_CAST")
                val joint = (r.get(0).value as Array<FloatArray>)[0]
                var norm = 0f
                for (x in joint) norm += x * x
                norm = kotlin.math.sqrt(norm).coerceAtLeast(1e-9f)
                val sims = FloatArray(MoodAnchors.vectors.size)
                for (i in MoodAnchors.vectors.indices) {
                    var dot = 0f
                    val a = MoodAnchors.vectors[i]
                    for (k in joint.indices) dot += (joint[k] / norm) * a[k]
                    sims[i] = dot * MoodAnchors.TEMPERATURE
                }
                val mx = sims.max()
                var sum = 0f
                val probs = FloatArray(sims.size)
                for (i in sims.indices) { probs[i] = kotlin.math.exp(sims[i] - mx); sum += probs[i] }
                for (i in probs.indices) probs[i] /= sum
                probs
            }
        } finally {
            tensor.close()
        }
    }

    private fun modelFile(): File {
        // 旧版本（fp32）把 294 MB 权重落在 filesDir：换成 fp16 副本后顺手清掉，回收空间
        runCatching {
            File(ctx.filesDir, "clap_music_v4.onnx").takeIf { it.exists() }?.delete()
        }
        return copyAssetOnce(ASSET, MODEL_FILE, ASSET_TAG)
    }

    /**
     * 把压缩资产一次性落到 filesDir，并用 `.ok` 标记避免重复拷贝。
     *
     * 背景：APK 里的 .onnx 是 DEFLATE 存储的，`assets.openFd()` 必然抛
     * "This file can not be opened as a file descriptor"，所以不能用
     * declaredLength 判断"是否已落盘"——旧实现因此每次都把 294 MB 重写一遍。
     * 标记文件记录「版本戳:实际字节数」，与目标文件长度一致即视为完成。
     */
    internal fun copyAssetOnce(
        asset: String,
        name: String,
        tag: String,
        // 仅测试用：测试资产打在测试 APK 里，需要从另一个 Context 读取
        assetContext: Context = ctx,
    ): File {
        val dst = File(ctx.filesDir, name)
        val marker = File(ctx.filesDir, name + ".ok")
        val assetLen = runCatching { assetContext.assets.openFd(asset).declaredLength }.getOrDefault(-1L)

        if (dst.isFile && marker.isFile) {
            val recorded = runCatching { marker.readText().trim() }.getOrDefault("")
            val expected = tag + ":" + dst.length()
            if (recorded == expected && dst.length() > 0) return dst
        }

        if (assetLen > 0) ensureSpace(ctx.filesDir, assetLen)
        val tmp = File(ctx.filesDir, name + ".tmp")
        runCatching {
            assetContext.assets.open(asset).use { ins ->
                tmp.outputStream().use { os -> ins.copyTo(os, 256 * 1024) }
            }
            if (assetLen > 0 && tmp.length() != assetLen) {
                error("资产长度不符: " + tmp.length() + " != " + assetLen)
            }
            if (dst.exists() && !dst.delete()) error("无法删除旧模型副本")
            if (!tmp.renameTo(dst)) error("无法重命名临时文件")
            marker.writeText(tag + ":" + dst.length())
        }.onFailure {
            runCatching { tmp.delete() }
            throw IllegalStateException("模型落盘失败（" + asset + "）：" + it.message, it)
        }
        Logx.i("LumenClap", "asset copied: " + name + " " + dst.length() + " bytes")
        return dst
    }

    /** 复制前做空间检查，避免写到一半 ENOSPC 留下半文件。 */
    private fun ensureSpace(dir: File, need: Long) {
        val required = (need * 1.2).toLong()
        val free = dir.usableSpace
        if (free in 1 until required) {
            error(
                "存储空间不足：需要约 " + (required / (1024 * 1024)) + " MB，可用 " +
                    (free / (1024 * 1024)) + " MB",
            )
        }
    }

    private val buildLock = Any()

    private fun build(): OrtSession = synchronized(buildLock) {
        val dst = modelFile()
        fun tryCreate(label: String, configure: (OrtSession.SessionOptions) -> Unit): OrtSession? =
            runCatching {
                val o = OrtSession.SessionOptions()
                o.setIntraOpNumThreads(4)
                o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                configure(o)
                val s = env.createSession(dst.absolutePath, o)
                app.lumen.player.util.Logx.i("LumenClap", "EP=$label ok")
                s
            }.onFailure {
                app.lumen.player.util.Logx.w("LumenClap", "EP=$label failed: " + it.message)
            }.getOrNull()

        // NNAPI 在 Pixel 上必然失败且失败路径会触发 ORT 原生断言，直接跳过。
        //
        // 顺序是「先 CPU 再 XNNPACK」，这是实测结论：
        //   fp16 权重 + CPU EP（fp32 累加）→ embedding 余弦 0.99998（对比 fp32 参考）
        //   fp16 权重 + XNNPACK（fp16 累加）→ 余弦 0.380，等于结果失真
        // 速度几乎相同（1688 ms vs 1558 ms，约 8%），所以精度优先。
        tryCreate("cpu") { }?.let { return it }

        return tryCreate("xnnpack") { o -> o.addXnnpack(mapOf("intra_op_num_threads" to "4")) }
            ?: error("no ORT session")
    }

    private fun session(): OrtSession = cached ?: build().also { cached = it }

    /** 任意采样率单声道 PCM → 48 kHz。 */
    fun resample48k(src: FloatArray, srcRate: Int): FloatArray {
        if (srcRate == SR || src.isEmpty()) return src
        val ratio = SR.toDouble() / srcRate.toDouble()
        val outLen = (src.size * ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val pos = i / ratio
            val i0 = pos.toInt().coerceIn(0, src.size - 1)
            val i1 = (i0 + 1).coerceAtMost(src.size - 1)
            val f = (pos - i0).toFloat()
            out[i] = src[i0] * (1f - f) + src[i1] * f
        }
        return out
    }

    /**
     * 取样窗（用户指定）：
     * 1) 第 11–21 秒（跳过前奏）
     * 2) 中间 10 秒
     * 3) 倒数第 20–10 秒
     */
    fun windowOffsets(samples: Int): List<Int> {
        val w = WINDOW
        if (samples <= w) return listOf(0)
        val first = (11 * SR).coerceAtMost(samples - w)
        val mid = (samples / 2 - w / 2).coerceIn(0, samples - w)
        val last = (samples - 2 * w).coerceIn(0, samples - w)
        return listOf(first, mid, last).distinct().sorted()
    }

    /** 单窗推理，返回 (embedding 1024, clipwise 527)。 */
    fun embedWindowPair(pcm48k: FloatArray, offset: Int): Pair<FloatArray, FloatArray> {
        val input = FloatArray(WINDOW)
        val n = minOf(WINDOW, pcm48k.size - offset).coerceAtLeast(0)
        if (n > 0) System.arraycopy(pcm48k, offset, input, 0, n)
        val sess = session()
        val shape = longArrayOf(1, WINDOW.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            sess.run(mapOf(sess.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val emb = result.get(0).value as Array<FloatArray>
                @Suppress("UNCHECKED_CAST")
                val clip = result.get(1).value as Array<FloatArray>
                return emb[0] to clip[0]
            }
        }
    }

    /** 兼容旧调用：只取 embedding。 */
    fun embedWindow(pcm48k: FloatArray, offset: Int): FloatArray = embedWindowPair(pcm48k, offset).first

    override fun close() {
        runCatching { cached?.close() }
        runCatching { projCached?.close() }
        projCached = null
        cached = null
    }
}
