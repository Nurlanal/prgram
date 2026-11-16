package az.market.app.feature.admin

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.market.app.R
import az.market.app.core.common.AzPhone
import az.market.app.core.common.Validators
import az.market.app.data.admin.CourierAdminRepository
import az.market.app.data.admin.CourierCreationError
import az.market.app.data.admin.CourierCreationException
import az.market.app.data.admin.NewCourier
import az.market.app.data.auth.AuthException
import az.market.app.feature.auth.passwordProblemMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Abstraction so the form can be tested without the Edge Function. */
fun interface CourierCreator {
    suspend fun create(courier: NewCourier): String
}

data class CreateCourierUiState(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val password: String = "",
    val vehicle: String = "",
    val errors: Map<String, Int> = emptyMap(),
    val loading: Boolean = false,
    @param:StringRes val error: Int? = null,
    val createdPhone: String? = null,
)

@HiltViewModel
class CreateCourierViewModel @Inject constructor(repository: CourierAdminRepository) : ViewModel() {
    internal var creator: CourierCreator = CourierCreator { repository.createCourier(it) }

    private val _state = MutableStateFlow(CreateCourierUiState())
    val state: StateFlow<CreateCourierUiState> = _state.asStateFlow()

    fun onChange(field: String, value: String) = _state.update {
        val s = it.copy(errors = it.errors - field, error = null, createdPhone = null)
        when (field) {
            "first" -> s.copy(firstName = value)
            "last" -> s.copy(lastName = value)
            "phone" -> s.copy(phone = value)
            "password" -> s.copy(password = value)
            else -> s.copy(vehicle = value)
        }
    }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val phone = AzPhone.normalize(s.phone)
        val errors = buildMap {
            if (!Validators.isValidName(s.firstName)) put("first", R.string.v_name_required)
            if (!Validators.isValidName(s.lastName)) put("last", R.string.v_name_required)
            if (phone == null) put("phone", R.string.v_phone_invalid)
            passwordProblemMessage(s.password)?.let { put("password", it) }
        }
        _state.update { it.copy(errors = errors) }
        if (errors.isNotEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                creator.create(NewCourier(s.firstName.trim(), s.lastName.trim(), phone!!, s.password, s.vehicle.trim().ifBlank { null }))
                _state.value = CreateCourierUiState(createdPhone = phone) // clear the form (incl. password)
            } catch (e: CourierCreationException) {
                _state.update {
                    it.copy(loading = false, error = when (e.reason) {
                        CourierCreationError.PHONE_EXISTS -> R.string.err_courier_phone_exists
                        CourierCreationError.ADMIN_MFA_REQUIRED -> R.string.err_courier_admin_mfa
                        CourierCreationError.INVALID_INPUT -> R.string.err_courier_invalid
                    })
                }
            } catch (e: AuthException) {
                _state.update { it.copy(loading = false, error = e.error.message) }
            }
        }
    }
}
