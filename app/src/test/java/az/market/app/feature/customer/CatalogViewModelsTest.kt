package az.market.app.feature.customer

import androidx.lifecycle.SavedStateHandle
import az.market.app.data.auth.AuthError
import az.market.app.testing.FakeCatalogRepository
import az.market.app.testing.FakeCustomerRepository
import az.market.app.testing.MainDispatcherRule
import az.market.app.data.catalog.ProductSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelsTest {
    @get:Rule val main = MainDispatcherRule()
    private val catalog = FakeCatalogRepository()
    private val customer = FakeCustomerRepository()

    // ---------------- Home
    @Test fun homeLoadsFeedAddressAndCart() {
        val vm = HomeViewModel(catalog, customer)
        val s = vm.state.value
        assertEquals(false, s.loading)
        assertEquals("Çay Azərçay 100q", s.feed!!.discounted.first().name)
        assertEquals("a1", s.selectedAddress!!.id)
        assertEquals(3, s.cartCount)
    }

    @Test fun homeErrorThenRetry() {
        catalog.failWith = AuthError.NETWORK
        val vm = HomeViewModel(catalog, customer)
        assertEquals(AuthError.NETWORK, vm.state.value.error)
        assertNull(vm.state.value.feed)
        catalog.failWith = null
        vm.retry()
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.feed != null)
    }

    @Test fun headerFailureDoesNotHideTheCatalogue() {
        customer.fail = true
        val vm = HomeViewModel(catalog, customer)
        assertTrue(vm.state.value.feed != null)
        assertNull(vm.state.value.error)
        assertEquals(0, vm.state.value.cartCount)
    }

    @Test fun selectingAnAddressUpdatesTheHeader() {
        val vm = HomeViewModel(catalog, customer)
        vm.selectAddress("a2")
        assertEquals("a2", vm.state.value.selectedAddress!!.id)
    }

    // ---------------- Search
    @Test fun searchIsDebouncedAndOnlyTheLastQueryRuns() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SearchViewModel(catalog)
        runCurrent()
        vm.onQueryChange("Ç"); advanceTimeBy(100)
        vm.onQueryChange("Ça"); advanceTimeBy(100)
        vm.onQueryChange("Çay"); advanceTimeBy(SEARCH_DEBOUNCE_MS + 1); runCurrent()
        assertEquals(1, catalog.productCalls.size)
        assertTrue(catalog.productCalls.single().startsWith("search=Çay"))
        val result = vm.result.value as SearchResult.Found
        assertEquals("Çay Azərçay 100q", result.products.single().name)
    }

    @Test fun shortQueryStaysIdleAndUnknownQueryIsEmpty() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SearchViewModel(catalog)
        vm.onQueryChange("a"); advanceTimeBy(SEARCH_DEBOUNCE_MS + 1); runCurrent()
        assertEquals(SearchResult.Idle, vm.result.value)
        assertTrue(catalog.productCalls.isEmpty())
        vm.onQueryChange("yoxdur"); advanceTimeBy(SEARCH_DEBOUNCE_MS + 1); runCurrent()
        assertEquals(SearchResult.Empty, vm.result.value)
    }

    @Test fun searchErrorAndRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        catalog.failWith = AuthError.NETWORK
        val vm = SearchViewModel(catalog)
        vm.onQueryChange("çay"); advanceTimeBy(SEARCH_DEBOUNCE_MS + 1); runCurrent()
        assertEquals(SearchResult.Failed(AuthError.NETWORK), vm.result.value)
        catalog.failWith = null
        vm.retry(); advanceTimeBy(SEARCH_DEBOUNCE_MS + 1); runCurrent()
        assertTrue(vm.result.value is SearchResult.Found)
    }

    // ---------------- Product list
    @Test fun categoryListUsesArgumentsSortAndPaging() {
        val template = catalog.feed.newArrivals.first()
        catalog.products = (1..50).map { template.copy(id = "p$it", name = "Məhsul $it") }
        val vm = ProductListViewModel(SavedStateHandle(mapOf("categoryId" to 3L, "discounted" to false)), catalog)
        assertEquals(40, vm.state.value.products.size)
        assertTrue(catalog.productCalls.last().contains("cat=3"))
        assertTrue(catalog.productCalls.last().endsWith("limit=40 offset=0"))
        assertEquals(false, vm.state.value.endReached)

        vm.loadMore()
        assertTrue(catalog.productCalls.last().endsWith("offset=40"))
        assertEquals(50, vm.state.value.products.size)
        assertEquals(true, vm.state.value.endReached)       // 10 < 40 → no more pages
        vm.loadMore()
        assertEquals(2, catalog.productCalls.size)          // no request after the end

        vm.onSortChange(ProductSort.PRICE_ASC)
        assertTrue(catalog.productCalls.last().contains("sort=price_asc limit=40 offset=0"))
        assertEquals(40, vm.state.value.products.size)      // list restarted for the new sort
    }

    @Test fun discountedListDefaultsToDiscountSort() {
        val vm = ProductListViewModel(SavedStateHandle(mapOf("categoryId" to -1L, "discounted" to true)), catalog)
        assertEquals(ProductSort.DISCOUNT, vm.state.value.sort)
        assertTrue(catalog.productCalls.single().contains("cat=null disc=true sort=discount"))
    }
}
