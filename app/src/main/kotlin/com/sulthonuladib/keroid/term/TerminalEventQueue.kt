package com.sulthonuladib.keroid.term

import java.io.ByteArrayOutputStream
import java.io.Closeable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Wire order is VT order. Only adjacent output may be combined. */
internal class TerminalEventQueue(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val write: (ByteArray) -> Unit,
    private val applyResize: (Int, Int) -> Unit,
    private val changed: () -> Unit,
) : Closeable {
    private sealed interface Event {
        data class Output(val bytes: ByteArray) : Event
        data class Resize(val cols: Int, val rows: Int) : Event
        data class Viewport(val apply: () -> Unit) : Event
    }
    private val inbox = Channel<Event>(Channel.UNLIMITED)
    private val writer = scope.launch(dispatcher) {
        var pending: Event? = null
        while (true) {
            val first = pending ?: inbox.receiveCatching().getOrNull() ?: break
            pending = null
            when (first) {
                is Event.Resize -> applyResize(first.cols, first.rows)
                is Event.Viewport -> first.apply()
                is Event.Output -> {
                    val batch = ByteArrayOutputStream(first.bytes.size)
                    batch.write(first.bytes)
                    while (true) {
                        val next = inbox.tryReceive().getOrNull() ?: break
                        if (next !is Event.Output) {
                            pending = next
                            break
                        }
                        batch.write(next.bytes)
                    }
                    write(batch.toByteArray())
                }
            }
            changed()
        }
    }

    fun output(bytes: ByteArray) {
        if (bytes.isNotEmpty()) inbox.trySend(Event.Output(bytes.copyOf()))
    }

    fun resize(cols: Int, rows: Int) {
        if (cols > 0 && rows > 0) inbox.trySend(Event.Resize(cols, rows))
    }

    fun viewport(apply: () -> Unit) { inbox.trySend(Event.Viewport(apply)) }

    override fun close() {
        inbox.cancel()
        writer.cancel()
    }
}
