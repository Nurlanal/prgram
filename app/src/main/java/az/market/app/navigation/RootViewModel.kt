package az.market.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.market.app.core.model.SessionState
import az.market.app.data.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RootViewModel @Inject constructor(
    sessionRepository: SessionRepository,
) : ViewModel() {
    val session: StateFlow<SessionState> = sessionRepository.session.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SessionState.Loading,
    )
}
