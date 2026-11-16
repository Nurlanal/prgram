package az.market.app.data.auth

import az.market.app.core.model.UserRole
import az.market.app.data.remote.SupabaseProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.mfa.FactorType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.providers.builtin.Phone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val provider: SupabaseProvider,
    private val gateway: AuthGateway?,
    private val flowState: AuthFlowState,
) : AuthRepository {

    private val client: SupabaseClient
        get() = provider.client ?: throw AuthException(AuthError.NOT_CONFIGURED)

    private suspend inline fun <T> call(block: () -> T): T =
        try { block() } catch (e: Exception) { throw e as? AuthException ?: AuthException(e.toAuthError(), e) }

    override suspend fun signInWithPhone(phoneE164: String, password: String) = call {
        client.auth.signInWith(Phone) {
            phone = phoneE164
            this.password = password
        }
        Unit
    }

    override suspend fun signInAdmin(email: String, password: String) = call {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        // The admin form must only open the admin flow: any other role is signed out again.
        val account = gateway?.fetchAccount()
        if (account?.role != UserRole.ADMIN) {
            client.auth.signOut()
            throw AuthException(AuthError.NOT_ADMIN_ACCOUNT)
        }
    }

    override suspend fun registerCustomer(registration: CustomerRegistration) = call {
        client.auth.signUpWith(Phone) {
            phone = registration.phoneE164
            password = registration.password
            data = buildJsonObject {
                put("first_name", registration.firstName.trim())
                put("last_name", registration.lastName.trim())
                put("address_line", registration.addressLine.trim())
                put("settlement", registration.settlement.trim())
            }
        }
        Unit
    }

    override suspend fun verifyRegistrationCode(phoneE164: String, code: String) = call {
        client.auth.verifyPhoneOtp(OtpType.Phone.SMS, phoneE164, code.trim())
        Unit
    }

    override suspend fun resendRegistrationCode(phoneE164: String) = call {
        client.auth.resendPhone(OtpType.Phone.SMS, phoneE164)
        Unit
    }

    override suspend fun requestPasswordResetCode(phoneE164: String?, email: String?) = call {
        try {
            client.auth.signInWith(OTP) {
                if (phoneE164 != null) phone = phoneE164 else this.email = email?.trim()
                createUser = false // never create accounts from the reset screen
            }
        } catch (e: AuthRestException) {
            // Do not reveal whether an account exists for this phone / e-mail.
            val hidden = e.errorCode == AuthErrorCode.OtpDisabled || e.errorCode == AuthErrorCode.UserNotFound ||
                e.errorCode == AuthErrorCode.SignupDisabled
            if (!hidden) throw e
        }
    }

    override suspend fun verifyPasswordResetCode(phoneE164: String?, email: String?, code: String) = call {
        flowState.setPasswordRecovery(true) // set BEFORE the session appears, so routing goes to "new password"
        try {
            if (phoneE164 != null) {
                client.auth.verifyPhoneOtp(OtpType.Phone.SMS, phoneE164, code.trim())
            } else {
                client.auth.verifyEmailOtp(OtpType.Email.EMAIL, email.orEmpty().trim(), code.trim())
            }
        } catch (e: Exception) {
            flowState.setPasswordRecovery(false)
            throw e
        }
    }

    override suspend fun setNewPassword(password: String) = call {
        client.auth.updateUser { this.password = password }
        flowState.setPasswordRecovery(false)
    }

    override suspend fun abandonPasswordRecovery() = call {
        flowState.setPasswordRecovery(false)
        client.auth.signOut()
    }

    override suspend fun startTotpEnrollment(): TotpEnrollment = call {
        val mfa = client.auth.mfa
        // Remove half-finished enrolments so a new QR can always be generated.
        mfa.retrieveFactorsForCurrentUser().filter { !it.isVerified }.forEach { mfa.unenroll(it.id) }
        val factor = mfa.enroll(FactorType.TOTP, friendlyName = "Market admin")
        TotpEnrollment(factorId = factor.id, secret = factor.data.secret, otpAuthUri = factor.data.uri)
    }

    override suspend fun verifyTotp(code: String, factorId: String?) = call {
        val mfa = client.auth.mfa
        val id = factorId ?: mfa.verifiedFactors.firstOrNull { it.factorType == "totp" }?.id
            ?: throw AuthException(AuthError.INVALID_MFA_CODE)
        mfa.createChallengeAndVerify(id, code.trim())
        Unit
    }

    override suspend fun signOut() = call {
        flowState.setPasswordRecovery(false)
        client.auth.signOut()
    }
}
