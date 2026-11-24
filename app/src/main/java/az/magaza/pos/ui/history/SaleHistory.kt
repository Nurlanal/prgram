package az.magaza.pos.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
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
import az.magaza.pos.data.db.Sale
import az.magaza.pos.data.db.SaleItem
import az.magaza.pos.data.db.SaleStatus
import az.magaza.pos.ui.Routes
import az.magaza.pos.ui.components.*
import az.magaza.pos.ui.theme.LocalExtraColors
import az.magaza.pos.ui.theme.NumberStyle
import az.magaza.pos.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

enum class StatusFilter(val label: String) { ALL("Hamısı"), ACTIVE("Aktiv"), CANCELLED("Ləğv edilmiş") }

data class SaleRow(val sale: Sale, val items: List<SaleItem>)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(val repo: PosRepository) : ViewModel() {
    val range = MutableStateFlow(DateRange.of(RangePreset.WEEK))
    val query = MutableStateFlow("")
    val status = MutableStateFlow(StatusFilter.ALL)

    private val rows = range.flatMapLatest { r -> repo.dao.observeSalesInRange(r.from, r.to) }.mapLatest { sales ->
        val items = sales.map { it.id }.chunked(500).flatMap { repo.dao.itemsForSales(it) }.groupBy { it.saleId }
        sales.map { SaleRow(it, items[it.id].orEmpty()) }
    }

    val filtered = combine(rows, query, status) { list, q, st ->
        val t = q.trim().removePrefix("#")
        list.filter { r ->
            (st == StatusFilter.ALL || (st == StatusFilter.ACTIVE) == (r.sale.status == SaleStatus.ACTIVE)) &&
                (t.isEmpty() || r.sale.id.toString() == t || r.sale.customerName.contains(t, true) ||
                    r.items.any { it.productName.contains(t, true) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun SaleHistoryScreen(nav: NavController) {
    val app = app()
    val vm: HistoryViewModel = viewModel { HistoryViewModel(app.repo) }
    val range by vm.range.collectAsState()
    val q by vm.query.collectAsState()
    val status by vm.status.collectAsState()
    val list by vm.filtered.collectAsState()
    val extra = LocalExtraColors.current

    Scaffold(topBar = { PosTopBar("Satış tarixçəsi", onBack = { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { RangeSelector(range, { vm.range.value = it }) }
            item { SearchField(q, { vm.query.value = it }, "Satış ID, müştəri və ya məhsul") }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusFilter.entries.forEach { f -> FilterChip(status == f, { vm.status.value = f }, { Text(f.label) }) }
                }
            }
            item {
                val active = list.filter { it.sale.status == SaleStatus.ACTIVE }.map { it.sale }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    StatCard("Satış", active.sumOf { it.total }.money(), Icons.Default.PointOfSale, Modifier.weight(1f).fillMaxHeight(),
                        subtitle = "${active.size} çek")
                    StatCard("Qazanc", active.sumOf { it.profit }.money(), Icons.Default.Savings, Modifier.weight(1f).fillMaxHeight(),
                        tint = extra.positive)
                }
            }
            if (list.isEmpty()) item { EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Satış tapılmadı") }
            items(list, key = { it.sale.id }) { r ->
                val s = r.sale
                val cancelled = s.status == SaleStatus.CANCELLED
                PosCard(onClick = { nav.navigate(Routes.sale(s.id)) }) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("#${s.id}", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.width(8.dp))
                                Text(Dates.dateTime(s.createdAt), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(r.items.joinToString(", ") { "${it.productName} ×${it.quantity}" }, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Pill(Receipt.methodSummary(s), MaterialTheme.colorScheme.primary)
                                if (s.customerName.isNotBlank()) Pill(s.customerName, MaterialTheme.colorScheme.onSurfaceVariant)
                                if (s.discount > 0) Pill("Endirim ${s.discount.money()}", extra.warning)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(s.total.money(), style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                                color = if (cancelled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                            if (cancelled) Pill("Ləğv edilib", extra.negative)
                            else Text("qazanc ${s.profit.money()}", style = MaterialTheme.typography.labelSmall, color = extra.positive)
                            Text("${s.itemCount} əd.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SaleDetailScreen(nav: NavController, id: Long) {
    val app = app()
    val runner = rememberRunner()
    val receipt = rememberReceiptActions(runner)
    val sale by app.repo.dao.observeSale(id).collectAsState(null)
    val items by app.repo.dao.observeSaleItems(id).collectAsState(emptyList())
    val debtTx by app.repo.dao.observeSaleDebtTx(id).collectAsState(null)
    var cancel by remember { mutableStateOf(false) }
    var editDue by remember { mutableStateOf(false) }
    val extra = LocalExtraColors.current

    Scaffold(topBar = {
        PosTopBar("Satış #$id", onBack = { nav.popBackStack() }, actions = {
            IconButton(onClick = { sale?.let { receipt.print(it, items) } }) { Icon(Icons.Default.Print, "Çap et") }
            IconButton(onClick = { sale?.let { receipt.pdf(it, items) } }) { Icon(Icons.Default.PictureAsPdf, "PDF") }
        })
    }) { pad ->
        val s = sale ?: run { Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Satış tapılmadı") }; return@Scaffold }
        val cancelled = s.status == SaleStatus.CANCELLED
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cancelled) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Bu satış ləğv edilib", style = MaterialTheme.typography.titleSmall)
                        Text("Səbəb: ${s.cancelReason}")
                        s.cancelledAt?.let { Text("Tarix: ${Dates.dateTime(it)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                PosCard {
                    InfoRow("Tarix və saat", Dates.dateTime(s.createdAt))
                    InfoRow("Müştəri", s.customerName.ifBlank { "—" })
                    InfoRow("Ödəniş üsulu", Receipt.methodSummary(s))
                    InfoRow("Məhsul sayı", "${s.itemCount} ədəd")
                    if (s.note.isNotBlank()) InfoRow("Qeyd", s.note)
                }
            }
            item { SectionTitle("Məhsullar") }
            items(items, key = { it.id }) { i ->
                PosCard {
                    Text(i.productName, style = MaterialTheme.typography.titleSmall)
                    Row {
                        Text("${i.quantity} × ${i.unitPrice.money()}", Modifier.weight(1f))
                        Text(i.lineTotal.money(), style = MaterialTheme.typography.titleSmall.merge(NumberStyle))
                    }
                    Text("Maya: ${(i.unitCost * i.quantity).money()} · Qazanc: ${(i.lineTotal - i.unitCost * i.quantity).money()}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                PosCard {
                    InfoRow("Ara cəm", s.subtotal.money())
                    InfoRow("Endirim", if (s.discount > 0) "−" + s.discount.money() else "—")
                    InfoRow("Ümumi məbləğ", s.total.money(), bold = true)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    InfoRow("Nağd", s.paidCash.money())
                    InfoRow("Kart", s.paidCard.money())
                    InfoRow("Borc", s.debtAmount.money(), valueColor = if (s.debtAmount > 0) extra.warning else MaterialTheme.colorScheme.onSurface)
                    if (s.debtAmount > 0 && !cancelled) debtTx?.let { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Borcun ödəniş tarixi", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { editDue = true }) { Text(t.dueAt?.let { Dates.date(it) } ?: "Təyin et") }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    InfoRow("Maya dəyəri", s.costTotal.money())
                    InfoRow("Satışdan qazanc", s.profit.money(), bold = true, valueColor = if (s.profit >= 0) extra.positive else extra.negative)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { receipt.print(s, items) }, Modifier.weight(1f)) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(4.dp)); Text("Çap") }
                    OutlinedButton(onClick = { receipt.pdf(s, items) }, Modifier.weight(1f)) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(4.dp)); Text("PDF") }
                }
            }
            if (!cancelled) item {
                BigButton("Satışı ləğv et", Icons.Default.Cancel, { cancel = true }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error))
            }
        }
    }
    if (editDue) debtTx?.let { t ->
        DueDateDialog(t.dueAt, "Satış #$id borcu: ${t.amount.money()}",
            onConfirm = { d -> runner.run(success = "Ödəniş tarixi yeniləndi") { app.repo.setDebtDueDate(t.id, d) } },
            onDismiss = { editDue = false })
    }
    sale?.let { s ->
        if (cancel) {
            var reason by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { cancel = false },
                title = { Text("Satışı ləğv et") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Məhsullar anbara qaytarılacaq, kassa əməliyyatları geri alınacaq" +
                            (if (s.debtAmount > 0) ", müştərinin borcu ${s.debtAmount.money()} azalacaq" else "") + ".")
                        TextInput(reason, { reason = it }, "Ləğv səbəbi *")
                    }
                },
                confirmButton = {
                    Button(enabled = reason.isNotBlank() && !runner.busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = { runner.run(success = "Satış ləğv edildi", onDone = { cancel = false }) { app.repo.cancelSale(s.id, reason) } }) {
                        Text("Ləğv et")
                    }
                },
                dismissButton = { TextButton(onClick = { cancel = false }) { Text("Bağla") } },
            )
        }
    }
}
