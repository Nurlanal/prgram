package az.market.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Default brand palette. The primary/accent colors are designed to be overridden at runtime
 * from the admin "Market parametrləri" (app_settings) once the backend is connected.
 */
object MarketPalette {
    val BrandPrimary = Color(0xFF0E7C5A)
    val BrandPrimaryDark = Color(0xFF6FDBB3)
    val Accent = Color(0xFFFFB547)

    /** Highlight used for discounts / price drops. Deliberately distinct from the brand color. */
    val Discount = Color(0xFFE5383B)
    val DiscountContainer = Color(0xFFFFE3E1)
    val DiscountDark = Color(0xFFFF8A80)
    val DiscountContainerDark = Color(0xFF5C1517)

    val Success = Color(0xFF2E9D57)
    val Warning = Color(0xFFF2A007)

    val LightBackground = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFF3F6F4)
    val LightOnSurface = Color(0xFF17201C)
    val LightOnSurfaceVariant = Color(0xFF5B6661)
    val LightOutline = Color(0xFFDCE3DF)

    val DarkBackground = Color(0xFF101412)
    val DarkSurface = Color(0xFF161B19)
    val DarkSurfaceVariant = Color(0xFF222A26)
    val DarkOnSurface = Color(0xFFE6ECE9)
    val DarkOnSurfaceVariant = Color(0xFFA9B4AF)
    val DarkOutline = Color(0xFF34403A)
}
