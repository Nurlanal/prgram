package az.market.app.core.model

/** Authentication state observed by the root navigation. Always derived from the server. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: String, val role: UserRole) : SessionState

    /** Admin password was correct, but admin rights need the authenticator code (aal2). */
    data class AdminMfaRequired(val userId: String, val hasVerifiedFactor: Boolean) : SessionState

    /** Signed in through a one-time code to reset the password: must set a new one first. */
    data class PasswordRecovery(val userId: String) : SessionState

    /** Account deactivated by the store. */
    data class Blocked(val userId: String) : SessionState

    /** Signed in, but the account could not be loaded (e.g. offline at start-up). */
    data object AccountUnavailable : SessionState
}
