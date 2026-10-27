package com.sulthonuladib.keroid.term

import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TerminalInputTest {
    @get:Rule val compose = createComposeRule()

    @Test fun encodedKeysAndImeTextShareOneOrderedDeliveryQueue() {
        lateinit var session: TerminalSession
        val received = java.io.ByteArrayOutputStream()
        compose.setContent {
            val scope = rememberCoroutineScope()
            val current = remember { TerminalSession(20, 3, scope) }
            SideEffect { session = current }
            DisposableEffect(current) { onDispose { current.close() } }
        }
        compose.runOnIdle {
            session.offer("\u001b[?1h".toByteArray())
            session.key(TerminalKeyStroke(TerminalKey.Up.code)) { received.write(it) }
            session.input("fast text".toByteArray()) { received.write(it) }
            session.key(TerminalKeyStroke(TerminalKey.Enter.code)) { received.write(it) }
            session.input("next".toByteArray()) { received.write(it) }
        }
        compose.waitUntil(5000) { received.toString("UTF-8") == "\u001bOAfast text\rnext" }
    }

    @Test fun actualInputConnectionPreservesCompositionAndFastCommits() {
        lateinit var view: View
        val bytes = java.io.ByteArrayOutputStream()
        compose.setContent {
            val local = LocalView.current
            SideEffect { view = local }
            Box(Modifier.fillMaxSize()) { TerminalInputLayer(false, { bytes.write(it) }, {}, Modifier.fillMaxSize()) }
        }
        compose.onNodeWithTag("terminal-ime").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            val connection = view.onCreateInputConnection(EditorInfo())
            assertNotNull("terminal must expose an active IME connection", connection)
            connection!!.beginBatchEdit()
            connection.setComposingText("h", 1)
            connection.setComposingText("he", 1)
            connection.setComposingText("hello", 1)
            connection.commitText("hello", 1)
            connection.endBatchEdit()
        }
        compose.runOnIdle { assertEquals("hello", bytes.toString("UTF-8")) }
        compose.runOnIdle {
            val connection = view.onCreateInputConnection(EditorInfo())!!
            for (part in listOf(" ", "fast", " ", "typing", "!")) connection.commitText(part, 1)
        }
        compose.runOnIdle { assertEquals("hello fast typing!", bytes.toString("UTF-8")) }
        compose.runOnIdle { view.onCreateInputConnection(EditorInfo())!!.deleteSurroundingText(1, 0) }
        compose.runOnIdle { assertEquals("hello fast typing!\u007f", bytes.toString("UTF-8")) }
    }
}
