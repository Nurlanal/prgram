package az.market.app.feature.customer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.market.app.data.auth.AuthError
import az.market.app.data.auth.AuthException
import az.market.app.data.catalog.CatalogRepository
import az.market.app.data.catalog.HomeFeed
import az.market.app.data.catalog.ProductCard
import az.market.app.data.catalog.ProductSort
import az.market.app.data.customer.CustomerRepository
import az.market.app.data.customer.DeliveryAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun Throwable.asAuthError(): AuthError = (this as? AuthException)?.error ?: AuthError.UNKNOWN

// ---------------------------------------------------------------- Home
data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val feed: HomeFeed? = null,
    val error: AuthError? = null,
    val addresses: List<DeliveryAddress> = emptyList(),
    val cartCount: Int = 0,
) {
    val selectedAddress: DeliveryAddress? get() = addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val customer: CustomerRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load(refresh = false) }

    fun retry() = load(refresh = false)
    fun refresh() = load(refresh = true)

    private fun load(refresh: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = !refresh && it.feed == null, refreshing = refresh, error = null) }
            try {
                coroutineScope {
                    val feed = async { catalog.homeFeed() }
                    // Header data is secondary: a failure there must not hide the catalogue.
                    val addresses = async { runCatchingNonCancel { customer.addresses() } ?: emptyList() }
                    val cart = async { runCatchingNonCancel { customer.cartItemCount() } ?: 0 }
                    val f = feed.await()
                    _state.update { it.copy(feed = f, addresses = addresses.await(), cartCount = cart.await()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.asAuthError()) }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    fun selectAddress(id: String) {
        viewModelScope.launch {
            try {
                customer.setDefaultAddress(id)
                _state.update { it.copy(addresses = customer.addresses()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.asAuthError()) }
            }
        }
    }

    private suspend fun <T> runCatchingNonCancel(block: suspend () -> T): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
}

// ---------------------------------------------------------------- Search
sealed interface SearchResult {
    data object Idle : SearchResult
    data object Loading : SearchResult
    data class Found(val products: List<ProductCard>) : SearchResult
    data object Empty : SearchResult
    data class Failed(val error: AuthError) : SearchResult
}

const val SEARCH_DEBOUNCE_MS = 300L
const val SEARCH_MIN_LENGTH = 2

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val catalog: CatalogRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _result = MutableStateFlow<SearchResult>(SearchResult.Idle)
    val result: StateFlow<SearchResult> = _result.asStateFlow()
    private val retryTick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_query.map { it.trim() }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MS), retryTick) { q, _ -> q }
                .collectLatest { q -> // a newer query cancels the older request
                    if (q.length < SEARCH_MIN_LENGTH) {
                        _result.value = SearchResult.Idle
                        return@collectLatest
                    }
                    _result.value = SearchResult.Loading
                    _result.value = try {
                        val list = catalog.products(search = q, limit = 60)
                        if (list.isEmpty()) SearchResult.Empty else SearchResult.Found(list)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SearchResult.Failed(e.asAuthError())
                    }
                }
        }
    }

    fun onQueryChange(value: String) { _query.value = value.take(80) }
    fun retry() { retryTick.value++ }
}

// ---------------------------------------------------------------- Product list (category / "see all")
data class ProductListUiState(
    val sort: ProductSort = ProductSort.POPULAR,
    val products: List<ProductCard> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: AuthError? = null,
)

@HiltViewModel
class ProductListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val categoryId: Long? = savedStateHandle.get<Long>("categoryId")?.takeIf { it > 0 }
    private val discountedOnly: Boolean = savedStateHandle.get<Boolean>("discounted") == true
    private val _state = MutableStateFlow(
        ProductListUiState(sort = if (discountedOnly) ProductSort.DISCOUNT else ProductSort.POPULAR),
    )
    val state: StateFlow<ProductListUiState> = _state.asStateFlow()
    private var job: kotlinx.coroutines.Job? = null

    init { reload() }

    fun onSortChange(sort: ProductSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        reload()
    }

    fun reload() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, products = emptyList(), endReached = false) }
            fetch(offset = 0)
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached || s.error != null) return
        job = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            fetch(offset = s.products.size)
        }
    }

    private suspend fun fetch(offset: Int) {
        try {
            val page = catalog.products(
                categoryId = categoryId, discountedOnly = discountedOnly, sort = _state.value.sort,
                limit = PAGE_SIZE, offset = offset,
            )
            _state.update {
                it.copy(products = it.products + page, endReached = page.size < PAGE_SIZE, loading = false, loadingMore = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e.asAuthError(), loading = false, loadingMore = false) }
        }
    }

    companion object { const val PAGE_SIZE = 40 }
}
