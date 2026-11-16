package az.market.app.data.admin

import az.market.app.data.auth.AuthError
import az.market.app.data.auth.AuthException
import az.market.app.data.auth.toAuthError
import az.market.app.data.remote.MarketJson
import az.market.app.data.remote.SupabaseProvider
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class NewCourier(
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val phone: String,
    val password: String,
    @SerialName("vehicle_info") val vehicleInfo: String? = null,
)

enum class CourierCreationError { PHONE_EXISTS, ADMIN_MFA_REQUIRED, INVALID_INPUT }

class CourierCreationException(val reason: CourierCreationError) : Exception(reason.name)

/**
 * Courier accounts are created server-side by the `admin-create-courier` Edge Function
 * (it holds the service-role key; the app never does). Returns the new user id.
 */
@Singleton
class CourierAdminRepository @Inject constructor(private val provider: SupabaseProvider) {

    suspend fun createCourier(courier: NewCourier): String {
        val client = provider.client ?: throw AuthException(AuthError.NOT_CONFIGURED)
        try {
            val response = client.functions.invoke("admin-create-courier", body = courier)
            val body = MarketJson.parseToJsonElement(response.bodyAsText()) as JsonObject
            return body["user_id"]!!.jsonPrimitive.content
        } catch (e: RestException) {
            throw when (e.statusCode) {
                409 -> CourierCreationException(CourierCreationError.PHONE_EXISTS)
                401, 403 -> CourierCreationException(CourierCreationError.ADMIN_MFA_REQUIRED)
                400 -> CourierCreationException(CourierCreationError.INVALID_INPUT)
                else -> AuthException(e.toAuthError(), e)
            }
        } catch (e: CourierCreationException) {
            throw e
        } catch (e: Exception) {
            throw AuthException(e.toAuthError(), e)
        }
    }
}
