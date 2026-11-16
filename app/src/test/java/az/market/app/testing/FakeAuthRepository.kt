package az.market.app.testing

import az.market.app.data.auth.AuthError
import az.market.app.data.auth.AuthException
import az.market.app.data.auth.AuthRepository
import az.market.app.data.auth.CustomerRegistration
import az.market.app.data.auth.TotpEnrollment

/** Records calls; `failWith` makes the next call fail with that error. */
class FakeAuthRepository : AuthRepository {
    val calls = mutableListOf<String>()
    var failWith: AuthError? = null
    var lastRegistration: CustomerRegistration? = null
    val enrollment = TotpEnrollment("factor-1", "JBSWY3DPEHPK3PXP", "otpauth://totp/Market:admin?secret=JBSWY3DPEHPK3PXP&issuer=Market")

    private fun record(call: String) {
        calls += call
        failWith?.let { failWith = null; throw AuthException(it) }
    }

    override suspend fun signInWithPhone(phoneE164: String, password: String) = record("phone:$phoneE164:$password")
    override suspend fun signInAdmin(email: String, password: String) = record("admin:$email:$password")
    override suspend fun registerCustomer(registration: CustomerRegistration) {
        lastRegistration = registration
        record("register:${registration.phoneE164}")
    }
    override suspend fun verifyRegistrationCode(phoneE164: String, code: String) = record("verify:$phoneE164:$code")
    override suspend fun resendRegistrationCode(phoneE164: String) = record("resend:$phoneE164")
    override suspend fun requestPasswordResetCode(phoneE164: String?, email: String?) = record("reset-request:${phoneE164 ?: email}")
    override suspend fun verifyPasswordResetCode(phoneE164: String?, email: String?, code: String) =
        record("reset-verify:${phoneE164 ?: email}:$code")
    override suspend fun setNewPassword(password: String) = record("new-password:$password")
    override suspend fun abandonPasswordRecovery() = record("abandon")
    override suspend fun startTotpEnrollment(): TotpEnrollment { record("enroll"); return enrollment }
    override suspend fun verifyTotp(code: String, factorId: String?) = record("totp:$code:$factorId")
    override suspend fun signOut() = record("sign-out")
}
