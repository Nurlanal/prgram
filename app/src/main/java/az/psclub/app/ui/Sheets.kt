package az.psclub.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.psclub.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.bg,
        contentColor = p.text,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)
                .navigationBarsPadding().imePadding(),
            content = content,
        )
    }
}

@Composable
private fun Row2(label: String, value: String, bold: Boolean = false, color: Color? = null) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = if (bold) p.text else p.dim, fontSize = if (bold) 18.sp else 15.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, color = color ?: p.text, fontSize = if (bold) 20.sp else 15.sp, fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold)
    }
}

// ================================================================ table
private val DURATIONS = listOf(30, 60, 120, 180, 300, 0)

@Composable
fun TableSheet(store: Store, table: GameTable, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val session = store.sessionFor(table.id)
    var receipt by remember { mutableStateOf<HistoryEntry?>(null) }
    Sheet(onDismiss) {
        ConsolePoster(table.console, Modifier.fillMaxWidth().height(210.dp), big = true, interactive = true)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(table.name, color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            if (table.vip) Badge("👑 VIP", Color(0xFFB8860B))
        }
        Text("${table.console.label} · ${table.seats} oyunçu · ${money(table.pricePerHour)}/saat", color = p.dim, fontSize = 15.sp)
        Spacer(Modifier.height(18.dp))

        AnimatedContent(
            targetState = when { receipt != null -> 2; session != null -> 1; else -> 0 },
            label = "state",
        ) { st ->
            Column {
                when (st) {
                    0 -> OpenTable(store, table, onDismiss)
                    1 -> session?.let { RunningTable(store, it) { receipt = store.closeSession(it) } }
                    2 -> receipt?.let { Receipt(it, onDismiss) }
                }
            }
        }
    }
}

@Composable
private fun OpenTable(store: Store, table: GameTable, onDone: () -> Unit) {
    val p = LocalPalette.current
    var minutes by remember { mutableIntStateOf(60) }
    var customer by remember { mutableStateOf("") }
    Text("Müddət seçin", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    DURATIONS.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { m ->
                val sel = minutes == m
                Box(
                    Modifier.weight(1f).bounceClick { minutes = m }.clip(RoundedCornerShape(18.dp))
                        .then(if (sel) Modifier.background(p.glow) else Modifier.background(p.card))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(durationLabel(m), color = if (sel) Color.White else p.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            if (m == 0) "dəqiqəlik" else money(Math.round(table.pricePerHour * m / 60.0 * 100) / 100.0),
                            color = if (sel) Color.White.copy(alpha = 0.85f) else p.dim, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Field("Müştərinin adı (istəyə görə)", customer, { customer = it })
    Spacer(Modifier.height(14.dp))
    NeonCard(Modifier.fillMaxWidth()) {
        if (minutes == 0) {
            Row2("Tarif", "${money(table.pricePerHour)}/saat")
            Row2("Dəqiqəlik", money(Math.round(table.pricePerHour / 60.0 * 100) / 100.0))
            Text("Masa açıq olduğu dəqiqələr qədər hesablanır.\nMəs: 25 dəq = ${money(Math.round(table.pricePerHour * 25 / 60.0 * 100) / 100.0)}", color = p.dim, fontSize = 12.sp)
        } else {
            Row2("Müddət", durationLabel(minutes))
            Row2("Bonus xal", "+${store.pointsFor(table.pricePerHour * minutes / 60.0)} ⭐")
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.border)
            Row2("Təxmini cəmi", money(Math.round(table.pricePerHour * minutes / 60.0 * 100) / 100.0), bold = true)
            Text("Tez bağlansa, yalnız oynanılan dəqiqələr ödənilir", color = p.dim, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(16.dp))
    BigButton("🎮  Masanı aç") { store.openTable(table, minutes, customer); onDone() }
}

@Composable
private fun RunningTable(store: Store, s: Session, onClose: () -> Unit) {
    val p = LocalPalette.current
    val now = LocalNow.current.value
    val elapsed = now - s.startedAt
    val planned = s.plannedMinutes * 60_000L
    val over = s.plannedMinutes > 0 && elapsed > planned
    var confirm by remember { mutableStateOf(false) }
    val inf = rememberInfiniteTransition(label = "blink")
    val blink by inf.animateFloat(1f, 0.35f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "b")

    NeonCard(Modifier.fillMaxWidth()) {
        Text(if (over) "⚠ Vaxt bitdi!" else "Oyun davam edir", color = if (over) p.danger else p.success,
            fontWeight = FontWeight.Bold, modifier = Modifier.graphicsLayer { alpha = if (over) blink else 1f })
        Text(
            clock(elapsed),
            style = TextStyle(brush = p.glow, fontSize = 52.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace),
        )
        if (s.plannedMinutes > 0) {
            val frac = (elapsed.toFloat() / planned).coerceIn(0f, 1f)
            val anim by animateFloatAsState(frac, label = "prog")
            LinearProgressIndicator(
                progress = { anim }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = if (over) p.danger else p.accent, trackColor = p.cardAlt,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (over) "Əlavə: ${clock(elapsed - planned)}" else "Qalıb: ${clock(planned - elapsed)} · Plan: ${durationLabel(s.plannedMinutes)}",
                color = p.dim, fontSize = 13.sp,
            )
        } else Text("Açıq vaxt · dəqiqəlik hesablama", color = p.dim, fontSize = 13.sp)
        if (s.customer.isNotBlank()) Text("Müştəri: ${s.customer}", color = p.dim, fontSize = 13.sp)
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = p.border)
        Row2("Oynanılan", "${store.billedMinutes(s, now)} dəq × ${money(Math.round(s.rate / 60.0 * 10000) / 10000.0)}")
        Row2("Oyun vaxtı", money(store.timeCost(s, now)))
        if (s.extrasTotal > 0) {
            Row2("Əlavələr", money(s.extrasTotal))
            Text(s.extrasNote, color = p.dim, fontSize = 12.sp)
        }
        Row2("Cəmi", money(store.totalCost(s, now)), bold = true)
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("+30 dəq", Modifier.weight(1f), leadingPlus = false) { store.extend(s, 30) }
        Pill("+1 saat", Modifier.weight(1f)) { store.extend(s, 60) }
        Pill("+2 saat", Modifier.weight(1f)) { store.extend(s, 120) }
    }
    Spacer(Modifier.height(14.dp))
    BigButton("Bağla və hesabla", danger = true) { confirm = true }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Masa bağlansın?") },
        text = { Text("Ödəniş: ${money(store.totalCost(s, now))}") },
        confirmButton = { TextButton({ confirm = false; onClose() }) { Text("Bağla") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Ləğv et") } },
    )
}

@Composable
private fun Receipt(e: HistoryEntry, onDone: () -> Unit) {
    val p = LocalPalette.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(if (shown) 1f else 0.6f, spring(dampingRatio = 0.4f), label = "rc")
    NeonCard(Modifier.fillMaxWidth().graphicsLayer { scaleX = s; scaleY = s }) {
        Text("✅", fontSize = 44.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Çek", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Text(e.title, color = p.text, fontWeight = FontWeight.Bold)
        Text(e.detail, color = p.dim, fontSize = 14.sp)
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = p.border)
        Row2("Ödəniləcək", money(e.amount), bold = true)
    }
    Spacer(Modifier.height(14.dp))
    BigButton("Hazırdır", onClick = onDone)
}

// =============================================================== repair
@Composable
fun RepairSheet(store: Store, r: RepairService, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var device by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var placed by remember { mutableStateOf<RepairOrder?>(null) }
    Sheet(onDismiss) {
        EmojiTile(r.emoji, r.id.toInt() + 3, Modifier.fillMaxWidth().height(170.dp), size = 80)
        Spacer(Modifier.height(16.dp))
        Text(r.name, color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(r.description, color = p.dim, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoBox("Qiymət", "${money(r.price)}-dan", Modifier.weight(1f))
            InfoBox("Müddət", r.duration, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(placed, label = "placed") { o ->
            Column {
                if (o == null) {
                    Text("Sifariş forması", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Field("Ad, soyad", name, { name = it })
                    Spacer(Modifier.height(8.dp))
                    Field("Telefon nömrəsi", phone, { phone = it }, keyboard = KeyboardType.Phone)
                    Spacer(Modifier.height(8.dp))
                    Field("Cihaz modeli (məs. iPhone 13)", device, { device = it })
                    Spacer(Modifier.height(8.dp))
                    Field("Problem haqqında qeyd", note, { note = it }, singleLine = false)
                    Spacer(Modifier.height(16.dp))
                    BigButton("Sifarişi təsdiqlə", enabled = name.isNotBlank() && phone.isNotBlank() && device.isNotBlank()) {
                        placed = store.placeOrder(r, name, phone, device, note)
                    }
                } else {
                    NeonCard(Modifier.fillMaxWidth()) {
                        Text("✅ Sifariş qəbul edildi", color = p.success, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Sifariş nömrəsi: #${o.number}", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("${o.device} · ${o.serviceName}", color = p.dim)
                        Text("Təxmini qiymət: ${money(o.price)}-dan", color = p.dim)
                    }
                    Spacer(Modifier.height(14.dp))
                    BigButton("Bağla", onClick = onDismiss)
                }
            }
        }
    }
}

// ================================================================= cart
@Composable
fun CartSheet(store: Store, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var target by remember { mutableStateOf<Long?>(null) }
    var done by remember { mutableStateOf(false) }
    Sheet(onDismiss) {
        Text("Səbət", color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        val items = store.cart.entries.mapNotNull { (id, q) -> store.services.firstOrNull { it.id == id }?.let { it to q } }
        if (done) {
            Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎉", fontSize = 56.sp)
                Text("Sifariş təsdiqləndi!", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            BigButton("Bağla", onClick = onDismiss)
            return@Sheet
        }
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🛒", fontSize = 56.sp)
                Text("Səbət boşdur", color = p.dim, fontSize = 16.sp)
                Text("Xidmətlər bölməsindən əlavə edin", color = p.dim, fontSize = 13.sp)
            }
            return@Sheet
        }
        items.forEach { (s, q) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                EmojiTile(s.emoji, s.id.toInt(), Modifier.size(58.dp), size = 28)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(money(s.price * q), color = p.dim, fontSize = 14.sp)
                }
                Stepper(q, { store.removeFromCart(s.id) }, { store.addToCart(s.id) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Ödəniş üsulu", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("💳 Ayrıca ödəniş", target == null) { target = null }
            store.sessions.forEach { s ->
                val t = store.table(s.tableId)
                Chip("🎮 ${t?.name ?: "Masa"}", target == s.id) { target = s.id }
            }
        }
        if (store.sessions.isNotEmpty()) Text("Aktiv masanın hesabına əlavə edə bilərsiniz", color = p.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        val total = store.cartTotal()
        NeonCard(Modifier.fillMaxWidth()) {
            Row2("Məhsul sayı", "${store.cartCount()}")
            Row2("Bonus xal", "+${store.pointsFor(total)} ⭐")
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.border)
            Row2("Cəmi", money(total), bold = true)
        }
        Spacer(Modifier.height(16.dp))
        BigButton(if (target == null) "Ödənişi təsdiqlə" else "Masaya əlavə et") { store.checkout(target); done = true }
    }
}

@Composable
private fun Modifier.horizontalScrollSafe(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))

// ============================================================== profile
@Composable
fun ProfileSheet(store: Store, onDismiss: () -> Unit, onAdmin: () -> Unit) {
    val p = LocalPalette.current
    Sheet(onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.accent2, p.accent))).padding(3.dp)
                    .clip(CircleShape).background(p.cardAlt),
                contentAlignment = Alignment.Center,
            ) { Text("🎮", fontSize = 44.sp) }
            Spacer(Modifier.height(10.dp))
            Text(store.clubName, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(store.address, color = p.dim)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoBox("Bonus xal", "${store.points} ⭐", Modifier.weight(1f))
            InfoBox("Aktiv masa", "${store.sessions.size}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        NeonCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🌙  Dark Mode", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Neon gaming görünüşü", color = p.dim, fontSize = 13.sp)
                }
                Switch(store.darkMode, { store.setDark(it) })
            }
        }
        Spacer(Modifier.height(10.dp))
        NeonCard(Modifier.fillMaxWidth().bounceClick(onClick = onAdmin)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = p.accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Admin panel", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Qiymətlər, masalar, xidmətlər, hesabat", color = p.dim, fontSize = 13.sp)
                }
                Text("›", color = p.dim, fontSize = 26.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "📴 Offline rejim — internet tələb olunmur.\nBütün məlumatlar telefonun yaddaşında saxlanılır.",
            color = p.dim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun PinDialog(store: Store, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text("Admin girişi") },
        text = {
            Column {
                Field("PIN kod", pin, { pin = it.filter(Char::isDigit).take(8); error = false }, keyboard = KeyboardType.NumberPassword, password = true)
                if (error) Text("PIN yanlışdır", color = LocalPalette.current.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                if (store.pin == "1234") Text("Standart PIN: 1234 (Ayarlardan dəyişin)", fontSize = 12.sp, color = LocalPalette.current.dim, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton({ if (pin == store.pin) onSuccess() else error = true }) { Text("Daxil ol") } },
        dismissButton = { TextButton(onDismiss) { Text("Ləğv et") } },
    )
}
