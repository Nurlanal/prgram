package az.market.app.feature.customer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import az.market.app.R
import az.market.app.core.designsystem.component.FeaturePlaceholder
import az.market.app.core.designsystem.component.MarketLogo
import az.market.app.core.designsystem.theme.Spacing
import az.market.app.navigation.CartTabRoute
import az.market.app.navigation.CategoriesTabRoute
import az.market.app.navigation.FavoritesTabRoute
import az.market.app.navigation.HomeTabRoute
import az.market.app.navigation.ProfileTabRoute
import kotlin.reflect.KClass

internal enum class CustomerTab(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    HOME(HomeTabRoute, HomeTabRoute::class, R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    CATEGORIES(CategoriesTabRoute, CategoriesTabRoute::class, R.string.tab_categories, Icons.Filled.Category, Icons.Outlined.Category),
    FAVORITES(FavoritesTabRoute, FavoritesTabRoute::class, R.string.tab_favorites, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    CART(CartTabRoute, CartTabRoute::class, R.string.tab_cart, Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
    PROFILE(ProfileTabRoute, ProfileTabRoute::class, R.string.tab_profile, Icons.Filled.Person, Icons.Outlined.Person),
}

/** Customer area with the 5-item bottom navigation. Tab state is saved/restored per tab. */
@Composable
fun CustomerShell(onSignOut: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                CustomerTab.entries.forEach { tab ->
                    val selected = current?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = {
                            Text(
                                stringResource(tab.label),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeTabRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<HomeTabRoute> { HomeTabPlaceholder() }
            composable<CategoriesTabRoute> {
                TabPlaceholder(Icons.Outlined.Category, R.string.tab_categories, R.string.categories_placeholder_desc, 5)
            }
            composable<FavoritesTabRoute> {
                TabPlaceholder(Icons.Outlined.FavoriteBorder, R.string.tab_favorites, R.string.favorites_placeholder_desc, 13)
            }
            composable<CartTabRoute> {
                TabPlaceholder(Icons.Outlined.ShoppingCart, R.string.tab_cart, R.string.cart_placeholder_desc, 6)
            }
            composable<ProfileTabRoute> {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FeaturePlaceholder(Icons.Outlined.Person, stringResource(R.string.tab_profile),
                        stringResource(R.string.profile_placeholder_desc), 13)
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.testTag("sign_out")) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null)
                        Text("  " + stringResource(R.string.action_sign_out))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabPlaceholder() {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        MarketLogo()
        // Visual only: search becomes functional in Stage 5.
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.lg, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.search_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        FeaturePlaceholder(
            icon = Icons.Outlined.Storefront,
            title = stringResource(R.string.tab_home),
            description = stringResource(R.string.home_placeholder_desc),
            stage = 4,
        )
    }
}

@Composable
private fun TabPlaceholder(icon: ImageVector, @StringRes title: Int, @StringRes desc: Int, stage: Int) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeaturePlaceholder(icon, stringResource(title), stringResource(desc), stage)
    }
}
