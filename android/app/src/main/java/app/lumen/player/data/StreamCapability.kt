package app.lumen.player.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 分析音频的取流方式。 */
enum class SegmentMode {
    /** 服务端接受 timeOffset，按时间偏移取三段转码流 */
    TIME_OFFSET,

    /** 服务端支持 HTTP Range，按字节范围取三段 */
    RANGE,

    /** 不支持以上任一，退回整段读取（读完即删） */
    FULL,
}

/**
 * 一台服务器的取流能力结论（探测一次，缓存 7 天）。
 *
 * [totalBytes] 仅在 RANGE 模式下有效：来自探测响应 `Content-Range: bytes 0-1023/<N>` 的总长度，
 * 用于换算字节偏移；转码流的总长度与原始文件不同，不能拿 Track.size 代替。
 */
data class StreamCapability(
    val mode: SegmentMode,
    val label: String,
    val detail: String = "",
    val totalBytes: Long = 0L,
    val probedAt: Long = System.currentTimeMillis(),
)

object StreamCapabilityStore {
    private const val PREFS = "lumen_stream_caps"
    private const val TTL_MS = 7L * 24 * 3600 * 1000
    /** 探测规则版本：升级后旧结论作废，强制重探一次 */
    private const val VERSION = 4

    fun get(ctx: Context, key: String): StreamCapability? = runCatching {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("cap_$key", null) ?: return null
        val o = JSONObject(raw)
        if (o.optInt("v") != VERSION) return null
        val at = o.optLong("at")
        if (System.currentTimeMillis() - at > TTL_MS) return null
        StreamCapability(
            mode = SegmentMode.valueOf(o.getString("mode")),
            label = o.getString("label"),
            detail = o.optString("detail"),
            totalBytes = o.optLong("total"),
            probedAt = at,
        )
    }.getOrNull()

    fun put(ctx: Context, key: String, cap: StreamCapability) {
        runCatching {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(
                    "cap_$key",
                    JSONObject()
                        .put("v", VERSION)
                        .put("mode", cap.mode.name)
                        .put("label", cap.label)
                        .put("detail", cap.detail)
                        .put("total", cap.totalBytes)
                        .put("at", cap.probedAt)
                        .toString(),
                )
                .apply()
        }
    }

    fun clear(ctx: Context) {
        runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply() }
    }
}

/**
 * 取流能力探测：只发 3 个很小的请求，任何异常都判为 FULL（保守降级），不阻塞分析。
 */
class StreamProber(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
) {

    fun probe(transcodeUrl: String, durationSec: Int): StreamCapability {
        val dur = durationSec.coerceAtLeast(1)

        // ① 是否接受 timeOffset（按时间偏移转码）——Navidrome 的原生能力，优先
        if (supportsTimeOffset(transcodeUrl, dur)) {
            return StreamCapability(
                mode = SegmentMode.TIME_OFFSET,
                label = "分段读取 · 约 1.1 MB/首",
                detail = "冷缓存曲目会自动回退整段",
            )
        }

        // ② 转码流是否支持 Range
        rangeTotal(transcodeUrl)?.let { total ->
            return StreamCapability(
                mode = SegmentMode.RANGE,
                label = "分段读取 · 约 1.1 MB/首",
                detail = "冷缓存曲目会自动回退整段",
                totalBytes = total,
            )
        }

        // ③ 兜底：整段读取
        return StreamCapability(
            mode = SegmentMode.FULL,
            label = "整段读取",
            detail = "服务端不支持分段，读完即删",
        )
    }

    /** 支持 Range 时返回总字节数，否则 null。 */
    private fun rangeTotal(url: String): Long? = runCatching {
        val req = Request.Builder().url(url + "&estimateContentLength=true")
            .header("Range", "bytes=0-1023").build()
        http.newCall(req).execute().use { r ->
            if (r.code != 206) return null
            val contentRange = r.header("Content-Range") ?: return null
            val total = contentRange.substringAfterLast('/', "0").trim().toLongOrNull() ?: 0L
            if (total <= 0) return null
            total
        }
    }.getOrNull()

    /**
     * 用「实际字节数」判断 timeOffset 是否生效，不依赖 Content-Length：
     * 请求接近结尾的偏移（只剩 ~8 秒），若服务端真的按偏移转码，读到 EOF 只会有约 8 秒的音频；
     * 若忽略该参数，则会连续读出远多于 8 秒的数据（碰到上限为止）。
     */
    private fun supportsTimeOffset(url: String, durationSec: Int): Boolean = runCatching {
        val offset = (durationSec - 8).coerceAtLeast(0)
        val cap = 400L * 1024L
        val req = Request.Builder()
            .url(url + "&estimateContentLength=true&timeOffset=" + offset)
            .build()
        http.newCall(req).execute().use { r ->
            if (r.code != 200) return false
            val body = r.body ?: return false
            var read = 0L
            val buf = ByteArray(16 * 1024)
            body.byteStream().use { ins ->
                while (read < cap) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    read += n
                }
            }
            // 8 秒 @96kbps ≈ 96 KB；放宽到 200 KB 仍算生效，超过则视为忽略参数
            read in 1..(200L * 1024L)
        }
    }.getOrDefault(false)
}
