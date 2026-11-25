package app.lumen.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 数值编码必须与系统语言无关。
 *
 * 回归背景：`"%.3f".format(v)` 用默认 Locale，在许多欧洲语言下小数点变成逗号，
 * 而特征本身就是逗号分隔 —— 解码时长度对不上，整条分析结果被静默丢弃。
 */
class FeatureEncodingTest {

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        return try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun featureEncodingUsesDotDecimalInGermanLocale() {
        val feature = floatArrayOf(0.5f, -1.25f, 0.125f)
        val encoded = withLocale(Locale.GERMANY) { MoodStore.encodeFeature(feature) }

        assertFalse("不应出现逗号小数点: " + encoded, encoded.contains("0,5") || encoded.contains("-1,25"))
        assertTrue("应为点小数点: " + encoded, encoded.contains("0.500"))
        assertEquals(3, encoded.split(',').size)
    }

    @Test
    fun featureEncodingIsStableAcrossLocales() {
        val feature = floatArrayOf(0.1f, 0.2f, 0.3f)
        val de = withLocale(Locale.GERMANY) { MoodStore.encodeFeature(feature) }
        val en = withLocale(Locale.US) { MoodStore.encodeFeature(feature) }
        assertEquals(de, en)
    }

    @Test
    fun floatEncodingRoundTripsUnderGermanLocale() {
        val values = floatArrayOf(1.5f, -2.25f, 0.125f)
        val encoded = withLocale(Locale.GERMANY) { MoodStore.encodeFloats(values) }
        val decoded = encoded.split(',').map { it.toFloat() }
        assertEquals(values.size, decoded.size)
        assertEquals(1.5f, decoded[0], 0.001f)
    }
}
