package app.lumen.player

import android.app.Application
import app.lumen.player.data.Appearance
import app.lumen.player.data.NetworkGate
import app.lumen.player.local.LegacyCleanup
import app.lumen.player.playback.Downloads
import app.lumen.player.util.L10n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okio.Path.Companion.toOkioPath

/**
 * Coil 3 起核心库不再包含网络能力：必须显式注册网络 fetcher，
 * 否则所有在线封面会静默失败（不抛异常，只是空白）。
 *
 * 磁盘缓存目录显式固定为 cacheDir/image_cache：Coil 3 默认改到了
 * cacheDir/coil3_disk_cache，若沿用默认，「设置 → 清理缓存」里删除
 * image_cache 的逻辑就会失效（封面缓存清不掉，旧目录也会残留）。
 */
class LumenApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // 进程可能被 PlaybackService / DownloadService 单独拉起（蓝牙按键、断点续传），
        // 此时 MainActivity 还没跑过：语言与外观迁移必须在 Application 就绪。
        L10n.init(this)
        Appearance.migrate(this)
        // 数据层的明文策略闸门：必须早于任何 SubsonicClient 构造
        NetworkGate.refresh(this)
        // 下线功能的一次性清理（释放 ~148 MB + 旧的听歌画像），幂等
        appScope.launch { runCatching { LegacyCleanup.run(this@LumenApp) } }
        // 下载索引预热：快照与「已完成下载」判定必须早于任何播放/管理页，
        // 否则进程被服务单独拉起时离线播放会回落网络（审计 A2/A4）
        runCatching { Downloads.warmUp(this) }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .build()
            }
            .build()
}
