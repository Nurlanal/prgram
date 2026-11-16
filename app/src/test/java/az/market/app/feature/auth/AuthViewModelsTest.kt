package az.market.app.feature.auth

import androidx.lifecycle.SavedStateHandle
import az.market.app.R
import az.market.app.data.auth.AuthError
import az.market.app.testing.FakeAuthRepository
import az.market.app.testing.MainDispatcherRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = FakeAuthRepository()

    // ---------------- Login
    @Test fun loginValidatesBeforeCallingTheServer() {
        val vm = LoginViewModel(repo)
        vm.onPhoneChange("12345")
        vm.submit()
        assertEquals(R.string.v_phone_invalid, vm.state.value.phoneError)
        assertEquals(R.string.v_name_required, vm.state.value.passwordError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test fun loginNormalisesThePhoneNumber() {
        val vm = LoginViewModel(repo)
        vm.onPhoneChange("050 123 45 67")
        vm.onPasswordChange("Market2026")
        vm.submit()
        assertEquals(listOf("phone:+994501234567:Market2026"), repo.calls)
        assertFalse(vm.state.value.loading)
    }

    @Test fun loginShowsServerError() {
        repo.failWith = AuthError.INVALID_CREDENTIALS
        val vm = LoginViewModel(repo)
        vm.onPhoneChange("0501234567"); vm.onPasswordChange("wrong")
        vm.submit()
        assertEquals(AuthError.INVALID_CREDENTIALS, vm.state.value.error)
    }

    @Test fun adminLoginUsesEmailAndSwitchingModeClearsThePassword() {
        val vm = LoginViewModel(repo)
        vm.onPasswordChange("secret1")
        vm.onModeChange(LoginMode.ADMIN)
        assertEquals("", vm.state.value.password)
        vm.onEmailChange("admin@market")
        vm.onPasswordChange("Admin2026")
        vm.submit()
        assertEquals(R.string.v_email_invalid, vm.state.value.emailError)
        vm.onEmailChange(" admin@market.az ")
        vm.submit()
        assertEquals(listOf("admin:admin@market.az:Admin2026"), repo.calls)
    }

    // ---------------- Registration
    private fun filledRegister(vm: RegisterViewModel) {
        vm.onChange(RegisterField.FIRST_NAME, "Leyla")
        vm.onChange(RegisterField.LAST_NAME, "Həsənova")
        vm.onChange(RegisterField.PHONE, "055 123 45 67")
        vm.onChange(RegisterField.PASSWORD, "Market2026")
        vm.onChange(RegisterField.CONFIRM, "Market2026")
        vm.onChange(RegisterField.SETTLEMENT, "Qəbələ")
        vm.onChange(RegisterField.ADDRESS, "Nizami küç. 12")
    }

    @Test fun registrationReportsEveryInvalidField() {
        val vm = RegisterViewModel(repo)
        vm.onChange(RegisterField.PASSWORD, "short")
        vm.onChange(RegisterField.CONFIRM, "other")
        vm.submitForm()
        val e = vm.state.value.errors
        assertEquals(R.string.v_name_required, e[RegisterField.FIRST_NAME])
        assertEquals(R.string.v_phone_invalid, e[RegisterField.PHONE])
        assertEquals(R.string.v_password_short, e[RegisterField.PASSWORD])
        assertEquals(R.string.v_password_mismatch, e[RegisterField.CONFIRM])
        assertEquals(R.string.v_address_short, e[RegisterField.ADDRESS])
        assertTrue(repo.calls.isEmpty())
    }

    @Test fun registrationSendsAllDataAndMovesToTheCodeStep() {
        val vm = RegisterViewModel(repo)
        filledRegister(vm)
        vm.submitForm()
        val reg = repo.lastRegistration!!
        assertEquals("+994551234567", reg.phoneE164)
        assertEquals("Nizami küç. 12", reg.addressLine)
        assertEquals("Qəbələ", reg.settlement)
        val s = vm.state.value
        assertEquals(RegisterStep.CODE, s.step)
        assertEquals("", s.password)       // password wiped from memory after sign-up
        assertEquals(RESEND_SECONDS, s.resendSeconds)
    }

    @Test fun registrationCodeIsValidatedAndVerified() {
        val vm = RegisterViewModel(repo)
        filledRegister(vm); vm.submitForm()
        vm.onChange(RegisterField.CODE, "12a3")
        vm.verifyCode()
        assertEquals(R.string.v_otp_invalid, vm.state.value.errors[RegisterField.CODE])
        vm.onChange(RegisterField.CODE, "123456")
        vm.verifyCode()
        assertEquals("verify:+994551234567:123456", repo.calls.last())
    }

    @Test fun existingPhoneShowsAHelpfulError() {
        repo.failWith = AuthError.PHONE_EXISTS
        val vm = RegisterViewModel(repo)
        filledRegister(vm); vm.submitForm()
        assertEquals(RegisterStep.FORM, vm.state.value.step)
        assertEquals(AuthError.PHONE_EXISTS, vm.state.value.error)
    }

    @Test fun resendIsAvailableOnlyAfterTheCountdown() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val vm = RegisterViewModel(repo)
        filledRegister(vm); vm.submitForm(); runCurrent()
        assertEquals(RESEND_SECONDS, vm.state.value.resendSeconds)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(30, vm.state.value.resendSeconds)
        advanceTimeBy(31_000); runCurrent()
        assertEquals(0, vm.state.value.resendSeconds)
        vm.resendCode(); runCurrent()
        assertEquals("resend:+994551234567", repo.calls.last())
        assertTrue(vm.state.value.codeResent)
    }

    // ---------------- Forgot password
    @Test fun phoneRecoveryFlow() {
        val vm = ForgotPasswordViewModel(SavedStateHandle(mapOf("admin" to false)), repo)
        assertFalse(vm.state.value.byEmail)
        vm.onPhoneChange("070 765 43 21")
        vm.requestCode()
        assertEquals(ForgotStep.CODE, vm.state.value.step)
        vm.onCodeChange("654321")
        vm.verifyCode()
        assertEquals(listOf("reset-request:+994707654321", "reset-verify:+994707654321:654321"), repo.calls)
    }

    @Test fun adminRecoveryUsesEmail() {
        val vm = ForgotPasswordViewModel(SavedStateHandle(mapOf("admin" to true)), repo)
        assertTrue(vm.state.value.byEmail)
        vm.onEmailChange("bad")
        vm.requestCode()
        assertEquals(R.string.v_email_invalid, vm.state.value.inputError)
        vm.onEmailChange("admin@market.az")
        vm.requestCode()
        assertEquals("reset-request:admin@market.az", repo.calls.single())
    }

    @Test fun newPasswordMustFollowThePolicyAndMatch() {
        val vm = NewPasswordViewModel(repo)
        vm.onPasswordChange("abcdefgh"); vm.onConfirmChange("abcdefgh")
        vm.submit()
        assertEquals(R.string.v_password_digit, vm.state.value.passwordError)
        vm.onPasswordChange("Yeni2026x"); vm.onConfirmChange("Yeni2026")
        vm.submit()
        assertEquals(R.string.v_password_mismatch, vm.state.value.confirmError)
        vm.onConfirmChange("Yeni2026x")
        vm.submit()
        assertEquals("new-password:Yeni2026x", repo.calls.single())
        vm.cancel()
        assertEquals("abandon", repo.calls.last())
    }

    // ---------------- Admin MFA
    @Test fun firstAdminLoginStartsEnrollmentAndVerifiesWithTheNewFactor() {
        val vm = AdminMfaViewModel(SavedStateHandle(mapOf("hasVerifiedFactor" to false)), repo)
        assertEquals(repo.enrollment, vm.state.value.enrollment)
        vm.onCodeChange("123 456")
        vm.verify()
        assertEquals(listOf("enroll", "totp:123456:factor-1"), repo.calls)
    }

    @Test fun enrolledAdminOnlyEntersTheCode() {
        val vm = AdminMfaViewModel(SavedStateHandle(mapOf("hasVerifiedFactor" to true)), repo)
        assertNull(vm.state.value.enrollment)
        repo.failWith = AuthError.INVALID_MFA_CODE
        vm.onCodeChange("000000"); vm.verify()
        assertEquals(AuthError.INVALID_MFA_CODE, vm.state.value.error)
        assertEquals(listOf("totp:000000:null"), repo.calls)
    }
}
