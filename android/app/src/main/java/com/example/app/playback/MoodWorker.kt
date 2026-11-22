package com.example.app.playback

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.app.data.MoodAnalyzer
import com.example.app.data.MoodStore
import com.example.app.data.SettingsStore
import com.example.app.data.SubsonicClient
import com.example.app.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 首次拉库后的整库心情分析：充电 + Wi-Fi 时后台跑，可断点续跑。 */
class MoodWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val PREFS = "lumen_mood"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_RUNNING = "running"
        const val WORK_NAME = "mood_analysis"

        fun start(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<MoodWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
        }

        fun progress(ctx: Context): Triple<Int, Int, Boolean> {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Triple(p.getInt(KEY_DONE, 0), p.getInt(KEY_TOTAL, 0), p.getBoolean(KEY_RUNNING, false))
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val cfg = SettingsStore.load(applicationContext)
        if (!cfg.complete) return@withContext Result.failure()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_RUNNING, true).apply()
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
            prefs.edit().putBoolean(KEY_RUNNING, false).apply()
        }
    }

    private fun streamUrl(client: SubsonicClient, t: Track): String =
        if (t.suffix.equals("m4a", ignoreCase = true)) {
            client.streamUrl(t.id) + "&format=mp3&maxBitRate=320"
        } else client.streamUrl(t.id)
}
