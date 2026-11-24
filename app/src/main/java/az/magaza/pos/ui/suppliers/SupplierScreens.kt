package az.magaza.pos.ui.suppliers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import az.magaza.pos.data.db.SupplierTxType
import az.magaza.pos.ui.Routes
import az.magaza.pos.ui.components.*
import az.magaza.pos.ui.purchase.SupplierDialog
import az.magaza.pos.ui.theme.LocalExtraColors
import az.magaza.pos.ui.theme.NumberStyle
import az.magaza.pos.util.Dates
import az.magaza.pos.util.money

@Composable
fun SuppliersScreen(nav: NavController) {
    val app = app()
    val runner = rememberRunner()
    val list by app.repo.dao.observeSuppliersWithDebt().collectAsState(emptyList())
    var add by remember { mutableStateOf(false) }
    val extra = LocalExtraColors.current

    Scaffold(
        topBar = { PosTopBar("Təchizatçılar", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { add = true }, icon = { Icon(Icons.Default.AddBusiness, null) }, text = { Text("Təchizatçı") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StatCard("Təchizatçılara ümumi borc", list.filter { it.debt > 0 }.sumOf { it.debt }.money(), Icons.Default.LocalShipping,
                    Modifier.fillMaxWidth(), tint = extra.negative)
            }
            if (list.isEmpty()) item { EmptyState(Icons.Default.LocalShipping, "Təchizatçı yoxdur") }
            items(list, key = { it.id }) { s ->
                PosCard(onClick = { nav.navigate(Routes.supplier(s.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(Icons.Default.LocalShipping, MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall)
                            if (s.phone.isNotBlank()) Text(s.phone, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(s.debt.money(), style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                                color = if (s.debt > 0) extra.negative else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("borcumuz", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    if (add) SupplierDialog(null, onSave = { runner.run(success = "Təchizatçı əlavə edildi") { app.repo.saveSupplier(it) } }) { add = false }
}

@Composable
fun SupplierDetailScreen(nav: NavController, id: Long) {
    val app = app()
    val runner = rememberRunner()
    val supplier by app.repo.dao.observeSupplier(id).collectAsState(null)
    val debt by app.repo.dao.observeSupplierDebt(id).collectAsState(0L)
    val txs by app.repo.dao.observeSupplierTx(id).collectAsState(emptyList())
    var dialog by remember { mutableStateOf<String?>(null) }
    val extra = LocalExtraColors.current

    Scaffold(topBar = {
        PosTopBar(supplier?.name ?: "Təchizatçı", onBack = { nav.popBackStack() }, actions = {
            IconButton(onClick = { dialog = "edit" }) { Icon(Icons.Default.Edit, "Redaktə et") }
            IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Default.Delete, "Sil") }
        })
    }) { pad ->
        val s = supplier ?: run { Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Tapılmadı") }; return@Scaffold }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PosCard {
                    Text(s.name, style = MaterialTheme.typography.titleLarge)
                    if (s.phone.isNotBlank()) Text(s.phone)
                    if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Ümumi borcla alış", txs.filter { it.type == SupplierTxType.PURCHASE }.sumOf { it.amount }.money())
                    InfoRow("Ödənilib", (-txs.filter { it.type == SupplierTxType.PAYMENT }.sumOf { it.amount }).money(), valueColor = extra.positive)
                    InfoRow("Qalan borcumuz", debt.money(), bold = true, valueColor = if (debt > 0) extra.negative else MaterialTheme.colorScheme.onSurface)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { dialog = "pay" }, enabled = debt > 0, modifier = Modifier.weight(1f)) { Text("Ödəniş et") }
                    FilledTonalButton(onClick = { nav.navigate(Routes.PURCHASES) }, modifier = Modifier.weight(1f)) { Text("Mədaxil") }
                }
            }
            item { SectionTitle("Əməliyyatlar") }
            if (txs.isEmpty()) item { EmptyState(Icons.Default.History, "Əməliyyat yoxdur") }
            items(txs, key = { it.id }) { t ->
                ListItem(
                    headlineContent = {
                        Text(when (t.type) { SupplierTxType.PURCHASE -> "Borcla alış"; SupplierTxType.PAYMENT -> "Ödəniş"; else -> "Ləğv" })
                    },
                    supportingContent = { Text(Dates.dateTime(t.createdAt) + if (t.note.isNotBlank()) " · ${t.note}" else "") },
                    trailingContent = {
                        Text((if (t.amount > 0) "+" else "") + t.amount.money(), style = MaterialTheme.typography.titleSmall.merge(NumberStyle),
                            color = if (t.amount > 0) extra.negative else extra.positive)
                    },
                )
            }
        }
    }
    supplier?.let { s ->
        when (dialog) {
            "pay" -> AmountDialog("Təchizatçıya ödəniş", "Ödə", initial = debt, showMethod = true, info = "Borcumuz: ${debt.money()}. Məbləğ kassadan çıxacaq.",
                onConfirm = { a, m, n -> runner.run(success = "Ödəniş qeyd edildi") { app.repo.paySupplier(s.id, a, m, n) } }, onDismiss = { dialog = null })
            "edit" -> SupplierDialog(s, onSave = { runner.run(success = "Yadda saxlanıldı") { app.repo.saveSupplier(it) } }) { dialog = null }
            "delete" -> ConfirmDialog("Təchizatçını sil", "${s.name} silinsin?", "Sil", destructive = true,
                onConfirm = { runner.run(success = "Silindi", onDone = { nav.popBackStack() }) { app.repo.deleteSupplier(s.id) } },
                onDismiss = { dialog = null })
        }
    }
}
