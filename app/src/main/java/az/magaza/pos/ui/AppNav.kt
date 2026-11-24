package az.magaza.pos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.magaza.pos.ui.cash.CashScreen
import az.magaza.pos.ui.components.LocalSnackbar
import az.magaza.pos.ui.components.app
import az.magaza.pos.ui.customers.CustomerDetailScreen
import az.magaza.pos.ui.customers.CustomersScreen
import az.magaza.pos.ui.customers.DebtsScreen
import az.magaza.pos.ui.dashboard.DashboardScreen
import az.magaza.pos.ui.expense.ExpenseScreen
import az.magaza.pos.ui.history.SaleDetailScreen
import az.magaza.pos.ui.history.SaleHistoryScreen
import az.magaza.pos.ui.inventory.InventoryScreen
import az.magaza.pos.ui.inventory.ProductDetailScreen
import az.magaza.pos.ui.inventory.ProductEditScreen
import az.magaza.pos.ui.purchase.PurchaseScreen
import az.magaza.pos.ui.reports.ReportsScreen
import az.magaza.pos.ui.sales.SalesScreen
import az.magaza.pos.ui.settings.ActivityLogScreen
import az.magaza.pos.ui.settings.BackupScreen
import az.magaza.pos.ui.settings.SettingsScreen
import az.magaza.pos.ui.suppliers.SupplierDetailScreen
import az.magaza.pos.ui.suppliers.SuppliersScreen
import kotlinx.coroutines.launch

object Routes {
    const val DASHBOARD = "dashboard"
    const val SALES = "sales"
    const val INVENTORY = "inventory"
    const val CASH = "cash"
    const val REPORTS = "reports"
    const val PURCHASES = "purchases"
    const val EXPENSES = "expenses"
    const val DEBTS = "debts"
    const val CUSTOMERS = "customers"
    const val SUPPLIERS = "suppliers"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val LOG = "log"
    const val STOCK_COUNT = "stockcount"
    fun productEdit(id: Long = 0, barcode: String = "") = "product/edit/$id?barcode=${android.net.Uri.encode(barcode)}"
    fun product(id: Long) = "product/$id"
    fun customer(id: Long) = "customer/$id"
    fun supplier(id: Long) = "supplier/$id"
    fun sale(id: Long) = "sale/$id"
    fun purchaseFor(productId: Long) = "purchases?product=$productId"
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.DASHBOARD, "Ana səhifə", Icons.Default.Dashboard),
    BottomItem(Routes.SALES, "Satış", Icons.Default.PointOfSale),
    BottomItem(Routes.INVENTORY, "Anbar", Icons.Default.Inventory2),
    BottomItem(Routes.CASH, "Kassa", Icons.Default.AccountBalanceWallet),
    BottomItem(Routes.REPORTS, "Hesabat", Icons.Default.BarChart),
)

private data class DrawerItem(val route: String, val label: String, val icon: ImageVector)

private val drawerItems = listOf(
    DrawerItem(Routes.PURCHASES, "Mədaxil (anbara daxil)", Icons.Default.MoveToInbox),
    DrawerItem(Routes.STOCK_COUNT, "Anbar sayımı (qalıq düzəlişi)", Icons.Default.FactCheck),
    DrawerItem(Routes.EXPENSES, "Məxaric (xərclər)", Icons.Default.Outbox),
    DrawerItem(Routes.DEBTS, "Borclar", Icons.Default.RequestQuote),
    DrawerItem(Routes.CUSTOMERS, "Müştərilər", Icons.Default.People),
    DrawerItem(Routes.SUPPLIERS, "Təchizatçılar", Icons.Default.LocalShipping),
    DrawerItem(Routes.HISTORY, "Satış tarixçəsi", Icons.AutoMirrored.Filled.ReceiptLong),
    DrawerItem(Routes.LOG, "Əməliyyat tarixçəsi", Icons.Default.History),
    DrawerItem(Routes.BACKUP, "Backup və bərpa", Icons.Default.Backup),
    DrawerItem(Routes.SETTINGS, "Ayarlar", Icons.Default.Settings),
)

@Composable
fun PosNavRoot(
    nav: NavHostController = rememberNavController(),
    openCustomer: Long? = null,
    onCustomerOpened: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottom = bottomItems.any { it.route == route }
    val settings by app().settings.state.collectAsState()

    fun go(r: String) {
        nav.navigate(r) {
            popUpTo(Routes.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }

    // Borc bildirişinə toxunulduqda müştərinin səhifəsini açırıq
    LaunchedEffect(openCustomer) {
        if (openCustomer != null) {
            nav.navigate(Routes.customer(openCustomer)) { launchSingleTop = true }
            onCustomerOpened()
        }
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = showBottom || drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(settings.storeName, style = MaterialTheme.typography.titleLarge)
                                Text("Offline satış sistemi", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        drawerItems.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label) },
                                icon = { Icon(item.icon, null) },
                                selected = route == item.route,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    nav.navigate(item.route) { launchSingleTop = true }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            },
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (showBottom) {
                        NavigationBar {
                            bottomItems.forEach { item ->
                                NavigationBarItem(
                                    selected = route == item.route,
                                    onClick = { go(item.route) },
                                    icon = { Icon(item.icon, null) },
                                    label = { Text(item.label, maxLines = 1) },
                                )
                            }
                        }
                    }
                },
                contentWindowInsets = WindowInsets(0),
            ) { pad ->
                Box(Modifier.padding(pad).consumeWindowInsets(pad)) {
                    NavHost(nav, startDestination = Routes.DASHBOARD) {
                        composable(Routes.DASHBOARD) { DashboardScreen(nav, openMenu) }
                        composable(Routes.SALES) { SalesScreen(nav, openMenu) }
                        composable(Routes.INVENTORY) { InventoryScreen(nav, openMenu) }
                        composable(Routes.CASH) { CashScreen(nav, openMenu) }
                        composable(Routes.REPORTS) { ReportsScreen(nav, openMenu) }
                        composable(
                            "purchases?product={product}",
                            arguments = listOf(navArgument("product") { type = NavType.LongType; defaultValue = 0L }),
                        ) { PurchaseScreen(nav, it.arguments?.getLong("product") ?: 0L) }
                        composable(Routes.EXPENSES) { ExpenseScreen(nav) }
                        composable(Routes.DEBTS) { DebtsScreen(nav) }
                        composable(Routes.CUSTOMERS) { CustomersScreen(nav) }
                        composable(Routes.SUPPLIERS) { SuppliersScreen(nav) }
                        composable(Routes.HISTORY) { SaleHistoryScreen(nav) }
                        composable(Routes.SETTINGS) { SettingsScreen(nav) }
                        composable(Routes.BACKUP) { BackupScreen(nav) }
                        composable(Routes.LOG) { ActivityLogScreen(nav) }
                        composable(Routes.STOCK_COUNT) { az.magaza.pos.ui.inventory.StockCountScreen(nav) }
                        composable(
                            "product/edit/{id}?barcode={barcode}",
                            arguments = listOf(
                                navArgument("id") { type = NavType.LongType },
                                navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) {
                            ProductEditScreen(nav, it.arguments!!.getLong("id"), it.arguments?.getString("barcode").orEmpty())
                        }
                        composable("product/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                            ProductDetailScreen(nav, it.arguments!!.getLong("id"))
                        }
                        composable("customer/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                            CustomerDetailScreen(nav, it.arguments!!.getLong("id"))
                        }
                        composable("supplier/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                            SupplierDetailScreen(nav, it.arguments!!.getLong("id"))
                        }
                        composable("sale/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                            SaleDetailScreen(nav, it.arguments!!.getLong("id"))
                        }
                    }
                }
            }
        }
    }
}
