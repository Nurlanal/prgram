package az.psclub.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.psclub.app.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US)

/** Payment method state shared by table close and cart checkout. */
class PayState {
    var credit by mutableStateOf(false)
    var name by mutableStateOf("")
    var phone by mutableStateOf("")
    val valid get() = !credit || name.isNotBlank()
    val debtorName get() = if (credit) name.trim() else null
}

@Composable
fun rememberPayState() = remember { PayState() }

@Composable
fun PaymentPicker(store: Store, state: PayState) {
    val p = LocalPalette.current
    Text("Ödəniş üsulu", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("💵 Nağd", !state.credit, Modifier.weight(1f)) { state.credit = false }
        Chip("📒 Nisyə (borc)", state.credit, Modifier.weight(1f)) { state.credit = true }
    }
    AnimatedVisibility(state.credit) {
        Column(Modifier.padding(top = 10.dp)) {
            Field("Müştərinin adı", state.name, { state.name = it })
            Spacer(Modifier.height(8.dp))
            Field("Telefon (istəyə görə)", state.phone, { state.phone = it }, keyboard = KeyboardType.Phone)
            val q = state.name.trim().lowercase()
            val known = store.debtors.filter { q.isEmpty() || it.name.lowercase().contains(q) }.take(10)
            if (known.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    known.forEach { d ->
                        Chip("${d.name} · ${money(store.debtOf(d.id))}", state.name.equals(d.name, true)) {
                            state.name = d.name; state.phone = d.phone
                        }
                    }
                }
            }
            Text("Məbləğ borc dəftərinə yazılacaq", color = p.warn, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ================================================================= screen
@Composable
fun DebtScreen(store: Store) {
    val p = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var openId by remember { mutableStateOf<Long?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val q = query.trim().lowercase()
    val list = store.debtors
        .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.phone.contains(q) }
        .map { it to store.debtOf(it.id) }
        .sortedByDescending { it.second }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle("Borc dəftəri", sub = "Nisyə yazılan məbləğlər") }
        item {
            val debtorsWithDebt = store.debtors.count { store.debtOf(it.id) > 0 }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3B0A1E), Color(0xFFB91C4B), Color(0xFF2A0712))))
                    .padding(20.dp),
            ) {
                Text("Ümumi borc", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Text(money(store.totalDebt()), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                Text("$debtorsWithDebt nəfər borcludur · ${store.debtors.size} nəfər dəftərdə", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    placeholder = { Text("Ad və ya telefon axtar…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(50), modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Pill("Yeni", filled = true, leadingPlus = true) { showNew = true }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("Ad", color = p.dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Borcu", color = p.dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (list.isEmpty()) item {
            NeonCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📒", fontSize = 44.sp)
                    Text(if (q.isEmpty()) "Borc dəftəri boşdur" else "Tapılmadı", color = p.dim)
                }
            }
        }
        items(list, key = { it.first.id }) { (d, debt) -> DebtorRow(d, debt) { openId = d.id } }
        if (list.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                Text("Ümumi", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text(money(list.sumOf { it.second }.let { Math.round(it * 100) / 100.0 }), color = p.danger, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
    openId?.let { id -> store.debtors.firstOrNull { it.id == id }?.let { DebtorSheet(store, it) { openId = null } } }
    if (showNew) NewDebtSheet(store) { showNew = false }
}

@Composable
private fun DebtorRow(d: Debtor, debt: Double, onClick: () -> Unit) {
    val p = LocalPalette.current
    NeonCard(Modifier.fillMaxWidth().bounceClick(onClick = onClick), radius = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.accent2, p.accent))),
                contentAlignment = Alignment.Center,
            ) { Text(d.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (d.phone.isNotBlank()) Text(d.phone, color = p.dim, fontSize = 13.sp)
            }
            if (debt > 0) Text(money(debt), color = p.danger, fontWeight = FontWeight.Black, fontSize = 17.sp)
            else Badge("Ödənilib", p.success)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtSheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.bg, contentColor = p.text,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 28.dp).navigationBarsPadding().imePadding(),
            content = content,
        )
    }
}

@Composable
private fun DebtorSheet(store: Store, d: Debtor, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var payText by remember { mutableStateOf("") }
    var addText by remember { mutableStateOf("") }
    var addNote by remember { mutableStateOf("") }
    var askDelete by remember { mutableStateOf(false) }
    val debt = store.debtOf(d.id)
    DebtSheetFrame(onDismiss) {
        Text(d.name, color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Black)
        if (d.phone.isNotBlank()) Text("📞 ${d.phone}", color = p.dim)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoBox("Borcu", money(debt), Modifier.weight(1f), if (debt > 0) p.danger else p.success)
            InfoBox("Ümumi alınıb", money(store.recordsOf(d.id).filter { it.amount > 0 }.sumOf { it.amount }.let { Math.round(it * 100) / 100.0 }), Modifier.weight(1f))
        }
        if (debt > 0) {
            Spacer(Modifier.height(14.dp))
            NeonCard(Modifier.fillMaxWidth()) {
                Text("💵 Ödəniş qəbul et (nağd)", color = p.text, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field("Məbləğ ₼", payText, { payText = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                    Spacer(Modifier.width(8.dp))
                    Pill("Hamısı") { payText = plain(debt) }
                }
                Spacer(Modifier.height(10.dp))
                BigButton("Ödənişi yaz", enabled = (parsePrice(payText) ?: 0.0) > 0) {
                    parsePrice(payText)?.let { store.payDebt(d, it) }; payText = ""
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        NeonCard(Modifier.fillMaxWidth()) {
            Text("📒 Borc əlavə et", color = p.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field("Məbləğ ₼", addText, { addText = it }, keyboard = KeyboardType.Decimal)
            Spacer(Modifier.height(8.dp))
            Field("Qeyd (nə üçün)", addNote, { addNote = it })
            Spacer(Modifier.height(10.dp))
            BigButton("Borca yaz", enabled = (parsePrice(addText) ?: 0.0) > 0) {
                parsePrice(addText)?.let { store.addDebt(d.name, d.phone, it, addNote.ifBlank { "Əl ilə əlavə" }) }
                addText = ""; addNote = ""
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Tarixçə", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        store.recordsOf(d.id).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (r.amount > 0) r.note.ifBlank { "Borc" } else "Ödəniş", color = p.text, fontSize = 14.sp, maxLines = 2)
                    Text(dFmt.format(Date(r.time)), color = p.dim, fontSize = 12.sp)
                }
                Text(
                    (if (r.amount > 0) "+" else "−") + money(Math.abs(r.amount)),
                    color = if (r.amount > 0) p.danger else p.success, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.danger.copy(alpha = 0.12f))
                .bounceClick { askDelete = true }.padding(14.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Delete, null, tint = p.danger)
            Spacer(Modifier.width(8.dp))
            Text("Dəftərdən sil", color = p.danger, fontWeight = FontWeight.Bold)
        }
    }
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text("${d.name} silinsin?") },
        text = { Text(if (debt > 0) "Qalan borc ${money(debt)} də silinəcək." else "Bütün qeydlər silinəcək.") },
        confirmButton = { TextButton({ askDelete = false; store.deleteDebtor(d); onDismiss() }) { Text("Sil") } },
        dismissButton = { TextButton({ askDelete = false }) { Text("Ləğv et") } },
    )
}

@Composable
private fun NewDebtSheet(store: Store, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    DebtSheetFrame(onDismiss) {
        Text("Yeni borc yazısı", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Field("Ad", name, { name = it })
        Spacer(Modifier.height(8.dp))
        Field("Telefon (istəyə görə)", phone, { phone = it }, keyboard = KeyboardType.Phone)
        Spacer(Modifier.height(8.dp))
        Field("Borcu ₼", amount, { amount = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(8.dp))
        Field("Qeyd", note, { note = it })
        store.debtorByName(name)?.let {
            Text("${it.name} artıq dəftərdədir (borcu ${money(store.debtOf(it.id))}). Məbləğ üstünə gələcək.",
                color = p.warn, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
        BigButton("Dəftərə yaz", enabled = name.isNotBlank() && (parsePrice(amount) ?: 0.0) > 0) {
            store.addDebt(name, phone, parsePrice(amount) ?: 0.0, note.ifBlank { "Əl ilə əlavə" }); onDismiss()
        }
    }
}
