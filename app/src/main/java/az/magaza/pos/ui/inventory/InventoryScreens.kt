package az.magaza.pos.ui.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import az.magaza.pos.data.Categories
import az.magaza.pos.data.PosRepository
import az.magaza.pos.data.db.*
import az.magaza.pos.ui.Routes
import az.magaza.pos.ui.components.*
import az.magaza.pos.ui.theme.LocalExtraColors
import az.magaza.pos.ui.theme.NumberStyle
import az.magaza.pos.util.Dates
import az.magaza.pos.util.ImageStore
import az.magaza.pos.util.Money
import az.magaza.pos.util.money
import kotlinx.coroutines.flow.*

enum class StockFilter(val label: String) { ALL("Hamısı"), LOW("Az qalan"), OUT("Stokda yoxdur") }

class InventoryViewModel(repo: PosRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val category = MutableStateFlow<String?>(null)
    val stockFilter = MutableStateFlow(StockFilter.ALL)
    private val products = repo.dao.observeProducts()

    val filtered = combine(products, query, category, stockFilter) { list, q, c, f ->
        val t = q.trim()
        list.filter { p ->
            (c == null || p.category == c) &&
                (t.isEmpty() || p.name.contains(t, true) || p.barcode.contains(t) || p.sku.contains(t, true) || p.brand.contains(t, true)) &&
                when (f) {
                    StockFilter.ALL -> true
                    StockFilter.LOW -> p.stock in 1..p.minStock
                    StockFilter.OUT -> p.stock <= 0
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totals = products.map { l ->
        Triple(l.sumOf { it.stock.coerceAtLeast(0).toLong() }, l.sumOf { it.stock.coerceAtLeast(0) * it.purchasePrice },
            l.sumOf { it.stock.coerceAtLeast(0) * it.salePrice })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0L, 0L, 0L))

    val alerts = products.map { l -> l.count { it.stock in 1..it.minStock } to l.count { it.stock <= 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0)
}

@Composable
fun InventoryScreen(nav: NavController, openMenu: () -> Unit) {
    val app = app()
    val vm: InventoryViewModel = viewModel { InventoryViewModel(app.repo) }
    val runner = rememberRunner()
    val list by vm.filtered.collectAsState()
    val query by vm.query.collectAsState()
    val category by vm.category.collectAsState()
    val filter by vm.stockFilter.collectAsState()
    val totals by vm.totals.collectAsState()
    val alerts by vm.alerts.collectAsState()
    val extra = LocalExtraColors.current
    val scan = rememberBarcodeScanner { code ->
        runner.run {
            val p = app.repo.dao.findByBarcode(code)
            if (p != null) nav.navigate(Routes.product(p.id)) else vm.query.value = code
        }
    }

    Scaffold(
        topBar = { PosTopBar("Anbar", onMenu = openMenu, actions = {
            IconButton(onClick = { nav.navigate(Routes.PURCHASES) }) { Icon(Icons.Default.MoveToInbox, "Mədaxil") }
        }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.productEdit()) }, icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Məhsul") })
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    StatCard("Məhsul sayı", "${totals.first}", Icons.Default.Inventory2, Modifier.weight(1f).fillMaxHeight())
                    StatCard("Alış dəyəri", totals.second.money(), Icons.Default.Savings, Modifier.weight(1f).fillMaxHeight(),
                        tint = MaterialTheme.colorScheme.tertiary)
                    StatCard("Satış dəyəri", totals.third.money(), Icons.Default.Sell, Modifier.weight(1f).fillMaxHeight(),
                        tint = MaterialTheme.colorScheme.secondary)
                }
            }
            item {
                SearchField(query, { vm.query.value = it }, "Ad, barkod, SKU, marka",
                    trailing = { IconButton(onClick = scan) { Icon(Icons.Default.QrCodeScanner, "Barkod skan et") } })
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StockFilter.entries.forEach { f ->
                        val count = when (f) { StockFilter.LOW -> " (${alerts.first})"; StockFilter.OUT -> " (${alerts.second})"; else -> "" }
                        FilterChip(selected = filter == f, onClick = { vm.stockFilter.value = f }, label = { Text(f.label + count) },
                            leadingIcon = if (f != StockFilter.ALL && (if (f == StockFilter.LOW) alerts.first else alerts.second) > 0)
                                ({ Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = if (f == StockFilter.OUT) extra.negative else extra.warning) }) else null)
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = category == null, onClick = { vm.category.value = null }, label = { Text("Bütün kateqoriyalar") })
                    Categories.products.forEach { c ->
                        FilterChip(selected = category == c, onClick = { vm.category.value = if (category == c) null else c }, label = { Text(c) })
                    }
                }
            }
            if (list.isEmpty()) item { EmptyState(Icons.Default.Inventory2, "Məhsul yoxdur") }
            items(list, key = { it.id }) { p ->
                PosCard(onClick = { nav.navigate(Routes.product(p.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductThumb(p.imagePath, p.name)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOf(p.category, p.brand).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Alış ${p.purchasePrice.money()} · Satış ${p.salePrice.money()}", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${p.stock}", style = MaterialTheme.typography.titleLarge.merge(NumberStyle),
                                color = when {
                                    p.stock <= 0 -> extra.negative
                                    p.stock <= p.minStock -> extra.warning
                                    else -> MaterialTheme.colorScheme.onSurface
                                })
                            Text("ədəd", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// ============================== Məhsul formu ==============================

@Composable
fun ProductEditScreen(nav: NavController, id: Long) {
    val app = app()
    val ctx = LocalContext.current
    val runner = rememberRunner()
    val suppliers by app.repo.dao.observeSuppliers().collectAsState(emptyList())
    var loaded by remember { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<Product?>(null) }

    var name by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<String?>(null) }
    var barcode by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.products.first()) }
    var brand by remember { mutableStateOf("") }
    var buy by remember { mutableStateOf("") }
    var sell by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var minStock by remember { mutableStateOf("2") }
    var supplierId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(id) {
        if (id != 0L) app.repo.dao.getProduct(id)?.let { p ->
            original = p
            name = p.name; image = p.imagePath; barcode = p.barcode; sku = p.sku; category = p.category; brand = p.brand
            buy = Money.toInput(p.purchasePrice); sell = Money.toInput(p.salePrice); stock = p.stock.toString()
            minStock = p.minStock.toString(); supplierId = p.supplierId; note = p.note
        }
        loaded = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val newName = ImageStore.importImage(ctx, uri)
            if (newName != null) {
                if (image != original?.imagePath) ImageStore.delete(ctx, image)
                image = newName
            } else runner.message("Şəkil yüklənmədi")
        }
    }
    val scan = rememberBarcodeScanner { barcode = it }

    val buyV = Money.parse(buy.ifBlank { "0" })
    val sellV = Money.parse(sell.ifBlank { "0" })
    val valid = name.isNotBlank() && buyV != null && sellV != null

    Scaffold(topBar = { PosTopBar(if (id == 0L) "Yeni məhsul" else "Məhsulu redaktə et", onBack = { nav.popBackStack() }) }) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    ProductThumb(image, name.ifBlank { "+" }, size = 88.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text(if (image == null) "Şəkil əlavə et" else "Şəkli dəyiş")
                    }
                    if (image != null) TextButton(onClick = { image = null }) { Text("Şəkli sil") }
                }
            }
            TextInput(name, { name = it }, "Məhsul adı *")
            TextInput(barcode, { barcode = it }, "Barkod", trailing = {
                IconButton(onClick = scan) { Icon(Icons.Default.QrCodeScanner, "Skan et") }
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(sku, { sku = it }, "SKU kodu", Modifier.weight(1f))
                TextInput(brand, { brand = it }, "Marka", Modifier.weight(1f))
            }
            Dropdown("Kateqoriya", Categories.products, category, { it }, { category = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(buy, { buy = it }, "Alış qiyməti", Modifier.weight(1f))
                MoneyField(sell, { sell = it }, "Satış qiyməti", Modifier.weight(1f))
            }
            if (buyV != null && sellV != null && sellV > 0) {
                val margin = sellV - buyV
                Text("Bir ədəddən qazanc: ${margin.money()}" + if (buyV > 0) "  (${margin * 100 / buyV}%)" else "",
                    color = if (margin >= 0) LocalExtraColors.current.positive else LocalExtraColors.current.negative,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField(stock, { stock = it }, "Anbar sayı", Modifier.weight(1f))
                IntField(minStock, { minStock = it }, "Minimum stok", Modifier.weight(1f))
            }
            Dropdown("Təchizatçı", suppliers, suppliers.firstOrNull { it.id == supplierId }, { it.name }, { supplierId = it.id },
                noneLabel = "— Seçilməyib —", onNone = { supplierId = null })
            TextInput(note, { note = it }, "Qeyd", singleLine = false)
            if (original != null) Text("Əlavə edilmə tarixi: ${Dates.dateTime(original!!.createdAt)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigButton("Yadda saxla", Icons.Default.Save, enabled = valid && !runner.busy, modifier = Modifier.fillMaxWidth(), onClick = {
                runner.run(success = "Məhsul yadda saxlanıldı", onDone = { nav.popBackStack() }) {
                    val base = original ?: Product(name = "")
                    app.repo.saveProduct(base.copy(
                        name = name, imagePath = image, barcode = barcode, sku = sku, category = category, brand = brand,
                        purchasePrice = buyV!!, salePrice = sellV!!, stock = stock.toIntOrNull() ?: 0,
                        minStock = minStock.toIntOrNull() ?: 0, supplierId = supplierId, note = note,
                    ))
                    if (original?.imagePath != null && original?.imagePath != image) ImageStore.delete(ctx, original?.imagePath)
                }
            })
        }
    }
}

// ============================== Məhsul detalları ==============================

@Composable
fun ProductDetailScreen(nav: NavController, id: Long) {
    val app = app()
    val ctx = LocalContext.current
    val runner = rememberRunner()
    val product by app.repo.dao.observeProduct(id).collectAsState(null)
    val sales by app.repo.dao.observeProductSales(id).collectAsState(emptyList())
    val moves by app.repo.dao.observeStockMoves(id).collectAsState(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var adjust by remember { mutableStateOf(false) }
    var priceDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val extra = LocalExtraColors.current

    Scaffold(topBar = {
        PosTopBar(product?.name ?: "Məhsul", onBack = { nav.popBackStack() }, actions = {
            IconButton(onClick = { nav.navigate(Routes.productEdit(id)) }) { Icon(Icons.Default.Edit, "Redaktə et") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Sil") }
        })
    }) { pad ->
        val p = product ?: run {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Məhsul tapılmadı") }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PosCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductThumb(p.imagePath, p.name, 72.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleLarge)
                            Text(listOf(p.category, p.brand).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Anbar qalığı", "${p.stock} ədəd", bold = true,
                        valueColor = if (p.stock <= 0) extra.negative else if (p.stock <= p.minStock) extra.warning else MaterialTheme.colorScheme.onSurface)
                    InfoRow("Minimum stok limiti", "${p.minStock}")
                    InfoRow("Alış qiyməti (maya)", p.purchasePrice.money())
                    InfoRow("Satış qiyməti", p.salePrice.money())
                    InfoRow("Bir ədəddən qazanc", (p.salePrice - p.purchasePrice).money(), valueColor = extra.positive)
                    InfoRow("Anbar dəyəri (alış / satış)", "${(p.stock.coerceAtLeast(0) * p.purchasePrice).money()} / ${(p.stock.coerceAtLeast(0) * p.salePrice).money()}")
                    if (p.barcode.isNotBlank()) InfoRow("Barkod", p.barcode)
                    if (p.sku.isNotBlank()) InfoRow("SKU", p.sku)
                    InfoRow("Əlavə edilib", Dates.dateTime(p.createdAt))
                    if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { adjust = true }, Modifier.weight(1f)) { Text("Stoku dəyiş") }
                    FilledTonalButton(onClick = { priceDialog = true }, Modifier.weight(1f)) { Text("Qiymətlər") }
                    FilledTonalButton(onClick = { nav.navigate(Routes.purchaseFor(id)) }, Modifier.weight(1f)) { Text("Mədaxil") }
                }
            }
            item {
                TabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Satışlar (${sales.count { it.status == SaleStatus.ACTIVE }})") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Stok hərəkəti") })
                }
            }
            if (tab == 0) {
                if (sales.isEmpty()) item { EmptyState(Icons.Default.ShoppingBag, "Bu məhsul hələ satılmayıb") }
                items(sales) { s ->
                    ListItem(
                        modifier = Modifier.clickable { nav.navigate(Routes.sale(s.saleId)) },
                        headlineContent = { Text("Satış #${s.saleId} · ${s.quantity} × ${s.unitPrice.money()}") },
                        supportingContent = { Text(Dates.dateTime(s.createdAt) + if (s.status == SaleStatus.CANCELLED) " · LƏĞV EDİLİB" else "") },
                        trailingContent = { Text(s.lineTotal.money(), style = MaterialTheme.typography.titleSmall.merge(NumberStyle)) },
                    )
                }
            } else {
                if (moves.isEmpty()) item { EmptyState(Icons.Default.SwapVert, "Hərəkət yoxdur") }
                items(moves, key = { "m" + it.id }) { m ->
                    ListItem(
                        headlineContent = { Text(stockTypeName(m.type) + (if (m.note.isNotBlank()) " · ${m.note}" else ""), maxLines = 2) },
                        supportingContent = { Text(Dates.dateTime(m.createdAt) + " · qalıq: ${m.stockAfter}") },
                        trailingContent = {
                            Text((if (m.qtyChange > 0) "+" else "") + m.qtyChange, style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                                color = if (m.qtyChange >= 0) extra.positive else extra.negative)
                        },
                    )
                }
            }
        }

    }
    product?.let { p ->
        if (adjust) {
            var v by remember { mutableStateOf(p.stock.toString()) }
            var note by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { adjust = false },
                title = { Text("Stok sayını dəyiş") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Hazırkı qalıq: ${p.stock}. Yeni mal alışı üçün Mədaxil bölməsindən istifadə edin.")
                        IntField(v, { v = it }, "Yeni say")
                        TextInput(note, { note = it }, "Səbəb (məs. sayım, zədələnmə)")
                    }
                },
                confirmButton = {
                    Button(onClick = { runner.run(success = "Stok yeniləndi", onDone = { adjust = false }) {
                        app.repo.adjustStock(p.id, v.toIntOrNull() ?: 0, note)
                    } }) { Text("Yadda saxla") }
                },
                dismissButton = { TextButton(onClick = { adjust = false }) { Text("İmtina") } },
            )
        }
        if (priceDialog) {
            var buy by remember { mutableStateOf(Money.toInput(p.purchasePrice)) }
            var sell by remember { mutableStateOf(Money.toInput(p.salePrice)) }
            AlertDialog(
                onDismissRequest = { priceDialog = false },
                title = { Text("Qiymətləri dəyiş") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoneyField(buy, { buy = it }, "Alış qiyməti")
                        MoneyField(sell, { sell = it }, "Satış qiyməti")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val b = Money.parse(buy.ifBlank { "0" }); val s = Money.parse(sell.ifBlank { "0" })
                        if (b == null || s == null) runner.message("Məbləği düzgün yazın")
                        else runner.run(success = "Qiymətlər yeniləndi", onDone = { priceDialog = false }) {
                            app.repo.saveProduct(p.copy(purchasePrice = b, salePrice = s))
                        }
                    }) { Text("Yadda saxla") }
                },
                dismissButton = { TextButton(onClick = { priceDialog = false }) { Text("İmtina") } },
            )
        }
        if (confirmDelete) ConfirmDialog(
            "Məhsulu sil", "\"${p.name}\" silinsin? Satış tarixçəsi saxlanılacaq, lakin məhsul anbardan çıxarılacaq.",
            "Sil", destructive = true,
            onConfirm = { runner.run(success = "Məhsul silindi", onDone = { nav.popBackStack() }) {
                app.repo.deleteProduct(p.id); ImageStore.delete(ctx, p.imagePath)
            } },
            onDismiss = { confirmDelete = false },
        )
    }
}

fun stockTypeName(t: String) = when (t) {
    StockType.INITIAL -> "İlkin qalıq"
    StockType.SALE -> "Satış"
    StockType.SALE_CANCEL -> "Satış ləğvi"
    StockType.PURCHASE -> "Mədaxil"
    StockType.PURCHASE_CANCEL -> "Mədaxil ləğvi"
    StockType.ADJUST -> "Düzəliş"
    else -> t
}
