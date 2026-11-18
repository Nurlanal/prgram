package az.market.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import az.market.app.MainActivity
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Launches the real MainActivity (Hilt graph, splash, theme, navigation) on the JVM and walks
 * through all three role shells. Screenshots are written to app/build/screenshots/ for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppLaunchUiTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun snap(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        compose.waitForIdle()
        // Draw the real view hierarchy (Robolectric NATIVE graphics) into a bitmap.
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun welcomeScreenOpens() {
        compose.onNodeWithText("Xoş gəlmisiniz!").assertExists()
        compose.onNodeWithText("Daxil ol").assertExists()
        // This build has no SUPABASE_URL / SUPABASE_ANON_KEY, so the app must say so explicitly.
        compose.onNodeWithTag("backend_status").performScrollTo()
        compose.onNodeWithText("Server: konfiqurasiya edilməyib", substring = true).assertExists()
        snap("01_welcome")
    }

    @Test fun signInOpensTheRealLoginScreen() {
        compose.onNodeWithText("Daxil ol").performScrollTo().performClick()
        compose.onNodeWithTag("login_phone").assertExists()
        compose.onNodeWithTag("login_password").assertExists()
        snap("07_login")
        // Backend is not configured in this test build → a clear error, no crash.
        compose.onNodeWithTag("login_phone").performTextInput("050 123 45 67")
        compose.onNodeWithTag("login_password").performTextInput("Market2026")
        compose.onNodeWithTag("login_submit").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Server konfiqurasiya edilməyib", substring = true).assertExists()
    }

    @Test fun registerOpensTheRegistrationForm() {
        compose.onNodeWithText("Qeydiyyatdan keç").performScrollTo().performClick()
        compose.onNodeWithTag("reg_first_name").assertExists()
        compose.onNodeWithTag("reg_address").assertExists()
        snap("08_register")
    }

    @Test fun customerShellBottomNavigationWorks() {
        compose.onNodeWithText("Müştəri").performScrollTo().performClick()
        // No backend in this build: the real home screen shows a clear error with retry.
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("home_retry").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Server konfiqurasiya edilməyib", substring = true).assertExists()
        snap("02_customer_home")
        compose.onNodeWithText("Səbət").performClick()
        compose.onNodeWithTag("placeholder_stage_6").assertExists()
        compose.onNodeWithText("Kateqoriyalar").performClick()
        compose.onNodeWithTag("placeholder_stage_5").assertExists()
        snap("03_customer_categories")
    }

    @Test fun adminShellOpens() {
        compose.onNodeWithText("Admin").performScrollTo().performClick()
        compose.onNodeWithText("Admin paneli").assertExists()
        snap("04_admin")
    }

    @Test fun courierShellOpens() {
        compose.onNodeWithText("Kuryer").performScrollTo().performClick()
        compose.onNodeWithText("Kuryer paneli").assertExists()
        compose.onNodeWithTag("placeholder_stage_10").assertExists()
        snap("05_courier")
    }

    @Test
    @Config(qualifiers = "+night")
    fun darkModeRenders() {
        compose.onNodeWithText("Müştəri").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("home_retry").fetchSemanticsNodes().isNotEmpty() }
        snap("06_customer_home_dark")
    }
}
