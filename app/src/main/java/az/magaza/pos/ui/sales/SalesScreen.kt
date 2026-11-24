package az.magaza.pos.ui.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import az.magaza.pos.data.CartLine
import az.magaza.pos.data.Categories
import az.magaza.pos.data.PosRepository
import az.magaza.pos.data.db.Customer
import az.magaza.pos.data.db.Product
import az.magaza.pos.data.db.Sale
import az.magaza.pos.data.db.SaleItem
import az.magaza.pos.ui.Routes
import az.magaza.pos.ui.components.*
import az.magaza.pos.ui.theme.LocalExtraColors
import az.magaza.pos.ui.theme.NumberStyle
import az.magaza.pos.util.Money
import az.magaza.pos.util.money
import kotlinx.coroutines.flow.*

class CartItem(val product: Product, qty: Int, price: Long) {
    var qty by mutableIntStateOf(qty)
    var price by mutableLongStateOf(price)
    val lineTotal get() = qty * price
}

enum class PayMode(val label: String) { CASH("Nağd"), CARD("Kart"), DEBT("Borc"), MIXED("Qarışıq") }

class SalesViewModel(val repo: PosRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val category = MutableStateFlow<String?>(null)
    val cart: SnapshotStateList<CartItem> = mutableStateListOf()

    val products: StateFlow<List<Product>> = repo.dao.observeProducts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filtered: StateFlow<List<Product>> = combine(products, query, category) { list, q, c ->
        val t = q.trim()
        list.filter { p ->
            (c == null || p.category == c) &&
                (t.isEmpty() || p.name.contains(t, true) || p.barcode.contains(t) || p.sku.contains(t, true) || p.brand.contains(t, true))
        }.sortedWith(compareBy<Product> { it.stock <= 0 }.thenBy { it.name.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<Customer>> = repo.dao.observeCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Ödəniş vəziyyəti
    var discountText by mutableStateOf("")
    var discountPercent by mutableStateOf(false)
    var customer by mutableStateOf<Customer?>(null)
    var payMode by mutableStateOf(PayMode.CASH)
    var cashText by mutableStateOf("")
    var cardText by mutableStateOf("")
    var note by mutableStateOf("")

    val subtotal get() = cart.sumOf { it.lineTotal }
    val discount: Long
        get() {
            val v = Money.parse(discountText) ?: 0L
            val d = if (discountPercent) (subtotal * v.coerceAtMost(10000) + 5000) / 10000 else v
            return d.coerceIn(0, subtotal)
        }
    val total get() = subtotal - discount
    val itemCount get() = cart.sumOf { it.qty }

    /** Ödəniş rejiminə görə (nağd, kart, borc, qaytarılan qalıq) hesablanır. */
    data class Split(val cash: Long, val card: Long, val debt: Long, val change: Long, val error: String?)

    val split: Split
        get() {
            val t = total
            val cashIn = Money.parse(cashText) ?: 0L
            val cardIn = Money.parse(cardText) ?: 0L
            return when (payMode) {
                PayMode.CASH -> {
                    val change = if (cashText.isNotBlank() && cashIn >= t) cashIn - t else 0
                    val err = if (cashText.isNotBlank() && cashIn < t) "Alınan məbləğ kifayət etmir" else null
                    Split(t, 0, 0, change, err)
                }
                PayMode.CARD -> Split(0, t, 0, 0, null)
                PayMode.DEBT -> {
                    val paid = cashIn.coerceAtMost(t)
                    Split(paid, 0, t - paid, 0, if (customer == null) "Borca satış üçün müştəri seçin" else null)
                }
                PayMode.MIXED -> {
                    if (cashIn + cardIn > t) Split(cashIn, cardIn, 0, 0, "Nağd + kart ümumi məbləğdən çoxdur")
                    else {
                        val debt = t - cashIn - cardIn
                        Split(cashIn, cardIn, debt, 0, if (debt > 0 && customer == null) "Qalan ${debt.money()} borc üçün müştəri seçin" else null)
                    }
                }
            }
        }

    /** Məhsulu səbətə əlavə edir. Xəta mesajı qaytarır (stok çatmırsa). */
    fun add(p: Product, qty: Int = 1): String? {
        val existing = cart.firstOrNull { it.product.id == p.id }
        val current = existing?.qty ?: 0
        val fresh = products.value.firstOrNull { it.id == p.id } ?: p
        if (fresh.stock <= 0) return "\"${p.name}\" stokda yoxdur"
        if (current + qty > fresh.stock) return "\"${p.name}\" üçün stokda yalnız ${fresh.stock} ədəd var"
        if (existing != null) existing.qty += qty else cart.add(0, CartItem(fresh, qty, fresh.salePrice))
        return null
    }

    fun setQty(item: CartItem, qty: Int): String? {
        if (qty <= 0) { cart.remove(item); return null }
        val stock = products.value.firstOrNull { it.id == item.product.id }?.stock ?: item.product.stock
        if (qty > stock) return "Stokda yalnız $stock ədəd var"
        item.qty = qty
        return null
    }

    fun clear() {
        cart.clear(); discountText = ""; discountPercent = false; customer = null
        payMode = PayMode.CASH; cashText = ""; cardText = ""; note = ""
    }

    suspend fun complete(): Long {
        val s = split
        if (s.error != null) throw az.magaza.pos.data.PosException(s.error)
        val id = repo.completeSale(
            lines = cart.map { CartLine(it.product.id, it.product.name, it.qty, it.price) },
            discount = discount, cashPaid = s.cash, cardPaid = s.card, customerId = customer?.id, note = note,
        )
        clear()
        return id
    }

    suspend fun findByCode(code: String): Product? = repo.dao.findByBarcode(code)
        ?: products.value.firstOrNull { it.sku.equals(code, true) && it.sku.isNotBlank() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(nav: NavController, openMenu: () -> Unit) {
    val app = app()
    val vm: SalesViewModel = viewModel { SalesViewModel(app.repo) }
    val runner = rememberRunner()
    val query by vm.query.collectAsState()
    val category by vm.category.collectAsState()
    val list by vm.filtered.collectAsState()
    val allProducts by vm.products.collectAsState()
    var showCheckout by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Long?>(null) }
    var unknownCode by remember { mutableStateOf<String?>(null) }
    val extra = LocalExtraColors.current

    fun addByCode(code: String) {
        runner.run {
            val p = vm.findByCode(code.trim())
            if (p == null) unknownCode = code.trim()
            else {
                val err = vm.add(p)
                if (err != null) runner.message(err) else { vm.query.value = ""; runner.message("${p.name} səbətə əlavə edildi") }
            }
        }
    }
    val scan = rememberBarcodeScanner { addByCode(it) }

    Scaffold(
        topBar = {
            PosTopBar("Satış", onMenu = openMenu, actions = {
                IconButton(onClick = { nav.navigate(Routes.HISTORY) }) { Icon(Icons.AutoMirrored.Filled.ReceiptLong, "Satış tarixçəsi") }
            })
        },
        bottomBar = {
            if (vm.cart.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { showCheckout = true }) {
                            Text("${vm.itemCount} məhsul", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(vm.total.money(), style = MaterialTheme.typography.headlineSmall.merge(NumberStyle))
                        }
                        IconButton(onClick = { vm.clear() }) { Icon(Icons.Default.RemoveShoppingCart, "Səbəti təmizlə", tint = extra.negative) }
                        BigButton("Ödəniş", Icons.Default.ShoppingCartCheckout, { showCheckout = true })
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SearchField(
                query, { vm.query.value = it }, "Məhsul adı, barkod və ya SKU",
                Modifier.padding(horizontal = 16.dp),
                trailing = { IconButton(onClick = scan) { Icon(Icons.Default.QrCodeScanner, "Barkod skan et") } },
                onSearch = { if (query.isNotBlank()) {
                    val exact = allProducts.firstOrNull { it.barcode == query.trim() && it.barcode.isNotEmpty() }
                    when {
                        exact != null -> addByCode(query)
                        list.size == 1 -> vm.add(list[0])?.let { runner.message(it) } ?: run { vm.query.value = "" }
                        list.isEmpty() && query.trim().length >= 4 && query.trim().none { it.isWhitespace() } -> unknownCode = query.trim()
                    }
                } },
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { vm.category.value = null }, label = { Text("Hamısı") })
                Categories.products.forEach { c ->
                    FilterChip(selected = category == c, onClick = { vm.category.value = if (category == c) null else c }, label = { Text(c) })
                }
            }
            if (list.isEmpty()) {
                EmptyState(Icons.Default.SearchOff,
                    if (allProducts.isEmpty()) "Anbarda məhsul yoxdur.\nƏvvəlcə Anbar bölməsindən məhsul əlavə edin." else "Məhsul tapılmadı")
                if (allProducts.isEmpty()) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(onClick = { nav.navigate(Routes.productEdit()) }) { Text("Məhsul əlavə et") }
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { p ->
                    val inCart = vm.cart.firstOrNull { it.product.id == p.id }?.qty ?: 0
                    ProductSaleRow(p, inCart, onAdd = { vm.add(p)?.let { runner.message(it) } },
                        onRemove = { vm.cart.firstOrNull { it.product.id == p.id }?.let { vm.setQty(it, it.qty - 1) } })
                }
            }
        }
    }

    if (showCheckout) {
        ModalBottomSheet(onDismissRequest = { showCheckout = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CheckoutSheet(vm, runner, onDone = { id -> showCheckout = false; done = id }, onAddCustomer = {})
        }
    }

    unknownCode?.let { code ->
        UnknownBarcodeDialog(
            code = code,
            products = allProducts,
            onAssign = { product ->
                runner.run {
                    val updated = vm.repo.assignBarcode(product.id, code)
                    unknownCode = null
                    vm.query.value = ""
                    val err = vm.add(updated)
                    runner.message(err ?: "Barkod \"${updated.name}\" məhsuluna təyin edildi və səbətə əlavə olundu")
                }
            },
            onCreate = { unknownCode = null; nav.navigate(Routes.productEdit(0, barcode = code)) },
            onDismiss = { unknownCode = null },
        )
    }

    done?.let { id -> SaleDoneDialog(id, runner, onClose = { done = null }, onOpen = { done = null; nav.navigate(Routes.sale(id)) }) }
}

@Composable
private fun ProductSaleRow(p: Product, inCart: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    val extra = LocalExtraColors.current
    val out = p.stock <= 0
    PosCard(onClick = if (out) null else onAdd) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductThumb(p.imagePath, p.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.salePrice.money(), style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    when {
                        out -> Pill("Stokda yoxdur", extra.negative)
                        p.stock <= p.minStock -> Pill("Az qalıb: ${p.stock}", extra.warning)
                        else -> Pill("Stok: ${p.stock}", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (inCart > 0) {
                IconButton(onClick = onRemove) { Icon(Icons.Default.RemoveCircleOutline, "Azalt") }
                Text("$inCart", style = MaterialTheme.typography.titleMedium.merge(NumberStyle))
            }
            FilledTonalIconButton(onClick = onAdd, enabled = !out) { Icon(Icons.Default.Add, "Əlavə et") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutSheet(vm: SalesViewModel, runner: Runner, onDone: (Long) -> Unit, onAddCustomer: () -> Unit) {
    val customers by vm.customers.collectAsState()
    var pickCustomer by remember { mutableStateOf(false) }
    var newCustomer by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<CartItem?>(null) }
    val extra = LocalExtraColors.current
    val split = vm.split

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding()) {
        Text("Səbət", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        vm.cart.forEach { item ->
            Row(Modifier.fillMaxWidth().clickable { editItem = item }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.product.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.price.money()} × ${item.qty}" + if (item.price != item.product.salePrice) " (qiymət dəyişdirilib)" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.setQty(item, item.qty - 1) }) { Icon(Icons.Default.Remove, "Azalt") }
                Text("${item.qty}", style = MaterialTheme.typography.titleMedium.merge(NumberStyle))
                IconButton(onClick = { vm.setQty(item, item.qty + 1)?.let { runner.message(it) } }) { Icon(Icons.Default.Add, "Artır") }
                Text(item.lineTotal.money(), style = MaterialTheme.typography.titleSmall.merge(NumberStyle), modifier = Modifier.widthIn(min = 80.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (vm.cart.isEmpty()) Text("Səbət boşdur", Modifier.padding(16.dp))

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoneyField(vm.discountText, { vm.discountText = it }, if (vm.discountPercent) "Endirim (%)" else "Endirim (₼)", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(!vm.discountPercent, { vm.discountPercent = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("₼") }
                SegmentedButton(vm.discountPercent, { vm.discountPercent = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("%") }
            }
        }

        // Müştəri
        OutlinedCard(onClick = { pickCustomer = true }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null)
                Spacer(Modifier.width(10.dp))
                Text(vm.customer?.name ?: "Müştəri seçin (borc üçün vacibdir)", Modifier.weight(1f))
                if (vm.customer != null) IconButton(onClick = { vm.customer = null }) { Icon(Icons.Default.Close, "Müştərini sil") }
                else Icon(Icons.Default.ChevronRight, null)
            }
        }

        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PayMode.entries.forEachIndexed { i, m ->
                SegmentedButton(vm.payMode == m, { vm.payMode = m }, SegmentedButtonDefaults.itemShape(i, PayMode.entries.size)) { Text(m.label, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(8.dp))
        when (vm.payMode) {
            PayMode.CASH -> MoneyField(vm.cashText, { vm.cashText = it }, "Müştəridən alınan (qalığı hesablamaq üçün)", imeAction = ImeAction.Done)
            PayMode.CARD -> Text("Bütün məbləğ kartla ödənilir", color = MaterialTheme.colorScheme.onSurfaceVariant)
            PayMode.DEBT -> MoneyField(vm.cashText, { vm.cashText = it }, "İlkin ödəniş (nağd, istəyə görə)", imeAction = ImeAction.Done)
            PayMode.MIXED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(vm.cashText, { vm.cashText = it }, "Nağd", Modifier.weight(1f))
                MoneyField(vm.cardText, { vm.cardText = it }, "Kart", Modifier.weight(1f), imeAction = ImeAction.Done)
            }
        }
        TextInput(vm.note, { vm.note = it }, "Qeyd (istəyə görə)")

        Spacer(Modifier.height(12.dp))
        PosCard {
            InfoRow("Ara cəm", vm.subtotal.money())
            if (vm.discount > 0) InfoRow("Endirim", "−" + vm.discount.money(), valueColor = extra.negative)
            InfoRow("Ümumi məbləğ", vm.total.money(), bold = true)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            if (split.cash > 0) InfoRow("Nağd (kassaya)", split.cash.money())
            if (split.card > 0) InfoRow("Kart", split.card.money())
            if (split.debt > 0) InfoRow("Borc", split.debt.money(), valueColor = extra.warning)
            if (split.change > 0) InfoRow("Qaytarılacaq qalıq", split.change.money(), bold = true, valueColor = extra.positive)
        }
        split.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(12.dp))
        BigButton(
            "Satışı tamamla · ${vm.total.money()}", Icons.Default.CheckCircle,
            onClick = { runner.run { val id = vm.complete(); onDone(id) } },
            modifier = Modifier.fillMaxWidth(),
            enabled = vm.cart.isNotEmpty() && split.error == null && !runner.busy,
        )
        TextButton(onClick = { vm.clear() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Satışı ləğv et (səbəti təmizlə)")
        }
    }

    if (pickCustomer) CustomerPickerDialog(customers, onPick = { vm.customer = it }, onAddNew = { pickCustomer = false; newCustomer = true },
        onDismiss = { pickCustomer = false })
    if (newCustomer) CustomerDialog(null, onSave = { c ->
        runner.run { val id = vm.repo.saveCustomer(c); vm.customer = vm.repo.dao.getCustomer(id) }
    }, onDismiss = { newCustomer = false })
    editItem?.let { item -> CartItemDialog(item, vm, runner) { editItem = null } }
}

@Composable
private fun CartItemDialog(item: CartItem, vm: SalesViewModel, runner: Runner, onDismiss: () -> Unit) {
    var qty by remember { mutableStateOf(item.qty.toString()) }
    var price by remember { mutableStateOf(Money.toInput(item.price)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.product.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField(qty, { qty = it }, "Say", supporting = "Stokda: ${item.product.stock}")
                MoneyField(price, { price = it }, "Satış qiyməti", supporting = "Standart: ${item.product.salePrice.money()}")
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qty.toIntOrNull() ?: 0
                val pr = Money.parse(price)
                if (pr == null) { runner.message("Qiyməti düzgün yazın"); return@Button }
                val err = vm.setQty(item, q)
                if (err != null) runner.message(err) else { item.price = pr; onDismiss() }
            }) { Text("Yadda saxla") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.cart.remove(item); onDismiss() }) { Text("Səbətdən sil", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("İmtina") }
            }
        },
    )
}

@Composable
fun SaleDoneDialog(saleId: Long, runner: Runner, onClose: () -> Unit, onOpen: () -> Unit) {
    val app = app()
    val receipt = rememberReceiptActions(runner)
    var data by remember { mutableStateOf<Pair<Sale, List<SaleItem>>?>(null) }
    LaunchedEffect(saleId) {
        val s = app.repo.dao.getSale(saleId)
        if (s != null) data = s to app.repo.dao.getSaleItems(saleId)
    }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Default.CheckCircle, null, tint = LocalExtraColors.current.positive, modifier = Modifier.size(48.dp)) },
        title = { Text("Satış tamamlandı") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Çek #$saleId")
                data?.first?.let { Text(it.total.money(), style = MaterialTheme.typography.headlineMedium.merge(NumberStyle)) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { data?.let { receipt.print(it.first, it.second) } }) {
                        Icon(Icons.Default.Print, null); Spacer(Modifier.width(4.dp)); Text("Çap et")
                    }
                    OutlinedButton(onClick = { data?.let { receipt.pdf(it.first, it.second) } }) {
                        Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(4.dp)); Text("PDF")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("Yeni satış") } },
        dismissButton = { TextButton(onClick = onOpen) { Text("Detallar") } },
    )
}

/**
 * Tanınmayan barkod skan edildikdə: barkodu mövcud (adətən barkodsuz) məhsula təyin edib dərhal səbətə atmaq
 * və ya bu barkodla yeni məhsul yaratmaq.
 */
@Composable
fun UnknownBarcodeDialog(
    code: String,
    products: List<Product>,
    onAssign: (Product) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    var onlyWithout by remember { mutableStateOf(true) }
    val filtered = remember(q, onlyWithout, products) {
        products.filter { (!onlyWithout || it.barcode.isBlank()) && (q.isBlank() || it.name.contains(q, true) || it.sku.contains(q, true) || it.brand.contains(q, true)) }
            .sortedWith(compareBy<Product> { it.barcode.isNotBlank() }.thenBy { it.name.lowercase() })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.QrCode2, null) },
        title = { Text("Barkod tapılmadı") },
        text = {
            Column {
                Text(code, style = MaterialTheme.typography.titleLarge.merge(NumberStyle))
                Text("Bu barkodu hansı məhsula təyin edək? Seçilən məhsul dərhal səbətə əlavə olunacaq, növbəti dəfə skan edəndə avtomatik tanınacaq.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                SearchField(q, { q = it }, "Məhsul adı")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(onlyWithout, { onlyWithout = it })
                    Text("Yalnız barkodsuz məhsullar", style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(filtered, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text("${p.salePrice.money()} · stok ${p.stock}" + if (p.barcode.isNotBlank()) " · köhnə barkod: ${p.barcode}" else "")
                            },
                            leadingContent = { ProductThumb(p.imagePath, p.name, 40.dp) },
                            trailingContent = { TextButton(onClick = { onAssign(p) }) { Text("Təyin et") } },
                        )
                    }
                    if (filtered.isEmpty()) item {
                        Text(if (onlyWithout) "Barkodsuz məhsul tapılmadı" else "Məhsul tapılmadı", Modifier.padding(12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Yeni məhsul yarat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bağla") } },
    )
}
