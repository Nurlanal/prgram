package az.market.app.core.model

/** Authentication state observed by the root navigation. Populated by the auth layer in Stage 3. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: String, val role: UserRole) : SessionState
}
