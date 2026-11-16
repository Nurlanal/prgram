package az.market.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.market.app.core.model.SessionState
import az.market.app.data.session.SessionRepository
import az.market.app.data.store.BackendStatus
import az.market.app.data.store.StoreSettings
import az.market.app.data.store.StoreSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
) : ViewModel() {

    val session: StateFlow<SessionState> = sessionRepository.session.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SessionState.Loading,
    )

    /** Store branding/settings from the server, kept live through Realtime. */
    val storeSettings: StateFlow<StoreSettings> = storeSettingsRepository.observeSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StoreSettings(),
    )

    private val _backendStatus = MutableStateFlow<BackendStatus>(BackendStatus.Checking)
    val backendStatus: StateFlow<BackendStatus> = _backendStatus.asStateFlow()

    init {
        checkBackend()
    }

    fun checkBackend() {
        _backendStatus.value = BackendStatus.Checking
        viewModelScope.launch { _backendStatus.value = storeSettingsRepository.checkBackend() }
    }
}
