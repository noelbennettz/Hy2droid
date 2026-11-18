package us.leaf3stones.hy2droid.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The `host:port` rendering and the canonical stored host, family by family. */
class ProfileTest {

    private fun profile(server: String, port: Int = 443) = Profile(
        id = "1",
        name = "Node",
        server = server,
        auth = "a",
        port = port,
    )

    @Test
    fun `IPv4 and names behave as before`() {
        assertEquals("example.com:443", profile("example.com").authority)
        assertEquals("example.com:8443", profile("example.com", 8443).authority)
        assertEquals("1.2.3.4:443", profile("1.2.3.4").authority)
        // An authority pasted as the host already carries its port.
        assertEquals("example.com:8443", profile("example.com:8443", 443).authority)
    }

    @Test
    fun `a bare IPv6 literal gets brackets and the port`() {
        assertEquals("[2001:db8::1]:443", profile("2001:db8::1").authority)
        assertEquals("[2001:db8::1]:8443", profile("2001:db8::1", 8443).authority)
        assertEquals("[::1]:443", profile("::1").authority)
    }

    @Test
    fun `a bracketed IPv6 literal is not double-bracketed or stripped of its port`() {
        assertEquals("[2001:db8::1]:443", profile("[2001:db8::1]").authority)
        assertEquals("[2001:db8::1]:8443", profile("[2001:db8::1]", 8443).authority)
        assertEquals("[2001:db8::1]:9000", profile("[2001:db8::1]:9000").authority)
    }

    @Test
    fun `normalizeServer strips brackets only from a bare literal`() {
        assertEquals("2001:db8::1", Profile.normalizeServer("  [2001:db8::1]  "))
        assertEquals("2001:db8::1", Profile.normalizeServer("2001:db8::1"))
        assertEquals("example.com", Profile.normalizeServer(" example.com "))
        // A complete authority keeps its brackets, so the port cannot move.
        assertEquals("[2001:db8::1]:8443", Profile.normalizeServer("[2001:db8::1]:8443"))
    }
}
