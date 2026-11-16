package az.market.app.navigation

import az.market.app.core.model.SessionState
import az.market.app.core.model.UserRole
import kotlinx.serialization.Serializable

// Type-safe Navigation Compose destinations (kotlinx.serialization).

@Serializable data object WelcomeRoute
@Serializable data object LoginRoute
@Serializable data object RegisterRoute
@Serializable data class ForgotPasswordRoute(val admin: Boolean)

// Session-driven destinations (chosen by the server state, not by the user)
@Serializable data class AdminMfaRoute(val hasVerifiedFactor: Boolean)
@Serializable data object NewPasswordRoute
@Serializable data object BlockedRoute
@Serializable data object AccountUnavailableRoute

/** Role "shells": each one hosts its own nested navigation (bottom bar / panels). */
@Serializable data object CustomerShellRoute
@Serializable data object AdminShellRoute
@Serializable data object CourierShellRoute

/** Customer bottom-navigation tabs. */
@Serializable data object HomeTabRoute
@Serializable data object CategoriesTabRoute
@Serializable data object FavoritesTabRoute
@Serializable data object CartTabRoute
@Serializable data object ProfileTabRoute

/** Admin panel inner destinations. */
@Serializable data object AdminHomeRoute
@Serializable data object AdminCreateCourierRoute

/**
 * Decides where the app starts for a given session. The role comes from the server,
 * so a customer can never land on the admin panel by changing local data.
 */
fun startDestinationFor(state: SessionState): Any = when (state) {
    SessionState.Loading, SessionState.SignedOut -> WelcomeRoute
    is SessionState.AdminMfaRequired -> AdminMfaRoute(state.hasVerifiedFactor)
    is SessionState.PasswordRecovery -> NewPasswordRoute
    is SessionState.Blocked -> BlockedRoute
    SessionState.AccountUnavailable -> AccountUnavailableRoute
    is SessionState.SignedIn -> when (state.role) {
        UserRole.CUSTOMER -> CustomerShellRoute
        UserRole.ADMIN -> AdminShellRoute
        UserRole.COURIER -> CourierShellRoute
    }
}
