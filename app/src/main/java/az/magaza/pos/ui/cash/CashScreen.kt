package az.magaza.pos.ui.cash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import az.magaza.pos.data.CashReport
import az.magaza.pos.data.PosRepository
import az.magaza.pos.data.Reports
import az.magaza.pos.data.db.CashTransaction
import az.magaza.pos.data.db.PayMethod
import az.magaza.pos.ui.components.*
import az.magaza.pos.ui.theme.LocalExtraColors
import az.magaza.pos.ui.theme.NumberStyle
import az.magaza.pos.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class CashViewModel(val repo: PosRepository) : ViewModel() {
    val range = MutableStateFlow(DateRange.of(RangePreset.TODAY))
    val balance = repo.dao.observeCashBalance().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val cash = repo.dao.observeCashBalanceBy(PayMethod.CASH).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val card = repo.dao.observeCashBalanceBy(PayMethod.CARD).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val session = repo.dao.observeOpenSession().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val sessions = repo.dao.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val data: StateFlow<Pair<List<CashTransaction>, CashReport?>> = range.flatMapLatest { r ->
        repo.dao.observeCashTxInRange(r.from, r.to).mapLatest { it to Reports.cash(repo.dao, r) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<CashTransaction>() to null)
}

@Composable
fun CashScreen(nav: NavController, openMenu: () -> Unit) {
    val app = app()
    val ctx = LocalContext.current
    val vm: CashViewModel = viewModel { CashViewModel(app.repo) }
    val runner = rememberRunner()
    val balance by vm.balance.collectAsState()
    val cash by vm.cash.collectAsState()
    val card by vm.card.collectAsState()
    val session by vm.session.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val range by vm.range.collectAsState()
    val data by vm.data.collectAsState()
    val (txs, report) = data
    val extra = LocalExtraColors.current
    val cs = MaterialTheme.colorScheme
    var dialog by remember { mutableStateOf<String?>(null) }
    var showSessions by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        PosTopBar("Kassa", onMenu = openMenu, actions = {
            IconButton(onClick = {
                report?.let { r -> Share.text(ctx, cashReportText(app.settings.state.value.storeName, range, r), "Kassa hesabatını paylaş") }
            }) { Icon(Icons.Default.Share, "Hesabatı paylaş") }
        })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = cs.secondary, contentColor = cs.onSecondary)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Cari kassa balansı", style = MaterialTheme.typography.labelLarge)
                        Text(balance.money(), style = MaterialTheme.typography.headlineMedium.merge(NumberStyle))
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("Nağd pul", style = MaterialTheme.typography.labelMedium)
                                Text(cash.money(), style = MaterialTheme.typography.titleLarge.merge(NumberStyle))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Kart (bank)", style = MaterialTheme.typography.labelMedium)
                                Text(card.money(), style = MaterialTheme.typography.titleLarge.merge(NumberStyle))
                            }
                        }
                    }
                }
            }
            item {
                PosCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(if (session != null) Icons.Default.LockOpen else Icons.Default.Lock,
                            if (session != null) extra.positive else cs.outline)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (session != null) "Kassa açıqdır" else "Kassa bağlıdır", style = MaterialTheme.typography.titleSmall)
                            Text(session?.let { "Açılış: ${Dates.dateTime(it.openedAt)} · ${it.openingBalance.money()}" }
                                ?: "Gün başlayanda kassanı açın", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        if (session == null) Button(onClick = { dialog = "open" }) { Text("Aç") }
                        else Button(onClick = { dialog = "close" }, colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary)) { Text("Bağla") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { dialog = "in" }, Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Default.AddCircle, null); Spacer(Modifier.width(6.dp)); Text("Pul əlavə et")
                    }
                    FilledTonalButton(onClick = { dialog = "out" }, Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Default.RemoveCircle, null); Spacer(Modifier.width(6.dp)); Text("Pul çıxar")
                    }
                }
            }
            item { SectionTitle("Kassa hesabatı", action = { TextButton(onClick = { showSessions = !showSessions }) { Text(if (showSessions) "Gizlət" else "Açılış/bağlanışlar") } }) }
            if (showSessions) {
                if (sessions.isEmpty()) item { Text("Hələ kassa açılışı olmayıb", color = cs.onSurfaceVariant) }
                items(sessions, key = { "sess" + it.id }) { s ->
                    PosCard {
                        Text("Açılış: ${Dates.dateTime(s.openedAt)} — ${s.openingBalance.money()}", style = MaterialTheme.typography.bodyMedium)
                        if (s.closedAt != null) {
                            Text("Bağlanış: ${Dates.dateTime(s.closedAt)} — sayılan ${s.countedBalance?.money()}", style = MaterialTheme.typography.bodyMedium)
                            val diff = (s.countedBalance ?: 0) - (s.expectedBalance ?: 0)
                            Text("Gözlənilən: ${s.expectedBalance?.money()} · Fərq: ${diff.money()}", style = MaterialTheme.typography.bodySmall,
                                color = if (diff == 0L) cs.onSurfaceVariant else extra.negative)
                        } else Pill("Açıqdır", extra.positive)
                    }
                }
            }
            item { RangeSelector(range, { vm.range.value = it }) }
            report?.let { r ->
                item {
                    PosCard {
                        InfoRow("Kassa açılışı (dövrün əvvəli)", r.opening.money(), bold = true)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        InfoRow("Nağd satış", "+" + r.cashSales.money(), valueColor = extra.positive)
                        InfoRow("Kart satışları", "+" + r.cardSales.money(), valueColor = extra.positive)
                        InfoRow("Borc ödənişləri", "+" + r.debtPayments.money(), valueColor = extra.positive)
                        InfoRow("Kassa mədaxili", "+" + r.cashIn.money(), valueColor = extra.positive)
                        InfoRow("Kassa məxarici", "−" + r.cashOut.money(), valueColor = extra.negative)
                        InfoRow("Xərclər (məxaric)", "−" + r.expenses.money(), valueColor = extra.negative)
                        InfoRow("Təchizatçıya ödənişlər", "−" + r.supplierPayments.money(), valueColor = extra.negative)
                        InfoRow("Mədaxil (alış) ödənişləri", "−" + r.purchasePayments.money(), valueColor = extra.negative)
                        if (r.cancellations != 0L) InfoRow("Satış ləğvləri", r.cancellations.money(), valueColor = extra.negative)
                        if (r.adjustments != 0L) InfoRow("Düzəlişlər", r.adjustments.money())
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        InfoRow("Kassa bağlanışı (dövrün sonu)", r.closing.money(), bold = true)
                        InfoRow("  o cümlədən nağd", r.closingCash.money())
                        InfoRow("  o cümlədən kart", r.closingCard.money())
                    }
                }
            }
            item { SectionTitle("Əməliyyatlar (${txs.size})") }
            if (txs.isEmpty()) item { EmptyState(Icons.Default.ReceiptLong, "Bu dövrdə kassa əməliyyatı yoxdur") }
            items(txs, key = { it.id }) { t ->
                ListItem(
                    headlineContent = { Text(Reports.cashTypeName(t.type) + " · " + PosRepository.methodName(t.method)) },
                    supportingContent = { Text(Dates.dateTime(t.createdAt) + if (t.note.isNotBlank()) "\n${t.note}" else "") },
                    trailingContent = {
                        Text((if (t.amount >= 0) "+" else "") + t.amount.money(), style = MaterialTheme.typography.titleSmall.merge(NumberStyle),
                            color = if (t.amount >= 0) extra.positive else extra.negative)
                    },
                )
            }
        }
    }

    when (dialog) {
        "in" -> AmountDialog("Kassaya pul əlavə et", showMethod = true,
            onConfirm = { a, m, n -> runner.run(success = "Kassaya əlavə edildi") { vm.repo.cashIn(a, m, n) } }, onDismiss = { dialog = null })
        "out" -> AmountDialog("Kassadan pul çıxar", showMethod = true,
            onConfirm = { a, m, n -> runner.run(success = "Kassadan çıxarıldı") { vm.repo.cashOut(a, m, n) } }, onDismiss = { dialog = null })
        "open" -> CountDialog("Kassanı aç", "Sistemdə nağd: ${cash.money()}. Kassadakı faktiki nağd pulu sayıb yazın.", cash,
            onConfirm = { a, n -> runner.run(success = "Kassa açıldı") { vm.repo.openCashSession(a, n) } }, onDismiss = { dialog = null })
        "close" -> CountDialog("Kassanı bağla", "Gözlənilən nağd: ${cash.money()}. Faktiki sayılan məbləği yazın. Fərq olarsa, düzəliş kimi qeyd ediləcək.", cash,
            onConfirm = { a, n -> runner.run(success = "Kassa bağlandı") { vm.repo.closeCashSession(a, n) } }, onDismiss = { dialog = null })
    }
}

@Composable
private fun CountDialog(title: String, info: String, initial: Long, onConfirm: (Long, String) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(Money.toInput(initial).ifEmpty { "0" }) }
    var note by remember { mutableStateOf("") }
    val parsed = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(info)
                MoneyField(amount, { amount = it }, "Sayılan nağd məbləğ")
                if (parsed != null && parsed != initial) Text("Fərq: ${(parsed - initial).money()}", color = MaterialTheme.colorScheme.error)
                TextInput(note, { note = it }, "Qeyd")
            }
        },
        confirmButton = { Button(enabled = parsed != null, onClick = { onConfirm(parsed!!, note); onDismiss() }) { Text("Təsdiqlə") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}

fun cashReportText(store: String, range: DateRange, r: CashReport): String = buildString {
    appendLine("$store — Kassa hesabatı")
    appendLine(range.label)
    appendLine("Açılış: ${Money.formatAzn(r.opening)}")
    appendLine("Nağd satış: ${Money.formatAzn(r.cashSales)}")
    appendLine("Kart satışları: ${Money.formatAzn(r.cardSales)}")
    appendLine("Borc ödənişləri: ${Money.formatAzn(r.debtPayments)}")
    appendLine("Kassa mədaxili: ${Money.formatAzn(r.cashIn)}")
    appendLine("Kassa məxarici: ${Money.formatAzn(r.cashOut)}")
    appendLine("Xərclər: ${Money.formatAzn(r.expenses)}")
    appendLine("Təchizatçıya ödənişlər: ${Money.formatAzn(r.supplierPayments)}")
    appendLine("Mədaxil ödənişləri: ${Money.formatAzn(r.purchasePayments)}")
    if (r.cancellations != 0L) appendLine("Satış ləğvləri: ${Money.formatAzn(r.cancellations)}")
    if (r.adjustments != 0L) appendLine("Düzəlişlər: ${Money.formatAzn(r.adjustments)}")
    appendLine("Bağlanış: ${Money.formatAzn(r.closing)} (nağd ${Money.formatAzn(r.closingCash)}, kart ${Money.formatAzn(r.closingCard)})")
}
