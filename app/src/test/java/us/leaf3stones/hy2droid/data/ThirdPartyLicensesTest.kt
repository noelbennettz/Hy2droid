package us.leaf3stones.hy2droid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdPartyLicensesTest {

    @Test
    fun `parses the generated dependency report`() {
        val report = "androidx.activity:activity\t1.13.0\tApache-2.0\n" +
            "org.jetbrains:annotations\t23.0.0\tApache-2.0\n" +
            "some.bsd:library\t2.19.3\tBSD-3-Clause"

        val entries = ThirdPartyLicenses.parseDependencies(report)

        assertEquals(3, entries.size)
        assertEquals("androidx.activity:activity", entries[0].name)
        assertEquals("1.13.0", entries[0].version)
        assertEquals("Apache-2.0", entries[0].license)
        assertEquals("BSD-3-Clause", entries[2].license)
        assertTrue(entries.all { it.licenseTextAsset == null })
    }

    @Test
    fun `keeps rows in the order the build wrote them`() {
        val report = "b:b\t2.0\tMIT\na:a\t1.0\tMIT\n"

        assertEquals(listOf("b:b", "a:a"), ThirdPartyLicenses.parseDependencies(report).map { it.name })
    }

    @Test
    fun `drops blank and malformed lines instead of failing`() {
        val report = "\nandroidx.core:core\t1.19.1\tApache-2.0\nnot a row\nname-only\t1.0\n\t1.0\tMIT\n"

        val entries = ThirdPartyLicenses.parseDependencies(report)

        assertEquals(1, entries.size)
        assertEquals("androidx.core:core", entries[0].name)
    }

    @Test
    fun `native components carry their licence text asset`() {
        val native = ThirdPartyLicenses.nativeComponents(
            hysteriaVersion = "v2.14.0",
            tun2socksVersion = "2.19.3",
        )

        assertEquals(2, native.size)
        assertEquals(ThirdPartyLicenses.HYSTERIA_NAME, native[0].name)
        assertEquals("v2.14.0", native[0].version)
        assertEquals("MIT", native[0].license)
        assertEquals(ThirdPartyLicenses.HYSTERIA_LICENSE_ASSET, native[0].licenseTextAsset)
        assertEquals(ThirdPartyLicenses.TUN2SOCKS_NAME, native[1].name)
        assertEquals("2.19.3", native[1].version)
        assertEquals("MIT", native[1].license)
        assertEquals(ThirdPartyLicenses.TUN2SOCKS_LICENSE_ASSET, native[1].licenseTextAsset)
    }

    @Test
    fun `an unreadable core version still lists the component`() {
        val native = ThirdPartyLicenses.nativeComponents(
            hysteriaVersion = null,
            tun2socksVersion = "2.19.3",
        )

        assertEquals("bundled", native[0].version)
        assertEquals("MIT", native[0].license)
    }
}
