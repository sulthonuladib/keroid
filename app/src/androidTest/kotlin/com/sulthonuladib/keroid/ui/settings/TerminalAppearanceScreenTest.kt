package com.sulthonuladib.keroid.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.sulthonuladib.keroid.data.ThemeMode
import com.sulthonuladib.keroid.term.TerminalAppearance
import com.sulthonuladib.keroid.term.TerminalFontChoice
import com.sulthonuladib.keroid.ui.theme.KeroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Terminal appearance is app-independent and never connects anywhere: these
 * tests only draw Compose and the offline Ghostty theme catalogue.
 */
class TerminalAppearanceScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsLeadsToTerminalAppearanceWithoutChangingTheAppTheme() {
        var opened = false
        compose.setContent {
            KeroidTheme(dynamicColor = false) {
                SettingsScreen(ThemeMode.Dark, onTheme = {}, onBack = {}, onTerminalTest = null,
                    onTerminalAppearance = { opened = true })
            }
        }
        compose.onNodeWithText("Terminal appearance").assertExists()
        compose.onNodeWithText("Terminal appearance").performClick()
        compose.runOnIdle { assertTrue("settings opens terminal appearance", opened) }
    }

    @Test fun previewAndSteppersChangeOnlyTheTerminalAppearance() {
        var appearance by mutableStateOf(TerminalAppearance())
        compose.setContent {
            KeroidTheme(dynamicColor = false) {
                TerminalAppearanceScreen(appearance, dark = true, onChange = { appearance = it },
                    onPickTheme = {}, onBack = {})
            }
        }
        compose.onNodeWithContentDescription("Terminal theme preview").assertExists()
        compose.onNodeWithText("Dark theme").assertExists()
        compose.onNodeWithText("Light theme").assertExists()

        val size = appearance.fontSize
        compose.onNodeWithContentDescription("Increase Font size").performClick()
        compose.runOnIdle { assertEquals(size + 1f, appearance.fontSize, 0.001f) }
        compose.onNodeWithContentDescription("Decrease Font size").performClick()
        compose.runOnIdle { assertEquals(size, appearance.fontSize, 0.001f) }

        val height = appearance.lineHeight
        compose.onNodeWithContentDescription("Increase Line height").performClick()
        compose.runOnIdle { assertEquals(height + 0.05f, appearance.lineHeight, 0.001f) }
        compose.runOnIdle { assertEquals(TerminalFontChoice.Bundled, appearance.font) }
        compose.onNodeWithText("System monospace").performClick()
        compose.runOnIdle { assertEquals(TerminalFontChoice.Monospace, appearance.font) }
    }

    @Test fun themePickerSearchesTheGhosttyCatalogueAndReportsTheChoice() {
        var picked: String? = null
        var closed = false
        compose.setContent {
            KeroidTheme(dynamicColor = false) {
                TerminalThemePickerScreen(dark = true, selected = TerminalAppearance.KEROID,
                    onSelect = { picked = it }, onBack = { closed = true })
            }
        }
        compose.onNodeWithText("Dark terminal theme").assertExists()
        compose.onNodeWithContentDescription("Keroid theme").assertExists()
        compose.onNodeWithText("Search ${TerminalAppearance.themeNames.size} themes").assertExists()

        compose.onNodeWithText("Search ${TerminalAppearance.themeNames.size} themes")
            .performTextInput("dracula")
        compose.waitForIdle()
        // The filter is case-insensitive and keeps only real Ghostty themes.
        compose.onNodeWithContentDescription("Dracula theme").assertExists()
        compose.onAllNodesWithContentDescription("Keroid theme").assertCountEquals(0)
        compose.onNodeWithContentDescription("Dracula theme").performClick()
        compose.runOnIdle {
            assertEquals("Dracula", picked)
            assertTrue("picking closes the picker", closed)
        }
    }

    @Test fun pickerWithNoMatchesSaysSoInsteadOfShowingEverything() {
        compose.setContent {
            KeroidTheme(dynamicColor = false) {
                TerminalThemePickerScreen(dark = false, selected = TerminalAppearance.KEROID,
                    onSelect = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Search ${TerminalAppearance.themeNames.size} themes")
            .performTextInput("zzzz no such theme")
        compose.waitForIdle()
        compose.onNodeWithText("No theme matches “zzzz no such theme”.", substring = true).assertExists()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Keroid theme").assertExists()
    }
}
