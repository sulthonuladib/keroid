package com.sulthonuladib.keroid.term

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the packaged libghostty-vt and the JNI bridge work on the device. */
@RunWith(AndroidJUnit4::class)
class GhosttyTerminalTest {
    private fun line(cells: IntArray, cols: Int, row: Int): String = (0 until cols)
        .map { column ->
            val codepoint = cells[(row * cols + column) * GhosttyTerminal.STRIDE]
            if (codepoint == 0) ' ' else codepoint.toChar()
        }
        .joinToString("")
        .trimEnd()

    @Test
    fun rendersTextFromTheNativeLibrary() {
        assertTrue(
            "native library missing: ${GhosttyTerminal.unavailableReason}",
            GhosttyTerminal.available,
        )
        val terminal = GhosttyTerminal(cols = 20, rows = 3)
        try {
            terminal.write("hello\r\nsecond")
            val cells = IntArray(20 * 3 * GhosttyTerminal.STRIDE)
            val cursor = IntArray(4)
            assertEquals(60, terminal.render(cells, cursor))
            assertEquals("hello", line(cells, 20, 0))
            assertEquals("second", line(cells, 20, 1))
            assertEquals(6, cursor[0])
            assertEquals(1, cursor[1])

            // Colours reach the cells: an SGR red is redder than it is green.
            terminal.write("\r\n\u001b[31mred\u001b[0m")
            terminal.render(cells, cursor)
            val fg = cells[(2 * 20) * GhosttyTerminal.STRIDE + 1]
            val red = (fg shr 16) and 0xFF
            val green = (fg shr 8) and 0xFF
            assertTrue("expected a red foreground, got ${Integer.toHexString(fg)}", red > green + 0x40)
        } finally {
            terminal.close()
        }
    }

    @Test fun themeRecoloursExistingCellsAndTheDefaultBackground() {
        val theme = TerminalThemes.byName("Dracula")!!
        val keroid = keroidTheme(dark = true)
        GhosttyTerminal(20, 2).use { terminal ->
            terminal.write("\u001b[31mred\u001b[0m plain")
            val cells = IntArray(20 * 2 * GhosttyTerminal.STRIDE)
            val cursor = IntArray(4)
            terminal.render(cells, cursor)
            val before = cells[1]
            assertTrue("a theme must be accepted by the native terminal", terminal.setTheme(theme))
            terminal.render(cells, cursor)
            // Indexed colour 1 comes from the theme's own palette…
            assertEquals(theme.palette[1], cells[1])
            // …and the grid's default background is the theme's, not the built-in one.
            assertEquals(theme.background, cells[(20 - 1) * GhosttyTerminal.STRIDE + 2])
            assertTrue("the theme actually changed the cell", before != cells[1])
            // Switching back recolours the same cached cells, so a live theme change works.
            assertTrue(terminal.setTheme(keroid))
            terminal.render(cells, cursor)
            assertEquals(keroid.palette[1], cells[1])
            assertEquals(keroid.background, cells[(20 - 1) * GhosttyTerminal.STRIDE + 2])
        }
    }

    @Test fun scrollbackAndTrackedSelectionDoNotResizeTheSharedGrid() {
        GhosttyTerminal(20, 3).use { terminal ->
            terminal.write((1..12).joinToString("") { "history $it\r\n" })
            val cells = IntArray(20 * 3 * GhosttyTerminal.STRIDE)
            val cursor = IntArray(4)
            terminal.scroll(-100)
            terminal.render(cells, cursor)
            assertEquals("history 1", line(cells, 20, 0))
            assertTrue(cursor[3] > 0)
            terminal.select(2, 0, start = true)
            assertEquals("history", terminal.selectedText())
            terminal.select(8, 1, start = false)
            assertTrue(terminal.selectedText().contains("history 1\nhistory 2"))
            terminal.render(cells, cursor)
            assertTrue(cells.indices.any { it % GhosttyTerminal.STRIDE == 3 && cells[it] and GhosttyTerminal.FLAG_SELECTED != 0 })
            terminal.write("new output\r\n")
            assertTrue(terminal.selectedText().contains("history 1"))
            terminal.scroll(0, bottom = true)
            terminal.render(cells, cursor)
            assertEquals(0, cursor[3])
            assertEquals(20, terminal.cols)
            assertEquals(3, terminal.rows)
        }
    }

    @Test fun keyEncoderFollowsMirroredModesAndModifiers() {
        GhosttyTerminal(20, 3).use { terminal ->
            fun encoded(code: Int, mods: Int = 0, text: String = "", action: Int = 1) =
                terminal.encode(TerminalKeyStroke(code, mods, text, action = action)).toString(Charsets.UTF_8)
            assertEquals("\u001b[A", encoded(TerminalKey.Up.code))
            terminal.write("\u001b[?1h")
            assertEquals("\u001bOA", encoded(TerminalKey.Up.code))
            assertEquals("\u001b[1;2A", encoded(TerminalKey.Up.code, TerminalKeyStroke.SHIFT))
            assertEquals("\u001b[1;5C", encoded(TerminalKey.Right.code, TerminalKeyStroke.CTRL))
            assertEquals("\u001b[Z", encoded(TerminalKey.Tab.code, TerminalKeyStroke.SHIFT))
            assertEquals("\u0003", encoded(android.view.KeyEvent.KEYCODE_C, TerminalKeyStroke.CTRL, "c"))
            assertEquals("", encoded(TerminalKey.Up.code, action = 0))
            terminal.write("\u001b[>3u") // Enter stays legacy until report-all is set.
            assertEquals("\r", encoded(TerminalKey.Enter.code))
            assertEquals("", encoded(TerminalKey.Enter.code, action = 0))
            terminal.write("\u001b[>11u") // Disambiguate + events + report-all.
            assertEquals("\u001b[13u", encoded(TerminalKey.Enter.code))
            assertEquals("\u001b[13;1:3u", encoded(TerminalKey.Enter.code, action = 0))
        }
    }

    @Test fun longGraphemesCannotOverflowTheNativeCaptureBuffer() {
        GhosttyTerminal(20, 3).use { terminal ->
            terminal.write("a" + "\u0301".repeat(64))
            assertEquals(60, terminal.render(IntArray(20 * 3 * GhosttyTerminal.STRIDE), IntArray(4)))
            terminal.select(0, 0, start = true)
            assertTrue(terminal.selectedText().startsWith("a"))
        }
    }

    @Test
    fun resizingKeepsTheGridConsistent() {
        assertTrue(GhosttyTerminal.available)
        val terminal = GhosttyTerminal(cols = 8, rows = 2)
        try {
            terminal.resize(12, 4)
            assertEquals(12, terminal.cols)
            assertEquals(4, terminal.rows)
            val cells = IntArray(12 * 4 * GhosttyTerminal.STRIDE)
            assertEquals(48, terminal.render(cells, IntArray(4)))
        } finally {
            terminal.close()
        }
    }
}
