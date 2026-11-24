package az.magaza.pos.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import az.magaza.pos.data.PosRepository
import az.magaza.pos.data.db.ProductSalesRow
import az.magaza.pos.data.db.Sale
import az.magaza.pos.data.db.SaleStatus
import az.magaza.pos.ui.Routes
import az.magaza.pos.ui.components.*
import az.magaza.pos.ui.theme.LocalExtraColors
import az.magaza.pos.ui.theme.NumberStyle
import az.magaza.pos.util.DateRange
import az.magaza.pos.util.Dates
import az.magaza.pos.util.RangePreset
import az.magaza.pos.util.money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class DashState(
    val sales: Long = 0,
    val profit: Long = 0,
    val salesCount: Int = 0,
    val itemsSold: Long = 0,
    val purchases: Long = 0,
    val expenses: Long = 0,
    val cash: Long = 0,
    val cashOnly: Long = 0,
    val card: Long = 0,
    val customerDebt: Long = 0,
    val supplierDebt: Long = 0,
    val stockCount: Long = 0,
    val stockCost: Long = 0,
    val stockSale: Long = 0,
    val lowStock: Int = 0,
    val chart: List<ChartBar> = emptyList(),
    val top: List<ProductSalesRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(repo: PosRepository) : ViewModel() {
    private val dao = repo.dao
    val range = MutableStateFlow(DateRange.of(RangePreset.TODAY))

    val state: StateFlow<DashState> = range.flatMapLatest { r ->
        val chartRange = chartRange(r)
        val flows: List<Flow<Any>> = listOf(
            dao.observeSalesTotal(r.from, r.to), dao.observeProfitTotal(r.from, r.to), dao.observeSalesCount(r.from, r.to),
            dao.observeItemsSold(r.from, r.to), dao.observePurchaseTotal(r.from, r.to), dao.observeExpenseTotal(r.from, r.to),
            dao.observeCashBalance(), dao.observeCashBalanceBy("CASH"), dao.observeCashBalanceBy("CARD"),
            dao.observeTotalCustomerDebt(), dao.observeTotalSupplierDebt(), dao.observeTotalStock(),
            dao.observeStockCostValue(), dao.observeStockSaleValue(), dao.observeLowStockCount(),
            dao.observeSalesInRange(chartRange.from, chartRange.to), dao.observeTopProducts(r.from, r.to, 5),
        )
        combine(flows) { a ->
            @Suppress("UNCHECKED_CAST")
            DashState(
                sales = a[0] as Long, profit = a[1] as Long, salesCount = a[2] as Int, itemsSold = a[3] as Long,
                purchases = a[4] as Long, expenses = a[5] as Long, cash = a[6] as Long, cashOnly = a[7] as Long,
                card = a[8] as Long, customerDebt = a[9] as Long, supplierDebt = a[10] as Long, stockCount = a[11] as Long,
                stockCost = a[12] as Long, stockSale = a[13] as Long, lowStock = a[14] as Int,
                chart = buildChart(r, a[15] as List<Sale>), top = a[16] as List<ProductSalesRow>,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashState())

    companion object {
        /** "Bu gün" seçiləndə qrafik son 7 günü göstərir. */
        fun chartRange(r: DateRange): DateRange =
            if (r.preset == RangePreset.TODAY) DateRange.of(RangePreset.WEEK) else r

        fun buildChart(r: DateRange, sales: List<Sale>): List<ChartBar> {
            val active = sales.filter { it.status == SaleStatus.ACTIVE }
            val cr = chartRange(r)
            val from = if (cr.preset == RangePreset.ALL) (active.minOfOrNull { it.createdAt } ?: System.currentTimeMillis()) else cr.from
            val days = ((Dates.startOfDay(cr.to) - Dates.startOfDay(from)) / 86_400_000L + 1).toInt()
            return if (days <= 31) {
                (0 until days).map { i ->
                    val c = Calendar.getInstance().apply { timeInMillis = Dates.startOfDay(from); add(Calendar.DAY_OF_YEAR, i) }
                    val s = c.timeInMillis
                    val e = Dates.endOfDay(s)
                    val inDay = active.filter { it.createdAt in s..e }
                    ChartBar(Dates.shortDay(s), inDay.sumOf { it.total }, inDay.sumOf { it.profit })
                }
            } else {
                val months = mutableListOf<ChartBar>()
                val c = Calendar.getInstance().apply { timeInMillis = Dates.startOfDay(cr.to); set(Calendar.DAY_OF_MONTH, 1) }
                repeat(12) {
                    val s = c.timeInMillis
                    val e = Calendar.getInstance().apply { timeInMillis = s; add(Calendar.MONTH, 1) }.timeInMillis - 1
                    if (e >= from) {
                        val inM = active.filter { it.createdAt in s..e }
                        months.add(0, ChartBar(String.format("%02d.%02d", c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR) % 100),
                            inM.sumOf { it.total }, inM.sumOf { it.profit }))
                    }
                    c.add(Calendar.MONTH, -1)
                }
                months
            }
        }
    }
}

@Composable
fun DashboardScreen(nav: NavController, openMenu: () -> Unit) {
    val app = app()
    val vm: DashboardViewModel = viewModel { DashboardViewModel(app.repo) }
    val s by vm.state.collectAsState()
    val range by vm.range.collectAsState()
    val settings by app.settings.state.collectAsState()
    val extra = LocalExtraColors.current
    val cs = MaterialTheme.colorScheme

    Scaffold(topBar = {
        PosTopBar(settings.storeName, onMenu = openMenu, actions = {
            IconButton(onClick = { nav.navigate(Routes.HISTORY) }) { Icon(Icons.Default.History, "Satış tarixçəsi") }
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, "Ayarlar") }
        })
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RangeSelector(range, { vm.range.value = it }) }
            item {
                // Əsas kart: satış və qazanc
                Card(colors = CardDefaults.cardColors(containerColor = cs.primary, contentColor = cs.onPrimary)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(if (range.preset == RangePreset.TODAY) "Bugünkü satış" else "Satış", style = MaterialTheme.typography.labelLarge)
                        Text(s.sales.money(), style = MaterialTheme.typography.headlineMedium.merge(NumberStyle))
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(if (range.preset == RangePreset.TODAY) "Bugünkü qazanc" else "Qazanc", style = MaterialTheme.typography.labelMedium)
                                Text(s.profit.money(), style = MaterialTheme.typography.titleLarge.merge(NumberStyle))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Satış / məhsul", style = MaterialTheme.typography.labelMedium)
                                Text("${s.salesCount} / ${s.itemsSold} əd.", style = MaterialTheme.typography.titleLarge.merge(NumberStyle))
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Yeni satış", Icons.Default.AddShoppingCart, { nav.navigate(Routes.SALES) { launchSingleTop = true } }, Modifier.weight(1f))
                    BigButton("Mədaxil", Icons.Default.MoveToInbox, { nav.navigate(Routes.PURCHASES) }, Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors())
                }
            }
            item {
                Grid(
                    listOf(
                        { m -> StatCard("Kassa balansı", s.cash.money(), Icons.Default.AccountBalanceWallet, m, tint = cs.secondary,
                            subtitle = "Nağd ${s.cashOnly.money()} · Kart ${s.card.money()}") { nav.navigate(Routes.CASH) { launchSingleTop = true } } },
                        { m -> StatCard("Satılan məhsul", "${s.itemsSold} əd.", Icons.Default.ShoppingBag, m, subtitle = "${s.salesCount} satış") },
                        { m -> StatCard("Mədaxil (alış)", s.purchases.money(), Icons.Default.MoveToInbox, m, tint = cs.tertiary) { nav.navigate(Routes.PURCHASES) } },
                        { m -> StatCard("Məxaric", s.expenses.money(), Icons.Default.Outbox, m, tint = extra.negative) { nav.navigate(Routes.EXPENSES) } },
                        { m -> StatCard("Müştəri borcu", s.customerDebt.money(), Icons.Default.RequestQuote, m, tint = extra.warning,
                            valueColor = if (s.customerDebt > 0) extra.warning else cs.onSurface) { nav.navigate(Routes.DEBTS) } },
                        { m -> StatCard("Təchizatçı borcu", s.supplierDebt.money(), Icons.Default.LocalShipping, m, tint = extra.negative) { nav.navigate(Routes.SUPPLIERS) } },
                        { m -> StatCard("Anbarda məhsul", "${s.stockCount} əd.", Icons.Default.Inventory2, m,
                            subtitle = if (s.lowStock > 0) "${s.lowStock} məhsul az qalıb" else "Stok qaydasındadır") { nav.navigate(Routes.INVENTORY) { launchSingleTop = true } } },
                        { m -> StatCard("Xalis nəticə", (s.profit - s.expenses).money(), Icons.AutoMirrored.Filled.TrendingUp, m, tint = extra.positive,
                            subtitle = "Qazanc − məxaric", valueColor = if (s.profit - s.expenses >= 0) extra.positive else extra.negative) },
                        { m -> StatCard("Anbar alış dəyəri", s.stockCost.money(), Icons.Default.Savings, m, tint = cs.tertiary) },
                        { m -> StatCard("Anbar satış dəyəri", s.stockSale.money(), Icons.Default.Sell, m, tint = cs.secondary,
                            subtitle = "Potensial qazanc ${(s.stockSale - s.stockCost).money()}") },
                    )
                )
            }
            item {
                PosCard {
                    Text(if (range.preset == RangePreset.TODAY) "Son 7 gün" else "Satış dinamikası", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (s.chart.all { it.value == 0L }) {
                        Text("Bu dövrdə satış yoxdur", color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                    } else BarChart(s.chart)
                }
            }
            if (s.lowStock > 0) item {
                Card(
                    onClick = { nav.navigate(Routes.INVENTORY) { launchSingleTop = true } },
                    colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null)
                        Spacer(Modifier.width(12.dp))
                        Text("${s.lowStock} məhsulun stoku minimum limitə çatıb və ya bitib", Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            if (s.top.isNotEmpty()) item {
                PosCard {
                    Text("Ən çox satılan məhsullar", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    val max = s.top.maxOf { it.qty }
                    s.top.forEach {
                        ShareBar(it.productName, it.qty, max, cs.primary, "${it.qty} əd. · ${it.revenue.money()}")
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** 2 sütunlu kart şəbəkəsi. */
@Composable
fun Grid(items: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                row.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
