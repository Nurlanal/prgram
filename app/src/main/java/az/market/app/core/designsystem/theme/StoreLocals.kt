package az.market.app.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import az.market.app.data.store.StoreSettings

/** Server-managed store settings (name, logo, colors…) available to every screen. */
val LocalStoreSettings = staticCompositionLocalOf { StoreSettings() }
