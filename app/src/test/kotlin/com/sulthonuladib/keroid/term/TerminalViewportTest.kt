package com.sulthonuladib.keroid.term

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalViewportTest {
    @Test fun largerGridFollowsItsLiveBottomAndPansToItsEdges() {
        // A grid the daemon owns entirely starts at its live row, not at the top
        // of the scrollback: the cursor must be visible without a manual scroll.
        val live = terminalViewport(400f, 300f, 1200f, 600f, false, Offset.Zero)
        assertEquals(1f, live.scale)
        assertEquals(Offset(0f, -300f), live.offset)
        assertEquals(Offset(-800f, -300f),
            terminalViewport(400f, 300f, 1200f, 600f, false, Offset(-999f, -999f)).offset)
        // Panning past the live row lands exactly on it; panning past the top
        // lands exactly on the first row.
        assertEquals(Offset.Zero,
            terminalViewport(400f, 300f, 1200f, 600f, false, live.panForOffset(Offset(0f, 999f))).offset)
        assertEquals(live, terminalViewport(400f, 300f, 1200f, 600f, false,
            live.panForOffset(live.offset)))
    }

    @Test fun dockClearanceKeepsFooterVisibleWithoutChangingTheGrid() {
        val view = terminalViewport(400f, 300f, 400f, 300f, false, Offset.Zero, 64f)
        assertEquals(1f, view.scale)
        assertEquals(-64f, view.offset.y)
        assertEquals(236f, 300f + view.offset.y)
        // The top can still be reached by panning. Reapplying a drag at the
        // same point never compounds the automatic dock shift.
        val top = terminalViewport(400f, 300f, 400f, 300f, false,
            view.panForOffset(Offset.Zero), 64f)
        assertEquals(Offset.Zero, top.offset)
        val unchanged = terminalViewport(400f, 300f, 400f, 300f, false,
            view.panForOffset(view.offset), 64f)
        assertEquals(view, unchanged)
    }

    @Test fun fitUsesUnoccludedHeightAndSmallGridsNeedNoShift() {
        val view = terminalViewport(400f, 300f, 400f, 300f, true, Offset.Zero, 60f)
        assertEquals(0.8f, view.scale)
        assertEquals(Offset.Zero, view.offset)
        assertEquals(TerminalViewport(1f, Offset.Zero),
            terminalViewport(400f, 300f, 200f, 100f, false, Offset.Zero, 60f))
    }

    @Test fun fitIsOptionalAndSmallerGridsAreNeverEnlarged() {
        assertEquals(1f / 3f,
            terminalViewport(400f, 300f, 1200f, 600f, true, Offset(-40f, -40f)).scale)
        assertEquals(TerminalViewport(1f, Offset.Zero),
            terminalViewport(400f, 300f, 200f, 100f, true, Offset.Zero))
    }

    /**
     * The grid request must leave the dock and one clear row uncovered, so the
     * program's cursor sits above the keys and folding the dock open inside that
     * row neither moves the cursor nor asks for a new shared-grid height.
     */
    @Test fun gridRequestKeepsTheLiveRowAboveTheDockAndIgnoresDockFoldsWithinOneRow() {
        val height = 1454f
        val cell = 45f
        val collapsedDock = 168f
        val expandedDock = 190f
        val rows = terminalRows(height, cell, collapsedDock)
        assertEquals(27, rows)
        assertEquals(((height - collapsedDock - cell) / cell).toInt(), rows)
        // No dock: every row the canvas holds is usable.
        assertEquals(32, terminalRows(height, cell, 0f))
        // Never zero rows, however small the canvas.
        assertEquals(1, terminalRows(10f, cell, collapsedDock))
        val gridHeight = rows * cell
        assertEquals(Offset.Zero,
            terminalViewport(1080f, height, 1080f, gridHeight, false, Offset.Zero, collapsedDock).offset)
        assertEquals(Offset.Zero,
            terminalViewport(1080f, height, 1080f, gridHeight, false, Offset.Zero, expandedDock).offset)
        // A dock that folds open past the reserved clearance still hides nothing:
        // the grid follows its live row instead of leaving the cursor behind it.
        val wideOpen = terminalViewport(1080f, height, 1080f, gridHeight, false, Offset.Zero, 300f)
        assertEquals(height - 300f - gridHeight, wideOpen.offset.y)
    }
}
