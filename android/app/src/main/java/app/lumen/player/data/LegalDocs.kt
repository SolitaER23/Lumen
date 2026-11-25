package app.lumen.player.data

import android.content.Context
import app.lumen.player.util.L10n
import org.json.JSONArray
import org.json.JSONObject

/** 法务文本的一节：标题 + 段落 + 要点。 */
data class LegalSection(
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
)

/**
 * 从 assets/legal 目录下的 JSON 读取协议与隐私，按 [L10n.lang] 选取中英文本。
 * 结构固定为 { version, updated, sections: [{ zh: {title, paragraphs, bullets}, en: {...} }] }，
 * 便于在不改代码的情况下增删条目或补第三种语言。
 */
object LegalDocs {
    fun load(ctx: Context, name: String): List<LegalSection> {
        val raw = runCatching {
            ctx.assets.open("legal/$name.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val lang = if (L10n.lang == "en") "en" else "zh"
        val sections = json.optJSONArray("sections") ?: return emptyList()
        return (0 until sections.length()).mapNotNull { i ->
            val sec = sections.optJSONObject(i) ?: return@mapNotNull null
            val body = sec.optJSONObject(lang) ?: sec.optJSONObject("zh") ?: return@mapNotNull null
            val title = body.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // 兼容两种写法：paragraphs/bullets 分列，或 body 统一成段
            val bodyText = body.optJSONArray("body").strings()
            LegalSection(
                title = title,
                paragraphs = body.optJSONArray("paragraphs").strings().ifEmpty { bodyText },
                bullets = body.optJSONArray("bullets").strings(),
            )
        }
    }
}

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList()
    else (0 until length()).mapNotNull { i -> optString(i).takeIf { s -> s.isNotBlank() } }
