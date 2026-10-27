package com.sulthonuladib.keroid.term

import androidx.compose.runtime.mutableIntStateOf
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One pane's mirror. Output and confirmed sizes enter the same ordered queue. */
class TerminalSession(cols: Int, rows: Int, scope: CoroutineScope) : Closeable {
    val terminal = GhosttyTerminal(cols, rows)
    private val framesState = mutableIntStateOf(0)
    val frames: Int get() = framesState.intValue
    private val deliveries = Channel<() -> Unit>(Channel.UNLIMITED)
    private val inputWriter = scope.launch(Dispatchers.Main.immediate) {
        for (deliver in deliveries) deliver()
    }

    private val events = TerminalEventQueue(
        scope = scope,
        write = terminal::write,
        applyResize = { columns, lines -> terminal.resize(columns, lines) },
        changed = { framesState.intValue++ },
    )

    fun offer(bytes: ByteArray) = events.output(bytes)

    /** Welcome / FrameResized only; never a local measurement. */
    fun adoptSize(cols: Int, rows: Int) = events.resize(cols, rows)

    /** All input follows one queue, including async key encoding, so an IME
     * commit cannot overtake an arrow/Enter waiting for mirrored key modes. */
    fun input(bytes: ByteArray, send: (ByteArray) -> Unit) {
        val owned = bytes.copyOf()
        events.viewport {
            terminal.scroll(0, bottom = true)
            terminal.select(-1, -1, false)
            deliveries.trySend { send(owned) }
        }
    }

    fun key(stroke: TerminalKeyStroke, send: (ByteArray) -> Unit) = events.viewport {
        val encoded = terminal.encode(stroke)
        if (encoded.isNotEmpty()) {
            terminal.scroll(0, bottom = true)
            terminal.select(-1, -1, false)
            deliveries.trySend { send(encoded) }
        }
    }

    /** Local history navigation, never a PTY resize or remote input. */
    fun scroll(rows: Int) = events.viewport { terminal.scroll(rows) }    fun scrollToBottom() = events.viewport { terminal.scroll(0, bottom = true) }
    fun select(x: Int, y: Int, start: Boolean = false) = events.viewport { terminal.select(x, y, start) }
    fun clearSelection() = select(-1, -1)
    fun copySelection(result: (String) -> Unit) = events.viewport { result(terminal.selectedText()) }

    /** Redraw after a change the daemon did not send, such as a theme. */
    fun invalidate() { framesState.intValue++ }

    override fun close() {
        events.close()
        deliveries.cancel()
        inputWriter.cancel()
        terminal.close()
    }
}
