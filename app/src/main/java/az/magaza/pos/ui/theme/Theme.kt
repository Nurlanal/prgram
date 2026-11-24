package az.magaza.pos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.magaza.pos.data.ThemeMode

private val Indigo = Color(0xFF3D3AD8)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E1FF),
    onPrimaryContainer = Color(0xFF14126B),
    secondary = Color(0xFF0E9F82),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDF5EA),
    onSecondaryContainer = Color(0xFF00382D),
    tertiary = Color(0xFFE2732B),
    tertiaryContainer = Color(0xFFFFE3D1),
    onTertiaryContainer = Color(0xFF4A1F00),
    error = Color(0xFFD63A3A),
    errorContainer = Color(0xFFFFE0DE),
    onErrorContainer = Color(0xFF5C0A0A),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF15171E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15171E),
    surfaceVariant = Color(0xFFEEF0F6),
    onSurfaceVariant = Color(0xFF5C6070),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFD),
    surfaceContainer = Color(0xFFF2F3F8),
    surfaceContainerHigh = Color(0xFFECEDF4),
    surfaceContainerHighest = Color(0xFFE5E7EF),
    outline = Color(0xFFC6C9D6),
    outlineVariant = Color(0xFFE2E4EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8A6FF),
    onPrimary = Color(0xFF1B1880),
    primaryContainer = Color(0xFF2E2BAE),
    onPrimaryContainer = Color(0xFFE2E1FF),
    secondary = Color(0xFF5FDDBE),
    onSecondary = Color(0xFF00382D),
    secondaryContainer = Color(0xFF0B5043),
    onSecondaryContainer = Color(0xFFCDF5EA),
    tertiary = Color(0xFFFFB68A),
    tertiaryContainer = Color(0xFF6B3410),
    onTertiaryContainer = Color(0xFFFFE3D1),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF6E1414),
    onErrorContainer = Color(0xFFFFE0DE),
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFE6E7EE),
    surface = Color(0xFF181A22),
    onSurface = Color(0xFFE6E7EE),
    surfaceVariant = Color(0xFF232631),
    onSurfaceVariant = Color(0xFFA3A7B7),
    surfaceContainerLowest = Color(0xFF0B0C11),
    surfaceContainerLow = Color(0xFF15171E),
    surfaceContainer = Color(0xFF1C1F28),
    surfaceContainerHigh = Color(0xFF232631),
    surfaceContainerHighest = Color(0xFF2B2F3B),
    outline = Color(0xFF4A4E5E),
    outlineVariant = Color(0xFF30333F),
)

/** Gəlir/zərər rəngləri (tema daxilində). */
data class ExtraColors(val positive: Color, val negative: Color, val warning: Color)

val LocalExtraColors = staticCompositionLocalOf { ExtraColors(Color(0xFF0E9F82), Color(0xFFD63A3A), Color(0xFFE2732B)) }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

val NumberStyle = TextStyle(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

@Composable
fun PosTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val extra = if (dark) ExtraColors(Color(0xFF5FDDBE), Color(0xFFFF8A80), Color(0xFFFFB68A))
    else ExtraColors(Color(0xFF0E9F82), Color(0xFFD63A3A), Color(0xFFE2732B))
    androidx.compose.runtime.CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
