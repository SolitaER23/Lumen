package app.lumen.player.local

import android.content.Context
import app.lumen.player.util.Logx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 0.90.0 下线端侧模型后的一次性清理。
 *
 * 背景：0.74~0.89 会把 148 MB 的 CLAP 权重解压到 `filesDir`，并把分析结果写进
 * `moods.db`、电台曲库写进 `radio_tracks.json`。功能删除后这些数据**没有归属**，
 * 但升级时不会自动消失（用户还在为已下线的能力付磁盘，且旧的听歌画像仍留在设备上）。
 *
 * 约束：
 *  - 幂等：完成后写 [KEY_DONE]，之后每次启动只是一次 prefs 读取；
 *  - 白名单式删除：只删本文件列出的路径/前缀，绝不按目录整体清理；
 *  - 与 [LocalData.wipe] 共用同一份清单，避免「清了数据却漏了旧模型」。
 */
object LegacyCleanup {
    private const val PREFS = "lumen_maintenance"
    private const val KEY_DONE = "legacy_removed_v090"

    /** 已下线模型在 filesDir 下的产物（含旧版本与落盘标记/临时文件）。 */
    private val FILES_DIR_NAMES = listOf(
        "clap_music_fp16.onnx",
        "clap_music_fp16.onnx.ok",
        "clap_music_fp16.onnx.tmp",
        "clap_music_v4.onnx",
        "clap_music_v4.onnx.ok",
        "clap_music_v4.onnx.tmp",
        "audio_projection.onnx",
        "audio_projection.onnx.ok",
        "audio_projection.onnx.tmp",
        "radio_tracks.json",
    )

    /** cacheDir 下以该前缀开头的文件（mood_tracklist.json / mood_*.mp3 / *.part）。 */
    private const val CACHE_PREFIX = "mood_"

    /** 已下线功能独占的 prefs 文件。 */
    private val PREFS_NAMES = listOf("lumen_mood", "lumen_radio", "lumen_stream_caps")

    private const val DB = "moods.db"

    fun alreadyDone(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    /**
     * 执行清理；返回释放的字节数（无法统计的部分计 0）。
     *
     * @param force 忽略幂等标记。`LocalData.wipe`（用户主动清数据）必须传 true，
     *   否则一旦首启清理过，后续「清除本机数据」就不会再碰这些历史文件。
     */
    suspend fun run(ctx: Context, force: Boolean = false): Long = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        if (!force && alreadyDone(app)) return@withContext 0L

        var freed = 0L
        FILES_DIR_NAMES.forEach { name ->
            val f = File(app.filesDir, name)
            if (f.isFile) {
                freed += f.length()
                if (!f.delete()) Logx.w("LumenLegacy", "删除失败: " + f.name)
            }
        }
        runCatching {
            app.cacheDir.listFiles { f -> f.name.startsWith(CACHE_PREFIX) }?.forEach { f ->
                freed += f.length()
                f.delete()
            }
        }

        // moods.db（含 WAL/SHM/journal）
        val dbFile = app.getDatabasePath(DB)
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"), File(dbFile.path + "-journal"))
            .forEach { f ->
                if (f.isFile) {
                    freed += f.length()
                    runCatching { f.delete() }
                }
            }
        runCatching { app.deleteDatabase(DB) }

        PREFS_NAMES.forEach { name ->
            runCatching {
                app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }

        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DONE, true)
            .putLong("legacy_freed_bytes", freed)
            .apply()
        Logx.i("LumenLegacy", "已清理已下线功能数据，释放 " + (freed / 1024 / 1024) + " MB")
        freed
    }
}
