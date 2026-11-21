package dev.netstream.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Runs the real activity and bundled catalogue without a device or Firebase credentials. Does not
 * assert decoder output or the availability of a third-party streaming host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h890dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NetStreamUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun awaitLibrary() {
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("Browse streams").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun catalogueLoadsAndFiltersByProtocol() {
        awaitLibrary()
        ui.onNodeWithText("Stream library").assertIsDisplayed()
        val output = File("build/reports/netstream/catalogue.png")
        output.parentFile?.mkdirs()
        // Draw the measured activity view. PixelCopy's forced vsync is not available on a host JVM.
        val bitmap =
            ui.runOnIdle {
                val view = ui.activity.window.decorView
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                    view.draw(Canvas(it))
                }
            }
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        ui.onNodeWithText("DASH  1").performClick()
        ui.onNodeWithText("Tears of Steel").assertDoesNotExist()
        ui.onAllNodesWithText("Sintel").assertCountEquals(2) // featured + matching row
        ui.onNodeWithText("HLS  2").performClick()
        ui.onNodeWithText("Tears of Steel").assertExists()
        ui.onAllNodesWithText("Sintel").assertCountEquals(1) // independent featured slot
    }

    @Test
    fun playerOpensAndBackFinalizesPrivateSession() {
        awaitLibrary()
        ui.onAllNodesWithText("Sintel").onFirst().performClick()
        ui.onNodeWithText("Adaptive player").assertIsDisplayed()
        ui.onNodeWithContentDescription("Enter fullscreen").assertExists()
        ui.onNodeWithContentDescription("Back to library").performClick()
        awaitLibrary()
        val root = File(ui.activity.filesDir, "qoe")
        fun summaries() =
            root
                .walk()
                .filter { it.name == "session.json" }
                .mapNotNull { runCatching { JSONObject(it.readText()) }.getOrNull() }
        ui.waitUntil(5_000) { summaries().any { it.optString("status") == "finished" } }
        assertTrue(
            summaries().any {
                it.optString("endReason") == "back" && it.optString("videoId") == "sintel"
            }
        )
    }
}
