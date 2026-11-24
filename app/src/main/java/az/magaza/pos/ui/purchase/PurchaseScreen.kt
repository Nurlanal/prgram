package az.magaza.pos.ui.purchase

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import az.magaza.pos.data.PosRepository
import az.magaza.pos.data.db.*
import az.magaza.pos.ui.Routes
import az.magaza.pos.ui.components.*
import az.magaza.pos.ui.theme.LocalExtraColors
import az.magaza.pos.ui.theme.NumberStyle
import az.magaza.pos.util.DateRange
import az.magaza.pos.util.Dates
import az.magaza.pos.util.Money
import az.magaza.pos.util.RangePreset
import az.magaza.pos.util.money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseViewModel(val repo: PosRepository) : ViewModel() {
    val range = MutableStateFlow(DateRange.of(RangePreset.MONTH))
    val purchases = range.flatMapLatest { repo.dao.observePurchasesInRange(it.from, it.to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val products = repo.dao.observeProducts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val suppliers = repo.dao.observeSuppliers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun PurchaseScreen(nav: NavController, preselectProduct: Long) {
    val app = app()
    val vm: PurchaseViewModel = viewModel { PurchaseViewModel(app.repo) }
    val runner = rememberRunner()
    val range by vm.range.collectAsState()
    val list by vm.purchases.collectAsState()
    var showForm by rememberSaveable { mutableStateOf(preselectProduct != 0L) }
    var cancel by remember { mutableStateOf<Purchase?>(null) }
    val extra = LocalExtraColors.current

    Scaffold(
        topBar = { PosTopBar("Mədaxil — anbara daxil", onBack = { nav.popBackStack() }, actions = {
            IconButton(onClick = { nav.navigate(Routes.SUPPLIERS) }) { Icon(Icons.Default.LocalShipping, "Təchizatçılar") }
        }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showForm = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Yeni mədaxil") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { RangeSelector(range, { vm.range.value = it }) }
            item {
                val active = list.filter { !it.cancelled }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    StatCard("Alış məbləği", active.sumOf { it.total }.money(), Icons.Default.MoveToInbox, Modifier.weight(1f).fillMaxHeight(),
                        tint = MaterialTheme.colorScheme.tertiary, subtitle = "${active.sumOf { it.quantity }} ədəd")
                    StatCard("Borcla alınıb", active.sumOf { it.total - it.paidAmount }.money(), Icons.Default.RequestQuote,
                        Modifier.weight(1f).fillMaxHeight(), tint = extra.negative)
                }
            }
            if (list.isEmpty()) item { EmptyState(Icons.Default.MoveToInbox, "Bu dövrdə mədaxil yoxdur") }
            items(list, key = { it.id }) { p ->
                PosCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(p.productName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${p.quantity} × ${p.unitCost.money()}", style = MaterialTheme.typography.bodyMedium)
                            Text("#${p.id} · ${Dates.dateTime(p.createdAt)}" + if (p.supplierName.isNotBlank()) " · ${p.supplierName}" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(p.total.money(), style = MaterialTheme.typography.titleMedium.merge(NumberStyle))
                            when {
                                p.cancelled -> Pill("Ləğv edilib", extra.negative)
                                p.paymentStatus == PurchaseStatus.PAID -> Pill("Ödənilib", extra.positive)
                                p.paymentStatus == PurchaseStatus.DEBT -> Pill("Borc", extra.warning)
                                else -> Pill("Qismən: ${p.paidAmount.money()}", extra.warning)
                            }
                            if (!p.cancelled) TextButton(onClick = { cancel = p }) { Text("Ləğv et") }
                        }
                    }
                }
            }
        }
    }

    if (showForm) PurchaseFormDialog(vm, runner, preselectProduct) { showForm = false }
    cancel?.let { p ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cancel = null },
            title = { Text("Mədaxili ləğv et") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${p.productName}: ${p.quantity} ədəd anbardan çıxarılacaq, ödəniş kassaya qaytarılacaq, təchizatçı borcu düzəldiləcək.")
                    TextInput(reason, { reason = it }, "Səbəb")
                }
            },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), onClick = {
                    runner.run(success = "Mədaxil ləğv edildi", onDone = { cancel = null }) { vm.repo.cancelPurchase(p.id, reason) }
                }) { Text("Ləğv et") }
            },
            dismissButton = { TextButton(onClick = { cancel = null }) { Text("Bağla") } },
        )
    }
}

enum class PurchasePay(val label: String) { PAID("Ödənilib"), DEBT("Borc"), PARTIAL("Qismən") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseFormDialog(vm: PurchaseViewModel, runner: Runner, preselect: Long, onDismiss: () -> Unit) {
    val products by vm.products.collectAsState()
    val suppliers by vm.suppliers.collectAsState()
    var product by remember { mutableStateOf<Product?>(null) }
    var pickProduct by remember { mutableStateOf(false) }
    var qty by remember { mutableStateOf("1") }
    var cost by remember { mutableStateOf("") }
    var newSale by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf<Supplier?>(null) }
    var pay by remember { mutableStateOf(PurchasePay.PAID) }
    var paid by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PayMethod.CASH) }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var addSupplier by remember { mutableStateOf(false) }

    LaunchedEffect(products, preselect) {
        if (product == null && preselect != 0L) products.firstOrNull { it.id == preselect }?.let {
            product = it; cost = Money.toInput(it.purchasePrice)
            supplier = suppliers.firstOrNull { s -> s.id == it.supplierId }
        }
    }

    val q = qty.toIntOrNull() ?: 0
    val c = Money.parse(cost.ifBlank { "0" })
    val total = if (c != null) c * q else 0L
    val paidAmount = when (pay) {
        PurchasePay.PAID -> total
        PurchasePay.DEBT -> 0L
        PurchasePay.PARTIAL -> (Money.parse(paid) ?: 0L)
    }
    val error = when {
        product == null -> "Məhsul seçin"
        q <= 0 -> "Say daxil edin"
        c == null -> "Alış qiymətini düzgün yazın"
        pay != PurchasePay.PAID && supplier == null -> "Borcla alış üçün təchizatçı seçin"
        pay == PurchasePay.PARTIAL && paidAmount > total -> "Ödənilən məbləğ ümumi məbləğdən çoxdur"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni mədaxil") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(onClick = { pickProduct = true }, Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, null); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(product?.name ?: "Məhsul seçin *")
                            product?.let { Text("Hazırkı qalıq: ${it.stock} · Alış: ${it.purchasePrice.money()}", style = MaterialTheme.typography.bodySmall) }
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntField(qty, { qty = it }, "Say", Modifier.weight(1f))
                    MoneyField(cost, { cost = it }, "Alış qiyməti", Modifier.weight(1f))
                }
                Text("Ümumi alış məbləği: ${total.money()}", style = MaterialTheme.typography.titleMedium)
                MoneyField(newSale, { newSale = it }, "Yeni satış qiyməti (istəyə görə)",
                    supporting = product?.let { "Hazırkı: ${it.salePrice.money()}" })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dropdown("Təchizatçı", suppliers, supplier, { it.name }, { supplier = it }, Modifier.weight(1f),
                        noneLabel = "— Seçilməyib —", onNone = { supplier = null })
                    IconButton(onClick = { addSupplier = true }) { Icon(Icons.Default.AddBusiness, "Yeni təchizatçı") }
                }
                Text("Ödəniş vəziyyəti", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PurchasePay.entries.forEachIndexed { i, p ->
                        SegmentedButton(pay == p, { pay = p }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(p.label) }
                    }
                }
                if (pay == PurchasePay.PARTIAL) MoneyField(paid, { paid = it }, "Ödənilən məbləğ")
                if (pay != PurchasePay.DEBT) MethodSelector(method, { method = it })
                if (paidAmount < total && total > 0) Text("Təchizatçı borcu: ${(total - paidAmount).money()}",
                    color = LocalExtraColors.current.warning)
                if (paidAmount > 0) Text("Kassadan çıxacaq: ${paidAmount.money()}", style = MaterialTheme.typography.bodySmall)
                DateField("Tarix", date, { date = it })
                TextInput(note, { note = it }, "Qeyd")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = error == null && !runner.busy, onClick = {
                runner.run(success = "Mədaxil təsdiqləndi, anbar yeniləndi", onDone = onDismiss) {
                    vm.repo.addPurchase(product!!.id, q, c!!, supplier?.id, paidAmount, method, date, note,
                        newSalePrice = Money.parse(newSale)?.takeIf { it > 0 })
                }
            }) { Text("Təsdiqlə") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )

    if (pickProduct) ProductPickerDialog(products, onPick = {
        product = it; cost = Money.toInput(it.purchasePrice)
        if (supplier == null) supplier = suppliers.firstOrNull { s -> s.id == it.supplierId }
    }) { pickProduct = false }
    if (addSupplier) SupplierDialog(null, onSave = { s ->
        runner.run { val id = vm.repo.saveSupplier(s); supplier = vm.repo.dao.getSupplier(id) }
    }) { addSupplier = false }
}

@Composable
fun ProductPickerDialog(products: List<Product>, onPick: (Product) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val scan = rememberBarcodeScanner { q = it }
    val filtered = remember(q, products) {
        products.filter { q.isBlank() || it.name.contains(q, true) || it.barcode.contains(q) || it.sku.contains(q, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Məhsul seçin") },
        text = {
            Column {
                SearchField(q, { q = it }, "Ad və ya barkod", trailing = { IconButton(onClick = scan) { Icon(Icons.Default.QrCodeScanner, "Skan") } })
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name, maxLines = 2) },
                            supportingContent = { Text("Qalıq: ${p.stock} · Alış: ${p.purchasePrice.money()}") },
                            leadingContent = { ProductThumb(p.imagePath, p.name, 40.dp) },
                            trailingContent = { TextButton(onClick = { onPick(p); onDismiss() }) { Text("Seç") } },
                        )
                    }
                    if (filtered.isEmpty()) item { Text("Məhsul tapılmadı. Əvvəlcə Anbar bölməsində məhsul yaradın.", Modifier.padding(12.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Bağla") } },
    )
}

@Composable
fun SupplierDialog(initial: Supplier?, onSave: (Supplier) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni təchizatçı" else "Təchizatçını redaktə et") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(name, { name = it }, "Ad *")
                TextInput(phone, { phone = it }, "Telefon", keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                TextInput(note, { note = it }, "Qeyd", singleLine = false)
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onSave((initial ?: Supplier(name = "")).copy(name = name, phone = phone, note = note)); onDismiss()
            }) { Text("Yadda saxla") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}
