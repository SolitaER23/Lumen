package app.lumen.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lumen.player.data.ClapEmbedder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * fp16 模型的真机质量回归（对应 OPTIMIZATION_PLAN 阶段 G）。
 *
 * 背景：fp32 → fp16 让随包权重从 294 MB 降到 147.9 MB（APK 278 MB → 约 160 MB）。
 * 桌面端已验证 embedding 余弦 0.999997，但 Android 的 ONNX Runtime 1.22 用
 * XNNPACK / CPU EP，必须单独确认算子都有 fp16 kernel、数值不塌。
 *
 * 断言方式：用确定性正弦输入（与 models_ref.py 生成参考值时同一公式）跑 fp16 模型，
 * 与桌面 fp32 输出的 1024 维 embedding 比余弦，只要 >= 0.99 即认为精度可用。
 */
@RunWith(AndroidJUnit4::class)
class ClapModelQualityTest {

    private val assetCtx get() = InstrumentationRegistry.getInstrumentation().context
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * 与生成参考值的 Python 脚本**逐样本一致**的 10 秒合成音频。
     *
     * 注意：必须带宽带分量（这里的 LCG 伪噪声）。纯音/扫频输入在频谱上过于稀疏，
     * 会让模型的 LayerNorm 落在极小方差上，输出对任何扰动都高度敏感 —— 用纯音做
     * 精度回归会得出「fp16 不可用」的假结论（实测纯音下 fp16 余弦 0.91，
     * 而宽带输入下是 0.999997）。
     */
    private fun syntheticWindow(size: Int = 480000): FloatArray {
        var state = 12345
        return FloatArray(size) { i ->
            state = state * 1664525 + 1013904223
            val noise = (state ushr 8) / 16777216.0 - 0.5
            val t = i / 48000.0
            (0.30 * sin(2 * PI * 220 * t) + 0.20 * sin(2 * PI * 440 * t) + 0.16 * noise).toFloat()
        }
    }

    private fun reference(): FloatArray {
        val raw = assetCtx.assets.open("ref_embedding_fp32.txt").bufferedReader().use { it.readText() }
        return raw.split(',').map { it.trim().toFloat() }.toFloatArray()
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i].toDouble()
            na += a[i].toDouble() * a[i].toDouble()
            nb += b[i].toDouble() * b[i].toDouble()
        }
        return dot / (sqrt(na) * sqrt(nb) + 1e-12)
    }

    @Test
    fun fp16ModelMatchesFp32ReferenceOnDevice() {
        val embedder = ClapEmbedder(ctx)
        try {
            // 触发资产落盘 + 建立 ORT session（XNNPACK），顺带验证移动端能加载 fp16 图
            val (emb, clip) = embedder.embedWindowPair(syntheticWindow(), 0)

            assertEquals("embedding 维度", 1024, emb.size)
            assertTrue("embedding 含非有限值", emb.all { it.isFinite() })
            assertTrue("clipwise 维度异常", clip.size > 0 && clip.all { it.isFinite() })

            val ref = reference()
            assertEquals("参考向量维度", 1024, ref.size)
            val cos = cosine(emb, ref)
            assertTrue("fp16 与 fp32 参考余弦过低: " + cos, cos >= 0.99)
        } finally {
            embedder.close()
        }
    }
}
