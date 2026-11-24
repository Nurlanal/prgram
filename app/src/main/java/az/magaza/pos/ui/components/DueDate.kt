package az.magaza.pos.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import az.magaza.pos.data.DebtSchedule
import az.magaza.pos.ui.theme.LocalExtraColors
import az.magaza.pos.util.Dates
import az.magaza.pos.util.Money
import java.util.Calendar

/** Android 13+ üçün bildiriş icazəsini soruşan funksiya qaytarır. */
@Composable
fun rememberNotificationPermission(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember {
        {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun plusDays(days: Int) = Calendar.getInstance().apply {
    timeInMillis = Dates.startOfDay(System.currentTimeMillis()); add(Calendar.DAY_OF_YEAR, days)
}.timeInMillis

private fun plusMonths(m: Int) = Calendar.getInstance().apply {
    timeInMillis = Dates.startOfDay(System.currentTimeMillis()); add(Calendar.MONTH, m)
}.timeInMillis

/** Borcun ödəniş tarixi seçimi: Yoxdur / 1 həftə / 2 həftə / 1 ay / istənilən tarix. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDateSelector(value: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val askPermission = rememberNotificationPermission()
    var picker by remember { mutableStateOf(false) }
    fun set(v: Long?) { onChange(v); if (v != null) askPermission() }
    val presets = listOf("1 həftə" to plusDays(7), "2 həftə" to plusDays(14), "1 ay" to plusMonths(1))
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NotificationsActive, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("Borcun ödəniş tarixi", style = MaterialTheme.typography.labelLarge)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(value == null, { set(null) }, { Text("Yoxdur") })
            presets.forEach { (label, v) -> FilterChip(value == v, { set(v) }, { Text(label) }) }
            val custom = value != null && presets.none { it.second == value }
            FilterChip(custom, { picker = true }, { Text(if (custom) Dates.date(value!!) else "Tarix seç") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)) })
        }
        Text(
            if (value == null) "Tarix seçilsə, həmin gün telefona bildiriş gələcək"
            else "${Dates.date(value)} tarixində telefona xatırlatma gələcək",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (picker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = Dates.localToUtcDay(value ?: plusDays(7)),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis >= Dates.localToUtcDay(System.currentTimeMillis())
            },
        )
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(enabled = state.selectedDateMillis != null, onClick = {
                    set(Dates.utcDayToLocalStart(state.selectedDateMillis!!)); picker = false
                }) { Text("Seç") }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("İmtina") } },
        ) { DatePicker(state) }
    }
}

/** Ödəniş gününə görə rəngli nişan. */
@Composable
fun DuePill(dueAt: Long?) {
    if (dueAt == null) return
    val days = ((Dates.startOfDay(dueAt) - Dates.startOfDay(System.currentTimeMillis())) / 86_400_000L).toInt()
    val extra = LocalExtraColors.current
    val color: Color = when {
        days < 0 -> extra.negative
        days == 0 -> extra.warning
        days <= 3 -> extra.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Pill("${Dates.date(dueAt)} · ${DebtSchedule.dueLabel(days)}", color)
}

/** Əl ilə borc əlavə etmək: məbləğ, ödəniş tarixi, qeyd. */
@Composable
fun DebtAddDialog(title: String, onConfirm: (amount: Long, dueAt: Long?, note: String) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<Long?>(null) }
    val parsed = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kassaya təsir etmir (məs. köhnə borcların qeydiyyatı).", style = MaterialTheme.typography.bodySmall)
                MoneyField(amount, { amount = it }, "Məbləğ")
                DueDateSelector(due, { due = it })
                TextInput(note, { note = it }, "Qeyd (istəyə görə)")
            }
        },
        confirmButton = {
            Button(enabled = parsed != null && parsed > 0, onClick = { onConfirm(parsed!!, due, note); onDismiss() }) { Text("Əlavə et") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}

/** Mövcud borcun ödəniş tarixini dəyişmək (məs. müştəri möhlət istədikdə). */
@Composable
fun DueDateDialog(initial: Long?, subtitle: String, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    var due by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ödəniş tarixi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                DueDateSelector(due, { due = it })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(due); onDismiss() }) { Text("Yadda saxla") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}
