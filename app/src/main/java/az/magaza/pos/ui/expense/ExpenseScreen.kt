package az.magaza.pos.ui.expense

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import az.magaza.pos.data.Categories
import az.magaza.pos.data.PosRepository
import az.magaza.pos.data.db.Expense
import az.magaza.pos.data.db.PayMethod
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
class ExpenseViewModel(val repo: PosRepository) : ViewModel() {
    val range = MutableStateFlow(DateRange.of(RangePreset.MONTH))
    val category = MutableStateFlow<String?>(null)
    val expenses = combine(range.flatMapLatest { repo.dao.observeExpensesInRange(it.from, it.to) }, category) { l, c ->
        if (c == null) l else l.filter { it.category == c }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun ExpenseScreen(nav: NavController) {
    val app = app()
    val vm: ExpenseViewModel = viewModel { ExpenseViewModel(app.repo) }
    val runner = rememberRunner()
    val range by vm.range.collectAsState()
    val category by vm.category.collectAsState()
    val list by vm.expenses.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf<Expense?>(null) }
    val extra = LocalExtraColors.current

    Scaffold(
        topBar = { PosTopBar("Məxaric — xərclər", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showForm = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Yeni xərc") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { RangeSelector(range, { vm.range.value = it }) }
            item {
                Dropdown("Kateqoriya", Categories.expenses, category, { it }, { vm.category.value = it },
                    noneLabel = "Bütün kateqoriyalar", onNone = { vm.category.value = null })
            }
            item {
                val active = list.filter { !it.cancelled }
                StatCard("Ümumi məxaric", active.sumOf { it.amount }.money(), Icons.Default.Outbox, Modifier.fillMaxWidth(),
                    tint = extra.negative, subtitle = "${active.size} əməliyyat")
            }
            if (list.isEmpty()) item { EmptyState(Icons.Default.Outbox, "Bu dövrdə xərc yoxdur") }
            items(list, key = { it.id }) { e ->
                PosCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(Icons.Default.Payments, extra.negative)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.category, style = MaterialTheme.typography.titleSmall)
                            if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodyMedium)
                            Text("${Dates.dateTime(e.createdAt)} · ${PosRepository.methodName(e.method)}" + if (e.note.isNotBlank()) " · ${e.note}" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("−" + e.amount.money(), style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                                color = if (e.cancelled) MaterialTheme.colorScheme.outline else extra.negative)
                            if (e.cancelled) Pill("Ləğv edilib", MaterialTheme.colorScheme.outline)
                            else TextButton(onClick = { cancel = e }) { Text("Ləğv et") }
                        }
                    }
                }
            }
        }
    }

    if (showForm) ExpenseFormDialog(vm, runner) { showForm = false }
    cancel?.let { e ->
        ConfirmDialog("Xərci ləğv et", "${e.category}: ${e.amount.money()} ləğv edilsin? Məbləğ kassaya geri qaytarılacaq.", "Ləğv et",
            destructive = true, onConfirm = { runner.run(success = "Xərc ləğv edildi") { vm.repo.cancelExpense(e.id) } },
            onDismiss = { cancel = null })
    }
}

@Composable
private fun ExpenseFormDialog(vm: ExpenseViewModel, runner: Runner, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.expenses.first()) }
    var description by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PayMethod.CASH) }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    val parsed = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni məxaric") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(amount, { amount = it }, "Məbləğ *")
                Dropdown("Kateqoriya", Categories.expenses, category, { it }, { category = it })
                TextInput(description, { description = it }, "Açıqlama")
                Text("Ödəniş üsulu", style = MaterialTheme.typography.labelLarge)
                MethodSelector(method, { method = it })
                DateField("Tarix", date, { date = it })
                TextInput(note, { note = it }, "Qeyd")
            }
        },
        confirmButton = {
            Button(enabled = parsed != null && parsed > 0 && !runner.busy, onClick = {
                runner.run(success = "Xərc qeyd edildi, kassa yeniləndi", onDone = onDismiss) {
                    vm.repo.addExpense(parsed!!, category, description, method, date, note)
                }
            }) { Text("Yadda saxla") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}
