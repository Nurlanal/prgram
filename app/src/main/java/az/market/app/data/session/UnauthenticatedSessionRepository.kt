package az.market.app.data.session

import az.market.app.core.model.SessionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Stage 1 implementation: there is no backend/auth yet, so the user is always signed out.
 * Replaced in Stage 3 by a Supabase-Auth-backed repository that reads the role from user_roles.
 */
@Singleton
class UnauthenticatedSessionRepository @Inject constructor() : SessionRepository {
    override val session: Flow<SessionState> = flowOf(SessionState.SignedOut)
}
