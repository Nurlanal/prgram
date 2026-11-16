package az.market.app.data.session

import az.market.app.core.model.SessionState
import kotlinx.coroutines.flow.Flow

/** Single source of truth for "who is signed in and with which server-assigned role". */
interface SessionRepository {
    val session: Flow<SessionState>
}
