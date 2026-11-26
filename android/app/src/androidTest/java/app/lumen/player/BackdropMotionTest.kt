package app.lumen.player

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import app.lumen.player.ui.rememberBackdropMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 背景相位时钟（0.91.2）：暂停 / 省电 / 关闭氛围动效时必须真的停下来，
 * 恢复时从停下的相位接着走，而不是跳回首帧。
 *
 * 用 Compose 测试时钟手动推进，不依赖真机帧率，因此可稳定跑在 connectedAndroidTest 里。
 */
class BackdropMotionTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun clockStopsWhenInactiveAndResumesFromSamePhase() {
        rule.mainClock.autoAdvance = false
        val active = mutableStateOf(true)
        val samples = ArrayList<Float>()

        rule.setContent {
            val motion = rememberBackdropMotion(active = active.value)
            val drift = motion.drift.value
            SideEffect { samples += drift }
        }

        rule.mainClock.advanceTimeBy(2_000)
        val running = samples.last()
        assertTrue("动效开启时相位应推进，实际 $running", running > 0f)

        active.value = false
        rule.mainClock.advanceTimeBy(500)
        val frozen = samples.last()
        rule.mainClock.advanceTimeBy(5_000)
        assertEquals("停用后不应再推进", frozen, samples.last(), 0.0001f)

        active.value = true
        rule.mainClock.advanceTimeBy(1_000)
        val resumed = samples.last()
        // 1 秒漂移步长 ≈ 2π/36 ≈ 0.175；跳回 0 或跳很远都算失败
        assertTrue("恢复后应从停下处继续，实际 $frozen → $resumed", resumed > frozen)
        assertTrue("恢复不应跳相位，实际 $frozen → $resumed", resumed - frozen < 0.5f)
    }
}
