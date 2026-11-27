package app.lumen.player.local

import android.content.Context
import app.lumen.player.data.Accounts
import app.lumen.player.data.AlbumCache
import app.lumen.player.data.AlbumTracksCache
import app.lumen.player.data.LyricsCache
import app.lumen.player.data.Ratings
import app.lumen.player.data.SettingsStore
import app.lumen.player.data.StatsStore
import app.lumen.player.playback.MediaCache
import app.lumen.player.playback.PlaybackEngine
import app.lumen.player.playback.PlaybackStateStore
import app.lumen.player.ui.PaletteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「清除本机数据」的唯一入口。
 *
 * 为什么集中在一处：历史上各 store 各自 clear()，新增缓存时很容易漏掉，
 * 导致设置页承诺「删除账号、评分、统计与缓存」而实际仍残留
 * 完整曲库目录（albums.json / album_tracks）与派生缓存。
 *
 * 约束：**新增任何落盘 store 时必须在此登记**，并在
 * `androidTest/.../LocalDataWipeTest.kt` 里加一条断言。
 *
 * 调用要求：在**主线程**发起（内部会把重活切到 IO）。ExoPlayer 只能在创建它的
 * 线程访问，所以停播必须在主线程完成，不能塞进 IO 里。
 */
object LocalData {

    /** 清空全部本机个人数据与缓存（外观偏好与已同意条款保留）。 */
    suspend fun wipe(ctx: Context) {
        val app = ctx.applicationContext

        // 1) 先停播并清队列：避免播放中的文件被删后报错，也避免旧账号队列串到下一次登录
        runCatching { PlaybackEngine.peek()?.stopAndClear() }

        withContext(Dispatchers.IO) {
            // 2) 媒体缓存（在线播放的流缓存）+ 封面缓存
            runCatching { MediaCache.clearAllCache(app) }
            runCatching { MediaCache.clearImageCache(app) }

            // 3) 曲库元数据：专辑列表、专辑曲目
            AlbumCache.clear(app)
            AlbumTracksCache.clear(app)

            // 4) 行为数据：评分、统计配置、上次播放队列
            Ratings.clear(app)
            StatsStore.clear(app)
            PlaybackStateStore.clear(app)

            // 5) 其他派生缓存：封面主题种子、歌词
            PaletteStore.clear(app)
            LyricsCache.clear(app)

            // 6) 已下线功能留下的数据（模型 / moods.db / 电台缓存 / 相关 prefs）
            runCatching { LegacyCleanup.run(app, force = true) }

            // 7) 其余个人数据：搜索历史、首页滚动位置与排序方式、网络偏好里的风险开关，
            //    以及旧版本「最近 / 收藏 / 下载」功能遗留的本地记录
            clearPrefs(
                app,
                "lumen_search_hist",
                "lumen_network",
                "lumen_recent",
                "lumen_home_scroll",
                "lumen_home_sort",
                "lumen_history",
                "lumen_downloads",
            )

            // 8) 最后清账号与凭据（含 salt）
            Accounts.clearAll(app)
            SettingsStore.clear(app)
        }
    }

    /**
     * 保留白名单（不清）：外观 `lumen_appearance`、语言 `lumen_lang`、已同意条款 `lumen_legal`、
     * 引导完成标记 `lumen_onboarding`。它们属于"本机设置"而非个人数据，清掉只会让用户重新配置。
     * 其余任何新增落盘 store 都必须在上面的步骤里登记。
     */
    private fun clearPrefs(ctx: Context, vararg names: String) {
        names.forEach { name ->
            runCatching { ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
        }
    }
}
