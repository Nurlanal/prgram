package az.market.app.feature.customer

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.market.app.R
import az.market.app.core.designsystem.component.FeaturePlaceholderIcon
import az.market.app.core.designsystem.component.MarketLogo
import az.market.app.core.designsystem.component.ProductCardPlaceholder
import az.market.app.core.designsystem.component.ProductCardSmall
import az.market.app.core.designsystem.component.ProductImage
import az.market.app.core.designsystem.theme.LocalMarketColors
import az.market.app.core.designsystem.theme.Spacing
import az.market.app.data.catalog.Banner
import az.market.app.data.catalog.BonusSummary
import az.market.app.data.catalog.HomeCategory
import az.market.app.data.catalog.ProductCard
import az.market.app.data.customer.DeliveryAddress
import kotlinx.coroutines.delay

/** Navigation callbacks of the home tab. */
data class HomeActions(
    val onSearch: () -> Unit = {},
    val onCart: () -> Unit = {},
    val onBonus: () -> Unit = {},
    val onProduct: (ProductCard) -> Unit = {},
    val onCategory: (HomeCategory) -> Unit = {},
    val onSeeAll: (sectionTitle: Int, discounted: Boolean) -> Unit = { _, _ -> },
    val onBanner: (Banner) -> Unit = {},
)

@Composable
fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel(), autoScrollBanners: Boolean = true) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state, actions, viewModel::retry, viewModel::refresh, viewModel::selectAddress, autoScrollBanners)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    actions: HomeActions,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onSelectAddress: (String) -> Unit,
    autoScrollBanners: Boolean = true,
) {
    var addressSheet by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("home_list"),
            contentPadding = PaddingValues(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item { HomeHeader(state, onAddressClick = { addressSheet = true }, onCart = actions.onCart) }
            item { SearchBarButton(actions.onSearch) }
            val feed = state.feed
            when {
                state.loading -> item { HomeSkeleton() }
                feed == null && state.error != null -> item {
                    MessageBlock(Icons.Outlined.CloudOff, R.string.home_error_title, state.error.message) {
                        OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("home_retry")) { Text(stringResource(R.string.action_retry)) }
                    }
                }
                feed != null -> {
                    feed.bonus?.let { bonus -> item { BonusMiniCard(bonus, actions.onBonus) } }
                    if (feed.banners.isNotEmpty()) item { BannerCarousel(feed.banners, actions.onBanner, autoScrollBanners) }
                    if (feed.categories.isNotEmpty()) item { CategoryRow(feed.categories, actions.onCategory) }
                    if (!feed.hasProducts) {
                        item { MessageBlock(Icons.Outlined.Inventory2, R.string.home_empty_title, R.string.home_empty_body) {} }
                    }
                    productSection(R.string.home_section_discounts, feed.discounted, actions, discounted = true)
                    productSection(R.string.home_section_for_you, feed.forYou, actions, discounted = false)
                    productSection(R.string.home_section_best, feed.bestSellers, actions, discounted = false)
                    productSection(R.string.home_section_new, feed.newArrivals, actions, discounted = false)
                }
            }
        }
    }
    if (addressSheet) {
        AddressSheet(state.addresses, state.selectedAddress?.id, onSelect = { onSelectAddress(it); addressSheet = false },
            onDismiss = { addressSheet = false })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.productSection(
    @StringRes title: Int,
    products: List<ProductCard>,
    actions: HomeActions,
    discounted: Boolean,
) {
    if (products.isEmpty()) return
    item(key = "section_$title") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader(title) { actions.onSeeAll(title, discounted) }
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.testTag("section_$title"),
            ) {
                items(products, key = { it.id }) { p -> ProductCardSmall(p) { actions.onProduct(p) } }
            }
        }
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, onAddressClick: () -> Unit, onCart: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketLogo(markSize = 40.dp, modifier = Modifier.weight(1f))
            IconButton(onClick = onCart, modifier = Modifier.testTag("home_cart")) {
                val description = stringResource(R.string.cart_content_description, state.cartCount)
                BadgedBox(badge = { if (state.cartCount > 0) Badge { Text(state.cartCount.toString()) } }) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = description)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onAddressClick)
                .padding(vertical = Spacing.xs)
                .testTag("home_address"),
        ) {
            Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.padding(horizontal = Spacing.sm).weight(1f, fill = false)) {
                Text(stringResource(R.string.home_deliver_to), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    state.selectedAddress?.let { "${it.label} · ${it.addressLine}" } ?: stringResource(R.string.home_no_address),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Outlined.ExpandMore, null)
        }
    }
}

@Composable
private fun SearchBarButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).testTag("home_search"),
    ) {
        Row(Modifier.padding(horizontal = Spacing.lg, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun BonusMiniCard(bonus: BonusSummary, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).testTag("home_bonus"),
    ) {
        Row(
            Modifier
                .background(Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.75f), LocalMarketColors.current.accent)))
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CardGiftcard, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Column(Modifier.padding(horizontal = Spacing.md).weight(1f)) {
                Text(stringResource(R.string.home_bonus_title), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
                Text(bonus.balance.format(), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.home_bonus_card, bonus.cardNumber.takeLast(4)), color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
        }
    }
}

@Composable
private fun BannerCarousel(banners: List<Banner>, onClick: (Banner) -> Unit, autoScroll: Boolean) {
    val pager = rememberPagerState { banners.size }
    if (autoScroll && banners.size > 1) {
        LaunchedEffect(pager) {
            while (true) {
                delay(5_000)
                pager.animateScrollToPage((pager.currentPage + 1) % banners.size)
            }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            pageSpacing = Spacing.md,
            modifier = Modifier.testTag("home_banners"),
        ) { page ->
            val banner = banners[page]
            Surface(onClick = { onClick(banner) }, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().aspectRatio(2.2f)) {
                Box {
                    ProductImage(banner.imageUrl, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(Spacing.lg)) {
                        Text(banner.title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                        if (banner.subtitle.isNotBlank()) {
                            Text(banner.subtitle, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (banners.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                banners.indices.forEach { i ->
                    Box(
                        Modifier.size(width = if (i == pager.currentPage) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(categories: List<HomeCategory>, onClick: (HomeCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.home_categories), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.lg))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.testTag("home_categories"),
        ) {
            items(categories, key = { it.id }) { c ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(76.dp).clip(MaterialTheme.shapes.medium).clickable { onClick(c) }
                        .padding(vertical = Spacing.xs).testTag("category_${c.id}"),
                ) {
                    Box(
                        Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (c.imageUrl != null) {
                            ProductImage(c.imageUrl, Modifier.fillMaxSize())
                        } else {
                            Text(c.name.take(1), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, minLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onSeeAll) { Text(stringResource(R.string.see_all)) }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.testTag("home_loading")) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).aspectRatio(2.2f).clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant))
        Row(Modifier.padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            repeat(3) { ProductCardPlaceholder() }
        }
    }
}

@Composable
private fun MessageBlock(icon: androidx.compose.ui.graphics.vector.ImageVector, @StringRes title: Int, @StringRes body: Int,
                         action: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FeaturePlaceholderIcon(icon)
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressSheet(addresses: List<DeliveryAddress>, selectedId: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = Spacing.xl)) {
            Text(stringResource(R.string.address_sheet_title), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
            addresses.forEach { a ->
                ListItem(
                    leadingContent = { RadioButton(selected = a.id == selectedId, onClick = { onSelect(a.id) }) },
                    headlineContent = { Text(a.label) },
                    supportingContent = { Text(listOf(a.addressLine, a.settlement).filter { it.isNotBlank() }.joinToString(", ")) },
                    modifier = Modifier.clickable { onSelect(a.id) }.testTag("address_${a.id}"),
                )
            }
            Text(stringResource(R.string.address_add_later), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.lg))
        }
    }
}

