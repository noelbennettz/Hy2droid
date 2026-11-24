package us.leaf3stones.hy2droid.data

import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/**
 * Parses and emits `hysteria2://` share links.
 *
 * Link shape:
 * `hysteria2://<auth>@<host>:<port>/?sni=<sni>&obfs=salamander&obfs-password=<pw>&insecure=1#<name>`
 *
 * Only what the app can actually honour is accepted: Salamander obfuscation,
 * `sni`, `insecure` and (from third-party links) the bandwidth hints. A link
 * that asks for another obfs type, certificate pinning (`pinSHA256`) or ECH is
 * rejected instead of imported with that part silently dropped.
 *
 * Pure Kotlin/`java.net` only: unit-testable on the JVM.
 */
object HysteriaUri {
    private const val SCHEME = "hysteria2"

    /** `hy2://` is the short alias other clients emit for the same link. */
    private val SCHEMES = setOf("hysteria2", "hy2")
    private const val DEFAULT_PORT = 443

    /** The only obfuscation the bundled core is configured for. */
    private const val SALAMANDER = "salamander"

    /** Parse a share link into a [Profile], or `null` if it is not a valid hysteria2 link. */
    fun parse(link: String): Profile? {
        val uri = try {
            URI(link.trim())
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme == null || uri.scheme.lowercase() !in SCHEMES) return null
        // `URI.host` keeps the brackets of an IPv6 literal; the stored server is
        // the bare address, and `Profile.authority` adds the brackets back.
        val host = Profile.normalizeServer(uri.host ?: return null)
        val port = if (uri.port > 0) uri.port else DEFAULT_PORT
        // `URI.userInfo` and `URI.fragment` are already percent-decoded; decoding
        // them again would turn a literal `+` into a space and `%41` into `A`.
        val auth = uri.userInfo.orEmpty()

        val params = parseQuery(uri.rawQuery)
        val obfsType = params["obfs"]?.trim()?.lowercase().orEmpty()
        if (obfsType.isNotEmpty() && obfsType != SALAMANDER) return null
        // Dropping these would either break the connection (ECH) or weaken it
        // compared with what the link promises (pinning); the editor can still
        // be used to build a profile by hand if the server allows that.
        if (!params["pinsha256"].isNullOrBlank()) return null
        if (!params["ech"].isNullOrBlank()) return null
        val sni = params["sni"].orEmpty()
        val obfsPassword = params["obfs-password"].orEmpty()
        val insecure = params["insecure"].let { it == "1" || it.equals("true", ignoreCase = true) }
        // Bandwidth is not part of the official scheme (it says client-specific
        // values must not be shared), but other clients put it in anyway. Accept
        // both the short and the legacy names, and only positive values.
        val tx = bandwidth(params["up"] ?: params["upmbps"])
        val rx = bandwidth(params["down"] ?: params["downmbps"])
        val name = uri.fragment.orEmpty().ifBlank { host }

        return Profile(
            id = UUID.randomUUID().toString(),
            name = name,
            server = host,
            port = port,
            auth = auth,
            sni = sni,
            obfsPassword = obfsPassword,
            insecure = insecure,
            brutal = tx != null || rx != null,
            txMbps = tx ?: 0,
            rxMbps = rx ?: 0,
        )
    }

    /**
     * The first share link inside [text], or `null`.
     *
     * Clipboard content often holds more than the link itself: a chat message, a
     * quoted paragraph, a trailing line break. A share link cannot contain a raw
     * space, so splitting on whitespace is enough to find it, and the `hy2://`
     * alias other clients emit is accepted too.
     */
    fun extractLink(text: String?): String? =
        text.orEmpty()
            .split(' ', '\t', '\n', '\r', '\u000B', '\u000C')
            .firstOrNull { token -> token.substringBefore("://", "").lowercase() in SCHEMES }

    /** `20`, `20Mbps` and `20 mbps` all mean the same thing in the wild. */
    private val BANDWIDTH = Regex("""^(\d+)\s*(?:mbps)?$""", RegexOption.IGNORE_CASE)

    private fun bandwidth(raw: String?): Int? =
        raw?.trim()?.let { BANDWIDTH.find(it)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it > 0 }

    /**
     * Emit a share link for [profile].
     *
     * The auth and name are RFC 3986 percent-encoded (a space is `%20`, never
     * `+`): `+` survives as a literal plus, which is what other Hysteria
     * clients read in the userinfo and fragment.
     */
    fun build(profile: Profile): String {
        val sb = StringBuilder()
        sb.append(SCHEME).append("://")
        if (profile.auth.isNotBlank()) {
            sb.append(encode(profile.auth, USERINFO_ALLOWED)).append('@')
        }
        sb.append(profile.authority).append('/')

        val query = buildList {
            if (profile.sni.isNotBlank()) add("sni=" + encode(profile.sni))
            if (profile.hasObfs) {
                add("obfs=$SALAMANDER")
                add("obfs-password=" + encode(profile.obfsPassword))
            }
            if (profile.insecure) add("insecure=1")
        }
        if (query.isNotEmpty()) {
            sb.append('?').append(query.joinToString("&"))
        }
        if (profile.name.isNotBlank()) {
            sb.append('#').append(encode(profile.name))
        }
        return sb.toString()
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            // Query strings follow x-www-form-urlencoded in every client that
            // writes them (`+` means a space), so URLDecoder is the right tool
            // here; the userinfo and fragment above are not form-encoded.
            decode(pair.substring(0, idx)).lowercase() to decode(pair.substring(idx + 1))
        }.toMap()
    }

    /** Percent-decode one form-encoded query component; malformed input is kept. */
    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }

    /**
     * Percent-encode [value] as UTF-8, leaving only unreserved characters (and
     * [extraAllowed]) alone. Deliberately not [URLEncoder]: that is
     * form-encoding, where a space becomes `+` and a literal plus is lost.
     */
    private fun encode(value: String, extraAllowed: String = ""): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val ch = code.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                ch == '-' || ch == '.' || ch == '_' || ch == '~' || ch in extraAllowed
            ) {
                append(ch)
            } else {
                append('%').append(HEX[code shr 4]).append(HEX[code and 0x0F])
            }
        }
    }

    /**
     * Sub-delimiters that are legal in the userinfo component. `:` is kept raw
     * because Hysteria's userpass auth is written as `username:password`.
     */
    private const val USERINFO_ALLOWED = ":!$&'()*,;="

    private const val HEX = "0123456789ABCDEF"
}
