package az.market.app.data.auth

import az.market.app.core.model.UserRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Result of public.get_my_account() — the role always comes from the database. */
@Serializable
data class AccountDto(
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val phone: String? = null,
    val email: String? = null,
)

data class Account(
    val userId: String,
    val role: UserRole?,
    val isActive: Boolean,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val email: String?,
)

fun AccountDto.toAccount() = Account(userId, UserRole.fromServerValue(role), isActive, firstName, lastName, phone, email)

data class CustomerRegistration(
    val firstName: String,
    val lastName: String,
    val phoneE164: String,
    val password: String,
    val addressLine: String,
    val settlement: String,
)

/** TOTP enrolment data shown to the admin once (QR + manual key). */
data class TotpEnrollment(val factorId: String, val secret: String, val otpAuthUri: String)
