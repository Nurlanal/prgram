package az.psclub.app.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class Palette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val cardAlt: Color,
    val text: Color,
    val dim: Color,
    val pill: Color,
    val accent: Color,
    val accent2: Color,
    val border: Color,
    val success: Color,
    val danger: Color,
    val warn: Color,
) {
    /** Filled "primary" surfaces: neon gradient in dark mode, ink black in light mode (like the reference). */
    val glow: Brush
        get() = if (dark) Brush.horizontalGradient(listOf(accent2, accent))
        else Brush.horizontalGradient(listOf(Color(0xFF111114), Color(0xFF2A2A30)))
}

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF07070E),
    card = Color(0xFF13131E),
    cardAlt = Color(0xFF1C1C2B),
    text = Color(0xFFF5F5FA),
    dim = Color(0xFF8C8CA6),
    pill = Color(0xFF1C1C2B),
    accent = Color(0xFF00E5FF),
    accent2 = Color(0xFFB026FF),
    border = Color(0x2EFFFFFF),
    success = Color(0xFF2EE59D),
    danger = Color(0xFFFF4D6D),
    warn = Color(0xFFFFC145),
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFFFFFFF),
    card = Color(0xFFF5F5F7),
    cardAlt = Color(0xFFEDEDF1),
    text = Color(0xFF111114),
    dim = Color(0xFF9A9AA5),
    pill = Color(0xFFF0F0F3),
    accent = Color(0xFF2D6BFF),
    accent2 = Color(0xFF7C4DFF),
    border = Color(0x14000000),
    success = Color(0xFF12B76A),
    danger = Color(0xFFE5304F),
    warn = Color(0xFFF59E0B),
)

val LocalPalette = compositionLocalOf { DarkPalette }
val LocalNow = staticCompositionLocalOf<State<Long>> { mutableLongStateOf(System.currentTimeMillis()) }

@Composable
fun PsTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, secondary = p.accent2, background = p.bg, surface = p.card,
        onSurface = p.text, onBackground = p.text, surfaceVariant = p.cardAlt, outline = p.border,
        surfaceContainerHigh = p.card, surfaceContainerHighest = p.cardAlt, surfaceContainerLow = p.bg,
    ) else lightColorScheme(
        primary = Color(0xFF111114), secondary = p.accent, background = p.bg, surface = p.bg,
        onSurface = p.text, onBackground = p.text, surfaceVariant = p.cardAlt,
        surfaceContainerHigh = p.bg, surfaceContainerHighest = p.cardAlt, surfaceContainerLow = p.bg,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
