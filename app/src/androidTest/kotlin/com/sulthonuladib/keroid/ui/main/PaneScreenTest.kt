package com.sulthonuladib.keroid.ui.main

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sulthonuladib.keroid.data.*
import com.sulthonuladib.keroid.ui.theme.KeroidTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** A fake stream only; never attaches to a real host or touches the saved library. */
class PaneScreenTest {
    @get:Rule val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun canvasFirstLayoutAndFloatingKeysDoNotRenegotiateGeometry() {
        val output = MutableSharedFlow<TerminalOutput>(extraBufferCapacity = 16)
        val measurements = mutableListOf<List<Int>>()
        val target = PaneTarget("machine", "session", "pane")
        compose.setContent {
            KeroidTheme(dynamicColor = false) {
                PaneScreen("machine", "Test machine", session(), state(), {}, {}, {}, { _, _ -> }, { _, _, _ -> },
                    output, { _, _, _ ->
                        output.tryEmit(TerminalOutput(target, 's'.code.toByte(), byteArrayOf(0, 120, 0, 40)))
                        output.tryEmit(TerminalOutput(target, 'o'.code.toByte(), "Fake terminal output".toByteArray()))
                    }, {}, { _, _ -> }, { _, c, r, w, h -> measurements += listOf(c, r, w, h) }, { _, _ -> })
            }
        }
        // Popup menus may legitimately dismiss an already-visible IME. Keep
        // this overlay/pan/fit contract test independent of the previous test's
        // keyboard state; IME geometry transitions are verified separately.
        // Measuring waits for a settled IME plus a 160 ms quiet window; a loaded
        // emulator can take several seconds for that, so this is a bounded wait.
        compose.waitUntil(15000) { measurements.isNotEmpty() }
        if (compose.onAllNodesWithContentDescription("Hide keyboard").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Hide keyboard").performClick()
        }
        // Await a quiet canvas rather than a particular IME state: hide() is
        // advisory when no input session is active, and the IME show/hide and
        // floating-vs-docked contract is verified in TerminalInteractionTest.
        var settling = android.os.SystemClock.uptimeMillis()
        var settled = compose.onNodeWithTag("terminal-canvas").fetchSemanticsNode().boundsInRoot
        compose.waitUntil(15000) {
            val next = compose.onNodeWithTag("terminal-canvas").fetchSemanticsNode().boundsInRoot
            val now = android.os.SystemClock.uptimeMillis()
            if (next != settled) {
                settled = next
                settling = now
            }
            now - settling >= 500
        }
        compose.waitForIdle()
        val canvas = compose.onNodeWithTag("terminal-canvas").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        // The available app area excludes a visible system keyboard. This must
        // hold with either the emulator hardware keyboard or the real IME up.
        assertTrue("terminal should occupy most of the available window",
            canvas.height > (canvas.bottom - root.top) * 0.75f)
        assertTrue(compose.onNodeWithText("Idle").fetchSemanticsNode().boundsInRoot.bottom <= canvas.top)
        compose.onNodeWithText("Shell", substring = false).assertDoesNotExist() // No redundant one-pane tab.
        val before = measurements.toList()
        compose.onNodeWithText("Keys").performClick()
        compose.onNodeWithText("Esc").assertExists()
        compose.waitForIdle()
        assertEquals(canvas, compose.onNodeWithTag("terminal-canvas").fetchSemanticsNode().boundsInRoot)
        assertEquals(before, measurements)
        compose.onNodeWithContentDescription("Hide extra keys").performClick()
        compose.onNodeWithTag("terminal-canvas").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Terminal actions").performClick()
        compose.onNodeWithText("Fit grid to screen").performClick()
        compose.waitForIdle()
        assertEquals(before, measurements) // Panning/fitting are renderer operations.
    }

    @Test fun claimsAreDrivenByResumeNotRecomposition() {
        val owner = Owner()
        val activations = mutableListOf<Boolean>()
        val name = mutableStateOf("Test machine")
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                KeroidTheme(dynamicColor = false) {
                    PaneScreen("machine", name.value, session(), state(), {}, {}, {}, { _, _ -> }, { _, _, _ -> },
                        MutableSharedFlow(), { _, _, _ -> }, {}, { _, _ -> }, { _, _, _, _, _ -> },
                        { _, active -> activations += active })
                }
            }
        }
        compose.runOnIdle { assertEquals(listOf(true), activations); name.value = "Renamed machine" }
        compose.runOnIdle { assertEquals(listOf(true), activations); owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { assertEquals(listOf(true, false), activations); name.value = "Background rename" }
        compose.runOnIdle { assertEquals(listOf(true, false), activations); owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(listOf(true, false, true), activations) }
    }

    private fun session() = KeroSession("session", "project", name = "Test session", panes = listOf(KeroPane("pane")))
    private fun state() = MainUiState(restoring = false, connections = mapOf("machine" to MachineUiState(ConnectionStatus.Connected)))
}
