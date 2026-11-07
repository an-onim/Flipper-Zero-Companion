package dev.filpper.companion.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.filpper.companion.domain.model.ThemePreference

private val Orange = Color(0xFFFF8200)
private val OrangeLight = Color(0xFFFFA640)
private val Ink = Color(0xFF151515)
private val Paper = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    // Primary
    primary = Color(0xFFB95100),
    onPrimary = Paper,
    primaryContainer = Color(0xFFFFDBBC),
    onPrimaryContainer = Color(0xFF3B1800),
    inversePrimary = OrangeLight,

    // Secondary
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Color(0xFFFFE6D2),
    onSecondaryContainer = Color(0xFF442500),

    // Tertiary
    tertiary = Color(0xFF8E4B1A),
    onTertiary = Paper,
    tertiaryContainer = Color(0xFFFFDBC2),
    onTertiaryContainer = Color(0xFF351A08),

    // Background
    background = Color(0xFFF9F8F6),
    onBackground = Ink,

    // Surface
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EEE9),
    onSurfaceVariant = Color(0xFF514D48),
    surfaceTint = Color(0xFFB95100),

    // Surface containers
    surfaceDim = Color(0xFFDEDAD4),
    surfaceBright = Paper,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Color(0xFFF7F5F1),
    surfaceContainer = Color(0xFFF0EEE9),
    surfaceContainerHigh = Color(0xFFE9E6E0),
    surfaceContainerHighest = Color(0xFFE2DED8),

    // Inverse
    inverseSurface = Color(0xFF31302E),
    inverseOnSurface = Color(0xFFF6F3EE),

    // Outline
    outline = Color(0xFF78716A),
    outlineVariant = Color(0xFFCBC5BC),

    // Error
    error = Color(0xFFB3261E),
    onError = Paper,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    // Scrim
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    // Primary
    primary = OrangeLight,
    onPrimary = Ink,
    primaryContainer = Color(0xFF713600),
    onPrimaryContainer = Paper,
    inversePrimary = Color(0xFFB95100),

    // Secondary
    secondary = Orange,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF57371E),
    onSecondaryContainer = Color(0xFFFFE6D2),

    // Tertiary
    tertiary = Color(0xFFFFD0A6),
    onTertiary = Color(0xFF442400),
    tertiaryContainer = Color(0xFF5C3A1F),
    onTertiaryContainer = Color(0xFFFFE2C6),

    // Background
    background = Color(0xFF101114),
    onBackground = Paper,

    // Surface
    surface = Color(0xFF1D1E20),
    onSurface = Paper,
    surfaceVariant = Color(0xFF2C2D30),
    onSurfaceVariant = Color(0xFFCCC7C1),
    surfaceTint = OrangeLight,

    // Surface containers
    surfaceDim = Color(0xFF101114),
    surfaceBright = Color(0xFF3A3B3D),
    surfaceContainerLowest = Color(0xFF0C0D0F),
    surfaceContainerLow = Color(0xFF191A1C),
    surfaceContainer = Color(0xFF222326),
    surfaceContainerHigh = Color(0xFF2C2D30),
    surfaceContainerHighest = Color(0xFF37383B),

    // Inverse
    inverseSurface = Color(0xFFE7E2DC),
    inverseOnSurface = Color(0xFF2D2E30),

    // Outline
    outline = Color(0xFF9C948D),
    outlineVariant = Color(0xFF4D4A46),

    // Error
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // Scrim
    scrim = Color.Black,
)

@Composable
fun FlipperTheme(
    preference: ThemePreference,
    content: @Composable (isDark: Boolean) -> Unit,
) {
    val isDark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    MaterialTheme(colorScheme = if (isDark) DarkColors else LightColors) {
        content(isDark)
    }
}
