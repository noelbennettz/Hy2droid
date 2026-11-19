package us.leaf3stones.hy2droid.data

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import us.leaf3stones.hy2droid.BuildConfig
import us.leaf3stones.hy2droid.ui.LicensesScreen
import us.leaf3stones.hy2droid.ui.theme.Hy2droidTheme

/**
 * The licences screen is a data screen: everything it shows ships inside the
 * APK. These tests read the *packaged* assets, so a build that forgets to copy
 * Licensee's report, or a licence text that lost its file, fails here instead of
 * silently rendering an empty section on a user's phone.
 */
@RunWith(AndroidJUnit4::class)
class LicensesAssetsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun assetText(name: String): String =
        app.assets.open(name).bufferedReader().use { it.readText() }

    @Test
    fun theDependencyReportIsPackagedAndParsesCompletely() {
        val text = assetText(ThirdPartyLicenses.DEPENDENCIES_ASSET)
        val entries = ThirdPartyLicenses.parseDependencies(text)
        val lines = text.lines().map(String::trim).filter(String::isNotEmpty)

        assertFalse("the packaged dependency report is empty", entries.isEmpty())
        // parseDependencies drops a malformed row rather than crashing; a shipped
        // report must never contain one.
        assertEquals("a row in the report does not parse: $text", lines.size, entries.size)
        entries.forEach { entry ->
            assertTrue("${entry.name} has no version", entry.version.isNotBlank())
            assertTrue("${entry.name} has no licence", entry.license.isNotBlank())
        }
    }

    @Test
    fun bothNativeComponentsShipTheirLicenceText() {
        val native = ThirdPartyLicenses.nativeComponents(
            hysteriaVersion = null,
            tun2socksVersion = BuildConfig.TUN2SOCKS_VERSION,
        )
        assertEquals(2, native.size)
        native.forEach { entry ->
            assertEquals("MIT", entry.license)
            val asset = requireNotNull(entry.licenseTextAsset) { "${entry.name} has no licence asset" }
            val text = assetText(asset)
            assertTrue("$asset does not look like an MIT licence", text.contains("Permission is hereby granted"))
            assertTrue("$asset is truncated", text.contains("THE SOFTWARE IS PROVIDED"))
        }
    }

    @Test
    fun theScreenListsBothNativeComponents() {
        compose.setContent { Hy2droidTheme { LicensesScreen(onBack = {}) } }

        compose.onNodeWithText(ThirdPartyLicenses.HYSTERIA_NAME).assertExists()
        compose.onNodeWithText(ThirdPartyLicenses.TUN2SOCKS_NAME).assertExists()
        // The tunnel engine row prints the pin from scripts/tun2socks.sh, so a
        // version bump that is not wired through shows up as a missing node.
        assertTrue(
            "the licences screen does not show ${BuildConfig.TUN2SOCKS_VERSION}",
            compose.onAllNodesWithText(BuildConfig.TUN2SOCKS_VERSION).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
