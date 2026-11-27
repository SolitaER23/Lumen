package app.lumen.player

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lumen.player.data.Accounts
import app.lumen.player.data.Appearance
import app.lumen.player.data.ServerConfig
import app.lumen.player.data.SettingsStore
import app.lumen.player.local.LocalData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 「清除本机数据」完整性回归。
 *
 * 每个落盘 store 在 [LocalData.wipe] 里都必须被清掉；新增 store 时在这里补一条断言，
 * 否则很容易出现「设置页承诺删除、实际残留曲库/画像」的问题。
 */
@RunWith(AndroidJUnit4::class)
class LocalDataWipeTest {

    private val ctx: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun prefs(name: String) = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun seed() {
        // 曲库元数据
        File(ctx.filesDir, "albums.json").writeText("[]")
        // 专辑曲目缓存
        val albumTracks = File(ctx.cacheDir, "album_tracks")
        albumTracks.mkdirs()
        File(albumTracks, "abc.json").writeText("[]")
        // 歌词缓存
        val lyrics = File(ctx.cacheDir, "lyrics")
        lyrics.mkdirs()
        File(lyrics, "t1.json").writeText("[]")

        // 各类偏好/凭据
        prefs("lumen_history").edit().putString("tracks", "[]").commit()
        prefs("lumen_ratings").edit().putFloat("t1", 5f).commit()
        prefs("lumen_stats").edit().putString("base", "http://192.0.2.1").commit()
        prefs("lumen_playback").edit().putString("state", "{}").commit()
        prefs("lumen_downloads").edit().putString("name_album:1", "Album").commit()
        prefs("lumen_palette").edit().putString("s_1", "[1,2]").commit()
        // 个人行为数据（A5：原先漏清）
        prefs("lumen_search_hist").edit().putString("hist", "[\"lumen\"]").commit()
        prefs("lumen_network").edit().putBoolean("allow_public_cleartext", true).commit()
        prefs("lumen_recent").edit().putString("recent", "[]").commit()
        prefs("lumen_home_scroll").edit().putInt("pos", 120).commit()
        prefs("lumen_home_sort").edit().putInt("album", 2).putInt("artist", 1).commit()

        // 已下线功能的历史残留（A1：升级用户可能还有）
        File(ctx.filesDir, "clap_music_fp16.onnx").writeBytes(ByteArray(4096))
        File(ctx.filesDir, "radio_tracks.json").writeText("[]")
        File(ctx.cacheDir, "mood_tracklist.json").writeText("{}")
        prefs("lumen_mood").edit().putBoolean("hint_shown", true).commit()
        prefs("lumen_radio").edit().putFloat("strength", 0.5f).commit()

        // 账号 + 凭据
        SettingsStore.save(ctx, ServerConfig("http://192.0.2.10:4533", "demo", "pw"))
        Accounts.add(ctx, "http://192.0.2.10:4533", "demo", "pw")

        // 外观偏好（wipe 后应当**保留**）
        Appearance.setContrast(ctx, 0.7f)
    }

    @Test
    fun wipeRemovesCredentialLibraryAndImageData() = runBlocking {
        seed()
        LocalData.wipe(ctx)

        // 曲库 / 缓存
        assertFalse("albums.json 未清", File(ctx.filesDir, "albums.json").exists())
        assertFalse("album_tracks 未清", File(ctx.cacheDir, "album_tracks").exists())
        assertFalse("lyrics 未清", File(ctx.cacheDir, "lyrics").exists())

        // 偏好与行为数据
        assertTrue("播放历史未清", prefs("lumen_history").all.isEmpty())
        assertTrue("评分未清", prefs("lumen_ratings").all.isEmpty())
        assertTrue("统计配置未清", prefs("lumen_stats").all.isEmpty())
        assertTrue("播放状态未清", prefs("lumen_playback").all.isEmpty())
        assertTrue("下载元数据未清", prefs("lumen_downloads").all.isEmpty())
        assertTrue("主题种子未清", prefs("lumen_palette").all.isEmpty())

        // 个人行为数据（A5）
        assertTrue("搜索历史未清", prefs("lumen_search_hist").all.isEmpty())
        assertTrue("网络偏好未清", prefs("lumen_network").all.isEmpty())
        assertTrue("最近浏览未清", prefs("lumen_recent").all.isEmpty())
        assertTrue("首页滚动位置未清", prefs("lumen_home_scroll").all.isEmpty())
        assertTrue("首页排序方式未清", prefs("lumen_home_sort").all.isEmpty())

        // 已下线功能的残留（A1）
        assertFalse("旧模型未清", File(ctx.filesDir, "clap_music_fp16.onnx").exists())
        assertFalse("电台曲库缓存未清", File(ctx.filesDir, "radio_tracks.json").exists())
        assertFalse("曲目清单缓存未清", File(ctx.cacheDir, "mood_tracklist.json").exists())
        assertTrue("旧分析提示未清", prefs("lumen_mood").all.isEmpty())
        assertTrue("旧电台偏好未清", prefs("lumen_radio").all.isEmpty())

        // 账号与凭据
        assertTrue("当前凭据未清", prefs("lumen_settings").all.isEmpty())
        assertTrue("账号索引未清", prefs("lumen_accounts").all.isEmpty())
        assertTrue("账号数量应为 0", Accounts.list(ctx).isEmpty())
    }

    @Test
    fun wipeKeepsAppearancePreferences() = runBlocking {
        seed()
        LocalData.wipe(ctx)
        assertEquals(
            "外观偏好属于用户设置，不应被「清除本机数据」重置",
            0.7f,
            Appearance.contrast(ctx),
            0.001f,
        )
    }
}
