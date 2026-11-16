package az.market.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import az.market.app.core.model.ThemeMode

/**
 * Brand colors that the admin can change (Stage 9, app_settings). Only the seed colors are
 * stored; the full Material 3 scheme is derived from them so any brand color stays legible.
 */
@Immutable
data class BrandColors(
    val primary: Color = MarketPalette.BrandPrimary,
    val accent: Color = MarketPalette.Accent,
)

/** Extra semantic colors that Material 3 does not provide (discount badges, success...). */
@Immutable
data class MarketExtraColors(
    val discount: Color,
    val discountContainer: Color,
    val success: Color,
    val warning: Color,
    val accent: Color,
)

val LocalMarketColors = staticCompositionLocalOf {
    MarketExtraColors(
        discount = MarketPalette.Discount,
        discountContainer = MarketPalette.DiscountContainer,
        success = MarketPalette.Success,
        warning = MarketPalette.Warning,
        accent = MarketPalette.Accent,
    )
}

private fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF111111) else Color.White

internal fun lightSchemeFor(brand: BrandColors): ColorScheme {
    val primary = brand.primary
    return lightColorScheme(
        primary = primary,
        onPrimary = onColorFor(primary),
        primaryContainer = lerp(primary, Color.White, 0.85f),
        onPrimaryContainer = lerp(primary, Color.Black, 0.55f),
        secondary = brand.accent,
        onSecondary = onColorFor(brand.accent),
        secondaryContainer = lerp(brand.accent, Color.White, 0.8f),
        onSecondaryContainer = lerp(brand.accent, Color.Black, 0.6f),
        background = MarketPalette.LightBackground,
        onBackground = MarketPalette.LightOnSurface,
        surface = MarketPalette.LightBackground,
        onSurface = MarketPalette.LightOnSurface,
        surfaceVariant = MarketPalette.LightSurfaceVariant,
        onSurfaceVariant = MarketPalette.LightOnSurfaceVariant,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF8FAF9),
        surfaceContainer = MarketPalette.LightSurfaceVariant,
        surfaceContainerHigh = Color(0xFFECF0EE),
        surfaceContainerHighest = Color(0xFFE5EAE7),
        outline = MarketPalette.LightOutline,
        outlineVariant = MarketPalette.LightOutline,
        error = MarketPalette.Discount,
    )
}

internal fun darkSchemeFor(brand: BrandColors): ColorScheme {
    val primary = lerp(brand.primary, Color.White, 0.45f)
    return darkColorScheme(
        primary = primary,
        onPrimary = onColorFor(primary),
        primaryContainer = lerp(brand.primary, Color.Black, 0.35f),
        onPrimaryContainer = lerp(brand.primary, Color.White, 0.8f),
        secondary = brand.accent,
        onSecondary = onColorFor(brand.accent),
        secondaryContainer = lerp(brand.accent, Color.Black, 0.6f),
        onSecondaryContainer = lerp(brand.accent, Color.White, 0.75f),
        background = MarketPalette.DarkBackground,
        onBackground = MarketPalette.DarkOnSurface,
        surface = MarketPalette.DarkSurface,
        onSurface = MarketPalette.DarkOnSurface,
        surfaceVariant = MarketPalette.DarkSurfaceVariant,
        onSurfaceVariant = MarketPalette.DarkOnSurfaceVariant,
        surfaceContainerLowest = MarketPalette.DarkBackground,
        surfaceContainerLow = MarketPalette.DarkSurface,
        surfaceContainer = Color(0xFF1C2220),
        surfaceContainerHigh = MarketPalette.DarkSurfaceVariant,
        surfaceContainerHighest = Color(0xFF2B3430),
        outline = MarketPalette.DarkOutline,
        outlineVariant = MarketPalette.DarkOutline,
        error = MarketPalette.DiscountDark,
    )
}

@Composable
fun MarketTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    brand: BrandColors = BrandColors(),
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val extra = MarketExtraColors(
        discount = if (dark) MarketPalette.DiscountDark else MarketPalette.Discount,
        discountContainer = if (dark) MarketPalette.DiscountContainerDark else MarketPalette.DiscountContainer,
        success = MarketPalette.Success,
        warning = MarketPalette.Warning,
        accent = brand.accent,
    )
    CompositionLocalProvider(LocalMarketColors provides extra) {
        MaterialTheme(
            colorScheme = if (dark) darkSchemeFor(brand) else lightSchemeFor(brand),
            typography = MarketTypography,
            shapes = MarketShapes,
            content = content,
        )
    }
}

/** Convenience accessor: `MarketTheme.colors.discount`. */
object MarketThemeExt {
    val colors: MarketExtraColors
        @Composable get() = LocalMarketColors.current
}
