package us.leaf3stones.hy2droid.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every translatable string the licences screen added has to exist in every
 * locale that ships, otherwise those users see English. `lintDebug` cannot see
 * this (`MissingTranslation` is disabled), so it is checked here.
 */
class SettingsStringsTest {

    private val locales = listOf(
        "values",
        "values-ar",
        "values-es",
        "values-fr",
        "values-ru",
        "values-zh-rCN",
        "values-zh-rTW",
    )

    private val keys = listOf(
        "settings_licenses",
        "settings_licenses_summary",
        "licenses_title",
        "licenses_section_native",
        "licenses_section_native_summary",
        "licenses_section_libraries",
        "licenses_section_libraries_summary",
        "licenses_native_note",
    )

    @Test
    fun `the advanced group is named in every locale`() {
        val advancedKeys = listOf("settings_advanced", "settings_advanced_summary")
        locales.forEach { locale ->
            val text = File("src/main/res/$locale/strings.xml").readText()
            advancedKeys.forEach { key ->
                assertTrue("$locale is missing $key", text.contains("name=\"$key\""))
            }
        }
    }

    @Test
    fun `the licence screen is named in every locale`() {
        locales.forEach { locale ->
            val text = File("src/main/res/$locale/strings.xml").readText()
            keys.forEach { key ->
                assertTrue("$locale is missing $key", text.contains("name=\"$key\""))
            }
        }
    }
}
