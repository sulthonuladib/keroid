package com.sulthonuladib.keroid.term

import org.junit.Assert.*
import org.junit.Test

/** The theme catalogue and the appearance model, without Android or a device. */
class TerminalAppearanceTest {
    @Test fun catalogueIsCompleteAndUnique() {
        val all = TerminalThemes.all
        assertTrue("Ghostty ships hundreds of themes", all.size >= 600)
        assertEquals("no duplicate names", all.size, all.map { it.name }.distinct().size)
        all.forEach { theme ->
            assertEquals(theme.name, TerminalTheme.COLOR_COUNT, theme.colors.size)
            theme.colors.forEach { color ->
                assertEquals("${theme.name}: opaque colours only", 0xFF, color ushr 24)
            }
        }
    }

    @Test fun catalogueIsListedCaseInsensitivelyAndCasePreserving() {
        val names = TerminalThemes.all.map { it.name }
        assertEquals("listed like Ghostty lists them",
            names.sortedWith(compareBy({ it.lowercase() }, { it })), names)
        // Both the shipped table and name lookup ignore case, as Ghostty's option does.
        val dracula = TerminalThemes.byName("Dracula")
        assertNotNull("Dracula ships with Ghostty", dracula)
        assertSame(dracula, TerminalThemes.byName("dracula"))
        assertNull(TerminalThemes.byName("no such theme"))
    }

    @Test fun themesResolvePerAppearanceWithKeroidFirstAndLast() {
        assertTrue(TerminalAppearance.themeNames.first() == TerminalAppearance.KEROID)
        assertEquals(TerminalAppearance.themeNames.size, TerminalThemes.all.size + 1)
        assertTrue(TerminalThemes.all.all { it.name in TerminalAppearance.themeNames })
    }

    @Test fun keroidIsTheDefaultInBothAppearances() {
        val appearance = TerminalAppearance()
        assertEquals(TerminalAppearance.KEROID, appearance.lightTheme)
        assertEquals(TerminalAppearance.KEROID, appearance.darkTheme)
        assertEquals(keroidTheme(dark = false), appearance.theme(dark = false))
        assertEquals(keroidTheme(dark = true), appearance.theme(dark = true))
        assertNotEquals(appearance.theme(false).background, appearance.theme(true).background)
        assertEquals("a light theme is light", false, appearance.theme(false).isDark)
        assertEquals("a dark theme is dark", true, appearance.theme(true).isDark)
    }

    @Test fun chosenThemesFollowTheAppAppearance() {
        val appearance = TerminalAppearance(lightTheme = "3024 Day", darkTheme = "Dracula")
        assertEquals(TerminalThemes.byName("3024 Day"), appearance.theme(dark = false))
        assertEquals(TerminalThemes.byName("Dracula"), appearance.theme(dark = true))
    }

    @Test fun unknownStoredNamesFallBackToKeroidInsteadOfFailing() {
        val appearance = TerminalAppearance(lightTheme = "A Theme Ghostty Dropped", darkTheme = "")
        assertEquals(keroidTheme(dark = false), appearance.theme(dark = false))
        assertEquals(keroidTheme(dark = true), appearance.theme(dark = true))
    }

    @Test fun keroidPaletteIsContrastingInBothAppearances() {
        listOf(keroidTheme(dark = false), keroidTheme(dark = true)).forEach { theme ->
            assertEquals(16, theme.palette.size)
            assertTrue("${theme.name}: text on its own background",
                luminance(theme.foreground) > 4f * luminance(theme.background) ||
                    luminance(theme.background) > 4f * luminance(theme.foreground))
            assertNotEquals("palette black is not the background",
                theme.palette[0], theme.background)
            assertNotEquals(theme.selection, theme.background)
        }
    }

    @Test fun lineHeightFollowsFontSizeAndStaysIntegral() {
        val appearance = TerminalAppearance(fontSize = 15f, lineHeight = 1.4f)
        assertEquals(21f, appearance.lineHeightSp, 0.001f)
        val stepped = appearance.copy(fontSize = appearance.fontSize + 1f)
        assertEquals("steppers stay on whole points", 16f, stepped.fontSize, 0.001f)
    }

    @Test fun fontChoicesAreTheOnesTheApkCarries() {
        assertEquals(listOf(TerminalFontChoice.Bundled, TerminalFontChoice.Monospace),
            TerminalFontChoice.entries.toList())
        assertNotEquals(terminalFontFamily(TerminalFontChoice.Bundled),
            terminalFontFamily(TerminalFontChoice.Monospace))
        assertEquals("JetBrains Mono", TerminalFontChoice.Bundled.label)
    }

    @Test fun luminanceSpansBlackToWhite() {
        assertEquals(0f, luminance(0xFF000000.toInt()), 0.0001f)
        assertEquals(1f, luminance(0xFFFFFFFF.toInt()), 0.0001f)
        assertTrue(luminance(0xFF808080.toInt()) in 0.14f..0.16f)
    }
}
