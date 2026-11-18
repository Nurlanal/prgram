package az.market.app.data.customer

import az.market.app.data.auth.AuthError
import az.market.app.data.auth.AuthException
import az.market.app.data.auth.toAuthError
import az.market.app.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class DeliveryAddress(
    val id: String,
    val label: String,
    @SerialName("address_line") val addressLine: String,
    val settlement: String = "",
    @SerialName("is_default") val isDefault: Boolean = false,
)

/** The signed-in customer's own data shown in the home header (RLS: own rows only). */
interface CustomerRepository {
    suspend fun addresses(): List<DeliveryAddress>
    suspend fun setDefaultAddress(addressId: String)
    suspend fun cartItemCount(): Int
}

@Singleton
class SupabaseCustomerRepository @Inject constructor(private val provider: SupabaseProvider) : CustomerRepository {

    private suspend inline fun <T> call(block: (io.github.jan.supabase.SupabaseClient) -> T): T {
        val client = provider.client ?: throw AuthException(AuthError.NOT_CONFIGURED)
        return try { block(client) } catch (e: Exception) { throw e as? AuthException ?: AuthException(e.toAuthError(), e) }
    }

    override suspend fun addresses(): List<DeliveryAddress> = call { client ->
        client.postgrest.from("delivery_addresses")
            .select(io.github.jan.supabase.postgrest.query.Columns.list("id", "label", "address_line", "settlement", "is_default")) {
                order("is_default", Order.DESCENDING)
                order("created_at", Order.ASCENDING)
            }.decodeList<DeliveryAddress>()
    }

    override suspend fun setDefaultAddress(addressId: String) = call { client ->
        client.postgrest.rpc("set_default_address", buildJsonObject { put("p_address_id", addressId) })
        Unit
    }

    override suspend fun cartItemCount(): Int = call { client ->
        client.postgrest.from("cart_items").select {
            count(Count.EXACT)
            limit(0)
        }.countOrNull()?.toInt() ?: 0
    }
}
