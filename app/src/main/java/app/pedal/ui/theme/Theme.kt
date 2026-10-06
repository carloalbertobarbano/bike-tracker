package app.pedal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Dark = darkColorScheme(
    primary = Color(0xFF3EE6A8),
    onPrimary = Color(0xFF00281C),
    primaryContainer = Color(0xFF0F3B2D),
    onPrimaryContainer = Color(0xFFB4FFE0),
    secondary = Color(0xFF8FA8FF),
    onSecondary = Color(0xFF0B1A4D),
    secondaryContainer = Color(0xFF1D2A55),
    onSecondaryContainer = Color(0xFFDCE3FF),
    tertiary = Color(0xFFFF5C7A),
    onTertiary = Color(0xFF3D0010),
    background = Color(0xFF0D0F12),
    onBackground = Color(0xFFECEFF3),
    surface = Color(0xFF0D0F12),
    onSurface = Color(0xFFECEFF3),
    surfaceVariant = Color(0xFF1D2127),
    onSurfaceVariant = Color(0xFF98A1AC),
    surfaceContainerLowest = Color(0xFF08090B),
    surfaceContainerLow = Color(0xFF121418),
    surfaceContainer = Color(0xFF16191E),
    surfaceContainerHigh = Color(0xFF1D2127),
    surfaceContainerHighest = Color(0xFF252A31),
    outline = Color(0xFF3A4048),
    outlineVariant = Color(0xFF262B32),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val Light = lightColorScheme(
    primary = Color(0xFF00A676),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F7E5),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4A6CF7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE4FF),
    onSecondaryContainer = Color(0xFF0E1E66),
    tertiary = Color(0xFFE8385D),
    onTertiary = Color.White,
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF111418),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF111418),
    surfaceVariant = Color(0xFFE9ECF0),
    onSurfaceVariant = Color(0xFF5D6670),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFBFC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F2F5),
    surfaceContainerHighest = Color(0xFFE8EBEF),
    outline = Color(0xFFCBD0D6),
    outlineVariant = Color(0xFFE3E6EA),
    error = Color(0xFFD93636),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1DE),
    onErrorContainer = Color(0xFF410002),
)

/** Colours used on top of map tiles; chosen to contrast with street, terrain and satellite maps. */
object MapColors {
    const val TRACK = 0xFFFF3D6E.toInt()
    const val ROUTE = 0xFF4C6FFF.toInt()
    const val CASING = 0xFFFFFFFF.toInt()
    const val LOCATION = 0xFF2B7CFF.toInt()
    const val START = 0xFF20C77F.toInt()
    const val END = 0xFFFF3D6E.toInt()
}

private val Base = Typography()

private val AppTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = Base.labelSmall.copy(letterSpacing = 0.8.sp),
)

/** Tabular figures so numbers don't jitter while they update. */
val Numeric = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun PedalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
