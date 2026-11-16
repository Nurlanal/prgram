package az.market.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import az.market.app.core.designsystem.theme.MarketTheme
import az.market.app.data.admin.CourierAdminRepository
import az.market.app.data.auth.AuthError
import az.market.app.data.auth.AuthFlowState
import az.market.app.data.remote.BackendConfig
import az.market.app.data.remote.SupabaseProvider
import az.market.app.feature.admin.CourierCreator
import az.market.app.feature.admin.CreateCourierScreen
import az.market.app.feature.admin.CreateCourierViewModel
import az.market.app.feature.auth.AdminMfaScreen
import az.market.app.feature.auth.AdminMfaViewModel
import az.market.app.feature.auth.BlockedScreen
import az.market.app.feature.auth.LoginScreen
import az.market.app.feature.auth.LoginViewModel
import az.market.app.feature.auth.NewPasswordScreen
import az.market.app.feature.auth.NewPasswordViewModel
import az.market.app.feature.auth.RegisterScreen
import az.market.app.feature.auth.RegisterViewModel
import az.market.app.feature.auth.SessionActionsViewModel
import az.market.app.testing.FakeAuthRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AuthScreensUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val repo = FakeAuthRepository()

    private fun show(content: @Composable () -> Unit) = compose.setContent { MarketTheme { content() } }

    private fun snap(name: String) {
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun loginShowsValidationAndServerErrors() {
        val vm = LoginViewModel(repo)
        show { LoginScreen({}, {}, {}, vm) }
        compose.onNodeWithTag("login_phone").performTextInput("12345")
        compose.onNodeWithTag("login_submit").performClick()
        compose.onNodeWithText("Düzgün mobil nömrə", substring = true).assertIsDisplayed()

        repo.failWith = AuthError.INVALID_CREDENTIALS
        compose.onNodeWithTag("login_mode_admin").performClick()
        compose.onNodeWithTag("login_email").performTextInput("admin@market.az")
        compose.onNodeWithTag("login_password").performTextInput("Admin2026")
        compose.onNodeWithTag("login_submit").performClick()
        compose.onNodeWithText("Məlumatlar yanlışdır", substring = true).assertIsDisplayed()
        assertEquals("admin:admin@market.az:Admin2026", repo.calls.last())
        snap("09_login_admin_error")
    }

    @Test fun registrationGoesToTheSmsCodeStep() {
        val vm = RegisterViewModel(repo)
        show { RegisterScreen({}, {}, vm) }
        compose.onNodeWithTag("reg_submit").performScrollTo().performClick()
        compose.onNodeWithText("Ünvanı daxil edin").assertExists()

        compose.onNodeWithTag("reg_first_name").performTextInput("Leyla")
        compose.onNodeWithTag("reg_last_name").performTextInput("Həsənova")
        compose.onNodeWithTag("reg_phone").performTextInput("055 123 45 67")
        compose.onNodeWithTag("reg_password").performTextInput("Market2026")
        compose.onNodeWithTag("reg_confirm").performTextInput("Market2026")
        compose.onNodeWithTag("reg_settlement").performScrollTo().performTextInput("Qəbələ")
        compose.onNodeWithTag("reg_address").performScrollTo().performTextInput("Nizami küç. 12")
        compose.onNodeWithTag("reg_submit").performScrollTo().performClick()

        compose.onNodeWithText("+994 55 123 45 67", substring = true).assertIsDisplayed()
        compose.onNodeWithText("1:00", substring = true).assertExists() // 60 s countdown shown as m:ss
        compose.onNodeWithTag("reg_code").performTextInput("123456")
        compose.onNodeWithTag("reg_verify").performClick()
        assertEquals("verify:+994551234567:123456", repo.calls.last())
        snap("10_register_code")
    }

    @Test fun adminFirstLoginShowsAuthenticatorSetup() {
        val vm = AdminMfaViewModel(SavedStateHandle(mapOf("hasVerifiedFactor" to false)), repo)
        show { AdminMfaScreen(vm) }
        compose.onNodeWithTag("mfa_qr").assertIsDisplayed()
        compose.onNodeWithText("JBSW Y3DP EHPK 3PXP").assertExists()
        compose.onNodeWithTag("mfa_code").performScrollTo().performTextInput("123456")
        compose.onNodeWithTag("mfa_verify").performScrollTo().performClick()
        assertEquals("totp:123456:factor-1", repo.calls.last())
        snap("11_admin_mfa_setup")
    }

    @Test fun newPasswordScreen() {
        val vm = NewPasswordViewModel(repo)
        show { NewPasswordScreen(vm) }
        compose.onNodeWithTag("new_password").performTextInput("Yeni2026")
        compose.onNodeWithTag("new_password_confirm").performTextInput("Yeni2027")
        compose.onNodeWithTag("new_password_save").performClick()
        compose.onNodeWithText("Şifrələr eyni deyil").assertIsDisplayed()
        snap("12_new_password")
    }

    @Test fun blockedScreenOffersSignOut() {
        val vm = SessionActionsViewModel(repo, AuthFlowState())
        show { BlockedScreen(vm) }
        compose.onNodeWithText("Hesab bloklanıb").assertIsDisplayed()
        compose.onNodeWithText("Çıxış").performClick()
        assertEquals("sign-out", repo.calls.last())
    }

    @Test fun adminCreatesCourier() {
        val created = mutableListOf<String>()
        val vm = CreateCourierViewModel(CourierAdminRepository(SupabaseProvider(BackendConfig("", "")) { error("unused") })).apply {
            creator = CourierCreator { created += it.phone; "id" }
        }
        show { CreateCourierScreen({}, vm) }
        compose.onNodeWithTag("courier_first").performTextInput("Orxan")
        compose.onNodeWithTag("courier_last").performTextInput("Məmmədov")
        compose.onNodeWithTag("courier_phone").performTextInput("070 765 43 21")
        compose.onNodeWithTag("courier_password").performTextInput("Kuryer2026")
        compose.onNodeWithTag("courier_submit").performScrollTo().performClick()
        compose.onNodeWithTag("courier_created").assertExists()
        assertEquals(listOf("+994707654321"), created)
        snap("13_admin_create_courier")
    }
}
