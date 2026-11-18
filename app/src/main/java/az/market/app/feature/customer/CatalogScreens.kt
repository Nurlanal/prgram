package az.market.app.feature.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.market.app.R
import az.market.app.core.designsystem.component.FeaturePlaceholder
import az.market.app.core.designsystem.component.FeaturePlaceholderIcon
import az.market.app.core.designsystem.component.ProductCardItem
import az.market.app.core.designsystem.theme.Spacing
import az.market.app.data.catalog.ProductCard
import az.market.app.data.catalog.ProductSort

@Composable
private fun ProductGrid(
    products: List<ProductCard>,
    onProduct: (ProductCard) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    onReachedEnd: () -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, products.size) { if (nearEnd && products.isNotEmpty()) onReachedEnd() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        state = gridState,
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier.fillMaxSize().testTag("product_grid"),
    ) {
        header?.let { h -> item(span = { GridItemSpan(maxLineSpan) }) { h() } }
        items(products, key = { it.id }) { p -> ProductCardItem(p, onClick = { onProduct(p) }) }
        footer?.let { f -> item(span = { GridItemSpan(maxLineSpan) }) { f() } }
    }
}

@Composable
private fun CenterMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String?, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FeaturePlaceholderIcon(icon)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
        action?.invoke()
    }
}

// ---------------------------------------------------------------- Search
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onProduct: (ProductCard) -> Unit, viewModel: SearchViewModel = hiltViewModel(), autoFocus: Boolean = true) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_prompt)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Outlined.Close, null) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search_input"),
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val r = result) {
                SearchResult.Idle -> CenterMessage(Icons.Outlined.Search, stringResource(R.string.search_prompt), null)
                SearchResult.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(Modifier.padding(Spacing.xl).testTag("search_loading"))
                }
                SearchResult.Empty -> CenterMessage(Icons.Outlined.SearchOff, stringResource(R.string.search_empty), stringResource(R.string.search_empty_hint))
                is SearchResult.Failed -> CenterMessage(Icons.Outlined.SearchOff, stringResource(R.string.home_error_title), stringResource(r.error.message)) {
                    OutlinedButton(onClick = viewModel::retry) { Text(stringResource(R.string.action_retry)) }
                }
                is SearchResult.Found -> ProductGrid(r.products, onProduct)
            }
        }
    }
}

// ---------------------------------------------------------------- Category / "see all" list
private val sortLabels = listOf(
    ProductSort.POPULAR to R.string.sort_popular,
    ProductSort.NEW to R.string.sort_new,
    ProductSort.DISCOUNT to R.string.sort_discount,
    ProductSort.PRICE_ASC to R.string.sort_price_asc,
    ProductSort.PRICE_DESC to R.string.sort_price_desc,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(title: String, onBack: () -> Unit, onProduct: (ProductCard) -> Unit, viewModel: ProductListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(sortLabels) { (sort, label) ->
                    FilterChip(selected = state.sort == sort, onClick = { viewModel.onSortChange(sort) },
                        label = { Text(stringResource(label)) }, modifier = Modifier.testTag("sort_${sort.server}"))
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(Modifier.padding(Spacing.xl))
                }
                state.error != null && state.products.isEmpty() ->
                    CenterMessage(Icons.Outlined.SearchOff, stringResource(R.string.home_error_title), stringResource(state.error!!.message)) {
                        OutlinedButton(onClick = viewModel::reload) { Text(stringResource(R.string.action_retry)) }
                    }
                state.products.isEmpty() ->
                    CenterMessage(Icons.Outlined.ShoppingBasket, stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_body))
                else -> ProductGrid(
                    state.products, onProduct,
                    footer = if (state.loadingMore) ({ Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }) else null,
                    onReachedEnd = viewModel::loadMore,
                )
            }
        }
    }
}

/** Product details arrive in Stage 5; until then the card opens a clearly-labelled placeholder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailPlaceholderScreen(name: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            FeaturePlaceholder(Icons.Outlined.ShoppingBasket, stringResource(R.string.product_detail_title),
                stringResource(R.string.product_detail_desc), 5)
        }
    }
}
