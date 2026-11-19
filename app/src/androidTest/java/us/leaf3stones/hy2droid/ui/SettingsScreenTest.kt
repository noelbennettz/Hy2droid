package us.leaf3stones.hy2droid.ui

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import us.leaf3stones.hy2droid.R
import us.leaf3stones.hy2droid.data.SettingsStore
import us.leaf3stones.hy2droid.ui.theme.Hy2droidTheme

/**
 * The settings screen on a real Android runtime.
 *
 * Two things are checked here that a JVM test cannot: that the Advanced group is
 * collapsed when the screen opens (a state Compose owns, not the settings store)
 * and that flipping a switch actually writes through `MainViewModel` into the
 * DataStore, not just into the composable's local state.
 *
 * No VPN is involved: nothing on this screen starts the service, which is why it
 * runs on an emulator without a server.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun showSettings() {
        val viewModel = MainViewModel(app)
        compose.setContent {
            Hy2droidTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenSplitTunnel = {},
                    onOpenBlockRules = {},
                    onOpenAbout = {},
                    onOpenLicenses = {},
                )
            }
        }
    }

    @Test
    fun advancedGroupStartsCollapsedAndTogglesWithATap() {
        val tcpBuffer = app.getString(R.string.settings_tcp_buffer)
        showSettings()

        // Collapsed on first open: the rows inside the group do not exist yet.
        compose.onNodeWithText(tcpBuffer).assertDoesNotExist()

        compose.onNodeWithTag(ADVANCED_ROW).performClick()
        compose.onNodeWithText(tcpBuffer).assertExists()

        compose.onNodeWithTag(ADVANCED_ROW).performClick()
        compose.onNodeWithText(tcpBuffer).assertDoesNotExist()
    }

    @Test
    fun dynamicColorSwitchWritesThroughToTheSettingsStore() {
        val store = SettingsStore(app)
        val before = runBlocking { store.dynamicColor.first() }
        showSettings()

        val switch = compose.onNodeWithTag(DYNAMIC_COLOR_SWITCH)
        if (before) switch.assertIsOn() else switch.assertIsOff()

        switch.performClick()
        compose.waitUntil(TIMEOUT_MS) {
            runBlocking { store.dynamicColor.first() } == !before
        }

        // Put the device back the way it was found; the app's DataStore is real.
        compose.onNodeWithTag(DYNAMIC_COLOR_SWITCH).performClick()
        compose.waitUntil(TIMEOUT_MS) {
            runBlocking { store.dynamicColor.first() } == before
        }
    }

    private companion object {
        /** Tags set in `SettingsScreen.kt`; a test should not depend on localised text. */
        const val ADVANCED_ROW = "settings-row-advanced"
        const val DYNAMIC_COLOR_SWITCH = "settings-row-dynamic-color"
        const val TIMEOUT_MS = 10_000L
    }
}
