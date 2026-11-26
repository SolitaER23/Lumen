package app.lumen.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lumen.player.data.ServerConfig
import app.lumen.player.data.SubsonicClient
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.lumen.player.playback.MediaCache
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真实 HTTP 服务器联调：token 认证 → 曲库 → 取流 → 播放。
 *
 * 既可对 mock 服务器（tools/mock-subsonic）跑，也可对真实 Navidrome 跑。参数全部走
 * instrumentation，仓库里**不保存任何真实凭据**：
 *
 *   gradlew :app:connectedDebugAndroidTest
 *     -Pandroid.testInstrumentationRunnerArguments.class=app.lumen.player.ServerE2ETest
 *     -Pandroid.testInstrumentationRunnerArguments.serverBase=http://host:4533
 *     -Pandroid.testInstrumentationRunnerArguments.serverUser=USER
 *     -Pandroid.testInstrumentationRunnerArguments.serverPass=PASS
 *
 * 默认指向 mock（test/testpass）。只有 writeActions=true 时才调用 star/unstar ——
 * 对真实曲库跑时必须保持 false，避免改动用户数据。
 */
@RunWith(AndroidJUnit4::class)
class ServerE2ETest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()

    private val base: String get() = args.getString("serverBase") ?: "http://192.168.1.92:8899"
    private val user: String get() = args.getString("serverUser") ?: "test"
    private val pass: String get() = args.getString("serverPass") ?: "testpass"
    private val writeActions: Boolean get() = args.getString("writeActions") == "true"

    @Test
    fun libraryAndStreamWork() = runBlocking {
        // 每次安装都会重置运行时权限；Android 17 起没有 ACCESS_LOCAL_NETWORK 就连不上局域网
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(ctx.packageName, "android.permission.ACCESS_LOCAL_NETWORK")
        }

        val client = SubsonicClient(ServerConfig(base, user, pass))
        // 服务器不可达时跳过（而不是红），这样默认的完整测试套件在没起 mock 时也能绿
        val reachable = runCatching { client.ping() }.getOrDefault(false)
        org.junit.Assume.assumeTrue("服务器不可达，跳过联调测试: " + base, reachable)

        val albums = client.newestAlbums(size = 12)
        assertTrue("曲库为空或接口异常", albums.isNotEmpty())

        val album = albums.first()
        val tracks = client.tracks(album.id)
        assertTrue("专辑没有曲目: " + album.name, tracks.isNotEmpty())
        assertTrue("曲目标题不应为空", tracks.all { it.title.isNotBlank() })

        if (writeActions) {
            // 仅 mock 环境：验证写接口的签名与响应解析
            client.star(tracks[0].id)
            client.unstar(tracks[0].id)
            client.scrobble(tracks[0].id, submission = false)
        }

        // 取流地址必须能构造出来（真实播放链路由 playbackBecomesReady 覆盖）
        val streamUrl = client.streamUrl(tracks[0].id)
        assertTrue("取流地址为空", streamUrl.startsWith("http"))
    }

    /** ExoPlayer 能否用「带 token 的 stream URL + 磁盘缓存」真正开始播放（验证解码链路）。 */
    @Test
    fun playbackBecomesReady() {
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(ctx.packageName, "android.permission.ACCESS_LOCAL_NETWORK")
        }
        val client = SubsonicClient(ServerConfig(base, user, pass))
        org.junit.Assume.assumeTrue(
            "服务器不可达，跳过联调测试: " + base,
            runCatching { client.ping() }.getOrDefault(false),
        )
        val album = client.newestAlbums(size = 6).firstOrNull() ?: error("曲库为空")
        val track = client.tracks(album.id).firstOrNull() ?: error("专辑无曲目")
        val url = client.streamUrl(track.id)

        val inst = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        var error: String? = null
        var player: ExoPlayer? = null

        inst.runOnMainSync {
            player = ExoPlayer.Builder(ctx)
                .setMediaSourceFactory(DefaultMediaSourceFactory(MediaCache.cacheDataSourceFactory(ctx)))
                .build()
                .apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) ready.countDown()
                            if (state == Player.STATE_ENDED) ready.countDown()
                        }

                        override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                            error = e.errorCodeName
                            ready.countDown()
                        }
                    })
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = false
                }
        }

        val ok = ready.await(45, TimeUnit.SECONDS)
        inst.runOnMainSync { runCatching { player?.release() } }
        assertTrue("ExoPlayer 未就绪（超时）", ok)
        assertTrue("播放器报错: " + error, error == null)
    }
}
