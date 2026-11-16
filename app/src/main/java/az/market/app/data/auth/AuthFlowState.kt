package az.market.app.data.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process flags shared by the auth actions and the session state:
 *  - passwordRecovery: a one-time code sign-in that must end with a new password;
 *  - reloadTick: ask the session layer to re-read the account (retry button).
 */
@Singleton
class AuthFlowState @Inject constructor() {
    private val _passwordRecovery = MutableStateFlow(false)
    val passwordRecovery: StateFlow<Boolean> = _passwordRecovery.asStateFlow()

    private val _reloadTick = MutableStateFlow(0)
    val reloadTick: StateFlow<Int> = _reloadTick.asStateFlow()

    fun setPasswordRecovery(active: Boolean) { _passwordRecovery.value = active }
    fun reloadAccount() { _reloadTick.value++ }
}
