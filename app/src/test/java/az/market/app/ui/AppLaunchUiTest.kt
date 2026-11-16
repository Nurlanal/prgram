package az.market.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        snap("01_welcome")
    }

    @Test fun signInLeadsToStage3Placeholder() {
        compose.onNodeWithText("Daxil ol").performScrollTo().performClick()
        compose.onNodeWithTag("placeholder_stage_3").assertExists()
    }

    @Test fun customerShellBottomNavigationWorks() {
        compose.onNodeWithText("Müştəri").performScrollTo().performClick()
        compose.onNodeWithTag("placeholder_stage_4").assertExists()
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
        compose.onNodeWithTag("placeholder_stage_4").assertExists()
        snap("06_customer_home_dark")
    }
}
