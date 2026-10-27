package com.sulthonuladib.keroid.data

import org.junit.Assert.*
import org.junit.Test

class ConnectionDraftTest {
    @Test fun parsesStandardSshAddress() {
        val profile = ConnectionDraft("Dev", "alice@devbox").profile()
        assertEquals("alice", profile.user)
        assertEquals("devbox", profile.host)
        assertEquals(22, profile.port)
    }
    @Test fun parsesExplicitPortAndIpv6() {
        val profile = ConnectionDraft("Dev", "ssh://alice@[::1]:2222").profile()
        assertEquals("::1", profile.host)
        assertEquals(2222, profile.port)
    }
    @Test fun usesSeparatePort() {
        assertEquals(2200, ConnectionDraft("Dev", "alice@devbox", "2200").profile().port)
    }
    @Test fun rejectsMissingUsernamePasswordsPathsAndInvalidPorts() {
        listOf(
            ConnectionDraft("Dev", "devbox"),
            ConnectionDraft("Dev", "alice:password@devbox"),
            ConnectionDraft("Dev", "alice@devbox/path"),
            ConnectionDraft("Dev", "alice@devbox", "0"),
            ConnectionDraft("Dev", "alice@devbox", "65536"),
            ConnectionDraft("", "alice@devbox"),
        ).forEach { draft -> assertTrue("Unexpectedly accepted $draft", runCatching { draft.profile() }.isFailure) }
    }
    @Test fun acceptsUnknownFieldsAndNullCollectionsFromDaemon() {
        val state = wireJson.decodeFromString<KeroState>("""{"projects":null,"sessions":[],"futureField":true}""")
        assertTrue(state.projects.isEmpty())
    }
    @Test fun statusesIdentifyAttention() {
        assertTrue(KeroSession("1","p",status="waiting").needsAttention)
        assertTrue(KeroSession("1","p",status="error").needsAttention)
        assertFalse(KeroSession("1","p",status="working").needsAttention)
    }
}
