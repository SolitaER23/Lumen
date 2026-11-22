package app.lumen.player.playback

import app.lumen.player.util.L10n
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 媒体会话服务：通知栏 / 锁屏控制 + 播放前台化（退到后台不会被回收）。
 *
 * 播放器是 [PlaybackEngine] 里的同一份实例，所以 UI 上的操作与通知按钮完全同步。
 * 通知用平台 `Notification.MediaStyle` 自己拼，避免依赖 Media3 的默认通知实现。
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var listener: Player.Listener? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val player = PlaybackEngine.get(this).player
        session = MediaSession.Builder(this, player).build()
        listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = post()
            override fun onIsPlayingChanged(isPlaying: Boolean) = post()
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = post()
        }.also { player.addListener(it) }
        post()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val p = PlaybackEngine.peek()
        when (intent?.action) {
            ACTION_PREV -> p?.prev()
            ACTION_NEXT -> p?.next()
            ACTION_TOGGLE -> p?.toggle()
        }
        post()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = PlaybackEngine.peek()?.player
        if (p == null || !p.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        listener?.let { PlaybackEngine.peek()?.player?.removeListener(it) }
        listener = null
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, L10n.t("播放控制"), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
    }

    private fun action(action: String, icon: Int, title: String): Notification.Action {
        val pi = PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()
    }

    private fun post() {
        val p = PlaybackEngine.peek()?.player ?: return
        val meta = p.mediaMetadata
        val title = meta.title?.toString() ?: getString(android.R.string.untitled)
        val artist = meta.artist?.toString().orEmpty()
        val token = session?.platformToken as? android.media.session.MediaSession.Token

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, Class.forName("app.lumen.player.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(open)
            .setOngoing(p.isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(action(ACTION_PREV, android.R.drawable.ic_media_previous, L10n.t("上一首")))
            .addAction(
                action(
                    ACTION_TOGGLE,
                    if (p.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (p.isPlaying) L10n.t("暂停") else L10n.t("播放"),
                ),
            )
            .addAction(action(ACTION_NEXT, android.R.drawable.ic_media_next, L10n.t("下一首")))

        if (token != null) {
            val style: Notification.Style = Notification.MediaStyle()
                .setMediaSession(token)
                .setShowActionsInCompactView(0, 1, 2)
            builder.setStyle(style)
        }
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

        // 后台启动可能被系统拒绝（ForegroundServiceStartNotAllowed），忽略即可，播放本身不受影响\n        runCatching { startForeground(NOTIF_ID, builder.build()) }
    }

    companion object {
        private const val CHANNEL = "lumen_playback"
        private const val NOTIF_ID = 1001
        const val ACTION_PREV = "app.lumen.player.PREV"
        const val ACTION_NEXT = "app.lumen.player.NEXT"
        const val ACTION_TOGGLE = "app.lumen.player.TOGGLE"
    }
}
