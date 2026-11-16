package az.market.app.feature.auth

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.market.app.R
import az.market.app.core.common.AzPhone
import az.market.app.core.common.PasswordPolicy
import az.market.app.core.common.Validators
import az.market.app.data.auth.AuthError
import az.market.app.data.auth.AuthException
import az.market.app.data.auth.AuthFlowState
import az.market.app.data.auth.AuthRepository
import az.market.app.data.auth.CustomerRegistration
import az.market.app.data.auth.TotpEnrollment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@StringRes
internal fun passwordProblemMessage(password: String): Int? = when (PasswordPolicy.check(password)) {
    PasswordPolicy.Problem.TOO_SHORT -> R.string.v_password_short
    PasswordPolicy.Problem.TOO_LONG -> R.string.v_password_long
    PasswordPolicy.Problem.NEEDS_LETTER -> R.string.v_password_letter
    PasswordPolicy.Problem.NEEDS_DIGIT -> R.string.v_password_digit
    null -> null
}

/** Runs an auth action with loading/error handling. Success is reported through [onSuccess]. */
internal fun ViewModel.runAuth(
    setLoading: (Boolean) -> Unit,
    setError: (AuthError?) -> Unit,
    action: suspend () -> Unit,
    onSuccess: () -> Unit = {},
) {
    viewModelScope.launch {
        setError(null)
        setLoading(true)
        try {
            action()
            onSuccess()
        } catch (e: AuthException) {
            setError(e.error)
        } finally {
            setLoading(false)
        }
    }
}

const val RESEND_SECONDS = 60

// ---------------------------------------------------------------- Login
enum class LoginMode { PHONE, ADMIN }

data class LoginUiState(
    val mode: LoginMode = LoginMode.PHONE,
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    @param:StringRes val phoneError: Int? = null,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    val loading: Boolean = false,
    val error: AuthError? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onModeChange(mode: LoginMode) = _state.update { LoginUiState(mode = mode) } // never carry a password across modes
    fun onPhoneChange(v: String) = _state.update { it.copy(phone = v, phoneError = null, error = null) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v, emailError = null, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, passwordError = null, error = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val passwordError = if (s.password.isEmpty()) R.string.v_name_required else null
        when (s.mode) {
            LoginMode.PHONE -> {
                val phone = AzPhone.normalize(s.phone)
                _state.update { it.copy(phoneError = if (phone == null) R.string.v_phone_invalid else null, passwordError = passwordError) }
                if (phone == null || passwordError != null) return
                runAuth({ l -> _state.update { it.copy(loading = l) } }, { e -> _state.update { it.copy(error = e) } },
                    { repository.signInWithPhone(phone, s.password) })
            }
            LoginMode.ADMIN -> {
                val emailOk = Validators.isValidEmail(s.email)
                _state.update { it.copy(emailError = if (emailOk) null else R.string.v_email_invalid, passwordError = passwordError) }
                if (!emailOk || passwordError != null) return
                runAuth({ l -> _state.update { it.copy(loading = l) } }, { e -> _state.update { it.copy(error = e) } },
                    { repository.signInAdmin(s.email.trim(), s.password) })
            }
        }
    }
}

// ---------------------------------------------------------------- Registration
enum class RegisterStep { FORM, CODE }

enum class RegisterField { FIRST_NAME, LAST_NAME, PHONE, PASSWORD, CONFIRM, ADDRESS, SETTLEMENT, CODE }

data class RegisterUiState(
    val step: RegisterStep = RegisterStep.FORM,
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val password: String = "",
    val confirm: String = "",
    val address: String = "",
    val settlement: String = "",
    val code: String = "",
    val phoneE164: String? = null,
    val errors: Map<RegisterField, Int> = emptyMap(),
    val resendSeconds: Int = 0,
    val codeResent: Boolean = false,
    val loading: Boolean = false,
    val error: AuthError? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()
    private var timer: Job? = null

    fun onChange(field: RegisterField, value: String) = _state.update {
        val s = it.copy(errors = it.errors - field, error = null)
        when (field) {
            RegisterField.FIRST_NAME -> s.copy(firstName = value)
            RegisterField.LAST_NAME -> s.copy(lastName = value)
            RegisterField.PHONE -> s.copy(phone = value)
            RegisterField.PASSWORD -> s.copy(password = value)
            RegisterField.CONFIRM -> s.copy(confirm = value)
            RegisterField.ADDRESS -> s.copy(address = value)
            RegisterField.SETTLEMENT -> s.copy(settlement = value)
            RegisterField.CODE -> s.copy(code = value.filter(Char::isDigit).take(6))
        }
    }

    internal fun validate(s: RegisterUiState): Map<RegisterField, Int> = buildMap {
        if (!Validators.isValidName(s.firstName)) put(RegisterField.FIRST_NAME, R.string.v_name_required)
        if (!Validators.isValidName(s.lastName)) put(RegisterField.LAST_NAME, R.string.v_name_required)
        if (AzPhone.normalize(s.phone) == null) put(RegisterField.PHONE, R.string.v_phone_invalid)
        passwordProblemMessage(s.password)?.let { put(RegisterField.PASSWORD, it) }
        if (s.confirm != s.password) put(RegisterField.CONFIRM, R.string.v_password_mismatch)
        if (!Validators.isValidAddress(s.address)) put(RegisterField.ADDRESS, R.string.v_address_short)
        if (!Validators.isValidName(s.settlement)) put(RegisterField.SETTLEMENT, R.string.v_name_required)
    }

    fun submitForm() {
        val s = _state.value
        if (s.loading) return
        val errors = validate(s)
        _state.update { it.copy(errors = errors) }
        if (errors.isNotEmpty()) return
        val phone = AzPhone.normalize(s.phone)!!
        runAuth(::setLoading, ::setError, {
            repository.registerCustomer(
                CustomerRegistration(s.firstName, s.lastName, phone, s.password, s.address, s.settlement),
            )
        }) {
            // The password is no longer needed in memory once the account exists.
            _state.update { it.copy(step = RegisterStep.CODE, phoneE164 = phone, password = "", confirm = "", code = "") }
            startTimer()
        }
    }

    fun verifyCode() {
        val s = _state.value
        if (s.loading) return
        if (!Validators.isValidOtp(s.code)) {
            _state.update { it.copy(errors = mapOf(RegisterField.CODE to R.string.v_otp_invalid)) }
            return
        }
        runAuth(::setLoading, ::setError, { repository.verifyRegistrationCode(s.phoneE164!!, s.code) })
        // On success the session appears and the app opens the customer home automatically.
    }

    fun resendCode() {
        val phone = _state.value.phoneE164 ?: return
        runAuth(::setLoading, ::setError, { repository.resendRegistrationCode(phone) }) {
            _state.update { it.copy(codeResent = true) }
            startTimer()
        }
    }

    fun editNumber() {
        timer?.cancel()
        _state.update { it.copy(step = RegisterStep.FORM, code = "", error = null, resendSeconds = 0) }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            for (left in RESEND_SECONDS downTo 0) {
                _state.update { it.copy(resendSeconds = left) }
                if (left > 0) delay(1_000)
            }
        }
    }

    private fun setLoading(v: Boolean) = _state.update { it.copy(loading = v) }
    private fun setError(e: AuthError?) = _state.update { it.copy(error = e) }
}

// ---------------------------------------------------------------- Forgot password
enum class ForgotStep { REQUEST, CODE }

data class ForgotPasswordUiState(
    val byEmail: Boolean,
    val step: ForgotStep = ForgotStep.REQUEST,
    val phone: String = "",
    val email: String = "",
    val code: String = "",
    @param:StringRes val inputError: Int? = null,
    @param:StringRes val codeError: Int? = null,
    val resendSeconds: Int = 0,
    val loading: Boolean = false,
    val error: AuthError? = null,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ForgotPasswordUiState(byEmail = savedStateHandle.get<Boolean>("admin") == true))
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()
    private var timer: Job? = null
    private var target: Pair<String?, String?> = null to null // phone, email

    fun onPhoneChange(v: String) = _state.update { it.copy(phone = v, inputError = null, error = null) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v, inputError = null, error = null) }
    fun onCodeChange(v: String) = _state.update { it.copy(code = v.filter(Char::isDigit).take(6), codeError = null, error = null) }

    fun requestCode() {
        val s = _state.value
        if (s.loading) return
        val phone = if (s.byEmail) null else AzPhone.normalize(s.phone)
        val email = if (s.byEmail) s.email.trim().takeIf(Validators::isValidEmail) else null
        if (phone == null && email == null) {
            _state.update { it.copy(inputError = if (s.byEmail) R.string.v_email_invalid else R.string.v_phone_invalid) }
            return
        }
        target = phone to email
        runAuth(::setLoading, ::setError, { repository.requestPasswordResetCode(phone, email) }) {
            _state.update { it.copy(step = ForgotStep.CODE, code = "") }
            startTimer()
        }
    }

    fun verifyCode() {
        val s = _state.value
        if (s.loading) return
        if (!Validators.isValidOtp(s.code)) {
            _state.update { it.copy(codeError = R.string.v_otp_invalid) }
            return
        }
        val (phone, email) = target
        runAuth(::setLoading, ::setError, { repository.verifyPasswordResetCode(phone, email, s.code) })
        // On success the root navigation shows the "new password" screen.
    }

    fun back() {
        timer?.cancel()
        _state.update { it.copy(step = ForgotStep.REQUEST, code = "", error = null) }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            for (left in RESEND_SECONDS downTo 0) {
                _state.update { it.copy(resendSeconds = left) }
                if (left > 0) delay(1_000)
            }
        }
    }

    private fun setLoading(v: Boolean) = _state.update { it.copy(loading = v) }
    private fun setError(e: AuthError?) = _state.update { it.copy(error = e) }
}

// ---------------------------------------------------------------- New password (after code sign-in)
data class NewPasswordUiState(
    val password: String = "",
    val confirm: String = "",
    @param:StringRes val passwordError: Int? = null,
    @param:StringRes val confirmError: Int? = null,
    val loading: Boolean = false,
    val error: AuthError? = null,
)

@HiltViewModel
class NewPasswordViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(NewPasswordUiState())
    val state: StateFlow<NewPasswordUiState> = _state.asStateFlow()

    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, passwordError = null, error = null) }
    fun onConfirmChange(v: String) = _state.update { it.copy(confirm = v, confirmError = null, error = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val pwError = passwordProblemMessage(s.password)
        val confirmError = if (s.confirm != s.password) R.string.v_password_mismatch else null
        _state.update { it.copy(passwordError = pwError, confirmError = confirmError) }
        if (pwError != null || confirmError != null) return
        runAuth({ l -> _state.update { it.copy(loading = l) } }, { e -> _state.update { it.copy(error = e) } },
            { repository.setNewPassword(s.password) })
    }

    fun cancel() = runAuth({}, { e -> _state.update { it.copy(error = e) } }, { repository.abandonPasswordRecovery() })
}

// ---------------------------------------------------------------- Admin MFA
data class AdminMfaUiState(
    val hasVerifiedFactor: Boolean,
    val enrollment: TotpEnrollment? = null,
    val code: String = "",
    @param:StringRes val codeError: Int? = null,
    val loading: Boolean = false,
    val error: AuthError? = null,
)

@HiltViewModel
class AdminMfaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminMfaUiState(hasVerifiedFactor = savedStateHandle.get<Boolean>("hasVerifiedFactor") == true))
    val state: StateFlow<AdminMfaUiState> = _state.asStateFlow()

    init {
        if (!_state.value.hasVerifiedFactor) startEnrollment()
    }

    fun startEnrollment() = runAuth(::setLoading, ::setError, {
        val enrollment = repository.startTotpEnrollment()
        _state.update { it.copy(enrollment = enrollment) }
    })

    fun onCodeChange(v: String) = _state.update { it.copy(code = v.filter(Char::isDigit).take(6), codeError = null, error = null) }

    fun verify() {
        val s = _state.value
        if (s.loading) return
        if (!Validators.isValidOtp(s.code)) {
            _state.update { it.copy(codeError = R.string.v_otp_invalid) }
            return
        }
        runAuth(::setLoading, ::setError, { repository.verifyTotp(s.code, s.enrollment?.factorId) }) {
            _state.update { it.copy(code = "") }
        }
        // Success upgrades the session to aal2 → the root navigation opens the admin panel.
    }

    fun signOut() = runAuth(::setLoading, ::setError, { repository.signOut() })

    private fun setLoading(v: Boolean) = _state.update { it.copy(loading = v) }
    private fun setError(e: AuthError?) = _state.update { it.copy(error = e) }
}

// ---------------------------------------------------------------- Shared session actions
@HiltViewModel
class SessionActionsViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val flowState: AuthFlowState,
) : ViewModel() {
    private val _error = MutableStateFlow<AuthError?>(null)
    val error: StateFlow<AuthError?> = _error.asStateFlow()

    fun signOut() = runAuth({}, { _error.value = it }, { repository.signOut() })
    fun retryLoadingAccount() = flowState.reloadAccount()
}
