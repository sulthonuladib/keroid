package com.sulthonuladib.keroid.data

import org.junit.Assert.*
import kotlinx.serialization.encodeToString
import org.junit.Test

/** Pure parsing, matched to the Go client and desktop Kero's address rules. */
class TailnetAddressTest {
    @Test fun bareHostIsPlainHttpOnKerosListenerPort() {
        val parsed = TailnetAddress.parse("devbox")
        assertEquals("http", parsed.scheme)
        assertEquals(7373, parsed.port)
        assertEquals("http://devbox:7373", parsed.normalized)
    }

    @Test fun listenerAndServeAddressesKeepTheirOwnDefaults() {
        assertEquals("http://devbox:7373", TailnetAddress.parse("http://devbox").normalized)
        assertEquals("http://devbox:8080", TailnetAddress.parse("http://devbox:8080").normalized)
        assertEquals("https://devbox.tail1234.ts.net:443",
            TailnetAddress.parse("https://devbox.tail1234.ts.net").normalized)
        assertEquals("https://devbox.tail1234.ts.net:8443",
            TailnetAddress.parse("https://devbox.tail1234.ts.net:8443").normalized)
        assertEquals("http://100.101.102.103:7373", TailnetAddress.parse("100.101.102.103").normalized)
    }

    @Test fun ipv6StaysBracketedWhenSaved() {
        val parsed = TailnetAddress.parse("http://[fd7a:115c:a1e0::1]:7373")
        assertEquals("fd7a:115c:a1e0::1", parsed.host)
        assertEquals("http://[fd7a:115c:a1e0::1]:7373", parsed.normalized)
    }

    @Test fun trailingSlashIsNotAPath() {
        assertEquals("http://devbox:7373", TailnetAddress.parse("http://devbox:7373/").normalized)
    }

    @Test fun rejectsWhatDesktopRejects() {
        listOf(
            "",
            "   ",
            "dev box",
            "ssh://devbox",
            "ftp://devbox",
            "http://alice:pw@devbox:7373",
            "http://alice@devbox:7373",
            "http://devbox:7373/path",
            "http://devbox:7373?x=1",
            "http://devbox:7373#fragment",
            "http://devbox:0",
            "http://devbox:70000",
            "http://devbox:abc",
            "http://-devbox:7373",
            "http://",
        ).forEach { input ->
            assertTrue("unexpectedly accepted '$input'",
                runCatching { TailnetAddress.parse(input) }.isFailure)
        }
    }

    @Test fun draftKeepsTailscaleMachinesFreeOfIdentityAndSshOnesRequireIt() {
        val tailscale = ConnectionDraft(name = "Dev", transport = Transport.Tailscale,
            endpoint = "https://devbox.tail1234.ts.net")
        val machine = tailscale.machine("m1")
        assertTrue(machine.isTailscale)
        assertEquals("", machine.identityId)
        assertEquals("https://devbox.tail1234.ts.net:443", machine.endpoint)
        assertEquals(machine.endpoint, machine.address)
        assertEquals(Transport.Tailscale, machine.profile().transport)

        val ssh = ConnectionDraft(name = "Dev", address = "alice@devbox")
        assertTrue("SSH still needs an identity", runCatching { ssh.machine("m2") }.isFailure)
        assertEquals(Transport.Ssh, ssh.profile().transport)
        assertEquals("alice@devbox", ssh.profile().address)
    }

    @Test fun draftStillValidatesTailscaleAddresses() {
        listOf("", "dev box", "ssh://devbox", "http://devbox/path").forEach { endpoint ->
            assertTrue("unexpectedly accepted '$endpoint'",
                runCatching {
                    ConnectionDraft(name = "Dev", transport = Transport.Tailscale, endpoint = endpoint).profile()
                }.isFailure)
        }
    }

    @Test fun wireJsonNamesTheTransportForTheGoClient() {
        val tailscale = wireJson.encodeToString(ConnectionDraft(
            name = "Dev", transport = Transport.Tailscale, endpoint = "devbox").profile())
        assertTrue(tailscale.contains("\"transport\":\"tailscale\""))
        assertTrue(tailscale.contains("\"endpoint\":\"http://devbox:7373\""))

        val ssh = wireJson.encodeToString(ConnectionDraft(name = "Dev", address = "alice@devbox").profile())
        assertTrue(ssh.contains("\"transport\":\"ssh\""))
        assertTrue(ssh.contains("\"user\":\"alice\""))
    }

    @Test fun savedMachinesWithoutATransportStillConnectOverSsh() {
        // A record written before Tailscale support must load unchanged.
        val legacy = """{"id":"m","name":"Dev","host":"devbox","user":"alice","identityId":"i"}"""
        val machine = wireJson.decodeFromString<Machine>(legacy)
        assertEquals(Transport.Ssh, machine.transport)
        assertEquals("", machine.endpoint)
        assertFalse(machine.isTailscale)
        assertEquals("alice@devbox", machine.address)
    }

    @Test fun savedTailscaleMachineRoundTrips() {
        val machine = Machine("m", "Dev", host = "", user = "", identityId = "",
            transport = Transport.Tailscale, endpoint = "http://devbox:7373")
        val restored = wireJson.decodeFromString<Machine>(wireJson.encodeToString(machine))
        assertEquals(machine, restored)
        assertTrue(restored.isTailscale)
    }
}
