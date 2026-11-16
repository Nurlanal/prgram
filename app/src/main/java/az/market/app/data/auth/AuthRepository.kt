package az.market.app.data.auth

/**
 * All sign-in / registration / recovery actions. Every method throws [AuthException] with a
 * user-facing [AuthError]. A successful sign-in is observed through SessionRepository, which
 * then routes the user by the role stored on the server.
 */
interface AuthRepository {
    suspend fun signInWithPhone(phoneE164: String, password: String)
    suspend fun signInAdmin(email: String, password: String)

    /** Creates the account and sends an SMS code. Name/address are stored by the DB trigger. */
    suspend fun registerCustomer(registration: CustomerRegistration)
    suspend fun verifyRegistrationCode(phoneE164: String, code: String)
    suspend fun resendRegistrationCode(phoneE164: String)

    /** Sends a one-time code (SMS for phone, e-mail for admins). Unknown accounts are not revealed. */
    suspend fun requestPasswordResetCode(phoneE164: String?, email: String?)
    suspend fun verifyPasswordResetCode(phoneE164: String?, email: String?, code: String)
    suspend fun setNewPassword(password: String)
    suspend fun abandonPasswordRecovery()

    suspend fun startTotpEnrollment(): TotpEnrollment
    suspend fun verifyTotp(code: String, factorId: String? = null)

    suspend fun signOut()
}
