package com.sulthonuladib.keroid.term

import android.os.SystemClock
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput

/**
 * Establish a real input session before hiding the IME. Initial focus can queue
 * a show after the first terminal measurement; hiding before that show has run
 * is advisory and leaves a delayed IME resize in the next assertion's baseline.
 */
internal fun ComposeTestRule.hideTerminalKeyboard(grid: SemanticsNodeInteraction) {
    grid.performTouchInput { click() }
    waitForTerminalKeyboard(visible = true, grid)
    onNodeWithContentDescription("Hide keyboard").performClick()
    waitForTerminalKeyboard(visible = false, grid)
}

/** Visibility and quiet bounds both matter: a floating IME has no bottom inset. */
internal fun ComposeTestRule.waitForTerminalKeyboard(visible: Boolean, grid: SemanticsNodeInteraction) {
    val description = if (visible) "Hide keyboard" else "Show keyboard"
    var bounds = grid.fetchSemanticsNode().boundsInRoot
    var stableSince = SystemClock.uptimeMillis()
    waitUntil(15000) {
        val next = grid.fetchSemanticsNode().boundsInRoot
        val now = SystemClock.uptimeMillis()
        if (next != bounds || onAllNodesWithContentDescription(description).fetchSemanticsNodes().isEmpty()) {
            bounds = next
            stableSince = now
        }
        now - stableSince >= 650
    }
}
