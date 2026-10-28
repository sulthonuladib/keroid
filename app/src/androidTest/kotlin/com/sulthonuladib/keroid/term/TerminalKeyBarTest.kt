package com.sulthonuladib.keroid.term

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.sulthonuladib.keroid.ui.theme.KeroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TerminalKeyBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pinnedControlAndEditingShortcutsUseModeAwareKeyStrokes() {
        val ctrl = androidx.compose.runtime.mutableStateOf(false)
        val received = mutableListOf<TerminalKeyStroke>()
        compose.setContent { KeroidTheme(dynamicColor = false) {
            Box(Modifier.width(360.dp)) {
                TerminalKeyBar(true, {}, ctrl.value, { ctrl.value = it }, {}, onKey = { received += it })
            }
        } }
        compose.onNodeWithContentDescription("Control").performClick()
        compose.onNodeWithContentDescription("Tab").performClick()
        compose.runOnIdle { assertEquals(false, ctrl.value) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(14)
        compose.onNodeWithContentDescription("Control R").performClick()
        compose.runOnIdle {
            val presses = received.filter { it.action != 0 }
            assertEquals(listOf(TerminalKey.Tab.code, android.view.KeyEvent.KEYCODE_R), presses.map { it.code })
            assertEquals(listOf(TerminalKeyStroke.CTRL, TerminalKeyStroke.CTRL), presses.map { it.mods })
            assertEquals("r", presses.last().text)
            assertEquals(2, received.count { it.action == 0 })
        }
    }

    @Test fun essentialKeysStayPinnedWhenNavigationScrolls() {
        val received = mutableListOf<TerminalKeyStroke>()
        compose.setContent { KeroidTheme(dynamicColor = false) {
            Box(Modifier.width(360.dp)) {
                TerminalKeyBar(true, {}, false, {}, {}, onKey = { received += it })
            }
        } }
        val names = listOf("Escape", "Control", "Tab")
        val before = names.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(10)
        names.forEachIndexed { index, name ->
            compose.onNodeWithContentDescription(name).assertIsDisplayed()
            assertEquals("$name must not scroll", before[index],
                compose.onNodeWithContentDescription(name).fetchSemanticsNode().boundsInRoot)
        }
        compose.onNodeWithContentDescription("Escape").performClick()
        compose.onNodeWithContentDescription("Tab").performClick()
        compose.runOnIdle {
            assertEquals(listOf(TerminalKey.Escape.code, TerminalKey.Tab.code),
                received.filter { it.action != 0 }.map { it.code })
        }
    }
}
