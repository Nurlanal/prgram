package az.market.app.data.auth

import az.market.app.data.remote.MarketJson
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.mfa.AuthenticatorAssuranceLevel
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** What the session layer needs from Supabase Auth, as plain data (testable without Supabase). */
sealed interface AuthStatus {
    data object Initializing : AuthStatus
    data object SignedOut : AuthStatus
    data object RefreshFailed : AuthStatus
    data class SignedIn(
        val userId: String,
        val aal2: Boolean,
        val hasVerifiedFactor: Boolean,
        val fromTokenRefresh: Boolean,
    ) : AuthStatus
}

interface AuthGateway {
    val status: Flow<AuthStatus>
    suspend fun fetchAccount(): Account?
    suspend fun signOut()
}

class SupabaseAuthGateway(private val client: SupabaseClient) : AuthGateway {
    override val status: Flow<AuthStatus> = client.auth.sessionStatus.map { s ->
        when (s) {
            is SessionStatus.Initializing -> AuthStatus.Initializing
            is SessionStatus.NotAuthenticated -> AuthStatus.SignedOut
            is SessionStatus.RefreshFailure -> AuthStatus.RefreshFailed
            is SessionStatus.Authenticated -> AuthStatus.SignedIn(
                userId = s.session.user?.id.orEmpty(),
                aal2 = client.auth.mfa.getAuthenticatorAssuranceLevel().current == AuthenticatorAssuranceLevel.AAL2,
                hasVerifiedFactor = client.auth.mfa.verifiedFactors.isNotEmpty(),
                fromTokenRefresh = s.source is SessionSource.Refresh,
            )
        }
    }

    override suspend fun fetchAccount(): Account? {
        val result = client.postgrest.rpc("get_my_account").decodeAs<kotlinx.serialization.json.JsonElement>()
        if (result is JsonNull || result !is JsonObject) return null
        return MarketJson.decodeFromJsonElement<AccountDto>(result).toAccount()
    }

    override suspend fun signOut() = client.auth.signOut()
}
