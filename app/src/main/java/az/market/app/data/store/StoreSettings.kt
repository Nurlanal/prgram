package az.market.app.data.store

import az.market.app.data.remote.AppSettingsDto
import java.math.BigDecimal

/** Store-wide settings managed by the admin (app_settings). Defaults are used until the server answers. */
data class StoreSettings(
    val storeName: String = "Market",
    val logoUrl: String? = null,
    val primaryColorArgb: Long = 0xFF0E7C5A,
    val accentColorArgb: Long = 0xFFFFB547,
    val storeAddress: String = "",
    val storePhone: String? = null,
    val supportPhone: String? = null,
    val pickupEnabled: Boolean = true,
    val deliveryEnabled: Boolean = true,
    val bonusEarnRate: BigDecimal = BigDecimal("0.10"),
)

data class Category(val id: Long, val name: String, val slug: String, val imageUrl: String?, val sortOrder: Int)

/** "#RRGGBB" → opaque ARGB, or null when the value is malformed (server also validates). */
fun parseHexColor(hex: String): Long? {
    if (!hex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) return null
    return 0xFF000000 or hex.substring(1).toLong(16)
}

fun AppSettingsDto.toDomain(defaults: StoreSettings = StoreSettings()) = StoreSettings(
    storeName = storeName.ifBlank { defaults.storeName },
    logoUrl = logoUrl,
    primaryColorArgb = parseHexColor(primaryColor) ?: defaults.primaryColorArgb,
    accentColorArgb = parseHexColor(accentColor) ?: defaults.accentColorArgb,
    storeAddress = storeAddress,
    storePhone = storePhone,
    supportPhone = supportPhone,
    pickupEnabled = pickupEnabled,
    deliveryEnabled = deliveryEnabled,
    bonusEarnRate = bonusEarnRate.toBigDecimalOrNull() ?: defaults.bonusEarnRate,
)
