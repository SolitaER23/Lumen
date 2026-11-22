package app.lumen.player.playback

import app.lumen.player.util.L10n
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService as Media3DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler

/**
 * 离线下载服务：前台服务 + 系统下载通知，由 [MediaCache.downloadManager] 驱动。
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class DownloadService : Media3DownloadService(FOREGROUND_ID) {

    private val helper: DownloadNotificationHelper by lazy {
        DownloadNotificationHelper(this, CHANNEL)
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, L10n.t("离线下载"), NotificationManager.IMPORTANCE_LOW),
            )
        }
        // 立即进入前台，避免还没开始下载就被系统以 "FGS timeout" 停掉
        runCatching {
            val placeholder = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(L10n.t("Lumen 下载"))
                .setOngoing(true)
                .build()
            startForeground(FOREGROUND_ID, placeholder)
        }
    }

    override fun getDownloadManager(): DownloadManager = MediaCache.downloadManager(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification = helper.buildProgressNotification(
        this,
        android.R.drawable.stat_sys_download,
        null,
        null,
        downloads,
        notMetRequirements,
    )

    companion object {
        const val CHANNEL = "lumen_downloads"
        private const val FOREGROUND_ID = 1002
    }
}
