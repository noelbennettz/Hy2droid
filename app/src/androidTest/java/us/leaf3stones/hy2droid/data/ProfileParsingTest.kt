package us.leaf3stones.hy2droid.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Share-link parsing and server normalisation on a real Android runtime.
 *
 * The same cases are covered by JVM unit tests, but the parsing goes through
 * `java.net.URI` and `java.net.URLDecoder`, whose behaviour for bracketed IPv6
 * literals and percent-escapes is not identical on Android. Running them here
 * pins what the *device* does, which is what the app actually ships.
 */
@RunWith(AndroidJUnit4::class)
class ProfileParsingTest {

    @Test
    fun aLinkWithEverythingOnItIsParsed() {
        val profile = requireNotNull(
            HysteriaUri.parse(
                "hy2://secret@[2001:db8::1]:8443/?sni=example.com&obfs=salamander" +
                    "&obfs-password=pw&insecure=1&up=20Mbps&down=50#Node%20A",
            ),
        )

        assertEquals("2001:db8::1", profile.server)
        assertEquals(8443, profile.port)
        assertEquals("secret", profile.auth)
        assertEquals("example.com", profile.sni)
        assertEquals("pw", profile.obfsPassword)
        assertTrue(profile.hasObfs)
        assertTrue(profile.insecure)
        assertTrue(profile.usesBrutal)
        assertEquals(20, profile.txMbps)
        assertEquals(50, profile.rxMbps)
        assertEquals("Node A", profile.name)
        // Bracket-free stored host, bracketed authority.
        assertEquals("[2001:db8::1]:8443", profile.authority)
    }

    @Test
    fun theDefaultPortIsUsedWhenTheLinkHasNone() {
        val profile = requireNotNull(HysteriaUri.parse("hysteria2://pw@example.com/"))
        assertEquals(443, profile.port)
        assertEquals("example.com:443", profile.authority)
        // No fragment: the display name falls back to the host.
        assertEquals("example.com", profile.name)
        assertFalse(profile.hasObfs)
        assertFalse(profile.insecure)
    }

    @Test
    fun aBareIpv6StoredByHandIsBracketedAndGivenAPort() {
        // Typed by hand the host is bare; the authority adds the brackets and port.
        val profile = Profile(id = "id", name = "n", server = "2001:db8::5", auth = "pw")
        assertEquals("[2001:db8::5]:443", profile.authority)
        // A link has to bracket the literal, because java.net.URI reads an
        // unbracketed one as an authority it cannot split; that is rejected
        // rather than guessed at.
        assertNull(HysteriaUri.parse("hysteria2://pw@2001:db8::5"))
    }

    @Test
    fun serverNormalisationOnlyTouchesBareLiterals() {
        assertEquals("2001:db8::1", Profile.normalizeServer("[2001:db8::1]"))
        // A complete authority is kept verbatim: guessing which half is the host
        // would silently move the port.
        assertEquals("[2001:db8::1]:8443", Profile.normalizeServer("[2001:db8::1]:8443"))
        assertEquals("example.com", Profile.normalizeServer("  example.com  "))
        assertEquals("example.com:8443", Profile.normalizeServer("example.com:8443"))
    }

    @Test
    fun aBuiltLinkParsesBackToTheSameProfile() {
        val original = Profile(
            id = "id",
            name = "Node é",
            server = "2001:db8::1",
            port = 8443,
            auth = "p@ss word",
            sni = "example.com",
            obfsPassword = "obfs pw",
            insecure = true,
        )

        val parsed = requireNotNull(HysteriaUri.parse(HysteriaUri.build(original)))

        assertEquals(original.name, parsed.name)
        assertEquals(original.auth, parsed.auth)
        assertEquals(original.server, parsed.server)
        assertEquals(original.port, parsed.port)
        assertEquals(original.sni, parsed.sni)
        assertEquals(original.obfsPassword, parsed.obfsPassword)
        assertEquals(original.insecure, parsed.insecure)
    }

    @Test
    fun anythingThatIsNotAHysteria2LinkIsRejected() {
        assertNull(HysteriaUri.parse(""))
        assertNull(HysteriaUri.parse("not a link"))
        assertNull(HysteriaUri.parse("vless://uuid@example.com:443"))
        assertNull(HysteriaUri.parse("hysteria2://"))
        assertNull(HysteriaUri.parse("hysteria2://@/"))
    }
}
