package app.lumen.player

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lumen.player.local.LegacyCleanup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 0.90.0 下线模型后的一次性清理（审计 P1-2）。
 *
 * 升级用户设备上会留下 148 MB 权重、moods.db、电台/曲目清单缓存与相关 prefs；
 * 这里锁定三件事：全部清掉、返回释放量、重复执行幂等。
 */
@RunWith(AndroidJUnit4::class)
class LegacyCleanupTest {

    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun prefs(name: String) = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test
    fun removesLegacyArtifactsAndIsIdempotent() = runBlocking {
        // 清掉幂等标记，模拟"升级后第一次启动"
        prefs("lumen_maintenance").edit().clear().commit()

        File(ctx.filesDir, "clap_music_fp16.onnx").writeBytes(ByteArray(8192))
        File(ctx.filesDir, "clap_music_v4.onnx.ok").writeText("clap-fp16-v1:1")
        File(ctx.filesDir, "audio_projection.onnx").writeBytes(ByteArray(1024))
        File(ctx.filesDir, "radio_tracks.json").writeText("[]")
        File(ctx.cacheDir, "mood_tracklist.json").writeText("{}")
        File(ctx.cacheDir, "mood_t1.mp3").writeText("x")
        prefs("lumen_mood").edit().putBoolean("hint_shown", true).commit()
        prefs("lumen_radio").edit().putFloat("strength", 0.5f).commit()
        prefs("lumen_stream_caps").edit().putString("cap_x", "{}").commit()

        val db = ctx.getDatabasePath("moods.db")
        db.parentFile?.mkdirs()
        db.writeText("stub")

        val freed = LegacyCleanup.run(ctx)
        assertTrue("应统计到释放字节，实际 " + freed, freed >= 8192)

        assertFalse(File(ctx.filesDir, "clap_music_fp16.onnx").exists())
        assertFalse(File(ctx.filesDir, "clap_music_v4.onnx.ok").exists())
        assertFalse(File(ctx.filesDir, "audio_projection.onnx").exists())
        assertFalse(File(ctx.filesDir, "radio_tracks.json").exists())
        assertFalse(File(ctx.cacheDir, "mood_tracklist.json").exists())
        assertFalse(File(ctx.cacheDir, "mood_t1.mp3").exists())
        assertFalse("moods.db 未删", ctx.getDatabasePath("moods.db").exists())
        assertTrue(prefs("lumen_mood").all.isEmpty())
        assertTrue(prefs("lumen_radio").all.isEmpty())
        assertTrue(prefs("lumen_stream_caps").all.isEmpty())
        assertTrue("应写入完成标记", LegacyCleanup.alreadyDone(ctx))

        // 幂等：第二次直接返回 0，且不抛异常
        assertEquals(0L, LegacyCleanup.run(ctx))

        // force 模式（清除数据）仍然会执行
        File(ctx.filesDir, "radio_tracks.json").writeText("[]")
        LegacyCleanup.run(ctx, force = true)
        assertFalse(File(ctx.filesDir, "radio_tracks.json").exists())
    }
}
