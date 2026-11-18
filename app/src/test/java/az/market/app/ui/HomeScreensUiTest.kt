package az.market.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import az.market.app.R
import az.market.app.core.designsystem.theme.MarketTheme
import az.market.app.data.auth.AuthError
import az.market.app.feature.customer.HomeActions
import az.market.app.feature.customer.HomeContent
import az.market.app.feature.customer.HomeUiState
import az.market.app.feature.customer.SEARCH_DEBOUNCE_MS
import az.market.app.feature.customer.SearchScreen
import az.market.app.feature.customer.SearchViewModel
import az.market.app.testing.FakeCatalogRepository
import az.market.app.testing.FakeCustomerRepository
import az.market.app.testing.fixtureHomeFeed
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
class HomeScreensUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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

    private val loaded = HomeUiState(loading = false, feed = fixtureHomeFeed(), addresses = FakeCustomerRepository().addresses, cartCount = 3)

    @Test fun homeShowsAllSectionsWithServerPrices() {
        val opened = mutableListOf<String>()
        show {
            HomeContent(loaded, HomeActions(onProduct = { opened += it.name }, onSeeAll = { _, d -> opened += "all:$d" }),
                {}, {}, {}, autoScrollBanners = false)
        }
        compose.onNodeWithText("Ev · Nizami küç. 12").assertIsDisplayed()
        compose.onNodeWithText("Ərzaq həftəsi -20%").assertIsDisplayed()
        compose.onNodeWithTag("home_bonus").assertIsDisplayed()
        snap("14_home_top")

        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("section_${R.string.home_section_discounts}"))
        compose.onNodeWithText("3,49 ₼").assertIsDisplayed()       // server final price
        compose.onNodeWithText("5,00 ₼").assertIsDisplayed()       // struck-through old price
        compose.onNodeWithText("-30%").assertIsDisplayed()
        snap("15_home_sections")
        compose.onNodeWithText("Çay Azərçay 100q").performClick()
        assertEquals("Çay Azərçay 100q", opened.last())
    }

    @Test fun homeErrorStateHasRetry() {
        var retried = false
        show { HomeContent(HomeUiState(loading = false, error = AuthError.NETWORK), HomeActions(), { retried = true }, {}, {}) }
        compose.onNodeWithText("İnternet bağlantısı yoxdur", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("home_retry").performClick()
        assertEquals(true, retried)
    }

    @Test fun emptyStoreShowsAFriendlyMessage() {
        val empty = fixtureHomeFeed().copy(discounted = emptyList(), newArrivals = emptyList(), bestSellers = emptyList(), forYou = emptyList(), banners = emptyList())
        show { HomeContent(HomeUiState(loading = false, feed = empty), HomeActions(), {}, {}, {}) }
        compose.onNodeWithText("Məhsullar tezliklə").assertIsDisplayed()
    }

    @Test fun addressSheetSwitchesTheDeliveryAddress() {
        var selected: String? = null
        show { HomeContent(loaded, HomeActions(), {}, {}, { selected = it }, autoScrollBanners = false) }
        compose.onNodeWithTag("home_address").performClick()
        compose.onNodeWithTag("address_a2").performClick()
        assertEquals("a2", selected)
    }

    // Letter folding ("seker" → "Şəkər") is verified against PostgreSQL in supabase/tests; here: the screen flow.
    @Test fun searchShowsResultsAfterTheDebounce() {
        val catalog = FakeCatalogRepository()
        val vm = SearchViewModel(catalog)
        show { SearchScreen({}, {}, vm, autoFocus = false) }
        compose.onNodeWithTag("search_input").performTextInput("Çay")
        // The 300 ms debounce runs on Dispatchers.Main = Robolectric's main looper.
        org.robolectric.shadows.ShadowLooper.idleMainLooper(SEARCH_DEBOUNCE_MS + 50, java.util.concurrent.TimeUnit.MILLISECONDS)
        compose.waitForIdle()
        assertEquals(1, catalog.productCalls.size)
        compose.onNodeWithText("Çay Azərçay 100q").assertIsDisplayed()
        snap("16_search")
    }
}
