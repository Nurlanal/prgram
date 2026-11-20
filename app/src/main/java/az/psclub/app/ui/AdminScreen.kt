package az.psclub.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.psclub.app.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AdminTab(val title: String) {
    DAY("🧾 Gün sonu"), DEBTS("📒 Borclar"), TABLES("🎮 Masalar"), SERVICES("🥤 Xidmətlər"), REPAIRS("🔧 Təmir"),
    ORDERS("📋 Sifarişlər"), REPORT("📊 Hesabat"), SETTINGS("⚙️ Ayarlar"),
}

@Composable
fun AdminScreen(store: Store, onBack: () -> Unit) {
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableStateOf(AdminTab.DAY) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(p.pill).bounceClick(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowBack, null, tint = p.text)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Admin panel", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Dəyişikliklər dərhal yadda saxlanılır", color = p.dim, fontSize = 13.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { AdminTab.entries.forEach { t -> Chip(t.title, tab == t) { tab = t } } }

        AnimatedContent(tab, label = "admin", transitionSpec = { fadeIn() togetherWith fadeOut() }) { t ->
            LazyColumn(
                Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (t) {
                    AdminTab.DAY -> dayTab(store)
                    AdminTab.DEBTS -> debtsTab(store)
                    AdminTab.TABLES -> tablesTab(store)
                    AdminTab.SERVICES -> servicesTab(store)
                    AdminTab.REPAIRS -> repairsTab(store)
                    AdminTab.ORDERS -> ordersTab(store)
                    AdminTab.REPORT -> reportTab(store)
                    AdminTab.SETTINGS -> settingsTab(store)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- helpers
@Composable
private fun Expandable(
    title: String, subtitle: String, leading: String, initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    NeonCard(Modifier.fillMaxWidth().animateContentSize(), radius = 22.dp) {
        Row(Modifier.fillMaxWidth().bounceClick { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(leading, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = p.dim, fontSize = 13.sp)
            }
            Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = p.dim)
        }
        if (open) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SaveDelete(onSave: () -> Unit, onDelete: (() -> Unit)?, saveEnabled: Boolean = true) {
    val p = LocalPalette.current
    var ask by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        BigButton(if (saved) "✓ Yadda saxlanıldı" else "Yadda saxla", Modifier.weight(1f), enabled = saveEnabled) { onSave(); saved = true }
        if (onDelete != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(22.dp)).background(p.danger.copy(alpha = 0.15f)).bounceClick { ask = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Delete, null, tint = p.danger) }
        }
    }
    LaunchedEffect(saved) { if (saved) { kotlinx.coroutines.delay(1500); saved = false } }
    if (ask && onDelete != null) ConfirmDialog("Silinsin?", "Bu əməliyyat geri qaytarılmır.", { ask = false }) { ask = false; onDelete() }
}

@Composable
private fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text("Bəli") } },
        dismissButton = { TextButton(onDismiss) { Text("Xeyr") } },
    )
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.pill).bounceClick(onClick = onClick).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text("＋  $text", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalPalette.current.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

// ------------------------------------------------------------------ tables
private fun LazyListScope.tablesTab(store: Store) {
    item { Label("Konsol üzrə saatlıq qiymət") }
    Console.entries.forEach { c -> item(key = "rate-${c.name}") { ConsoleRateRow(store, c) } }
    item { Label("Masalar (${store.tables.size})") }
    items(store.tables, key = { it.id }) { t -> TableEditor(store, t) }
    item { AddButton("Yeni masa") { store.newTable() } }
}

@Composable
private fun ConsoleRateRow(store: Store, c: Console) {
    val p = LocalPalette.current
    val current = store.tables.filter { it.console == c }.minOfOrNull { it.pricePerHour } ?: 0.0
    var text by remember(current) { mutableStateOf(plain(current)) }
    var done by remember { mutableStateOf(false) }
    NeonCard(Modifier.fillMaxWidth(), radius = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConsolePoster(c, Modifier.size(64.dp), animated = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.label, color = p.text, fontWeight = FontWeight.Bold)
                Field("₼ / saat", text, { text = it; done = false }, keyboard = KeyboardType.Decimal)
            }
            Spacer(Modifier.width(10.dp))
            Pill(if (done) "✓" else "Tətbiq et", filled = true) {
                parsePrice(text)?.let { store.setConsoleRate(c, it); done = true }
            }
        }
        Text("Bu konsolun bütün masalarına tətbiq olunur", color = p.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun TableEditor(store: Store, t: GameTable) {
    var name by remember(t) { mutableStateOf(t.name) }
    var price by remember(t) { mutableStateOf(plain(t.pricePerHour)) }
    var seats by remember(t) { mutableStateOf(t.seats.toString()) }
    var console by remember(t) { mutableStateOf(t.console) }
    var vip by remember(t) { mutableStateOf(t.vip) }
    val busy = store.sessionFor(t.id) != null
    Expandable(t.name, "${t.console.short} · ${money(t.pricePerHour)}/saat${if (t.vip) " · VIP" else ""}${if (busy) " · məşğul" else ""}", "🎮") {
        Field("Masanın adı", name, { name = it })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Console.entries.forEach { c -> Chip(c.short, console == c, Modifier.weight(1f)) { console = c } }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("₼ / saat", price, { price = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            Field("Oyunçu", seats, { seats = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("👑 VIP masa", color = LocalPalette.current.text, modifier = Modifier.weight(1f))
            Switch(vip, { vip = it })
        }
        SaveDelete(
            onSave = {
                store.upsertTable(t.copy(name = name.trim().ifBlank { t.name }, console = console,
                    pricePerHour = parsePrice(price) ?: t.pricePerHour, vip = vip, seats = seats.toIntOrNull()?.coerceAtLeast(1) ?: t.seats))
            },
            onDelete = { store.deleteTable(t) },
            saveEnabled = parsePrice(price) != null,
        )
    }
}

// ---------------------------------------------------------------- services
private fun LazyListScope.servicesTab(store: Store) {
    item { AddButton("Yeni xidmət") { store.newService() } }
    items(store.services, key = { it.id }) { s -> ServiceEditor(store, s) }
}

@Composable
private fun ServiceEditor(store: Store, s: ServiceItem) {
    var name by remember(s) { mutableStateOf(s.name) }
    var desc by remember(s) { mutableStateOf(s.description) }
    var price by remember(s) { mutableStateOf(plain(s.price)) }
    var emoji by remember(s) { mutableStateOf(s.emoji) }
    var cat by remember(s) { mutableStateOf(s.category) }
    Expandable(s.name, "${s.category} · ${money(s.price)}", s.emoji) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Emoji", emoji, { emoji = it.take(4) }, Modifier.width(90.dp))
            Field("Ad", name, { name = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Field("Təsvir", desc, { desc = it })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Qiymət ₼", price, { price = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            Field("Kateqoriya", cat, { cat = it }, Modifier.weight(1.4f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            store.services.map { it.category }.distinct().forEach { c -> Chip(c, cat == c) { cat = c } }
        }
        SaveDelete(
            onSave = {
                store.upsertService(s.copy(name = name.trim().ifBlank { s.name }, description = desc.trim(),
                    price = parsePrice(price) ?: s.price, emoji = emoji.ifBlank { "⭐" }, category = cat.trim().ifBlank { "Xidmət" }))
            },
            onDelete = { store.deleteService(s) },
            saveEnabled = parsePrice(price) != null,
        )
    }
}

// ----------------------------------------------------------------- repairs
private fun LazyListScope.repairsTab(store: Store) {
    item { AddButton("Yeni təmir xidməti") { store.newRepair() } }
    items(store.repairs, key = { it.id }) { r -> RepairEditor(store, r) }
}

@Composable
private fun RepairEditor(store: Store, r: RepairService) {
    var name by remember(r) { mutableStateOf(r.name) }
    var desc by remember(r) { mutableStateOf(r.description) }
    var price by remember(r) { mutableStateOf(plain(r.price)) }
    var dur by remember(r) { mutableStateOf(r.duration) }
    var emoji by remember(r) { mutableStateOf(r.emoji) }
    Expandable(r.name, "${money(r.price)}-dan · ${r.duration}", r.emoji) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Emoji", emoji, { emoji = it.take(4) }, Modifier.width(90.dp))
            Field("Xidmət növü", name, { name = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Field("Təsvir", desc, { desc = it })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Qiymət ₼", price, { price = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            Field("Müddət", dur, { dur = it }, Modifier.weight(1f))
        }
        SaveDelete(
            onSave = {
                store.upsertRepair(r.copy(name = name.trim().ifBlank { r.name }, description = desc.trim(),
                    price = parsePrice(price) ?: r.price, duration = dur.trim(), emoji = emoji.ifBlank { "🔧" }))
            },
            onDelete = { store.deleteRepair(r) },
            saveEnabled = parsePrice(price) != null,
        )
    }
}

// ------------------------------------------------------------------ orders
private fun LazyListScope.ordersTab(store: Store) {
    if (store.orders.isEmpty()) item {
        NeonCard(Modifier.fillMaxWidth()) { Text("Hələ təmir sifarişi yoxdur", color = LocalPalette.current.dim) }
    }
    items(store.orders, key = { it.id }) { o -> OrderAdmin(store, o) }
}

@Composable
private fun OrderAdmin(store: Store, o: RepairOrder) {
    val p = LocalPalette.current
    var ask by remember { mutableStateOf(false) }
    Column {
        OrderRow(o)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            (0..2).forEach { s -> Chip(statusOf(s, p).first, o.status == s) { store.setOrderStatus(o, s) } }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(CircleShape).background(p.danger.copy(alpha = 0.15f)).bounceClick { ask = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Delete, null, tint = p.danger)
            }
        }
        Text("📞 ${o.phone}${if (o.note.isNotBlank()) " · ${o.note}" else ""}", color = p.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
    if (ask) ConfirmDialog("Sifariş silinsin?", "#${o.number}", { ask = false }) { ask = false; store.deleteOrder(o) }
}

// ------------------------------------------------------------------ report
private fun LazyListScope.reportTab(store: Store) {
    item {
        val p = LocalPalette.current
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoBox("Bu gün", money(store.revenueSince(store.startOfToday())), Modifier.weight(1f), p.success)
                InfoBox("Bu ay", money(store.revenueSince(store.startOfMonth())), Modifier.weight(1f), p.accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoBox("Ümumi", money(store.totalRevenue()), Modifier.weight(1f))
                InfoBox("Əməliyyat", "${store.history.size}", Modifier.weight(1f))
            }
            val byKind = store.history.groupBy { it.kind }.mapValues { (_, v) -> v.sumOf { it.amount } }
            NeonCard(Modifier.fillMaxWidth()) {
                Text("Gəlir mənbələri", color = p.text, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val total = byKind.values.sum().coerceAtLeast(0.01)
                listOf("table" to "🎮 Oyun masaları", "service" to "🥤 Xidmətlər", "repair" to "🔧 Təmir").forEach { (k, l) ->
                    val v = byKind[k] ?: 0.0
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(l, color = p.text, modifier = Modifier.weight(1f)); Text(money(Math.round(v * 100) / 100.0), color = p.text, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { (v / total).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        color = p.accent, trackColor = p.cardAlt,
                    )
                }
            }
            Label("Tarixçə")
        }
    }
    items(store.history.take(200), key = { it.id }) { HistoryRow(it) }
    if (store.history.isNotEmpty()) item {
        var ask by remember { mutableStateOf(false) }
        BigButton("Tarixçəni təmizlə", danger = true) { ask = true }
        if (ask) ConfirmDialog("Tarixçə silinsin?", "Bütün hesabat məlumatları silinəcək.", { ask = false }) { ask = false; store.clearHistory() }
    }
}

// ---------------------------------------------------------------- settings
private fun LazyListScope.settingsTab(store: Store) {
    item {
        val p = LocalPalette.current
        var name by remember { mutableStateOf(store.clubName) }
        var addr by remember { mutableStateOf(store.address) }
        NeonCard(Modifier.fillMaxWidth()) {
            Text("Klub məlumatları", color = p.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field("Klubun adı", name, { name = it })
            Spacer(Modifier.height(8.dp))
            Field("Ünvan", addr, { addr = it })
            SaveDelete(onSave = { store.updateInfo(name, addr) }, onDelete = null)
        }
    }
    item {
        val p = LocalPalette.current
        var bonus by remember { mutableStateOf(store.bonusPerManat.toString()) }
        NeonCard(Modifier.fillMaxWidth()) {
            Text("Bonus sistemi", color = p.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field("Hər 1 ₼ üçün bonus xal", bonus, { bonus = it.filter(Char::isDigit).take(4) }, keyboard = KeyboardType.Number)
            SaveDelete(onSave = { store.updateBonus(bonus.toIntOrNull() ?: 0) }, onDelete = null)
        }
    }
    item {
        val p = LocalPalette.current
        var g5 by remember { mutableStateOf(store.games[Console.PS5] ?: "") }
        var g4 by remember { mutableStateOf(store.games[Console.PS4] ?: "") }
        var g3 by remember { mutableStateOf(store.games[Console.PS3] ?: "") }
        NeonCard(Modifier.fillMaxWidth()) {
            Text("Oyun siyahıları (vergüllə ayırın)", color = p.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field("PS5 oyunları", g5, { g5 = it }, singleLine = false)
            Spacer(Modifier.height(8.dp))
            Field("PS4 oyunları", g4, { g4 = it }, singleLine = false)
            Spacer(Modifier.height(8.dp))
            Field("PS3 oyunları", g3, { g3 = it }, singleLine = false)
            SaveDelete(onSave = {
                store.setGames(Console.PS5, g5); store.setGames(Console.PS4, g4); store.setGames(Console.PS3, g3)
            }, onDelete = null)
        }
    }
    item {
        val p = LocalPalette.current
        var old by remember { mutableStateOf("") }
        var new1 by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf<Pair<String, Color>?>(null) }
        NeonCard(Modifier.fillMaxWidth()) {
            Text("Admin PIN kodu", color = p.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field("Köhnə PIN", old, { old = it.filter(Char::isDigit).take(8) }, keyboard = KeyboardType.NumberPassword, password = true)
            Spacer(Modifier.height(8.dp))
            Field("Yeni PIN (min. 4 rəqəm)", new1, { new1 = it.filter(Char::isDigit).take(8) }, keyboard = KeyboardType.NumberPassword, password = true)
            msg?.let { Text(it.first, color = it.second, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(12.dp))
            BigButton("PIN-i dəyiş", enabled = new1.length >= 4) {
                msg = if (old != store.pin) "Köhnə PIN yanlışdır" to p.danger
                else { store.updatePin(new1); old = ""; new1 = ""; "PIN dəyişdirildi ✓" to p.success }
            }
        }
    }
    item {
        val p = LocalPalette.current
        NeonCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌙  Dark Mode", color = p.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(store.darkMode, { store.setDark(it) })
            }
        }
    }
    item {
        var ask by remember { mutableStateOf(false) }
        BigButton("Bütün məlumatları sıfırla", danger = true) { ask = true }
        if (ask) ConfirmDialog(
            "Sıfırlansın?", "Masalar, qiymətlər, sifarişlər və hesabat standart vəziyyətə qayıdacaq.",
            { ask = false },
        ) { ask = false; store.resetAll() }
    }
}

// ----------------------------------------------------------- end of day
private val dayFmt = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("az"))
private const val DAY_MS = 24L * 3600_000L

private fun LazyListScope.dayTab(store: Store) {
    item(key = "day") { DayReportCard(store) }
}

@Composable
private fun DayReportCard(store: Store) {
    val p = LocalPalette.current
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val today = store.startOfToday()
    // calendar-safe: step back by days, then snap to that day's midnight
    val dayStart = java.util.Calendar.getInstance().apply {
        timeInMillis = today; add(java.util.Calendar.DAY_OF_YEAR, -offset)
    }.timeInMillis
    val r = store.dayReport(dayStart)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("‹") { offset += 1 }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (offset == 0) "Bu gün" else if (offset == 1) "Dünən" else "$offset gün əvvəl", color = p.text, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(dayFmt.format(Date(dayStart)), color = p.dim, fontSize = 13.sp)
            }
            Pill("›") { if (offset > 0) offset -= 1 }
        }
        // big total
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.glow).padding(20.dp),
        ) {
            Text("Ümumi satış", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            Text(money(r.sales.r2()), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
            Text("${r.entries.count { it.method != "debtpay" }} əməliyyat", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoBox("💵 Nağd", money(r.cash), Modifier.weight(1f), p.success)
            InfoBox("📒 Nisyə", money(r.credit), Modifier.weight(1f), p.warn)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoBox("Borc ödənişi", money(r.debtPaid), Modifier.weight(1f), p.accent2)
            InfoBox("Kassada nağd", money(r.cashInRegister.r2()), Modifier.weight(1f), p.success)
        }
        NeonCard(Modifier.fillMaxWidth()) {
            Text("Hesablama", color = p.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Line("Nağd satış", money(r.cash))
            Line("+ Nisyə satış", money(r.credit))
            Line("= Ümumi satış", money(r.sales.r2()), bold = true)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.border)
            Line("Nağd satış", money(r.cash))
            Line("+ Alınan borc ödənişləri", money(r.debtPaid))
            Line("= Kassada olmalı", money(r.cashInRegister.r2()), bold = true)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.border)
            Line("🎮 Oyun masaları", money(r.byKind["table"] ?: 0.0))
            Line("🥤 Xidmətlər", money(r.byKind["service"] ?: 0.0))
            Line("🔧 Təmir", money(r.byKind["repair"] ?: 0.0))
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.border)
            Line("Açıq qalan ümumi borc", money(store.totalDebt()), color = p.danger)
        }
        Label(if (r.entries.isEmpty()) "Bu gün əməliyyat yoxdur" else "Əməliyyatlar (${r.entries.size})")
        r.entries.forEach { HistoryRow(it) }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false, color: Color? = null) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = if (bold) p.text else p.dim, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, color = color ?: p.text, fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold)
    }
}

private fun Double.r2() = Math.round(this * 100) / 100.0

// ------------------------------------------------------------- debts
private fun LazyListScope.debtsTab(store: Store) {
    item {
        val p = LocalPalette.current
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoBox("Ümumi borc", money(store.totalDebt()), Modifier.weight(1f), p.danger)
            InfoBox("Borclu", "${store.debtors.count { store.debtOf(it.id) > 0 }} nəfər", Modifier.weight(1f))
        }
    }
    item {
        val p = LocalPalette.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            Text("Ad", color = p.dim, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Borcu", color = p.dim, fontWeight = FontWeight.SemiBold)
        }
    }
    val rows = store.debtors.map { it to store.debtOf(it.id) }.filter { it.second > 0 }.sortedByDescending { it.second }
    items(rows, key = { it.first.id }) { (d, v) ->
        val p = LocalPalette.current
        NeonCard(Modifier.fillMaxWidth(), radius = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, color = p.text, fontWeight = FontWeight.Bold)
                    if (d.phone.isNotBlank()) Text(d.phone, color = p.dim, fontSize = 12.sp)
                }
                Text(money(v), color = p.danger, fontWeight = FontWeight.Black)
            }
        }
    }
    item {
        val p = LocalPalette.current
        Row(Modifier.fillMaxWidth().padding(6.dp)) {
            Text("Ümumi", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text(money(store.totalDebt()), color = p.danger, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Text("Borc yazmaq / ödəniş qəbul etmək üçün aşağı paneldə “Borc” bölməsini açın.", color = p.dim, fontSize = 12.sp)
    }
}
