package app.lumen.player.util

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * 进程级 OkHttp：连接池与线程池全局唯一。
 *
 * 以前 SubsonicClient（每个实例）、StatsClient、StreamProber 各建一套，
 * 账号切换/离线重试会不断累积线程与连接。需要不同超时的调用方用 [newBuilder] 派生，
 * 派生实例共享连接池与 Dispatcher。
 */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 长任务（音频流、封面）用更宽松的读超时。 */
    fun slowClient(): OkHttpClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
