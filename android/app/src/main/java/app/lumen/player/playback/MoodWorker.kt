package app.lumen.player.playback

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.lumen.player.data.MoodAnalyzer
import app.lumen.player.data.MoodStore
import app.lumen.player.data.SettingsStore
import app.lumen.player.data.SubsonicClient
import app.lumen.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 整库心情分析：手动启动后立即在后台运行，可随时暂停/继续。 */
class MoodWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val PREFS = "lumen_mood"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_RUNNING = "running"
        const val KEY_PENDING = "pending"
        const val WORK_NAME = "mood_analysis"

        fun start(ctx: Context) {
            // 手动启动不再强制充电/仅 Wi-Fi，否则未插电时点击看起来像没反应
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_PENDING, true).putBoolean(KEY_RUNNING, false).apply()
            val req = OneTimeWorkRequestBuilder<MoodWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            // REPLACE：暂停后再次启动时不会被旧的挂起任务挡住
            WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_RUNNING, false)
                .putBoolean(KEY_PENDING, false)
                .apply()
        }

        fun progress(ctx: Context): Triple<Int, Int, Boolean> {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val active = p.getBoolean(KEY_RUNNING, false) || p.getBoolean(KEY_PENDING, false)
            return Triple(p.getInt(KEY_DONE, 0), p.getInt(KEY_TOTAL, 0), active)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val cfg = SettingsStore.load(applicationContext)
        if (!cfg.complete) return@withContext Result.failure()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_RUNNING, true).putBoolean(KEY_PENDING, false).apply()
        try {
            val client = SubsonicClient(cfg)
            val albums = client.albums()
            val tracks = albums.flatMap { runCatching { client.tracks(it.id) }.getOrDefault(emptyList()) }
            prefs.edit().putInt(KEY_TOTAL, tracks.size).apply()
            MoodAnalyzer(applicationContext).use { analyzer ->
                var done = 0
                tracks.forEach { t ->
                    if (MoodStore.get(applicationContext, t.id) == null) {
                        runCatching { analyzer.analyze(t.id, streamUrl(client, t)) { _, _ -> } }
                            .onSuccess { MoodStore.put(applicationContext, it) }
                    }
                    done++
                    prefs.edit().putInt(KEY_DONE, done).apply()
                    setProgress(workDataOf("done" to done, "total" to tracks.size))
                    if (isStopped) return@withContext Result.retry()
                }
                Result.success()
            }
        } catch (e: Exception) {
            Result.retry()
        } finally {
            prefs.edit().putBoolean(KEY_RUNNING, false).putBoolean(KEY_PENDING, false).apply()
        }
    }

    private fun streamUrl(client: SubsonicClient, t: Track): String =
        // 分析只关心节奏/音色，低码率转码减少等待
        client.streamUrl(t.id) + "&format=mp3&maxBitRate=96"
}
