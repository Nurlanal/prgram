package az.magaza.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import az.magaza.pos.PosApp
import az.magaza.pos.data.PosException
import az.magaza.pos.data.db.PayMethod
import az.magaza.pos.ui.theme.NumberStyle
import az.magaza.pos.util.DateRange
import az.magaza.pos.util.Dates
import az.magaza.pos.util.Money
import az.magaza.pos.util.RangePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun app(): PosApp = LocalContext.current.applicationContext as PosApp

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("Snackbar yoxdur") }

/** Suspend əməliyyatı icra edir, xəta olarsa istifadəçiyə göstərir. */
class Runner(private val scope: CoroutineScope, private val snackbar: SnackbarHostState) {
    var busy by mutableStateOf(false)
        private set

    fun run(success: String? = null, onDone: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
                onDone()
                success?.let { snackbar.showSnackbar(it, withDismissAction = true) }
            } catch (e: PosException) {
                snackbar.showSnackbar(e.message ?: "Xəta", withDismissAction = true)
            } catch (e: Exception) {
                snackbar.showSnackbar("Xəta: ${e.message ?: e.javaClass.simpleName}", withDismissAction = true)
            } finally {
                busy = false
            }
        }
    }

    fun message(text: String) { scope.launch { snackbar.showSnackbar(text, withDismissAction = true) } }
}

@Composable
fun rememberRunner(): Runner {
    val scope = rememberCoroutineScope()
    val sb = LocalSnackbar.current
    return remember { Runner(scope, sb) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                onMenu != null -> IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Menyu") }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.merge(NumberStyle), color = valueColor, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun InfoRow(label: String, value: String, bold: Boolean = false, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleMedium.merge(NumberStyle) else MaterialTheme.typography.bodyLarge.merge(NumberStyle.copy(fontWeight = FontWeight.Medium)),
            color = valueColor,
        )
    }
}

@Composable
fun PosCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Təmizlə") }
                trailing?.invoke()
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
    )
}

@Composable
fun MoneyField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    supporting: String? = null,
) {
    val invalid = value.isNotBlank() && Money.parse(value) == null
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        suffix = { Text("₼") },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        isError = invalid,
        supportingText = if (invalid) ({ Text("Düzgün məbləğ yazın") }) else supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
    )
}

@Composable
fun IntField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, supporting: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(7)) },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
    )
}

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine, minLines = if (singleLine) 1 else 2,
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T?,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String? = null,
    onNone: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(display) ?: (noneLabel ?: ""),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (noneLabel != null && onNone != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onNone(); open = false })
            }
            options.forEach { o ->
                DropdownMenuItem(text = { Text(display(o)) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodSelector(method: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        SegmentedButton(selected = method == PayMethod.CASH, onClick = { onChange(PayMethod.CASH) },
            shape = SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Icons.Default.Payments, null, Modifier.size(18.dp)) }) { Text("Nağd") }
        SegmentedButton(selected = method == PayMethod.CARD, onClick = { onChange(PayMethod.CARD) },
            shape = SegmentedButtonDefaults.itemShape(1, 2), icon = { Icon(Icons.Default.CreditCard, null, Modifier.size(18.dp)) }) { Text("Kart") }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "Bəli",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}

/** Məbləğ (və istəyə görə qeyd) soruşan ümumi dialoq. */
@Composable
fun AmountDialog(
    title: String,
    confirmText: String = "Təsdiqlə",
    initial: Long = 0,
    showMethod: Boolean = false,
    info: String? = null,
    onConfirm: (amount: Long, method: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(Money.toInput(initial)) }
    var note by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PayMethod.CASH) }
    val parsed = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info != null) Text(info, style = MaterialTheme.typography.bodyMedium)
                MoneyField(amount, { amount = it }, "Məbləğ")
                if (showMethod) MethodSelector(method, { method = it })
                TextInput(note, { note = it }, "Qeyd (istəyə görə)")
            }
        },
        confirmButton = {
            Button(enabled = parsed != null && parsed > 0, onClick = { onConfirm(parsed!!, method, note); onDismiss() }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeSelector(range: DateRange, onChange: (DateRange) -> Unit, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    var picker by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RangePreset.entries.forEach { p ->
                FilterChip(
                    selected = range.preset == p,
                    onClick = { if (p == RangePreset.CUSTOM) picker = true else onChange(DateRange.of(p)) },
                    label = { Text(p.label) },
                    leadingIcon = if (p == RangePreset.CUSTOM) ({ Icon(Icons.Default.DateRange, null, Modifier.size(18.dp)) }) else null,
                )
            }
        }
        if (showLabel) Text(range.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (picker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = Dates.localToUtcDay(range.from.coerceAtLeast(0)),
            initialSelectedEndDateMillis = Dates.localToUtcDay(range.to),
        )
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(enabled = state.selectedStartDateMillis != null, onClick = {
                    val s = Dates.utcDayToLocalStart(state.selectedStartDateMillis!!)
                    val e = Dates.utcDayToLocalStart(state.selectedEndDateMillis ?: state.selectedStartDateMillis!!)
                    onChange(DateRange.custom(s, e)); picker = false
                }) { Text("Seç") }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("İmtina") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.weight(1f), title = {
                Text("Tarix aralığı", Modifier.padding(start = 24.dp, top = 16.dp))
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = Dates.date(value), onValueChange = {}, readOnly = true, label = { Text(label) },
        trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Default.CalendarMonth, "Tarix seç") } },
        modifier = modifier.fillMaxWidth(),
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.localToUtcDay(value))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val day = Dates.utcDayToLocalStart(utc)
                        // Bu günün tarixi seçilibsə, cari saatı saxlayırıq.
                        val nowDay = Dates.startOfDay(System.currentTimeMillis())
                        onChange(if (day == nowDay) System.currentTimeMillis() else day + 12L * 3600_000)
                    }
                    open = false
                }) { Text("Seç") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("İmtina") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = color,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.13f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun IconCircle(icon: ImageVector, tint: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun BigButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
              colors: ButtonColors = ButtonDefaults.buttonColors()) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp), colors = colors) {
        if (icon != null) { Icon(icon, null); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
