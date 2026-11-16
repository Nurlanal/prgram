package az.market.app.data.session

import az.market.app.core.model.SessionState
import az.market.app.core.model.UserRole
import az.market.app.data.auth.AuthFlowState
import az.market.app.data.auth.AuthGateway
import az.market.app.data.auth.AuthStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Turns Supabase Auth state + the server-side account (role, active flag) into [SessionState].
 * Nothing here trusts local data or token claims for the role: it is fetched with get_my_account().
 */
@Singleton
class SupabaseSessionRepository @Inject constructor(
    private val gateway: AuthGateway?,
    private val flowState: AuthFlowState,
) : SessionRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val session: Flow<SessionState> = if (gateway == null) {
        flowOf(SessionState.SignedOut) // backend not configured in this build
    } else {
        channelFlow {
            var last: SessionState = SessionState.Loading
            combine(gateway.status, flowState.passwordRecovery, flowState.reloadTick) { status, recovery, _ ->
                status to recovery
            }.collectLatest { (status, recovery) ->
                last = resolve(gateway, status, recovery, last)
                send(last)
            }
        }.distinctUntilChanged()
    }

    private suspend fun resolve(
        gateway: AuthGateway,
        status: AuthStatus,
        recovery: Boolean,
        last: SessionState,
    ): SessionState = when (status) {
        AuthStatus.Initializing -> SessionState.Loading
        AuthStatus.SignedOut -> {
            flowState.setPasswordRecovery(false)
            SessionState.SignedOut
        }
        // Offline token refresh: keep the user where they are if we already knew them.
        AuthStatus.RefreshFailed -> if (last.isSignedInLike()) last else SessionState.AccountUnavailable
        is AuthStatus.SignedIn -> {
            val account = try {
                gateway.fetchAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return if (status.fromTokenRefresh && last.isSignedInLike()) last else SessionState.AccountUnavailable
            }
            when {
                account == null || account.role == null -> {
                    gateway.signOut() // no profile/role on the server → no access
                    SessionState.SignedOut
                }
                !account.isActive -> SessionState.Blocked(account.userId)
                account.role == UserRole.ADMIN && !status.aal2 ->
                    SessionState.AdminMfaRequired(account.userId, status.hasVerifiedFactor)
                recovery -> SessionState.PasswordRecovery(account.userId)
                else -> SessionState.SignedIn(account.userId, account.role)
            }
        }
    }

    private fun SessionState.isSignedInLike() =
        this is SessionState.SignedIn || this is SessionState.AdminMfaRequired || this is SessionState.PasswordRecovery
}
