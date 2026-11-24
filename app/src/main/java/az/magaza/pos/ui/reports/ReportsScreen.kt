package az.magaza.pos.ui.reports

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import az.magaza.pos.data.CashReport
import az.magaza.pos.data.PosRepository
import az.magaza.pos.data.Reports
import az.magaza.pos.data.db.*
import az.magaza.pos.ui.Routes
import az.magaza.pos.ui.components.*
import az.magaza.pos.ui.dashboard.DashboardViewModel
import az.magaza.pos.ui.theme.LocalExtraColors
import az.magaza.pos.ui.theme.NumberStyle
import az.magaza.pos.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class ReportData(
    val range: DateRange,
    val sales: List<Sale> = emptyList(),
    val productRows: List<ProductSalesRow> = emptyList(),
    val chart: List<ChartBar> = emptyList(),
    val expensesByCat: List<CategorySum> = emptyList(),
    val products: List<Product> = emptyList(),
    val cash: CashReport? = null,
    val customers: List<CustomerWithDebt> = emptyList(),
    val debtTx: List<CustomerDebtTx> = emptyList(),
) {
    val active get() = sales.filter { it.status == SaleStatus.ACTIVE }
    val revenue get() = active.sumOf { it.total }
    val cost get() = active.sumOf { it.costTotal }
    val grossProfit get() = active.sumOf { it.profit }
    val discount get() = active.sumOf { it.discount }
    val items get() = active.sumOf { it.itemCount }
    val expenses get() = expensesByCat.sumOf { it.total }
    val netProfit get() = grossProfit - expenses
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(val repo: PosRepository) : ViewModel() {
    val range = MutableStateFlow(DateRange.of(RangePreset.MONTH))
    private val dao = repo.dao

    val data: StateFlow<ReportData?> = combine(range, dao.observeLog(1), dao.observeProducts(), dao.observeCustomersWithDebt()) { r, _, p, c ->
        Triple(r, p, c)
    }.mapLatest { (r, products, customers) ->
        ReportData(
            range = r,
            sales = dao.salesInRange(r.from, r.to),
            productRows = dao.productSales(r.from, r.to),
            chart = DashboardViewModel.buildChart(r, dao.salesInRange(DashboardViewModel.chartRange(r).from, r.to)),
            expensesByCat = dao.expenseByCategory(r.from, r.to),
            products = products,
            cash = Reports.cash(dao, r),
            customers = customers,
            debtTx = dao.debtTxInRange(r.from, r.to),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

private val tabs = listOf("Satış", "Qazanc", "Anbar", "Kassa", "Borc")

@Composable
fun ReportsScreen(nav: NavController, openMenu: () -> Unit) {
    val app = app()
    val ctx = LocalContext.current
    val vm: ReportsViewModel = viewModel { ReportsViewModel(app.repo) }
    val runner = rememberRunner()
    val range by vm.range.collectAsState()
    val data by vm.data.collectAsState()
    var tab by rememberSaveableInt()

    Scaffold(topBar = {
        PosTopBar("Hesabatlar", onMenu = openMenu, actions = {
            IconButton(onClick = {
                data?.let { d -> runner.run { Share.file(ctx, exportCsv(ctx, tab, d), "text/csv", "Hesabatı paylaş") } }
            }) { Icon(Icons.Default.IosShare, "Excel/CSV ixrac") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t, maxLines = 1, softWrap = false) }) }
            }
            val d = data
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tab != 2) item { RangeSelector(range, { vm.range.value = it }) }
                if (d == null) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else when (tab) {
                    0 -> salesTab(d, nav)
                    1 -> profitTab(d)
                    2 -> stockTab(d, nav)
                    3 -> cashTab(d)
                    else -> debtTab(d, nav)
                }
                item {
                    OutlinedButton(onClick = { d?.let { runner.run { Share.file(ctx, exportCsv(ctx, tab, it), "text/csv", "Hesabatı paylaş") } } },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.TableChart, null); Spacer(Modifier.width(8.dp)); Text("Excel (CSV) kimi ixrac et və paylaş")
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSaveableInt() = androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

private fun LazyListScope.salesTab(d: ReportData, nav: NavController) {
    item {
        val n = d.active.size
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            StatCard("Satış gəliri", d.revenue.money(), Icons.Default.PointOfSale, Modifier.weight(1f).fillMaxHeight())
            StatCard("Satış sayı", "$n", Icons.Default.Receipt, Modifier.weight(1f).fillMaxHeight(),
                subtitle = "Orta çek ${(if (n > 0) d.revenue / n else 0).money()}")
        }
    }
    item {
        PosCard {
            Text("Satış dinamikası", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            BarChart(d.chart)
        }
    }
    item {
        PosCard {
            InfoRow("Satılan məhsul", "${d.items} ədəd")
            InfoRow("Nağd", d.active.sumOf { it.paidCash }.money())
            InfoRow("Kart", d.active.sumOf { it.paidCard }.money())
            InfoRow("Borca", d.active.sumOf { it.debtAmount }.money())
            InfoRow("Endirimlər", d.discount.money())
            InfoRow("Ləğv edilmiş satışlar", "${d.sales.count { it.status == SaleStatus.CANCELLED }}")
        }
    }
    item { SectionTitle("Məhsul üzrə satış") }
    if (d.productRows.isEmpty()) item { Text("Satış yoxdur", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    items(d.productRows, key = { "p" + it.productId + it.productName }) { r ->
        PosCard(onClick = { nav.navigate(Routes.product(r.productId)) }) {
            Text(r.productName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                Text("${r.qty} ədəd · qazanc ${(r.revenue - r.cost).money()}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(r.revenue.money(), style = MaterialTheme.typography.titleSmall.merge(NumberStyle))
            }
        }
    }
}

private fun LazyListScope.profitTab(d: ReportData) {
    item {
        PosCard {
            val ex = LocalExtraColors.current
            InfoRow("Satış dəyəri (gəlir)", d.revenue.money())
            InfoRow("Məhsulların alış (maya) dəyəri", "−" + d.cost.money())
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("Brüt qazanc", d.grossProfit.money(), bold = true, valueColor = ex.positive)
            InfoRow("Məxaric (xərclər)", "−" + d.expenses.money(), valueColor = ex.negative)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("Xalis qazanc", d.netProfit.money(), bold = true, valueColor = if (d.netProfit >= 0) ex.positive else ex.negative)
            if (d.revenue > 0) InfoRow("Brüt marja", "${d.grossProfit * 100 / d.revenue}%")
        }
    }
    item {
        PosCard {
            Text("Qazanc dinamikası", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            BarChart(d.chart)
        }
    }
    item { SectionTitle("Xərclər kateqoriya üzrə") }
    if (d.expensesByCat.isEmpty()) item { Text("Xərc yoxdur", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    item {
        if (d.expensesByCat.isNotEmpty()) PosCard {
            val max = d.expensesByCat.maxOf { it.total }
            d.expensesByCat.forEach { ShareBar(it.category, it.total, max, LocalExtraColors.current.negative) }
        }
    }
}

private fun LazyListScope.stockTab(d: ReportData, nav: NavController) {
    val ps = d.products
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            StatCard("Stok sayı", "${ps.sumOf { it.stock.coerceAtLeast(0) }}", Icons.Default.Inventory2, Modifier.weight(1f).fillMaxHeight(),
                subtitle = "${ps.size} növ")
            StatCard("Alış dəyəri", ps.filter { it.stock > 0 }.sumOf { it.stockCost }.money(), Icons.Default.Savings,
                Modifier.weight(1f).fillMaxHeight(), tint = MaterialTheme.colorScheme.tertiary)
            StatCard("Satış dəyəri", ps.sumOf { it.stock.coerceAtLeast(0) * it.salePrice }.money(), Icons.Default.Sell,
                Modifier.weight(1f).fillMaxHeight(), tint = MaterialTheme.colorScheme.secondary)
        }
    }
    val low = ps.filter { it.stock in 1..it.minStock }
    val out = ps.filter { it.stock <= 0 }
    item { SectionTitle("Az stokda olanlar (${low.size})") }
    items(low, key = { "l" + it.id }) { StockRow(it, nav) }
    item { SectionTitle("Stokda olmayanlar (${out.size})") }
    items(out, key = { "o" + it.id }) { StockRow(it, nav) }
    item { SectionTitle("Mövcud məhsullar (${ps.count { it.stock > 0 }})") }
    items(ps.filter { it.stock > 0 }, key = { "a" + it.id }) { StockRow(it, nav) }
}

@Composable
private fun StockRow(p: Product, nav: NavController) {
    val extra = LocalExtraColors.current
    ListItem(
        modifier = Modifier.padding(0.dp),
        headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("Alış ${(if (p.stock > 0) p.stockCost else 0L).money()} · Satış ${(p.stock.coerceAtLeast(0) * p.salePrice).money()}") },
        trailingContent = {
            TextButton(onClick = { nav.navigate(Routes.product(p.id)) }) {
                Text("${p.stock} əd.", color = when { p.stock <= 0 -> extra.negative; p.stock <= p.minStock -> extra.warning; else -> MaterialTheme.colorScheme.onSurface })
            }
        },
    )
}

private fun LazyListScope.cashTab(d: ReportData) {
    val r = d.cash ?: return
    item {
        PosCard {
            val e = LocalExtraColors.current
            InfoRow("Kassa açılışı", r.opening.money(), bold = true)
            InfoRow("Mədaxil (cəmi daxilolma)", "+" + r.totalIn.money(), valueColor = e.positive)
            InfoRow("Məxaric (cəmi çıxış)", "−" + r.totalOut.money(), valueColor = e.negative)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("Nağd satış", r.cashSales.money())
            InfoRow("Kart satışları", r.cardSales.money())
            InfoRow("Borc ödənişləri", r.debtPayments.money())
            InfoRow("Kassa mədaxili", r.cashIn.money())
            InfoRow("Kassa məxarici", r.cashOut.money())
            InfoRow("Xərclər", r.expenses.money())
            InfoRow("Təchizatçıya ödənişlər", r.supplierPayments.money())
            InfoRow("Mədaxil ödənişləri", r.purchasePayments.money())
            if (r.cancellations != 0L) InfoRow("Satış ləğvləri", r.cancellations.money())
            if (r.adjustments != 0L) InfoRow("Düzəlişlər", r.adjustments.money())
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("Kassa bağlanışı", r.closing.money(), bold = true)
            InfoRow("  nağd / kart", "${r.closingCash.money()} / ${r.closingCard.money()}")
        }
    }
}

private fun LazyListScope.debtTab(d: ReportData, nav: NavController) {
    val debtors = d.customers.filter { it.debt > 0 }.sortedByDescending { it.debt }
    item {
        PosCard {
            val e = LocalExtraColors.current
            InfoRow("Ümumi qalan borc", debtors.sumOf { it.debt }.money(), bold = true, valueColor = e.warning)
            InfoRow("Borclu müştəri sayı", "${debtors.size}")
            InfoRow("Ödənilmiş borclar (bütün dövr)", d.customers.sumOf { it.paid }.money(), valueColor = e.positive)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("Seçilmiş dövrdə:", style = MaterialTheme.typography.labelLarge)
            InfoRow("Yeni borc", d.debtTx.filter { it.amount > 0 }.sumOf { it.amount }.money())
            InfoRow("Ödənilən", (-d.debtTx.filter { it.type == DebtType.PAYMENT }.sumOf { it.amount }).money())
        }
    }
    item { SectionTitle("Müştəri üzrə borc") }
    if (debtors.isEmpty()) item { Text("Borclu yoxdur", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    items(debtors, key = { "c" + it.id }) { c ->
        PosCard(onClick = { nav.navigate(Routes.customer(c.id)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    Text("Ödənilib: ${c.paid.money()}", style = MaterialTheme.typography.bodySmall)
                }
                Text(c.debt.money(), style = MaterialTheme.typography.titleMedium.merge(NumberStyle), color = LocalExtraColors.current.warning)
            }
        }
    }
}

private fun exportCsv(ctx: Context, tab: Int, d: ReportData): java.io.File {
    val csv = Csv()
    val p = Money::plain
    val stamp = Dates.fileStamp()
    when (tab) {
        0 -> {
            csv.row("Satış hesabatı", d.range.label); csv.blank()
            csv.row("Satış ID", "Tarix", "Müştəri", "Məhsul sayı", "Ara cəm", "Endirim", "Cəm", "Nağd", "Kart", "Borc", "Maya", "Qazanc", "Status")
            d.sales.forEach { s ->
                csv.row(s.id, Dates.dateTime(s.createdAt), s.customerName, s.itemCount, p(s.subtotal), p(s.discount), p(s.total),
                    p(s.paidCash), p(s.paidCard), p(s.debtAmount), p(s.costTotal), p(s.profit),
                    if (s.status == SaleStatus.ACTIVE) "Aktiv" else "Ləğv: ${s.cancelReason}")
            }
            csv.blank(); csv.row("Məhsul üzrə"); csv.row("Məhsul", "Say", "Gəlir", "Maya", "Qazanc")
            d.productRows.forEach { csv.row(it.productName, it.qty, p(it.revenue), p(it.cost), p(it.revenue - it.cost)) }
        }
        1 -> {
            csv.row("Qazanc hesabatı", d.range.label); csv.blank()
            csv.row("Satış dəyəri", p(d.revenue)); csv.row("Alış (maya) dəyəri", p(d.cost)); csv.row("Brüt qazanc", p(d.grossProfit))
            csv.row("Məxaric", p(d.expenses)); csv.row("Xalis qazanc", p(d.netProfit)); csv.blank()
            csv.row("Xərc kateqoriyası", "Məbləğ"); d.expensesByCat.forEach { csv.row(it.category, p(it.total)) }
        }
        2 -> {
            csv.row("Anbar hesabatı", Dates.dateTime(System.currentTimeMillis())); csv.blank()
            csv.row("Məhsul", "Kateqoriya", "Marka", "Barkod", "SKU", "Stok", "Min. stok", "Orta alış qiyməti", "Satış qiyməti", "Alış dəyəri", "Satış dəyəri", "Vəziyyət")
            d.products.forEach { x ->
                csv.row(x.name, x.category, x.brand, x.barcode, x.sku, x.stock, x.minStock, p(x.purchasePrice), p(x.salePrice),
                    p(if (x.stock > 0) x.stockCost else 0L), p(x.stock.coerceAtLeast(0) * x.salePrice),
                    when { x.stock <= 0 -> "Stokda yoxdur"; x.stock <= x.minStock -> "Az qalıb"; else -> "Var" })
            }
        }
        3 -> {
            val r = d.cash!!
            csv.row("Kassa hesabatı", d.range.label); csv.blank()
            listOf("Kassa açılışı" to r.opening, "Nağd satış" to r.cashSales, "Kart satışları" to r.cardSales,
                "Borc ödənişləri" to r.debtPayments, "Kassa mədaxili" to r.cashIn, "Kassa məxarici" to r.cashOut,
                "Xərclər" to r.expenses, "Təchizatçıya ödənişlər" to r.supplierPayments, "Mədaxil ödənişləri" to r.purchasePayments,
                "Satış ləğvləri" to r.cancellations, "Düzəlişlər" to r.adjustments, "Kassa bağlanışı" to r.closing,
                "Bağlanış (nağd)" to r.closingCash, "Bağlanış (kart)" to r.closingCard).forEach { csv.row(it.first, p(it.second)) }
        }
        else -> {
            csv.row("Borc hesabatı", Dates.dateTime(System.currentTimeMillis())); csv.blank()
            csv.row("Müştəri", "Telefon", "Ödənilib", "Qalan borc", "Borc tarixi")
            d.customers.filter { it.debt != 0L }.forEach { c ->
                csv.row(c.name, c.phone, p(c.paid), p(c.debt), c.firstDebtAt?.let { Dates.date(it) } ?: "")
            }
        }
    }
    return csv.save(ctx, "hesabat_${tabs[tab].lowercase()}_$stamp.csv".replace("ş", "s").replace("ç", "c").replace("ğ", "g"))
}
