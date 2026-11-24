package az.magaza.pos.ui.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import az.magaza.pos.data.Categories
import az.magaza.pos.data.Cost
import az.magaza.pos.data.PosRepository
import az.magaza.pos.data.db.Product
import az.magaza.pos.ui.components.*
import az.magaza.pos.ui.theme.LocalExtraColors
import az.magaza.pos.ui.theme.NumberStyle
import az.magaza.pos.util.money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class StockCountViewModel(val repo: PosRepository) : ViewModel() {
    val products = repo.dao.observeProducts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Faktiki sayılan qalıqlar (məhsul id → mətn). Boş — dəyişməyib. */
    val counts = mutableStateMapOf<Long, String>()
    var query by mutableStateOf("")
    var category by mutableStateOf<String?>(null)
    var onlyChanged by mutableStateOf(false)

    fun countOf(p: Product): Int? = counts[p.id]?.toIntOrNull()

    data class Change(val product: Product, val newStock: Int) {
        val qtyDiff get() = newStock - product.stock
        val valueDiff get() = Cost.afterRecount(product, newStock) - product.stockCost
    }

    fun changes(list: List<Product>): List<Change> =
        list.mapNotNull { p -> countOf(p)?.takeIf { it != p.stock }?.let { Change(p, it) } }
}

@Composable
fun StockCountScreen(nav: NavController) {
    val app = app()
    val vm: StockCountViewModel = viewModel { StockCountViewModel(app.repo) }
    val runner = rememberRunner()
    val products by vm.products.collectAsState()
    val extra = LocalExtraColors.current
    var confirm by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }

    val changes = vm.changes(products)
    val filtered = products.filter { p ->
        (vm.category == null || p.category == vm.category) &&
            (vm.query.isBlank() || p.name.contains(vm.query, true) || p.barcode.contains(vm.query) || p.sku.contains(vm.query, true)) &&
            (!vm.onlyChanged || changes.any { it.product.id == p.id })
    }

    // Skan etdikcə həmin məhsulun sayılan sayı +1 artır (malları bir-bir skan edərək saymaq üçün)
    val scan = rememberBarcodeScanner { code ->
        val p = products.firstOrNull { it.barcode == code || (it.sku.isNotBlank() && it.sku.equals(code, true)) }
        if (p == null) runner.message("Barkod \"$code\" ilə məhsul tapılmadı")
        else {
            val n = (vm.countOf(p) ?: 0) + 1
            vm.counts[p.id] = n.toString()
            runner.message("${p.name}: sayıldı $n (sistemdə ${p.stock})")
        }
    }

    Scaffold(
        topBar = {
            PosTopBar("Anbar sayımı", onBack = { nav.popBackStack() }, actions = {
                IconButton(onClick = scan) { Icon(Icons.Default.QrCodeScanner, "Skan edərək say") }
                IconButton(onClick = { reset = true }, enabled = vm.counts.isNotEmpty()) { Icon(Icons.Default.RestartAlt, "Sayımı sıfırla") }
            })
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${changes.size} məhsulda fərq", style = MaterialTheme.typography.labelMedium)
                        val q = changes.sumOf { it.qtyDiff }
                        val v = changes.sumOf { it.valueDiff }
                        Text("${if (q > 0) "+" else ""}$q əd. · ${if (v > 0) "+" else ""}${v.money()}",
                            style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                            color = if (v < 0) extra.negative else if (v > 0) extra.positive else MaterialTheme.colorScheme.onSurface)
                    }
                    BigButton("Təsdiqlə", Icons.Default.Check, { confirm = true }, enabled = changes.isNotEmpty() && !runner.busy)
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Hər məhsulun faktiki sayını yazın (boş saxlanılanlar dəyişmir) və ya malları skan edin — hər skan +1 sayır. " +
                    "Təsdiqlədikdə qalıqlar və anbar dəyəri qəpiyinə qədər düzəldilir, hər düzəliş stok tarixçəsinə yazılır.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { SearchField(vm.query, { vm.query = it }, "Ad, barkod, SKU") }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(vm.onlyChanged, { vm.onlyChanged = !vm.onlyChanged }, { Text("Yalnız fərqlilər (${changes.size})") })
                    FilterChip(vm.category == null, { vm.category = null }, { Text("Hamısı") })
                    Categories.products.forEach { c -> FilterChip(vm.category == c, { vm.category = if (vm.category == c) null else c }, { Text(c) }) }
                }
            }
            if (filtered.isEmpty()) item { EmptyState(Icons.Default.Inventory2, "Məhsul yoxdur") }
            items(filtered, key = { it.id }) { p ->
                val counted = vm.countOf(p)
                val diff = counted?.let { it - p.stock }
                PosCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductThumb(p.imagePath, p.name, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Sistemdə: ${p.stock} · dəyər ${(if (p.stock > 0) p.stockCost else 0L).money()}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (diff != null && diff != 0) {
                                val v = Cost.afterRecount(p, counted) - p.stockCost
                                Text("${if (diff > 0) "+" else ""}$diff əd. · ${if (v > 0) "+" else ""}${v.money()}",
                                    style = MaterialTheme.typography.labelMedium, color = if (diff < 0) extra.negative else extra.positive)
                            } else if (diff == 0) Text("Uyğundur", style = MaterialTheme.typography.labelMedium, color = extra.positive)
                        }
                        IconButton(onClick = { vm.counts[p.id] = ((counted ?: p.stock) - 1).coerceAtLeast(0).toString() }) {
                            Icon(Icons.Default.RemoveCircleOutline, "Azalt")
                        }
                        OutlinedTextField(
                            value = vm.counts[p.id] ?: "",
                            onValueChange = { v -> val t = v.filter(Char::isDigit).take(6); if (t.isEmpty()) vm.counts.remove(p.id) else vm.counts[p.id] = t },
                            placeholder = { Text("${p.stock}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(76.dp),
                        )
                        IconButton(onClick = { vm.counts[p.id] = ((counted ?: p.stock) + 1).toString() }) {
                            Icon(Icons.Default.AddCircleOutline, "Artır")
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        var note by remember { mutableStateOf("Anbar sayımı") }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Sayımı təsdiqlə") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    changes.take(8).forEach { c ->
                        Text("${c.product.name}: ${c.product.stock} → ${c.newStock}", style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (changes.size > 8) Text("… və daha ${changes.size - 8} məhsul")
                    Text("Anbar dəyəri dəyişikliyi: ${changes.sumOf { it.valueDiff }.money()}", style = MaterialTheme.typography.titleSmall)
                    TextInput(note, { note = it }, "Qeyd / səbəb")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val map = changes.associate { it.product.id to it.newStock }
                    runner.run(onDone = { confirm = false }) {
                        val r = vm.repo.applyStockCount(map, note)
                        vm.counts.clear()
                        runner.message("${r.changed} məhsulun qalığı düzəldildi (${r.valueDiff.money()})")
                    }
                }) { Text("Təsdiqlə") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("İmtina") } },
        )
    }
    if (reset) ConfirmDialog("Sayımı sıfırla", "Daxil edilmiş bütün saylar silinsin?", "Sıfırla",
        onConfirm = { vm.counts.clear() }, onDismiss = { reset = false })
}
