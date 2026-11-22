package app.lumen.player

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class LitertSmokeTest {
    @Test
    fun yamnetInterpreterRunsOnDevice() {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = ctx.assets.open("mood/yamnet.tflite").use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
            .apply { rewind() }
        val interpreter = Interpreter(buffer)
        try {
            val output = Array(1) { FloatArray(521) }
            interpreter.run(FloatArray(15600), output)
            assertTrue("YAMNet returned no positive scores", output[0].any { it > 0f })
        } finally {
            interpreter.close()
        }
    }
}
