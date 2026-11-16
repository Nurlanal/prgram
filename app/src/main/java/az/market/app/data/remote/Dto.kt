package az.market.app.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * PostgreSQL `numeric` arrives as a JSON number (e.g. 0.0500). Reading it through Double would lose
 * exactness, so it is kept as its literal text and converted with BigDecimal / Money where needed.
 */
object DecimalStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("DecimalString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String =
        if (decoder is JsonDecoder) (decoder.decodeJsonElement() as JsonPrimitive).content else decoder.decodeString()

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String) {
        if (encoder is kotlinx.serialization.json.JsonEncoder) encoder.encodeJsonElement(JsonUnquotedLiteral(value))
        else encoder.encodeString(value)
    }
}

/** Row of public.app_settings (columns: supabase/migrations/…_tables.sql). */
@Serializable
data class AppSettingsDto(
    val id: Int = 1,
    @SerialName("store_name") val storeName: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("primary_color") val primaryColor: String,
    @SerialName("accent_color") val accentColor: String,
    @SerialName("store_address") val storeAddress: String = "",
    @SerialName("store_latitude") val storeLatitude: Double? = null,
    @SerialName("store_longitude") val storeLongitude: Double? = null,
    @SerialName("store_phone") val storePhone: String? = null,
    @SerialName("support_phone") val supportPhone: String? = null,
    @SerialName("support_whatsapp") val supportWhatsapp: String? = null,
    @SerialName("pickup_enabled") val pickupEnabled: Boolean = true,
    @SerialName("delivery_enabled") val deliveryEnabled: Boolean = true,
    @Serializable(with = DecimalStringSerializer::class)
    @SerialName("bonus_earn_rate") val bonusEarnRate: String = "0",
)

/** Row of public.categories. */
@Serializable
data class CategoryDto(
    val id: Long,
    @SerialName("parent_id") val parentId: Long? = null,
    val name: String,
    val slug: String,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
)
