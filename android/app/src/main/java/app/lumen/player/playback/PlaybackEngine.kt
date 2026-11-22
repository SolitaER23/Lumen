package app.lumen.player.playback

import android.content.Context

/** 全进程唯一的播放器：Activity 与 MediaSessionService 共用同一份。 */
object PlaybackEngine {
    @Volatile
    private var instance: Playback? = null

    fun get(ctx: Context): Playback =
        instance ?: synchronized(this) {
            instance ?: Playback(ctx.applicationContext).also { instance = it }
        }

    fun peek(): Playback? = instance
}