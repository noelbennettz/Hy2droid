package us.leaf3stones.hy2droid.data

/**
 * A single Hysteria 2 connection profile. This is the pure, framework-free model
 * shared across the data layer; it carries no Android types so it can be exercised
 * from plain JVM unit tests.
 *
 * @property id stable unique identifier (a v4 UUID).
 * @property name user-facing display name.
 * @property server `host:port` of the Hysteria 2 server.
 * @property auth authentication secret sent during the HTTP/3 handshake.
 * @property sni TLS Server Name Indication; blank means "derive from [server]".
 * @property obfsPassword Salamander obfuscation password; blank means no obfs.
 * @property insecure whether to skip TLS certificate verification (dangerous).
 * @property brutal whether Brutal congestion control is used at all (otherwise BBR).
 * @property txMbps uplink bandwidth in Mbps for Brutal congestion control (0 = unset).
 * @property rxMbps downlink bandwidth in Mbps for Brutal congestion control (0 = unset).
 * @property fastOpen whether to enable QUIC fast open (accept each connection
 *   without waiting for the server to confirm the destination: one less round
 *   trip per connection, which is what makes browsing feel fast).
 */
data class Profile(
    val id: String,
    val name: String,
    val server: String,
    val auth: String,
    val sni: String = "",
    val obfsPassword: String = "",
    val insecure: Boolean = false,
    val brutal: Boolean = false,
    val txMbps: Int = 0,
    val rxMbps: Int = 0,
    val fastOpen: Boolean = true,
    val port: Int = 443,
) {
    /** True when Salamander obfuscation is configured. */
    val hasObfs: Boolean get() = obfsPassword.isNotBlank()

    /** True when both rates a Brutal profile needs are present. */
    val hasBandwidth: Boolean get() = txMbps > 0 || rxMbps > 0

    /**
     * True when the core should be told to pace at the configured rates.
     *
     * Brutal is a fixed-rate algorithm: it does not probe capacity and does not
     * back off on loss, so a rate above what the path can carry turns into loss
     * and retransmissions. Keeping the switch explicit means a profile cannot
     * end up on Brutal just because somebody typed a number once.
     */
    val usesBrutal: Boolean get() = brutal && hasBandwidth

    /**
     * `host:port` authority, tolerant of a [server] that already embeds a port.
     *
     * IPv6 literals are the reason this is not a `contains(':')` test: a bare
     * literal contains colons but no port, so it has to be bracketed
     * (`[2001:db8::1]:8443`) before Hysteria — or a SOCKS/URL parser — can read
     * the port. A bracketed literal may already carry its port and is left alone.
     */
    val authority: String
        get() {
            val host = server.trim()
            if (host.isEmpty()) return host
            if (host.startsWith("[")) {
                // Bracketed literal, with or without a trailing `:port`.
                val close = host.indexOf(']')
                val hasPort = close >= 0 && host.getOrNull(close + 1) == ':'
                return if (hasPort) host else "$host:$port"
            }
            return when (host.count { it == ':' }) {
                // More than one colon can only be an unbracketed IPv6 literal.
                in 2..Int.MAX_VALUE -> "[$host]:$port"
                // Exactly one colon is `host:port`, already complete.
                1 -> host
                else -> "$host:$port"
            }
        }

    companion object {
        /**
         * Canonical form of a [server] value: trimmed, and brackets around a
         * *bare* IPv6 literal are dropped (`[2001:db8::1]` → `2001:db8::1`).
         *
         * Brackets belong to the `host:port` rendering ([authority]) and to share
         * links, not to the stored host. Links are parsed through this so a
         * pasted IPv6 node is stored the same way one typed by hand is.
         * `[addr]:port` is kept verbatim: that is a complete authority, and
         * guessing which half is the host would silently move the port.
         */
        fun normalizeServer(raw: String): String {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("[")) return trimmed
            val close = trimmed.indexOf(']')
            if (close < 0 || close == trimmed.lastIndex) return trimmed.removeSurrounding("[", "]")
            return if (trimmed.getOrNull(close + 1) == ':') trimmed else trimmed.removeSurrounding("[", "]")
        }
    }
}
