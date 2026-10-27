package com.sulthonuladib.keroid.data

import java.net.URI
import java.net.URISyntaxException

/**
 * A machine's Tailscale address, matched to the Go client and desktop Kero's
 * rules: kerod's own listener is plain `http://` (port 7373 by default) and
 * Tailscale Serve is `https://` (port 443 by default). No user, password, path,
 * query or fragment: Keroid sends no credentials of its own.
 */
object TailnetAddress {
    const val DEFAULT_HTTP_PORT = 7373
    const val DEFAULT_HTTPS_PORT = 443

    data class Parsed(val secure: Boolean, val host: String, val port: Int) {
        val scheme: String get() = if (secure) "https" else "http"
        /** Explicit port, so what is saved is exactly what connects; IPv6 stays bracketed. */
        val normalized: String
            get() = "$scheme://${if (host.contains(':')) "[$host]" else host}:$port"
        val isDefaultPort: Boolean get() = port == if (secure) DEFAULT_HTTPS_PORT else DEFAULT_HTTP_PORT
    }

    /**
     * Reads what a host's app or `kero daemon status` shows. A bare host is
     * plain http on the listener's default port, which is what most tailnets use.
     */
    fun parse(input: String): Parsed {
        val raw = input.trim()
        require(raw.isNotEmpty()) { "Enter the host's Tailscale address, such as http://devbox:7373." }
        require(raw.none { it <= ' ' || it == '\u007f' }) { "A Tailscale address has no spaces." }
        val withScheme = if ("://" in raw) raw else "http://$raw"
        val uri = try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            throw IllegalArgumentException("Enter a Tailscale address such as http://devbox:7373.")
        }
        val secure = when (uri.scheme?.lowercase()) {
            "http" -> false
            "https" -> true
            else -> throw IllegalArgumentException(
                "Use http:// for kerod's tailnet listener or https:// for Tailscale Serve.")
        }
        require(uri.userInfo == null) { "A Tailscale address takes no user or password." }
        require(uri.rawQuery == null && uri.rawFragment == null) {
            "A Tailscale address has no query or fragment."
        }
        require(uri.path.orEmpty().let { it.isEmpty() || it == "/" }) { "A Tailscale address has no path." }
        val host = uri.host.orEmpty().removeSurrounding("[", "]")
        require(host.isNotBlank()) { "Enter a Tailscale host, such as devbox or devbox.tail1234.ts.net." }
        require(!host.startsWith("-") && host.none { it == '/' || it == '\\' || it == '@' }) {
            "A host is a name of letters, digits, dots and dashes, or an IP address."
        }
        val port = if (uri.port != -1) uri.port
        else if (secure) DEFAULT_HTTPS_PORT else DEFAULT_HTTP_PORT
        require(port in 1..65535) { "Port must be between 1 and 65535." }
        return Parsed(secure, host, port)
    }
}
