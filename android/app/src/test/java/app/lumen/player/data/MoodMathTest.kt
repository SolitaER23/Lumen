package app.lumen.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodMathTest {

    private fun dsp(
        rms: Float,
        centroid: Float,
        onset: Float,
        tempo: Float,
    ) = MoodMath.Dsp(
        rms = rms,
        centroidHz = centroid,
        onsetRate = onset,
        tempoBpm = tempo,
        beatsMs = intArrayOf(0, 500, 1000),
        env = floatArrayOf(0f, 0.5f, 1f),
        envStepMs = 100,
    )

    @Test
    fun fuseReturnsCoordinateInUnitRange() {
        val result = MoodMath.fuse(null, dsp(0.2f, 2000f, 4f, 120f), "track-1")
        assertTrue("valence=${result.valence}", result.valence in 0f..1f)
        assertTrue("arousal=${result.arousal}", result.arousal in 0f..1f)
        assertEquals("track-1", result.trackId)
        assertTrue(result.label.isNotBlank())
    }

    @Test
    fun louderAndFasterMusicRaisesArousal() {
        val calm = MoodMath.fuse(null, dsp(0.02f, 600f, 1f, 60f), "calm")
        val intense = MoodMath.fuse(null, dsp(0.35f, 4000f, 8f, 160f), "intense")
        assertTrue(
            "calm=${calm.arousal} intense=${intense.arousal}",
            intense.arousal > calm.arousal,
        )
    }

    @Test
    fun labelIsOneOfKnownClasses() {
        val known = setOf("Happy", "Tender", "Calm", "Angry", "Tense", "Sad")
        val result = MoodMath.fuse(null, dsp(0.1f, 1200f, 3f, 100f), "t")
        assertTrue("unexpected label=${result.label}", result.label in known)
    }

    @Test
    fun topTagsKeepsHighestScoresOnly() {
        val scores = FloatArray(527)
        scores[0] = 0.61f
        scores[1] = 0.34f
        scores[2] = 0.01f
        val tags = MoodMath.topTags(scores, limit = 3)
        assertEquals(2, tags.size)
        assertEquals("Speech", tags[0].name)
        assertTrue(tags[0].score >= tags[1].score)
        assertTrue(tags.none { it.score < 0.02f })
    }

    @Test
    fun labelsCoverWholeClapOutput() {
        // AudioSet 527 类，与 clap clipwise_output 一一对应
        assertEquals(527, CLAP_LABELS.size)
        assertTrue(CLAP_LABELS.all { it.isNotBlank() })
    }
}
