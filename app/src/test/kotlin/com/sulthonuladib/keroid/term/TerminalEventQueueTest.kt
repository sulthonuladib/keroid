package com.sulthonuladib.keroid.term

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalEventQueueTest {
    @Test fun outputBatchesNeverCrossAConfirmedResize() = runTest {
        val calls = mutableListOf<String>()
        val queue = TerminalEventQueue(this, StandardTestDispatcher(testScheduler),
            write = { calls += "output:${it.decodeToString()}" },
            applyResize = { cols, rows -> calls += "resize:$cols,$rows" },
            changed = {})
        queue.resize(80, 24) // Welcome precedes snapshot.
        queue.output("old1".toByteArray())
        queue.output("old2".toByteArray())
        queue.resize(40, 12)
        queue.output("new1".toByteArray())
        queue.output("new2".toByteArray())
        queue.resize(100, 30)
        runCurrent()
        assertEquals(listOf("resize:80,24", "output:old1old2", "resize:40,12",
            "output:new1new2", "resize:100,30"), calls)
        queue.close()
    }

    @Test fun closeDropsPendingWorkAndInvalidSizesAreIgnored() = runTest {
        var calls = 0
        val queue = TerminalEventQueue(this, StandardTestDispatcher(testScheduler),
            write = { calls++ }, applyResize = { _, _ -> calls++ }, changed = {})
        queue.resize(0, 24)
        queue.output(byteArrayOf())
        runCurrent()
        assertEquals(0, calls)
        queue.output(byteArrayOf(1))
        queue.close()
        runCurrent()
        assertEquals(0, calls)
    }
}
