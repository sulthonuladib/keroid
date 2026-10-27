package com.sulthonuladib.keroid.term

import org.junit.Assert.*
import org.junit.Test

class TerminalSizingTest {
    private val phone = TerminalGeometry(40, 20, 9, 18)
    private val landscape = TerminalGeometry(80, 10, 9, 18)

    @Test fun measurementBeforeAttachmentIsSentBeforeClaim() {
        val sizing = TerminalSizing()
        val commands = mutableListOf<TerminalSizing.Command>()
        sizing.request(phone)
        sizing.setActive(true)
        sizing.attach { commands += it }
        assertEquals(TerminalSizing.Command.Resize(phone), commands[0])
        assertTrue(commands[1] is TerminalSizing.Command.Claim)
        assertEquals(2, commands.size)
    }

    @Test fun attachmentBeforeMeasurementDefersClaimUntilGeometryExists() {
        val sizing = TerminalSizing()
        val commands = mutableListOf<TerminalSizing.Command>()
        sizing.setActive(true)
        sizing.attach { commands += it }
        assertTrue(commands.isEmpty())
        sizing.request(phone)
        assertEquals(TerminalSizing.Command.Resize(phone), commands[0])
        assertTrue(commands[1] is TerminalSizing.Command.Claim)
    }

    @Test fun reconnectReplaysLatestGeometryAndClaimsIfStillActive() {
        val sizing = TerminalSizing()
        val first = mutableListOf<TerminalSizing.Command>()
        val second = mutableListOf<TerminalSizing.Command>()
        sizing.request(phone)
        sizing.setActive(true)
        sizing.attach { first += it }
        sizing.detach()
        sizing.request(landscape)
        sizing.attach { second += it }
        assertEquals(TerminalSizing.Command.Resize(landscape), second[0])
        assertTrue(second[1] is TerminalSizing.Command.Claim)
        assertEquals(2, second.size)
    }

    @Test fun claimsOncePerActivationNeverForLayoutOrRecomposition() {
        val sizing = TerminalSizing()
        val commands = mutableListOf<TerminalSizing.Command>()
        sizing.request(phone)
        sizing.attach { commands += it }
        assertEquals(listOf(TerminalSizing.Command.Resize(phone)), commands)
        sizing.setActive(true)
        repeat(5) { sizing.setActive(true); sizing.request(phone) }
        sizing.request(landscape) // Rotation / IME / extra keys only request resize.
        assertEquals(1, commands.count { it is TerminalSizing.Command.Claim })
        val oldClaim = commands.filterIsInstance<TerminalSizing.Command.Claim>().single()
        sizing.setActive(false)
        assertFalse(sizing.mayClaim(oldClaim))
        sizing.request(phone)
        sizing.detach()
        sizing.attach { commands += it }
        assertEquals(1, commands.count { it is TerminalSizing.Command.Claim })
        sizing.setActive(true)
        assertEquals(2, commands.count { it is TerminalSizing.Command.Claim })
        assertFalse(sizing.mayClaim(oldClaim)) // Old queued claim cannot fire on resume.
        assertTrue(sizing.mayClaim(commands.last() as TerminalSizing.Command.Claim))
    }

    @Test fun pausingDuringAttachDoesNotLeaveAPendingClaim() {
        val sizing = TerminalSizing()
        val commands = mutableListOf<TerminalSizing.Command>()
        sizing.request(phone)
        sizing.setActive(true)
        sizing.setActive(false)
        sizing.attach { commands += it }
        assertEquals(listOf(TerminalSizing.Command.Resize(phone)), commands)
    }
}
