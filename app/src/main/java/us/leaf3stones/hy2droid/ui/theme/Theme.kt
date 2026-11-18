package us.leaf3stones.hy2droid.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Fallback schemes for Android 11 (no Material You) and for users who switch
// dynamic colour off. Monochrome on purpose: black accents on white paper in the
// light theme, white accents on near-black in the dark one. The M3 role defaults
// are overridden as well — they are lavender-tinted, which is exactly the pastel
// look these two schemes replace.
internal val DarkColorScheme = darkColorScheme(
    primary = Neutral95,
    onPrimary = Neutral10,
    primaryContainer = Neutral24,
    onPrimaryContainer = Neutral92,
    inversePrimary = Neutral10,
    secondary = Neutral80,
    onSecondary = Neutral10,
    secondaryContainer = Neutral30,
    onSecondaryContainer = Neutral92,
    tertiary = Neutral80,
    onTertiary = Neutral10,
    tertiaryContainer = Neutral22,
    onTertiaryContainer = Neutral92,
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    background = Neutral6,
    onBackground = Neutral92,
    surface = Neutral6,
    onSurface = Neutral92,
    surfaceVariant = Neutral24,
    onSurfaceVariant = Neutral80,
    surfaceTint = Neutral95,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    outline = Neutral60,
    outlineVariant = Neutral30,
    scrim = Neutral0,
    surfaceDim = Neutral6,
    surfaceBright = Neutral24,
    surfaceContainerLowest = Neutral4,
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral22,
)

internal val LightColorScheme = lightColorScheme(
    primary = Neutral10,
    onPrimary = Neutral100,
    primaryContainer = Neutral90,
    onPrimaryContainer = Neutral10,
    inversePrimary = Neutral80,
    secondary = Neutral40,
    onSecondary = Neutral100,
    secondaryContainer = Neutral90,
    onSecondaryContainer = Neutral10,
    tertiary = Neutral40,
    onTertiary = Neutral100,
    tertiaryContainer = Neutral92,
    onTertiaryContainer = Neutral10,
    error = Error40,
    onError = Neutral100,
    errorContainer = Error90,
    onErrorContainer = Error10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral30,
    surfaceTint = Neutral10,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    outline = Neutral50,
    outlineVariant = Neutral80,
    scrim = Neutral0,
    surfaceDim = Neutral87,
    surfaceBright = Neutral98,
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Neutral92,
    surfaceContainerHighest = Neutral90,
)

@Composable
fun Hy2droidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
