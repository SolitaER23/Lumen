package app.lumen.player.local

import android.content.Context
import app.lumen.player.data.Accounts
import app.lumen.player.data.AlbumCache
import app.lumen.player.data.AlbumTracksCache
import app.lumen.player.data.LyricsCache
import app.lumen.player.data.PlayHistory
import app.lumen.player.data.Ratings
import app.lumen.player.data.SettingsStore
import app.lumen.player.data.StatsStore
import app.lumen.player.data.StreamCapabilityStore
import app.lumen.player.playback.Downloads
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
 * 导致设置页承诺「删除账号、播放历史、评分、下载与缓存」而实际仍残留
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
            // 2) 离线下载：先按组删除（异步投递给 DownloadService），再清索引与元数据
            runCatching { Downloads.summaries(app).forEach { Downloads.delete(app, it.key) } }
            Downloads.clearMeta(app)

            // 3) 媒体流缓存 + 封面缓存
            runCatching { MediaCache.clearStreamingCache(app, emptySet()) }

            // 4) 曲库元数据：专辑列表、专辑曲目
            AlbumCache.clear(app)
            AlbumTracksCache.clear(app)

            // 5) 行为数据：播放历史、评分、统计配置、上次播放队列
            PlayHistory.clear(app)
            Ratings.clear(app)
            StatsStore.clear(app)
            PlaybackStateStore.clear(app)

            // 6) 其他派生缓存：封面主题种子、服务器取流能力、歌词
            PaletteStore.clear(app)
            StreamCapabilityStore.clear(app)
            LyricsCache.clear(app)

            // 7) 最后清账号与凭据（含 salt）
            Accounts.clearAll(app)
            SettingsStore.clear(app)
        }
    }
}
