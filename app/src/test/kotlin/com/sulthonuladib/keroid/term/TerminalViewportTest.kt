package com.sulthonuladib.keroid.term

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalViewportTest {
    @Test fun largerGridStaysReadableAndPansToItsEdges() {
        val view = terminalViewport(400f, 300f, 1200f, 600f, false, Offset(-999f, -999f))
        assertEquals(1f, view.scale)
        assertEquals(Offset(-800f, -300f), view.offset)
        assertEquals(Offset.Zero, terminalViewport(400f, 300f, 1200f, 600f,
            false, Offset(50f, 50f)).offset)
    }

    @Test fun fitIsOptionalAndSmallerGridsAreNeverEnlarged() {
        assertEquals(1f / 3f,
            terminalViewport(400f, 300f, 1200f, 600f, true, Offset(-40f, -40f)).scale)
        assertEquals(TerminalViewport(1f, Offset.Zero),
            terminalViewport(400f, 300f, 200f, 100f, true, Offset.Zero))
    }
}
