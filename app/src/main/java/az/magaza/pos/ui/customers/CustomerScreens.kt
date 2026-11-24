package az.magaza.pos.ui.customers

import androidx.compose.foundation.clickable
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
import az.magaza.pos.data.DebtSchedule
import az.magaza.pos.data.OpenDebt
import az.magaza.pos.data.PosRepository
import az.magaza.pos.data.db.*
import az.magaza.pos.ui.Routes
import az.magaza.pos.ui.components.*
import az.magaza.pos.ui.theme.LocalExtraColors
import az.magaza.pos.ui.theme.NumberStyle
import az.magaza.pos.util.Dates
import az.magaza.pos.util.Receipt
import az.magaza.pos.util.money

@Composable
fun CustomersScreen(nav: NavController) {
    val app = app()
    val runner = rememberRunner()
    val list by app.repo.dao.observeCustomersWithDebt().collectAsState(emptyList())
    var q by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }
    val filtered = list.filter { q.isBlank() || it.name.contains(q, true) || it.phone.contains(q) }

    Scaffold(
        topBar = { PosTopBar("Müştərilər", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { add = true }, icon = { Icon(Icons.Default.PersonAdd, null) }, text = { Text("Müştəri") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SearchField(q, { q = it }, "Ad və ya telefon") }
            if (filtered.isEmpty()) item { EmptyState(Icons.Default.People, "Müştəri yoxdur") }
            items(filtered, key = { it.id }) { c -> CustomerRow(c) { nav.navigate(Routes.customer(c.id)) } }
        }
    }
    if (add) CustomerDialog(null, onSave = { runner.run(success = "Müştəri əlavə edildi") { app.repo.saveCustomer(it) } }) { add = false }
}

@Composable
private fun CustomerRow(c: CustomerWithDebt, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    PosCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Default.Person, if (c.debt > 0) extra.warning else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall)
                if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodySmall)
                c.firstDebtAt?.takeIf { c.debt > 0 }?.let {
                    Text("Borc yaranıb: ${Dates.date(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(c.debt.money(), style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                    color = if (c.debt > 0) extra.warning else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (c.debt > 0) "qalan borc" else "borc yoxdur", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun DebtsScreen(nav: NavController) {
    val app = app()
    val runner = rememberRunner()
    val list by app.repo.dao.observeCustomersWithDebt().collectAsState(emptyList())
    val allTx by app.repo.dao.observeAllDebtTx().collectAsState(emptyList())
    val openBy = remember(allTx) { DebtSchedule.openByCustomer(allTx) }
    var q by remember { mutableStateOf("") }
    var pay by remember { mutableStateOf<CustomerWithDebt?>(null) }
    var add by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    fun nextDue(id: Long) = openBy[id]?.let { DebtSchedule.nextDue(it) }
    // Əvvəlcə gecikmiş/bu gün olanlar, sonra yaxın tarixlilər, sonra tarixsizlər
    val debtors = list.filter { it.debt > 0 && (q.isBlank() || it.name.contains(q, true) || it.phone.contains(q)) }
        .sortedWith(compareBy<CustomerWithDebt> { nextDue(it.id) ?: Long.MAX_VALUE }.thenByDescending { it.debt })
    val dueToday = openBy.values.flatten().filter { (it.daysLeft(now) ?: 1) == 0 }
    val overdue = openBy.values.flatten().filter { (it.daysLeft(now) ?: 0) < 0 }
    val extra = LocalExtraColors.current

    Scaffold(
        topBar = { PosTopBar("Borclar", onBack = { nav.popBackStack() }, actions = {
            IconButton(onClick = { nav.navigate(Routes.CUSTOMERS) }) { Icon(Icons.Default.People, "Bütün müştərilər") }
        }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { add = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Borc əlavə et") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    StatCard("Ümumi qalan borc", list.filter { it.debt > 0 }.sumOf { it.debt }.money(), Icons.Default.RequestQuote,
                        Modifier.weight(1f).fillMaxHeight(), tint = extra.warning, subtitle = "${list.count { it.debt > 0 }} borclu")
                    StatCard("Ödənilmiş", list.sumOf { it.paid }.money(), Icons.Default.CheckCircle, Modifier.weight(1f).fillMaxHeight(),
                        tint = extra.positive)
                }
            }
            if (dueToday.isNotEmpty() || overdue.isNotEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            if (dueToday.isNotEmpty()) Text("Bu gün ödəniş günü: ${dueToday.map { it.tx.customerId }.distinct().size} müştəri · ${dueToday.sumOf { it.remaining }.money()}",
                                style = MaterialTheme.typography.titleSmall)
                            if (overdue.isNotEmpty()) Text("Gecikmiş: ${overdue.map { it.tx.customerId }.distinct().size} müştəri · ${overdue.sumOf { it.remaining }.money()}",
                                style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            item { SearchField(q, { q = it }, "Borclu axtar") }
            if (debtors.isEmpty()) item { EmptyState(Icons.Default.CheckCircle, "Borclu müştəri yoxdur") }
            items(debtors, key = { it.id }) { c ->
                PosCard(onClick = { nav.navigate(Routes.customer(c.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            Text(listOfNotNull(c.phone.takeIf { it.isNotBlank() }, c.firstDebtAt?.let { "Borc tarixi: " + Dates.date(it) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(c.debt.money(), style = MaterialTheme.typography.titleLarge.merge(NumberStyle), color = extra.warning)
                            DuePill(nextDue(c.id))
                        }
                        Button(onClick = { pay = c }) { Text("Ödə") }
                    }
                }
            }
        }
    }
    pay?.let { c ->
        AmountDialog("Borc ödənişi: ${c.name}", "Ödə", initial = c.debt, showMethod = true,
            info = "Qalan borc: ${c.debt.money()}. Tam və ya qismən ödəniş edə bilərsiniz.",
            onConfirm = { a, m, n -> runner.run(success = "Ödəniş qəbul edildi") { app.repo.payDebt(c.id, a, m, n) } },
            onDismiss = { pay = null })
    }
    if (add) {
        val customers by app.repo.dao.observeCustomers().collectAsState(emptyList())
        var picked by remember { mutableStateOf<Customer?>(null) }
        var newC by remember { mutableStateOf(false) }
        if (picked == null && !newC) CustomerPickerDialog(customers, onPick = { picked = it }, onAddNew = { newC = true }, onDismiss = { if (picked == null) add = false })
        if (newC) CustomerDialog(null, onSave = { c -> runner.run { picked = app.repo.dao.getCustomer(app.repo.saveCustomer(c)) } }) { newC = false }
        picked?.let { c ->
            DebtAddDialog("Borc əlavə et: ${c.name}",
                onConfirm = { a, due, n -> runner.run(success = "Borc əlavə edildi") { app.repo.addManualDebt(c.id, a, n, due) } },
                onDismiss = { picked = null; add = false })
        }
    }
}

@Composable
fun CustomerDetailScreen(nav: NavController, id: Long) {
    val app = app()
    val runner = rememberRunner()
    val customer by app.repo.dao.observeCustomer(id).collectAsState(null)
    val debt by app.repo.dao.observeCustomerDebt(id).collectAsState(0L)
    val txs by app.repo.dao.observeDebtTx(id).collectAsState(emptyList())
    val sales by app.repo.dao.observeCustomerSales(id).collectAsState(emptyList())
    val open = remember(txs) { DebtSchedule.open(txs) }
    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var dueFor by remember { mutableStateOf<OpenDebt?>(null) }
    val extra = LocalExtraColors.current

    Scaffold(topBar = {
        PosTopBar(customer?.name ?: "Müştəri", onBack = { nav.popBackStack() }, actions = {
            IconButton(onClick = { dialog = "edit" }) { Icon(Icons.Default.Edit, "Redaktə et") }
            IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Default.Delete, "Sil") }
        })
    }) { pad ->
        val c = customer ?: run { Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Müştəri tapılmadı") }; return@Scaffold }
        val totalDebt = txs.filter { it.amount > 0 }.sumOf { it.amount }
        val paid = -txs.filter { it.type == DebtType.PAYMENT }.sumOf { it.amount }
        val active = sales.filter { it.status == SaleStatus.ACTIVE }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PosCard {
                    Text(c.name, style = MaterialTheme.typography.titleLarge)
                    if (c.phone.isNotBlank()) Text(c.phone)
                    if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Borc məbləği (ümumi)", totalDebt.money())
                    InfoRow("Ödənilən məbləğ", paid.money(), valueColor = extra.positive)
                    InfoRow("Qalan borc", debt.money(), bold = true, valueColor = if (debt > 0) extra.warning else MaterialTheme.colorScheme.onSurface)
                    txs.filter { it.amount > 0 }.minOfOrNull { it.createdAt }?.let { InfoRow("Borcun yaranma tarixi", Dates.date(it)) }
                    DebtSchedule.nextDue(open)?.let { InfoRow("Növbəti ödəniş günü", Dates.date(it)) }
                    InfoRow("Alış-veriş", "${active.size} satış · ${active.sumOf { it.total }.money()}")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { dialog = "pay" }, enabled = debt > 0, modifier = Modifier.weight(1f)) { Text("Borc ödə") }
                    FilledTonalButton(onClick = { dialog = "add" }, modifier = Modifier.weight(1f)) { Text("Borc əlavə et") }
                }
            }
            if (open.isNotEmpty()) {
                item { SectionTitle("Açıq borclar (${open.size})") }
                items(open, key = { "o" + it.tx.id }) { o ->
                    PosCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(o.tx.note.ifBlank { debtTypeName(o.tx.type) }, style = MaterialTheme.typography.titleSmall)
                                Text("Yaranıb: ${Dates.date(o.tx.createdAt)}" + if (o.remaining != o.tx.amount) " · ilkin ${o.tx.amount.money()}" else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                if (o.dueAt != null) DuePill(o.dueAt) else Pill("Ödəniş tarixi yoxdur", MaterialTheme.colorScheme.outline)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(o.remaining.money(), style = MaterialTheme.typography.titleMedium.merge(NumberStyle), color = extra.warning)
                                TextButton(onClick = { dueFor = o }) {
                                    Icon(Icons.Default.EditCalendar, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                                    Text(if (o.dueAt == null) "Tarix təyin et" else "Tarixi dəyiş")
                                }
                            }
                        }
                    }
                }
            }
            item {
                TabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Borc tarixçəsi") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Satışlar (${sales.size})") })
                }
            }
            if (tab == 0) {
                if (txs.isEmpty()) item { EmptyState(Icons.Default.History, "Borc əməliyyatı yoxdur") }
                items(txs, key = { "d" + it.id }) { t ->
                    ListItem(
                        modifier = if (t.saleId != null) Modifier.clickable { nav.navigate(Routes.sale(t.saleId)) } else Modifier,
                        headlineContent = { Text(debtTypeName(t.type) + (t.method?.let { " · " + PosRepository.methodName(it) } ?: "")) },
                        supportingContent = {
                            Text(Dates.dateTime(t.createdAt) + (if (t.note.isNotBlank()) " · ${t.note}" else "") +
                                (t.dueAt?.let { "\nÖdəniş tarixi: ${Dates.date(it)}" } ?: ""))
                        },
                        trailingContent = {
                            Text((if (t.amount > 0) "+" else "") + t.amount.money(), style = MaterialTheme.typography.titleSmall.merge(NumberStyle),
                                color = if (t.amount > 0) extra.warning else extra.positive)
                        },
                    )
                }
            } else {
                if (sales.isEmpty()) item { EmptyState(Icons.Default.ShoppingBag, "Satış yoxdur") }
                items(sales, key = { "s" + it.id }) { s ->
                    ListItem(
                        modifier = Modifier.clickable { nav.navigate(Routes.sale(s.id)) },
                        headlineContent = { Text("Satış #${s.id} · ${Receipt.methodSummary(s)}") },
                        supportingContent = { Text(Dates.dateTime(s.createdAt) + if (s.status == SaleStatus.CANCELLED) " · LƏĞV EDİLİB" else "") },
                        trailingContent = { Text(s.total.money(), style = MaterialTheme.typography.titleSmall.merge(NumberStyle)) },
                    )
                }
            }
        }
    }
    customer?.let { c ->
        when (dialog) {
            "pay" -> AmountDialog("Borc ödənişi", "Ödə", initial = debt, showMethod = true, info = "Qalan borc: ${debt.money()}",
                onConfirm = { a, m, n -> runner.run(success = "Ödəniş qəbul edildi") { app.repo.payDebt(c.id, a, m, n) } }, onDismiss = { dialog = null })
            "add" -> DebtAddDialog("Borc əlavə et",
                onConfirm = { a, due, n -> runner.run(success = "Borc əlavə edildi") { app.repo.addManualDebt(c.id, a, n, due) } }, onDismiss = { dialog = null })
            "edit" -> CustomerDialog(c, onSave = { runner.run(success = "Yadda saxlanıldı") { app.repo.saveCustomer(it) } }) { dialog = null }
            "delete" -> ConfirmDialog("Müştərini sil", "${c.name} silinsin?", "Sil", destructive = true,
                onConfirm = { runner.run(success = "Müştəri silindi", onDone = { nav.popBackStack() }) { app.repo.deleteCustomer(c.id) } },
                onDismiss = { dialog = null })
        }
    }
    dueFor?.let { o ->
        DueDateDialog(o.dueAt, "${o.tx.note.ifBlank { debtTypeName(o.tx.type) }}: qalan ${o.remaining.money()}",
            onConfirm = { d -> runner.run(success = "Ödəniş tarixi yeniləndi") { app.repo.setDebtDueDate(o.tx.id, d) } },
            onDismiss = { dueFor = null })
    }
}

fun debtTypeName(t: String) = when (t) {
    DebtType.SALE -> "Borca satış"
    DebtType.MANUAL -> "Borc əlavə edildi"
    DebtType.PAYMENT -> "Ödəniş"
    DebtType.CANCEL -> "Satış ləğvi"
    else -> t
}
