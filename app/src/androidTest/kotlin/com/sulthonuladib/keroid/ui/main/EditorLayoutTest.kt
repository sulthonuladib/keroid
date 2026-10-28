package com.sulthonuladib.keroid.ui.main

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.sulthonuladib.keroid.ui.theme.KeroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Saving must not insert/remove a row and make the editor jump before navigation. */
class EditorLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overlappingSecretOwnersKeepWindowProtectedUntilBothLeave() {
        val first = mutableStateOf(true)
        val second = mutableStateOf(true)
        lateinit var window: android.view.Window
        var originalSecure = false
        val flag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        compose.setContent {
            var owner = androidx.compose.ui.platform.LocalContext.current
            while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
            val currentWindow = (owner as android.app.Activity).window
            val initial = androidx.compose.runtime.remember(currentWindow) { currentWindow.attributes.flags and flag != 0 }
            androidx.compose.runtime.SideEffect { window = currentWindow; originalSecure = initial }
            androidx.compose.runtime.key("first") { if (first.value) ProtectSecrets(true) }
            androidx.compose.runtime.key("second") { if (second.value) ProtectSecrets(true) }
        }
        compose.runOnIdle {
            org.junit.Assert.assertTrue(window.attributes.flags and flag != 0)
            first.value = false
        }
        compose.runOnIdle {
            org.junit.Assert.assertTrue("One owner cannot clear another owner's protection", window.attributes.flags and flag != 0)
            second.value = false
        }
        compose.runOnIdle { assertEquals(originalSecure, window.attributes.flags and flag != 0) }
    }

    @Test fun machineFieldsStayInPlaceWhileSaving() {
        val saving = mutableStateOf(false)
        compose.setContent {
            KeroidTheme(dynamicColor = false) {
                MachineEditorScreen(MainUiState(restoring = false, saving = saving.value), null,
                    {}, {}, {}, {}, {})
            }
        }
        val before = compose.onNodeWithText("Machine name").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { saving.value = true }
        assertEquals("Saving must not move machine fields", before,
            compose.onNodeWithText("Machine name").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { saving.value = false }
        assertEquals(before, compose.onNodeWithText("Machine name").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun identityFieldsStayInPlaceWhileSaving() {
        val saving = mutableStateOf(false)
        compose.setContent {
            KeroidTheme(dynamicColor = false) {
                IdentityEditorScreen(null, "Fixture", "", saving.value, null, {}, {}, {}, {}, {}, {})
            }
        }
        val before = compose.onNodeWithText("Identity name").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { saving.value = true }
        assertEquals("Saving must not move identity fields", before,
            compose.onNodeWithText("Identity name").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { saving.value = false }
        assertEquals(before, compose.onNodeWithText("Identity name").fetchSemanticsNode().boundsInRoot)
    }
}
