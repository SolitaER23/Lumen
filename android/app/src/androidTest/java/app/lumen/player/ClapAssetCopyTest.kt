package app.lumen.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lumen.player.data.ClapEmbedder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 模型资产落盘回归（对应审查 P0-2）。
 *
 * 背景：APK 里的 .onnx 是压缩存储的，`assets.openFd()` 必然抛异常，
 * 旧实现用 declaredLength 判断"是否已落盘"→ 恒为 0 → **每次进程启动重拷 294 MB**。
 * 现在改为「标记文件记录 版本戳:字节数」，本测试锁定两个行为：
 *   1) 第二次调用不再重写文件（mtime 不变）；
 *   2) 标记损坏或文件被删时能自愈。
 */
@RunWith(AndroidJUnit4::class)
class ClapAssetCopyTest {

    /** 落盘目标：被测应用自己的 filesDir。 */
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    /** 测试资产打在测试 APK 里（src/androidTest/assets），需要另一个 Context 读取。 */
    private val assetCtx get() = InstrumentationRegistry.getInstrumentation().context
    private val embedder by lazy { ClapEmbedder(ctx) }

    private val asset = "fixture_model.bin"
    private val destName = "fixture_model_test.out"
    private val tag = "test-tag"

    private fun dest() = File(ctx.filesDir, destName)
    private fun marker() = File(ctx.filesDir, destName + ".ok")
    private fun tmp() = File(ctx.filesDir, destName + ".tmp")

    @After
    fun cleanup() {
        dest().delete()
        marker().delete()
        tmp().delete()
    }

    @Test
    fun copiesOnceThenSkipsOnSecondCall() {
        dest().delete(); marker().delete()

        val first = embedder.copyAssetOnce(asset, destName, tag, assetCtx)
        assertTrue("首次应落盘", first.isFile)
        assertEquals(256 * 1024L, first.length())
        assertEquals(tag + ":" + first.length(), marker().readText().trim())
        assertFalse("不应残留临时文件", tmp().exists())

        val firstModified = first.lastModified()
        // 放大时间精度：确保「被重写」可被 lastModified 反映
        Thread.sleep(1_100)

        val second = embedder.copyAssetOnce(asset, destName, tag, assetCtx)
        assertEquals("第二次不应重写文件", firstModified, second.lastModified())
    }

    @Test
    fun recoversWhenMarkerIsCorrupted() {
        embedder.copyAssetOnce(asset, destName, tag, assetCtx)
        marker().writeText("corrupted")
        val before = dest().lastModified()
        Thread.sleep(1_100)

        val again = embedder.copyAssetOnce(asset, destName, tag, assetCtx)
        assertTrue("标记损坏应重新落盘", again.lastModified() > before)
        assertEquals(tag + ":" + again.length(), marker().readText().trim())
    }

    @Test
    fun recoversWhenDestinationIsDeleted() {
        embedder.copyAssetOnce(asset, destName, tag, assetCtx)
        dest().delete()

        val again = embedder.copyAssetOnce(asset, destName, tag, assetCtx)
        assertTrue("文件被删应自愈", again.isFile)
        assertEquals(256 * 1024L, again.length())
    }
}
