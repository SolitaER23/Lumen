package app.lumen.player.data

import app.lumen.player.util.L10n

enum class EventRole { PRIMARY, SECONDARY, TERTIARY }

/** 当前声音类型：family 只用于背景，detail 用于迷你条更细的子标签。 */
data class AudioMoment(val event: AudioEvent?, val detail: String?)

enum class EventGlyph { RINGS, BARS, ARCS, DOTS, WAVE }

/**
 * 把 YAMNet 的 AudioSet 标签归并为可展示的声音类型。
 * 只做类别映射；颜色仍由 UI 使用现有的 Material/MCU token。
 */
enum class AudioEvent(
    private val zh: String,
    private val en: String,
    val role: EventRole,
    val glyph: EventGlyph,
    val intensity: Float,
) {
    PIANO("钢琴", "Piano", EventRole.PRIMARY, EventGlyph.RINGS, 0.55f),
    GUITAR("吉他", "Guitar", EventRole.SECONDARY, EventGlyph.WAVE, 0.60f),
    STRINGS("弦乐", "Strings", EventRole.PRIMARY, EventGlyph.ARCS, 0.62f),
    DRUMS("鼓", "Drums", EventRole.TERTIARY, EventGlyph.BARS, 0.90f),
    BASS("贝斯", "Bass", EventRole.PRIMARY, EventGlyph.DOTS, 0.55f),
    VOCALS("人声", "Vocals", EventRole.SECONDARY, EventGlyph.ARCS, 0.75f),
    AMBIENT("环境声", "Ambient", EventRole.SECONDARY, EventGlyph.DOTS, 0.35f),
    ELECTRONIC("电子", "Electronic", EventRole.TERTIARY, EventGlyph.WAVE, 0.80f),
    BRASS("铜管", "Brass", EventRole.TERTIARY, EventGlyph.BARS, 0.70f),
    WOODWIND("木管", "Woodwind", EventRole.PRIMARY, EventGlyph.WAVE, 0.50f),
    INSTRUMENT("器乐", "Instrument", EventRole.PRIMARY, EventGlyph.WAVE, 0.45f),
    MUSIC("音乐", "Music", EventRole.PRIMARY, EventGlyph.DOTS, 0.40f);

    fun label(): String = L10n.tf(zh, en)

    companion object {
        fun fromTag(name: String): AudioEvent? {
            val n = name.lowercase()
            return when {
                n.contains("piano") || n.contains("keyboard") || n.contains("harpsichord") -> PIANO
                n.contains("guitar") -> GUITAR
                n.contains("violin") || n.contains("cello") || n.contains("string") ||
                    n.contains("harp") || n.contains("viola") -> STRINGS
                n.contains("drum") || n.contains("percussion") || n.contains("snare") ||
                    n.contains("cymbal") || n.contains("hi-hat") || n.contains("gong") -> DRUMS
                n.contains("bass") -> BASS
                n.contains("sing") || n.contains("vocal") || n.contains("speech") ||
                    n.contains("choir") || n.contains("rap") || n.contains("chant") ||
                    n.contains("humming") || n.contains("whisper") -> VOCALS
                n.contains("ambient") || n.contains("environment") || n.contains("rain") ||
                    n.contains("wind") || n.contains("water") || n.contains("nature") ||
                    n.contains("silence") -> AMBIENT
                n.contains("electronic") || n.contains("synth") || n.contains("techno") ||
                    n.contains("house") || n.contains("edm") || n.contains("dubstep") -> ELECTRONIC
                n.contains("trumpet") || n.contains("sax") || n.contains("brass") ||
                    n.contains("trombone") -> BRASS
                n.contains("flute") || n.contains("clarinet") || n.contains("oboe") ||
                    n.contains("bassoon") -> WOODWIND
                n.contains("instrument") -> INSTRUMENT
                n.contains("music") -> MUSIC
                else -> null
            }
        }

        fun fromTags(tags: List<String>): AudioEvent? {
            val mapped = tags.mapNotNull { fromTag(it) }
            return mapped.firstOrNull { it != MUSIC && it != INSTRUMENT }
                ?: mapped.firstOrNull()
        }

        /** 更细的子标签；视觉家族仍使用 AudioEvent。 */
        fun detailFor(tags: List<String>): String? {
            var generic: String? = null
            for (tag in tags) {
                val n = tag.lowercase()
                val detail = when {
                    n.contains("electric piano") -> "电钢琴"
                    n.contains("piano") -> "钢琴"
                    n.contains("acoustic guitar") -> "木吉他"
                    n.contains("electric guitar") -> "电吉他"
                    n.contains("bass guitar") -> "电贝斯"
                    n.contains("double bass") -> "低音提琴"
                    n.contains("bass") && !n.contains("drum") -> "贝斯"
                    n.contains("guitar") -> "吉他"
                    n.contains("violin") -> "小提琴"
                    n.contains("viola") -> "中提琴"
                    n.contains("cello") -> "大提琴"
                    n.contains("harp") -> "竖琴"
                    n.contains("string") -> "弦乐"
                    n.contains("snare") -> "军鼓"
                    n.contains("hi-hat") -> "踩镲"
                    n.contains("cymbal") -> "镲"
                    n.contains("gong") -> "锣"
                    n.contains("percussion") -> "打击乐"
                    n.contains("drum kit") || n.contains("drum") -> "爵士鼓"
                    n.contains("male speech") -> "男声"
                    n.contains("female speech") -> "女声"
                    n.contains("child speech") -> "童声"
                    n.contains("choir") -> "合唱"
                    n.contains("rap") -> "说唱"
                    n.contains("humming") -> "哼唱"
                    n.contains("whisper") -> "耳语"
                    n.contains("singing") -> "演唱"
                    n.contains("speech") -> "说话"
                    n.contains("vocal") -> "人声"
                    n.contains("rain") -> "雨声"
                    n.contains("wind") -> "风声"
                    n.contains("water") -> "水声"
                    n.contains("nature") -> "自然声"
                    n.contains("silence") -> "静音"
                    n.contains("ambient") -> "环境声"
                    n.contains("environment") -> "环境声"
                    n.contains("synth") -> "合成器"
                    n.contains("techno") -> "Techno"
                    n.contains("house") -> "House"
                    n.contains("dubstep") -> "Dubstep"
                    n.contains("electronic") -> "电子"
                    n.contains("trumpet") -> "小号"
                    n.contains("sax") -> "萨克斯"
                    n.contains("trombone") -> "长号"
                    n.contains("brass") -> "铜管"
                    n.contains("flute") -> "长笛"
                    n.contains("clarinet") -> "单簧管"
                    n.contains("oboe") -> "双簧管"
                    n.contains("bassoon") -> "巴松"
                    n.contains("instrument") -> "器乐"
                    n.contains("music") || n.contains("rhythm and blues") -> "音乐"
                    else -> null
                }
                if (detail != null) {
                    if (detail == "音乐" || detail == "器乐") {
                        if (generic == null) generic = detail
                    } else {
                        return detail
                    }
                }
            }
            return generic
        }
    }
}
