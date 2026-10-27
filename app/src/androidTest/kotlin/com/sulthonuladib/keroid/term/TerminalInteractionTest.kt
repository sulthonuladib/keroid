package com.sulthonuladib.keroid.term

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.sulthonuladib.keroid.ui.theme.KeroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real native mirror, fake PTY only. Exercises the same overlay/input siblings. */
class TerminalInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keyboardAnimationsRequestOnlySettledGeometryAndDockDoesNotResize() {
        val sizes = mutableListOf<Int>()
        compose.setContent { KeroidTheme(dynamicColor = false) {
            TerminalProbeScreen(onGeometry = { _, rows -> sizes += rows }) {}
        } }
        compose.waitUntil(5000) { sizes.isNotEmpty() }
        val grid = compose.onNodeWithContentDescription("Terminal grid")
        compose.hideTerminalKeyboard(grid)
        val hiddenBounds = grid.fetchSemanticsNode().boundsInRoot
        val before = sizes.size
        compose.onNodeWithContentDescription("Show keyboard").performClick()
        compose.waitForTerminalKeyboard(visible = true, grid)
        val shownBounds = grid.fetchSemanticsNode().boundsInRoot
        // Floating IMEs are genuinely visible but reserve no bottom inset. Only
        // changed canvas geometry should produce a request, never visibility alone.
        val openRequests = if (shownBounds.size == hiddenBounds.size) 0 else 1
        assertEquals("IME open must send only changed, settled geometry", before + openRequests, sizes.size)
        val afterOpen = sizes.size
        compose.onNodeWithContentDescription("Show extra keys").performClick()
        compose.waitForIdle()
        assertEquals(shownBounds, grid.fetchSemanticsNode().boundsInRoot)
        assertEquals("Extra keys must not renegotiate the shared grid", afterOpen, sizes.size)
        // The expanded dock is one compact row: a full-height or two-row dock is a
        // layout regression that canvas bounds alone would not catch.
        val key = compose.onNodeWithContentDescription("Escape").fetchSemanticsNode().boundsInRoot
        val oneRow = with(compose.density) { 56.dp.toPx() }
        org.junit.Assert.assertTrue("expanded dock must stay one row, was ${key.height}px", key.height <= oneRow)
        compose.onNodeWithContentDescription("Hide keyboard").performClick()
        compose.waitForTerminalKeyboard(visible = false, grid)
        assertEquals(hiddenBounds, grid.fetchSemanticsNode().boundsInRoot)
        assertEquals("IME close must send only changed, settled geometry", afterOpen + openRequests, sizes.size)
    }

    @Test fun animatedCanvasHeightRequestsOnlyFinalGeometry() {
        val sizes = mutableListOf<Int>()
        var target by mutableStateOf(500.dp)
        compose.setContent { KeroidTheme(dynamicColor = false) {
            val scope = rememberCoroutineScope()
            val session = androidx.compose.runtime.remember { TerminalSession(80, 24, scope) }
            DisposableEffect(session) { onDispose { session.close() } }
            val height by animateDpAsState(target, tween(300), label = "synthetic IME height")
            Box(Modifier.fillMaxWidth().height(height)) {
                TerminalView(session, onGrid = { _, rows, _, _ -> sizes += rows })
            }
        } }
        compose.waitUntil(5000) { sizes.size == 1 }
        val tall = sizes.single()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { target = 280.dp }
        // Exercise actual layout/cell measurements at animation frames, not a fake
        // geometry callback. No real IME preference can turn this into a no-op.
        repeat(21) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5000) { sizes.size >= 2 }
        assertEquals("300ms height animation must make one final request", 2, sizes.size)
        org.junit.Assert.assertTrue(sizes.last() < tall)
        compose.runOnIdle { target = 500.dp }
        compose.waitUntil(5000) { sizes.size >= 3 }
        assertEquals(3, sizes.size)
        assertEquals(tall, sizes.last())
    }

    @Test fun floatingInputDoesNotInterceptHistoryOrSelectionGestures() {
        compose.setContent { KeroidTheme(dynamicColor = false) { TerminalProbeScreen {} } }
        compose.onNodeWithText("200 lines").performClick()
        compose.waitForIdle()
        val grid = compose.onNodeWithContentDescription("Terminal grid")
        grid.performTouchInput { swipeDown() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Jump to live").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Jump to live").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Jump to live").fetchSemanticsNodes().isEmpty() }
        grid.performTouchInput { longClick(Offset(65f, 20f)) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Copy").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Copy").fetchSemanticsNodes().isEmpty() }
        val before = compose.onNodeWithText("Offline · input", substring = true).fetchSemanticsNode().config.toString()
        compose.onNodeWithContentDescription("Show extra keys").performClick()
        compose.onNodeWithText("Esc").assertExists()
        compose.onNodeWithContentDescription("Hide extra keys").performClick()
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithText("Offline · input", substring = true).fetchSemanticsNode().config.toString())
    }
}
