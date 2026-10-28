package com.sulthonuladib.keroid.term

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.sulthonuladib.keroid.ui.theme.KeroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Native cells, synthetic footer, no real session content or credentials. */
class TerminalDockLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun softwareKeyboardKeepsLiveCursorVisibleBeforeRemoteResizeConfirmation() {
        val grids = mutableListOf<List<Int>>()
        cursorFixture(grids, fixedRemoteGrid = true)
        val grid = compose.onNodeWithContentDescription("Terminal grid")
        compose.hideTerminalKeyboard(grid)
        compose.waitUntil(5000) { cursorBounds(grid) != null }
        val hidden = grid.fetchSemanticsNode().boundsInRoot
        grid.performTouchInput { click() }
        compose.waitForTerminalKeyboard(visible = true, grid)
        assertTrue("Requires a docked software keyboard",
            grid.fetchSemanticsNode().boundsInRoot.height < hidden.height)
        assertTrue("Live cursor must stay visible while the daemon still owns the larger grid; no manual scroll",
            cursorBounds(grid) != null)
        compose.onNodeWithContentDescription("Show extra keys").performClick()
        compose.waitForIdle()
        assertTrue("Expanded keys must not cover the cursor", cursorBounds(grid) != null)
        compose.onNodeWithContentDescription("Hide keyboard").performClick()
        compose.waitForTerminalKeyboard(visible = false, grid)
        assertTrue("Cursor must remain visible after keyboard dismissal", cursorBounds(grid) != null)
    }

    @Test fun dockExpansionDoesNotMoveCursorResizeCanvasOrRequestGrid() {
        val grids = mutableListOf<List<Int>>()
        cursorFixture(grids, fixedRemoteGrid = false)
        val grid = compose.onNodeWithContentDescription("Terminal grid")
        grid.performTouchInput { click() }
        compose.waitForTerminalKeyboard(visible = true, grid)
        compose.waitUntil(5000) { cursorBounds(grid) != null }
        val canvas = grid.fetchSemanticsNode().boundsInRoot
        val cursor = cursorBounds(grid)
        val before = grids.toList()
        repeat(3) {
            compose.onNodeWithContentDescription("Show extra keys").performClick()
            compose.waitForIdle()
            assertEquals("Dock expansion must not change canvas bounds", canvas, grid.fetchSemanticsNode().boundsInRoot)
            assertEquals("Dock expansion must not request a shared-grid resize", before, grids)
            assertEquals("Dock expansion must not jump the live cursor", cursor, cursorBounds(grid))
            compose.onNodeWithContentDescription("Hide extra keys").performClick()
            compose.waitForIdle()
            assertEquals("Dock collapse must not jump the live cursor", cursor, cursorBounds(grid))
            assertEquals(before, grids)
        }
    }

    private fun cursorFixture(grids: MutableList<List<Int>>, fixedRemoteGrid: Boolean) {
        val expanded = mutableStateOf(false)
        val showKeyboard = androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent { KeroidTheme(dynamicColor = false) {
            val scope = rememberCoroutineScope()
            val session = remember { TerminalSession(53, 40, scope) }
            DisposableEffect(session) { onDispose { session.close() } }
            androidx.compose.material3.Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                TerminalDockLayout(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), dock = {
                    TerminalKeyBar(expanded.value, { expanded.value = !expanded.value }, false, {}, {}, Modifier.padding(8.dp))
                }) { clearance ->
                    TerminalView(session, bottomOcclusion = clearance,
                        onTap = { showKeyboard.intValue++ }, onGrid = { c, r, _, _ ->
                            grids += listOf(c, r)
                            // Fixed case models a delayed confirmation / desktop-owned grid.
                            // The phone is not allowed to resize its mirror optimistically.
                            if (!fixedRemoteGrid) session.adoptSize(c, r)
                            val row = if (fixedRemoteGrid) 40 else r
                            session.offer("\u001b[0m\u001b[2J\u001b[${row};16H\u001b[38;2;0;255;0m \b\u001b[?25h".toByteArray())
                        })
                    TerminalInputLayer(false, {}, {}, Modifier.fillMaxSize(), showKeyboard = showKeyboard.intValue)
                }
            }
        } }
    }

    private fun cursorBounds(grid: SemanticsNodeInteraction): androidx.compose.ui.geometry.Rect? {
        val pixels = grid.captureToImage().toPixelMap()
        var left = pixels.width
        var top = pixels.height
        var right = -1
        var bottom = -1
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
            val color = pixels[x, y]
            if (color.green > 0.35f && color.green > color.red + 0.25f && color.green > color.blue + 0.25f) {
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        return if (right < 0) null else androidx.compose.ui.geometry.Rect(left.toFloat(), top.toFloat(),
            (right + 2).toFloat(), (bottom + 2).toFloat())
    }

    /** Vertical extent of a full-width red row in the canvas capture. */
    private class Band(val top: Int, val bottom: Int)

    /**
     * Reads the left edge of the canvas, away from the right-aligned dock, so a
     * row painted red by the fake daemon is visible only where the dock does not
     * cover it. A footer hidden behind the keys therefore yields no band.
     */
    private fun footerBand(grid: SemanticsNodeInteraction): Band? {
        val pixels = grid.captureToImage().toPixelMap()
        var top = -1
        var bottom = -1
        for (y in 0 until pixels.height) {
            val color = pixels[10, y]
            if (color.red > color.green + 0.2f && color.red > color.blue + 0.2f) {
                if (top < 0) top = y
                bottom = y + 1
            }
        }
        return if (top < 0) null else Band(top, bottom)
    }

    /** The live row must be drawn completely above the dock, never under it. */
    private fun assertFooterClear(grid: SemanticsNodeInteraction, dockTag: String, cellHeight: Int) {
        val band = footerBand(grid) ?: throw AssertionError("live footer row is not drawn at all")
        val dock = compose.onNodeWithTag(dockTag).fetchSemanticsNode().boundsInRoot
        val canvas = grid.fetchSemanticsNode().boundsInRoot
        val dockTop = dock.top - canvas.top
        assertTrue("live footer row must end above the dock (footer=$band dockTop=$dockTop)",
            band.bottom <= dockTop + 1)
        assertTrue("dock must not clip the live footer row (footer=${band.top}..${band.bottom}, cell=$cellHeight)",
            band.bottom - band.top >= (cellHeight * 0.8f).toInt())
    }

    @Test fun dockedSoftwareKeyboardAndExpandedRealKeysKeepFooterClear() {
        val expanded = mutableStateOf(false)
        val showKeyboard = androidx.compose.runtime.mutableIntStateOf(0)
        val grids = mutableListOf<List<Int>>()
        var cellHeight = 0
        compose.setContent { KeroidTheme(dynamicColor = false) {
            val scope = rememberCoroutineScope()
            val session = remember { TerminalSession(80, 24, scope) }
            DisposableEffect(session) { onDispose { session.close() } }
            // Match PaneScreen's IME/safe-drawing handling, not a fixed-size box.
            androidx.compose.material3.Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                TerminalDockLayout(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), dock = {
                    TerminalKeyBar(expanded.value, { expanded.value = !expanded.value }, false, {}, {},
                        Modifier.padding(8.dp).testTag("real-dock"))
                }) { clearance ->
                    TerminalView(session, bottomOcclusion = clearance,
                        onTap = { showKeyboard.intValue++ }, onGrid = { c, r, _, ch ->
                            grids += listOf(c, r); cellHeight = ch
                            session.adoptSize(c, r)
                            session.offer("\u001b[${r};1H\u001b[41m\u001b[2K\u001b[?25l".toByteArray())
                        })
                    TerminalInputLayer(false, {}, {}, Modifier.fillMaxSize(), showKeyboard = showKeyboard.intValue)
                }
            }
        } }
        val grid = compose.onNodeWithContentDescription("Terminal grid")
        compose.hideTerminalKeyboard(grid)
        val hidden = grid.fetchSemanticsNode().boundsInRoot
        grid.performTouchInput { click() }
        compose.waitForTerminalKeyboard(visible = true, grid)
        val shown = grid.fetchSemanticsNode().boundsInRoot
        assertTrue("This regression requires an actual docked software keyboard, not a floating IME",
            shown.height < hidden.height)
        val before = grids.toList()
        compose.onNodeWithContentDescription("Show extra keys").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Hide keyboard").assertExists()
        compose.onNodeWithContentDescription("Escape").assertIsDisplayed()
        compose.onNodeWithContentDescription("Control").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tab").assertIsDisplayed()
        compose.waitUntil(5000) { footerBand(grid) != null && footerBand(grid)!!.bottom <=
            (compose.onNodeWithTag("real-dock").fetchSemanticsNode().boundsInRoot.top - shown.top + 1) }
        assertFooterClear(grid, "real-dock", cellHeight)
        assertEquals(shown, grid.fetchSemanticsNode().boundsInRoot)
        assertEquals("Expanding keys with the IME open must not resize the PTY", before, grids)
    }

    @Test fun footerIsDrawnAboveMeasuredDockWithoutResizing() {
        val expanded = mutableStateOf(false)
        val grids = mutableListOf<List<Int>>()
        var cellHeight = 0
        compose.setContent { KeroidTheme(dynamicColor = false) {
            val scope = rememberCoroutineScope()
            val session = remember { TerminalSession(80, 24, scope) }
            DisposableEffect(session) { onDispose { session.close() } }
            TerminalDockLayout(Modifier.size(300.dp, 400.dp), dock = {
                Box(Modifier.fillMaxWidth().height(if (expanded.value) 96.dp else 48.dp)
                    .background(Color.Blue).testTag("fake-dock"))
            }) { clearance ->
                TerminalView(session, bottomOcclusion = clearance, onGrid = { c, r, _, ch ->
                    grids += listOf(c, r); cellHeight = ch
                    session.adoptSize(c, r)
                    session.offer("\u001b[${r};1H\u001b[41m\u001b[2K\u001b[?25l".toByteArray())
                })
            }
        } }
        compose.waitUntil(5000) { grids.isNotEmpty() }
        val grid = compose.onNodeWithContentDescription("Terminal grid")
        compose.waitUntil(5000) { footerBand(grid) != null }
        assertFooterClear(grid, "fake-dock", cellHeight)
        val bounds = grid.fetchSemanticsNode().boundsInRoot
        val before = grids.toList()
        compose.runOnIdle { expanded.value = true }
        compose.waitForIdle()
        assertFooterClear(grid, "fake-dock", cellHeight)
        assertEquals(bounds, grid.fetchSemanticsNode().boundsInRoot)
        assertEquals("dock must not request a PTY resize", before, grids)
    }
}
