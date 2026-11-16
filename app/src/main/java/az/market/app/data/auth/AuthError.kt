package az.market.app.data.auth

import androidx.annotation.StringRes
import az.market.app.R
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** User-facing auth failures (messages in Azerbaijani, see strings.xml). */
enum class AuthError(@param:StringRes val message: Int) {
    NOT_CONFIGURED(R.string.err_not_configured),
    NETWORK(R.string.err_network),
    INVALID_CREDENTIALS(R.string.err_invalid_credentials),
    PHONE_EXISTS(R.string.err_phone_exists),
    INVALID_OTP(R.string.err_invalid_otp),
    WEAK_PASSWORD(R.string.err_weak_password),
    SAME_PASSWORD(R.string.err_same_password),
    RATE_LIMITED(R.string.err_rate_limited),
    SMS_FAILED(R.string.err_sms_failed),
    PHONE_NOT_CONFIRMED(R.string.err_phone_not_confirmed),
    ACCOUNT_BLOCKED(R.string.err_account_blocked),
    INVALID_MFA_CODE(R.string.err_invalid_mfa_code),
    NOT_ADMIN_ACCOUNT(R.string.err_not_admin_account),
    PERMISSION_DENIED(R.string.err_permission_denied),
    UNKNOWN(R.string.err_unknown),
}

class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)

/** Maps supabase-kt / network exceptions to [AuthError]. Cancellation is always rethrown. */
fun Throwable.toAuthError(): AuthError = when (this) {
    is CancellationException -> throw this
    is AuthException -> error
    is AuthRestException -> when (errorCode) {
        AuthErrorCode.InvalidCredentials -> AuthError.INVALID_CREDENTIALS
        AuthErrorCode.PhoneExists, AuthErrorCode.UserAlreadyExists -> AuthError.PHONE_EXISTS
        AuthErrorCode.OtpExpired -> AuthError.INVALID_OTP
        AuthErrorCode.WeakPassword -> AuthError.WEAK_PASSWORD
        AuthErrorCode.SamePassword -> AuthError.SAME_PASSWORD
        AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverSmsSendRateLimit,
        AuthErrorCode.OverEmailSendRateLimit -> AuthError.RATE_LIMITED
        AuthErrorCode.SmsSendFailed, AuthErrorCode.PhoneProviderDisabled -> AuthError.SMS_FAILED
        AuthErrorCode.PhoneNotConfirmed -> AuthError.PHONE_NOT_CONFIRMED
        AuthErrorCode.UserBanned -> AuthError.ACCOUNT_BLOCKED
        AuthErrorCode.MfaVerificationFailed, AuthErrorCode.MfaChallengeExpired,
        AuthErrorCode.MfaVerificationRejected -> AuthError.INVALID_MFA_CODE
        AuthErrorCode.InsufficientAal -> AuthError.PERMISSION_DENIED
        else -> if (statusCode == 429) AuthError.RATE_LIMITED else AuthError.UNKNOWN
    }
    is RestException -> if (statusCode == 429) AuthError.RATE_LIMITED else AuthError.UNKNOWN
    is HttpRequestException, is HttpRequestTimeoutException, is IOException -> AuthError.NETWORK
    else -> AuthError.UNKNOWN
}
