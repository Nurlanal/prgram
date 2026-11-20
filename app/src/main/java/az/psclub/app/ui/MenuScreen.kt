package az.psclub.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.psclub.app.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Cat(val title: String) {
    SEARCH("Axtarış"), FAV("Seçilmişlər"), PS5("PS5"), PS4("PS4"), PS3("PS3"),
    SERVICES("Xidmətlər"), REPAIR("Telefon təmiri"),
}

@Composable
fun MenuScreen(
    store: Store,
    startCat: Cat = Cat.PS5,
    onTable: (GameTable) -> Unit,
    onRepair: (RepairService) -> Unit,
    onProfile: () -> Unit,
) {
    var cat by rememberSaveable { mutableStateOf(startCat) }
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header(store, onProfile)
        CategoryRow(cat) { cat = it }
        AnimatedContent(
            targetState = cat,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(320)) { it / 5 * dir } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 5 * dir } + fadeOut(tween(220)))
            },
            label = "cat",
        ) { c ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (c) {
                    Cat.PS5 -> consoleSection(store, Console.PS5, onTable)
                    Cat.PS4 -> consoleSection(store, Console.PS4, onTable)
                    Cat.PS3 -> consoleSection(store, Console.PS3, onTable)
                    Cat.SERVICES -> servicesSection(store)
                    Cat.REPAIR -> repairSection(store, onRepair)
                    Cat.FAV -> favSection(store, onTable, onRepair)
                    Cat.SEARCH -> searchSection(store, query, { query = it }, onTable, onRepair)
                }
            }
        }
    }
}

@Composable
private fun Header(store: Store, onProfile: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).bounceClick(onClick = onProfile)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${store.clubName}® × ARENA", color = p.text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Default.KeyboardArrowRight, null, tint = p.text)
            }
            Text(store.address, color = p.dim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.bounceClick(onClick = onProfile), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.size(54.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(p.accent2, p.accent)))
                    .padding(2.5.dp).clip(CircleShape).background(p.cardAlt),
                contentAlignment = Alignment.Center,
            ) { Text("🎮", fontSize = 26.sp) }
            Row(
                Modifier.padding(top = 44.dp).clip(RoundedCornerShape(50)).background(p.bg)
                    .border(1.dp, p.border, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(String.format(Locale("az"), "%,d", store.points).replace(',', ' '), color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(" ⭐", fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CategoryRow(cat: Cat, onSelect: (Cat) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconChip(cat == Cat.SEARCH, { onSelect(Cat.SEARCH) }) {
            Icon(Icons.Default.Search, null, tint = if (cat == Cat.SEARCH) Color.White else p.text)
        }
        IconChip(cat == Cat.FAV, { onSelect(Cat.FAV) }) {
            Icon(Icons.Default.Favorite, null, tint = if (cat == Cat.FAV) Color.White else Color(0xFFFF2D4B))
        }
        listOf(Cat.PS5, Cat.PS4, Cat.PS3, Cat.SERVICES, Cat.REPAIR).forEach { c ->
            Chip(c.title, cat == c) { onSelect(c) }
        }
    }
}

@Composable
private fun IconChip(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.bounceClick(onClick = onClick).clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(p.glow) else Modifier.background(p.pill))
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun LazyGridScope.full(key: Any? = null, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

// ------------------------------------------------------------------ sections

private fun LazyGridScope.consoleSection(store: Store, console: Console, onTable: (GameTable) -> Unit) {
    val list = store.tables.filter { it.console == console }
    full("title") { SectionTitle(console.label) }
    full("hero") {
        Column {
            ConsolePoster(console, Modifier.fillMaxWidth().height(250.dp), big = true, interactive = true)
            Spacer(Modifier.height(12.dp))
            ConsoleInfo(store, console, list)
        }
    }
    full("tables") { SubTitle("Masalar") }
    items(list, key = { it.id }) { t -> TableCard(store, t) { onTable(t) } }
    if (list.isEmpty()) full("empty") { Empty("Bu konsol üçün masa yoxdur.\nAdmin paneldən əlavə edin.") }
}

@Composable
private fun ConsoleInfo(store: Store, console: Console, list: List<GameTable>) {
    val p = LocalPalette.current
    val free = list.count { store.sessionFor(it.id) == null }
    val from = list.minOfOrNull { it.pricePerHour }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoBox("Saatlıq", if (from != null) "${money(from)}-dan" else "—", Modifier.weight(1f))
        InfoBox("Boş masa", "$free / ${list.size}", Modifier.weight(1f), if (free > 0) p.success else p.danger)
    }
    val games = store.gamesFor(console)
    if (games.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Oyunlar", color = p.dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            games.forEach { g ->
                Text(
                    g, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(glowColor(console).copy(alpha = if (p.dark) 0.16f else 0.12f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
fun InfoBox(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(p.card).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = p.dim, fontSize = 12.sp)
        Text(value, color = valueColor ?: p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(text, color = LocalPalette.current.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Empty(text: String, emoji: String = "🎮") {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 52.sp)
        Spacer(Modifier.height(10.dp))
        Text(text, color = p.dim, textAlign = TextAlign.Center, fontSize = 15.sp)
    }
}

private fun LazyGridScope.servicesSection(store: Store) {
    full("title") { SectionTitle("Əlavə xidmətlər", sub = "Masaya əlavə edin və ya ayrıca ödəyin") }
    store.services.groupBy { it.category }.forEach { (cat, items) ->
        full("cat-$cat") { SubTitle(cat) }
        items(items, key = { it.id }) { s -> ServiceCard(store, s) }
    }
    if (store.services.isEmpty()) full("empty") { Empty("Xidmət yoxdur") }
}

private fun LazyGridScope.repairSection(store: Store, onRepair: (RepairService) -> Unit) {
    full("title") { SectionTitle("Telefon təmiri") }
    full("hero") { RepairHero() }
    items(store.repairs, key = { it.id }) { r -> RepairCard(store, r) { onRepair(r) } }
    if (store.orders.isNotEmpty()) {
        full("orders") { SubTitle("Sifarişlər") }
        items(store.orders.take(20), key = { "o${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { o -> OrderRow(o) }
    }
}

private fun LazyGridScope.favSection(store: Store, onTable: (GameTable) -> Unit, onRepair: (RepairService) -> Unit) {
    full("title") { SectionTitle("Seçilmişlər") }
    val t = store.tables.filter { store.isFav("t:${it.id}") }
    val s = store.services.filter { store.isFav("s:${it.id}") }
    val r = store.repairs.filter { store.isFav("r:${it.id}") }
    items(t, key = { "t${it.id}" }) { TableCard(store, it) { onTable(it) } }
    items(s, key = { "s${it.id}" }) { ServiceCard(store, it) }
    items(r, key = { "r${it.id}" }) { RepairCard(store, it) { onRepair(it) } }
    if (t.isEmpty() && s.isEmpty() && r.isEmpty()) full("empty") { Empty("Hələ seçilmiş yoxdur.\n♥ işarəsinə toxunaraq əlavə edin.", "💜") }
}

private fun LazyGridScope.searchSection(
    store: Store, query: String, onQuery: (String) -> Unit,
    onTable: (GameTable) -> Unit, onRepair: (RepairService) -> Unit,
) {
    full("field") {
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            placeholder = { Text("Masa, xidmət və ya təmir axtar…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth(),
        )
    }
    val q = query.trim().lowercase()
    if (q.isEmpty()) { full("hint") { Empty("Axtarmağa başlayın", "🔍") }; return }
    val t = store.tables.filter { it.name.lowercase().contains(q) || it.console.short.lowercase().contains(q) }
    val s = store.services.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
    val r = store.repairs.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
    items(t, key = { "t${it.id}" }) { TableCard(store, it) { onTable(it) } }
    items(s, key = { "s${it.id}" }) { ServiceCard(store, it) }
    items(r, key = { "r${it.id}" }) { RepairCard(store, it) { onRepair(it) } }
    if (t.isEmpty() && s.isEmpty() && r.isEmpty()) full("none") { Empty("Heç nə tapılmadı", "🤷") }
}

// --------------------------------------------------------------------- cards

@Composable
fun TableCard(store: Store, t: GameTable, onClick: () -> Unit) {
    val p = LocalPalette.current
    val s = store.sessionFor(t.id)
    val now = LocalNow.current.value
    Column(Modifier.fillMaxWidth().bounceClick(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.92f)) {
            ConsolePoster(t.console, Modifier.fillMaxSize(), animated = s != null)
            Row(Modifier.align(Alignment.TopStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (s == null) Badge("Boş", p.success) else Badge(clock(now - s.startedAt), p.danger)
            }
            FavButton(store.isFav("t:${t.id}"), Modifier.align(Alignment.TopEnd).padding(8.dp)) { store.toggleFav("t:${t.id}") }
            if (t.vip) Badge("👑 VIP", Color(0xFFB8860B), Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(t.name, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
        Text("${t.console.short} · ${t.seats} oyunçu", color = p.dim, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        if (s == null) Pill("${money(t.pricePerHour)}/saat", leadingPlus = true, onClick = onClick)
        else Pill("⏱ ${money(store.totalCost(s, now))}", filled = true, onClick = onClick)
    }
}

@Composable
fun ServiceCard(store: Store, s: ServiceItem) {
    val p = LocalPalette.current
    val qty = store.cart[s.id] ?: 0
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            EmojiTile(s.emoji, s.id.toInt(), Modifier.fillMaxSize())
            FavButton(store.isFav("s:${s.id}"), Modifier.align(Alignment.TopEnd).padding(8.dp)) { store.toggleFav("s:${s.id}") }
        }
        Spacer(Modifier.height(10.dp))
        Text(s.name, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
        Text(s.description, color = p.dim, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        AnimatedContent(qty > 0, label = "qty") { inCart ->
            if (!inCart) Pill(money(s.price), leadingPlus = true) { store.addToCart(s.id) }
            else Stepper(qty, { store.removeFromCart(s.id) }, { store.addToCart(s.id) })
        }
    }
}

@Composable
fun RepairCard(store: Store, r: RepairService, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().bounceClick(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            EmojiTile(r.emoji, r.id.toInt() + 3, Modifier.fillMaxSize())
            FavButton(store.isFav("r:${r.id}"), Modifier.align(Alignment.TopEnd).padding(8.dp)) { store.toggleFav("r:${r.id}") }
            Badge("⏱ ${r.duration}", Color.Black.copy(alpha = 0.55f), Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(r.name, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
        Text(r.description, color = p.dim, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Pill("${money(r.price)}-dan", leadingPlus = true, onClick = onClick)
    }
}

@Composable
private fun RepairHero() {
    val inf = rememberInfiniteTransition(label = "hero")
    val rot by inf.animateFloat(-8f, 8f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "r")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0E3B2E), Color(0xFF0F766E), Color(0xFF052E2B))))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Peşəkar təmir", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text("Bütün markalar · 3 ay zəmanət\nPulsuz diaqnostika", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        }
        Text("📱", fontSize = 64.sp, modifier = Modifier.graphicsLayerRotate(rot))
    }
}

private fun Modifier.graphicsLayerRotate(deg: Float) = this.graphicsLayer { rotationY = deg * 2; rotationZ = deg }

private val dateFmt = SimpleDateFormat("dd.MM HH:mm", Locale.US)

@Composable
fun OrderRow(o: RepairOrder) {
    val p = LocalPalette.current
    val (label, color) = statusOf(o.status, p)
    NeonCard(Modifier.fillMaxWidth(), radius = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("#${o.number} · ${o.serviceName}", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${o.device} · ${o.customer}", color = p.dim, fontSize = 13.sp)
                Text(dateFmt.format(Date(o.createdAt)), color = p.dim, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Badge(label, color)
                Spacer(Modifier.height(6.dp))
                Text(money(o.price), color = p.text, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun statusOf(s: Int, p: Palette): Pair<String, Color> = when (s) {
    0 -> "Yeni" to p.accent2
    1 -> "Təmirdə" to p.warn
    else -> "Hazırdır" to p.success
}
