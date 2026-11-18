package us.leaf3stones.hy2droid.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fallback palette (Android 11, or dynamic colour switched off) is written by
 * hand rather than generated, so the pairs that carry text are guarded here: a
 * pastel ink or a surface that is too light for it still compiles, and only shows
 * up on a real screen.
 */
class MonochromePaletteTest {

    @Test
    fun `dark fallback keeps text readable`() = assertReadable(DarkColorScheme)

    @Test
    fun `light fallback keeps text readable`() = assertReadable(LightColorScheme)

    @Test
    fun `fallback palette has no hue`() {
        for (scheme in listOf(DarkColorScheme, LightColorScheme)) {
            for ((role, color) in monochromeRoles(scheme)) {
                assertTrue(
                    "$role should be grey but is ${hex(color)}",
                    color.red == color.green && color.green == color.blue,
                )
            }
        }
    }

    private fun assertReadable(scheme: ColorScheme) {
        for (ink in pairs(scheme)) {
            val ratio = contrastRatio(ink.foreground, ink.background)
            assertTrue(
                "${ink.role}: %.2f:1 is below the required %.1f:1"
                    .format(ratio, ink.minimum),
                ratio >= ink.minimum,
            )
        }
    }

    private data class Ink(
        val role: String,
        val foreground: Color,
        val background: Color,
        val minimum: Double,
    )

    /** Pairs the UI draws together, plus the 3:1 roles used as icons and dots. */
    private fun pairs(scheme: ColorScheme) = listOf(
        Ink("onBackground on background", scheme.onBackground, scheme.background, 4.5),
        Ink("onSurface on surface", scheme.onSurface, scheme.surface, 4.5),
        Ink("onSurface on surfaceContainer", scheme.onSurface, scheme.surfaceContainer, 4.5),
        Ink(
            "onSurface on surfaceContainerHighest",
            scheme.onSurface,
            scheme.surfaceContainerHighest,
            4.5,
        ),
        Ink("onSurfaceVariant on surface", scheme.onSurfaceVariant, scheme.surface, 4.5),
        Ink(
            "onSurfaceVariant on surfaceVariant",
            scheme.onSurfaceVariant,
            scheme.surfaceVariant,
            4.5,
        ),
        Ink("onPrimary on primary", scheme.onPrimary, scheme.primary, 4.5),
        Ink(
            "onPrimaryContainer on primaryContainer",
            scheme.onPrimaryContainer,
            scheme.primaryContainer,
            4.5,
        ),
        Ink("onSecondary on secondary", scheme.onSecondary, scheme.secondary, 4.5),
        Ink(
            "onSecondaryContainer on secondaryContainer",
            scheme.onSecondaryContainer,
            scheme.secondaryContainer,
            4.5,
        ),
        Ink("onTertiary on tertiary", scheme.onTertiary, scheme.tertiary, 4.5),
        Ink(
            "onTertiaryContainer on tertiaryContainer",
            scheme.onTertiaryContainer,
            scheme.tertiaryContainer,
            4.5,
        ),
        Ink("onError on error", scheme.onError, scheme.error, 4.5),
        Ink(
            "onErrorContainer on errorContainer",
            scheme.onErrorContainer,
            scheme.errorContainer,
            4.5,
        ),
        Ink(
            "inverseOnSurface on inverseSurface",
            scheme.inverseOnSurface,
            scheme.inverseSurface,
            4.5,
        ),
        Ink("primary on surface", scheme.primary, scheme.surface, 3.0),
        Ink("secondary on surface", scheme.secondary, scheme.surface, 3.0),
        Ink("tertiary on surface", scheme.tertiary, scheme.surface, 3.0),
        Ink("error on surface", scheme.error, scheme.surface, 3.0),
        Ink("outline on surface", scheme.outline, scheme.surface, 3.0),
    )

    /** Every role except the red error family, which is allowed to keep its hue. */
    private fun monochromeRoles(scheme: ColorScheme) = listOf(
        "primary" to scheme.primary,
        "onPrimary" to scheme.onPrimary,
        "primaryContainer" to scheme.primaryContainer,
        "onPrimaryContainer" to scheme.onPrimaryContainer,
        "inversePrimary" to scheme.inversePrimary,
        "secondary" to scheme.secondary,
        "onSecondary" to scheme.onSecondary,
        "secondaryContainer" to scheme.secondaryContainer,
        "onSecondaryContainer" to scheme.onSecondaryContainer,
        "tertiary" to scheme.tertiary,
        "onTertiary" to scheme.onTertiary,
        "tertiaryContainer" to scheme.tertiaryContainer,
        "onTertiaryContainer" to scheme.onTertiaryContainer,
        "background" to scheme.background,
        "onBackground" to scheme.onBackground,
        "surface" to scheme.surface,
        "onSurface" to scheme.onSurface,
        "surfaceVariant" to scheme.surfaceVariant,
        "onSurfaceVariant" to scheme.onSurfaceVariant,
        "surfaceTint" to scheme.surfaceTint,
        "inverseSurface" to scheme.inverseSurface,
        "inverseOnSurface" to scheme.inverseOnSurface,
        "outline" to scheme.outline,
        "outlineVariant" to scheme.outlineVariant,
        "scrim" to scheme.scrim,
        "surfaceDim" to scheme.surfaceDim,
        "surfaceBright" to scheme.surfaceBright,
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
    )

    /** WCAG 2.1 relative luminance of an sRGB colour. */
    private fun luminance(color: Color): Double {
        fun linear(value: Float): Double {
            val channel = value.toDouble()
            return if (channel <= 0.03928) channel / 12.92
            else Math.pow((channel + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun hex(color: Color): String =
        "#%08X".format((color.value shr 32).toLong() and 0xFFFFFFFFL)
}
