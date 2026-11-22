package app.lumen.player.data

import app.lumen.player.util.L10n

enum class EventRole { PRIMARY, SECONDARY, TERTIARY }

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
    }
}
