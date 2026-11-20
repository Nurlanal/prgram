package az.psclub.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Offline data store. Everything is persisted as JSON in SharedPreferences,
 * so the app never needs network access.
 */
class Store private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("psclub_store", Context.MODE_PRIVATE)
    private val ids = AtomicLong(System.currentTimeMillis())
    private fun newId(): Long = ids.incrementAndGet()

    val tables = mutableStateListOf<GameTable>()
    val sessions = mutableStateListOf<Session>()
    val services = mutableStateListOf<ServiceItem>()
    val repairs = mutableStateListOf<RepairService>()
    val orders = mutableStateListOf<RepairOrder>()
    val history = mutableStateListOf<HistoryEntry>()
    val favorites = mutableStateListOf<String>()
    val cart = mutableStateMapOf<Long, Int>()
    val games = mutableStateMapOf<Console, String>()

    var darkMode by mutableStateOf(true); private set
    var clubName by mutableStateOf("PS CLUB"); private set
    var address by mutableStateOf("Bakı, Nizami küç. 12"); private set
    var pin by mutableStateOf("1234"); private set
    var points by mutableIntStateOf(0); private set
    var bonusPerManat by mutableIntStateOf(10); private set
    private var orderCounter = 100

    init { load() }

    // ---------------------------------------------------------------- tables
    fun sessionFor(tableId: Long): Session? = sessions.firstOrNull { it.tableId == tableId }
    fun table(id: Long): GameTable? = tables.firstOrNull { it.id == id }

    fun openTable(t: GameTable, minutes: Int, customer: String) {
        if (sessionFor(t.id) != null) return
        sessions.add(Session(newId(), t.id, System.currentTimeMillis(), minutes, customer.trim(), t.pricePerHour))
        saveSessions()
    }

    fun extend(s: Session, minutes: Int) {
        val i = sessions.indexOfFirst { it.id == s.id }
        if (i < 0) return
        val cur = sessions[i]
        val base = if (cur.plannedMinutes == 0) billedMinutes(cur, System.currentTimeMillis()) else cur.plannedMinutes
        sessions[i] = cur.copy(plannedMinutes = base + minutes)
        saveSessions()
    }

    /** Billing = only the minutes actually played (whole minutes, at least 1). */
    fun billedMinutes(s: Session, now: Long): Int =
        ((now - s.startedAt) / 60_000L).toInt().coerceAtLeast(1)

    fun timeCost(s: Session, now: Long): Double = round2(billedMinutes(s, now) * s.rate / 60.0)
    fun totalCost(s: Session, now: Long): Double = round2(timeCost(s, now) + s.extrasTotal)

    fun closeSession(s: Session, now: Long = System.currentTimeMillis()): HistoryEntry {
        val t = table(s.tableId)
        val total = totalCost(s, now)
        val mins = billedMinutes(s, now)
        val detail = buildString {
            append(t?.console?.short ?: "")
            append(" · ").append(durationLabel(mins)).append(" oynanıldı")
            if (s.extrasTotal > 0) append(" · əlavələr ").append(money(s.extrasTotal))
            if (s.customer.isNotBlank()) append(" · ").append(s.customer)
        }
        val e = HistoryEntry(newId(), "table", t?.name ?: "Masa", detail, total, now)
        history.add(0, e)
        points += (total * bonusPerManat).roundToInt()
        sessions.removeAll { it.id == s.id }
        saveSessions(); saveHistory(); saveSettings()
        return e
    }

    // --------------------------------------------------------------- cart
    fun addToCart(id: Long) { cart[id] = (cart[id] ?: 0) + 1; saveCart() }
    fun removeFromCart(id: Long) {
        val q = (cart[id] ?: 0) - 1
        if (q <= 0) cart.remove(id) else cart[id] = q
        saveCart()
    }
    fun cartTotal(): Double = round2(cart.entries.sumOf { (id, q) -> (services.firstOrNull { it.id == id }?.price ?: 0.0) * q })
    fun cartCount(): Int = cart.values.sum()
    fun pointsFor(amount: Double): Int = (amount * bonusPerManat).roundToInt()

    /** @param sessionId attach to an open table, or null for a separate payment */
    fun checkout(sessionId: Long?) {
        val items = cart.entries.mapNotNull { (id, q) -> services.firstOrNull { it.id == id }?.let { it to q } }
        if (items.isEmpty()) return
        val total = round2(items.sumOf { it.first.price * it.second })
        val note = items.joinToString(", ") { "${it.first.name} ×${it.second}" }
        val i = sessions.indexOfFirst { it.id == sessionId }
        if (sessionId != null && i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(
                extrasTotal = round2(s.extrasTotal + total),
                extrasNote = listOf(s.extrasNote, note).filter { it.isNotBlank() }.joinToString(", "),
            )
            saveSessions()
        } else {
            history.add(0, HistoryEntry(newId(), "service", "Əlavə xidmətlər", note, total, System.currentTimeMillis()))
            points += pointsFor(total)
            saveHistory(); saveSettings()
        }
        cart.clear(); saveCart()
    }

    // ------------------------------------------------------------- repair
    fun placeOrder(r: RepairService, customer: String, phone: String, device: String, note: String): RepairOrder {
        orderCounter += 1
        val o = RepairOrder(newId(), orderCounter, customer.trim(), phone.trim(), device.trim(), r.name, r.price, note.trim(), System.currentTimeMillis(), 0)
        orders.add(0, o)
        saveOrders(); saveSettings()
        return o
    }

    fun setOrderStatus(o: RepairOrder, status: Int) {
        val i = orders.indexOfFirst { it.id == o.id }
        if (i < 0) return
        val old = orders[i]
        orders[i] = old.copy(status = status)
        if (status == 2 && old.status != 2) {
            history.add(0, HistoryEntry(newId(), "repair", "Təmir #${old.number}", "${old.serviceName} · ${old.device}", old.price, System.currentTimeMillis()))
            saveHistory()
        }
        saveOrders()
    }

    fun deleteOrder(o: RepairOrder) { orders.removeAll { it.id == o.id }; saveOrders() }

    // ---------------------------------------------------------- favorites
    fun isFav(key: String) = key in favorites
    fun toggleFav(key: String) {
        if (key in favorites) favorites.remove(key) else favorites.add(key)
        prefs.edit().putString("favorites", JSONArray(favorites.toList()).toString()).apply()
    }

    // -------------------------------------------------------------- admin
    fun upsertTable(t: GameTable) { upsert(tables, t, { it.id }); saveTables() }
    fun newTable(): GameTable {
        val t = GameTable(newId(), "Masa ${tables.size + 1}", Console.PS5, tables.firstOrNull { it.console == Console.PS5 }?.pricePerHour ?: 6.0)
        tables.add(t); saveTables(); return t
    }
    fun deleteTable(t: GameTable) {
        sessions.removeAll { it.tableId == t.id }
        tables.removeAll { it.id == t.id }
        saveTables(); saveSessions()
    }
    fun setConsoleRate(c: Console, price: Double) {
        for (i in tables.indices) if (tables[i].console == c) tables[i] = tables[i].copy(pricePerHour = price)
        saveTables()
    }

    fun upsertService(s: ServiceItem) { upsert(services, s, { it.id }); saveServices() }
    fun newService(): ServiceItem {
        val s = ServiceItem(newId(), "Yeni xidmət", "", 1.0, "⭐", "Xidmət")
        services.add(0, s); saveServices(); return s
    }
    fun deleteService(s: ServiceItem) { services.removeAll { it.id == s.id }; cart.remove(s.id); saveServices(); saveCart() }

    fun upsertRepair(r: RepairService) { upsert(repairs, r, { it.id }); saveRepairs() }
    fun newRepair(): RepairService {
        val r = RepairService(newId(), "Yeni təmir xidməti", "", 10.0, "1 saat", "🔧")
        repairs.add(0, r); saveRepairs(); return r
    }
    fun deleteRepair(r: RepairService) { repairs.removeAll { it.id == r.id }; saveRepairs() }

    fun setDark(v: Boolean) { darkMode = v; saveSettings() }
    fun updateInfo(name: String, addr: String) { clubName = name.trim().ifBlank { "PS CLUB" }; address = addr.trim(); saveSettings() }
    fun updatePin(p: String) { pin = p; saveSettings() }
    fun updateBonus(v: Int) { bonusPerManat = v.coerceIn(0, 1000); saveSettings() }
    fun setGames(c: Console, text: String) { games[c] = text; saveSettings() }
    fun gamesFor(c: Console): List<String> = (games[c] ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun clearHistory() { history.clear(); saveHistory() }

    fun resetAll() {
        prefs.edit().clear().apply()
        tables.clear(); sessions.clear(); services.clear(); repairs.clear(); orders.clear()
        history.clear(); favorites.clear(); cart.clear(); games.clear()
        darkMode = true; clubName = "PS CLUB"; address = "Bakı, Nizami küç. 12"; pin = "1234"
        points = 0; bonusPerManat = 10; orderCounter = 100
        load()
    }

    // ------------------------------------------------------------- stats
    fun revenueSince(from: Long) = round2(history.filter { it.time >= from }.sumOf { it.amount })
    fun totalRevenue() = round2(history.sumOf { it.amount })
    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ======================================================== persistence
    private fun <T> upsert(list: MutableList<T>, item: T, key: (T) -> Long) {
        val i = list.indexOfFirst { key(it) == key(item) }
        if (i >= 0) list[i] = item else list.add(item)
    }

    private fun <T> saveList(key: String, list: List<T>, f: (T) -> JSONObject) {
        val a = JSONArray(); list.forEach { a.put(f(it)) }
        prefs.edit().putString(key, a.toString()).apply()
    }

    private fun <T> loadList(key: String, f: (JSONObject) -> T): List<T>? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val a = JSONArray(raw); (0 until a.length()).map { f(a.getJSONObject(it)) }
        } catch (e: Exception) { null }
    }

    private fun saveTables() = saveList("tables", tables) {
        JSONObject().put("id", it.id).put("name", it.name).put("console", it.console.name)
            .put("price", it.pricePerHour).put("vip", it.vip).put("seats", it.seats)
    }
    private fun saveSessions() = saveList("sessions", sessions) {
        JSONObject().put("id", it.id).put("table", it.tableId).put("start", it.startedAt).put("planned", it.plannedMinutes)
            .put("customer", it.customer).put("rate", it.rate).put("extras", it.extrasTotal).put("extrasNote", it.extrasNote)
    }
    private fun saveServices() = saveList("services", services) {
        JSONObject().put("id", it.id).put("name", it.name).put("desc", it.description).put("price", it.price)
            .put("emoji", it.emoji).put("cat", it.category)
    }
    private fun saveRepairs() = saveList("repairs", repairs) {
        JSONObject().put("id", it.id).put("name", it.name).put("desc", it.description).put("price", it.price)
            .put("dur", it.duration).put("emoji", it.emoji)
    }
    private fun saveOrders() = saveList("orders", orders) {
        JSONObject().put("id", it.id).put("num", it.number).put("customer", it.customer).put("phone", it.phone)
            .put("device", it.device).put("service", it.serviceName).put("price", it.price).put("note", it.note)
            .put("at", it.createdAt).put("status", it.status)
    }
    private fun saveHistory() = saveList("history", history.take(500)) {
        JSONObject().put("id", it.id).put("kind", it.kind).put("title", it.title).put("detail", it.detail)
            .put("amount", it.amount).put("time", it.time)
    }
    private fun saveCart() {
        val o = JSONObject(); cart.forEach { (k, v) -> o.put(k.toString(), v) }
        prefs.edit().putString("cart", o.toString()).apply()
    }
    private fun saveSettings() {
        val g = JSONObject(); games.forEach { (k, v) -> g.put(k.name, v) }
        prefs.edit()
            .putBoolean("dark", darkMode).putString("club", clubName).putString("address", address)
            .putString("pin", pin).putInt("points", points).putInt("bonus", bonusPerManat)
            .putInt("orderCounter", orderCounter).putString("games", g.toString())
            .apply()
    }

    private fun load() {
        darkMode = prefs.getBoolean("dark", true)
        clubName = prefs.getString("club", "PS CLUB") ?: "PS CLUB"
        address = prefs.getString("address", address) ?: address
        pin = prefs.getString("pin", "1234") ?: "1234"
        points = prefs.getInt("points", 0)
        bonusPerManat = prefs.getInt("bonus", 10)
        orderCounter = prefs.getInt("orderCounter", 100)

        tables.addAll(loadList("tables") {
            GameTable(it.getLong("id"), it.getString("name"), Console.valueOf(it.getString("console")),
                it.getDouble("price"), it.optBoolean("vip"), it.optInt("seats", 2))
        } ?: Defaults.tables(::newId).also { tables.addAll(it); saveTables(); tables.clear() })
        sessions.addAll(loadList("sessions") {
            Session(it.getLong("id"), it.getLong("table"), it.getLong("start"), it.getInt("planned"),
                it.optString("customer"), it.getDouble("rate"), it.optDouble("extras", 0.0), it.optString("extrasNote"))
        } ?: emptyList())
        services.addAll(loadList("services") {
            ServiceItem(it.getLong("id"), it.getString("name"), it.optString("desc"), it.getDouble("price"),
                it.optString("emoji", "⭐"), it.optString("cat", "Xidmət"))
        } ?: Defaults.services(::newId).also { services.addAll(it); saveServices(); services.clear() })
        repairs.addAll(loadList("repairs") {
            RepairService(it.getLong("id"), it.getString("name"), it.optString("desc"), it.getDouble("price"),
                it.optString("dur"), it.optString("emoji", "🔧"))
        } ?: Defaults.repairs(::newId).also { repairs.addAll(it); saveRepairs(); repairs.clear() })
        orders.addAll(loadList("orders") {
            RepairOrder(it.getLong("id"), it.optInt("num"), it.optString("customer"), it.optString("phone"),
                it.optString("device"), it.optString("service"), it.optDouble("price", 0.0), it.optString("note"),
                it.optLong("at"), it.optInt("status"))
        } ?: emptyList())
        history.addAll(loadList("history") {
            HistoryEntry(it.getLong("id"), it.optString("kind"), it.optString("title"), it.optString("detail"),
                it.optDouble("amount", 0.0), it.optLong("time"))
        } ?: emptyList())
        prefs.getString("favorites", null)?.let { raw ->
            runCatching { val a = JSONArray(raw); for (i in 0 until a.length()) favorites.add(a.getString(i)) }
        }
        prefs.getString("cart", null)?.let { raw ->
            runCatching { val o = JSONObject(raw); o.keys().forEach { k -> cart[k.toLong()] = o.getInt(k) } }
        }
        val g = prefs.getString("games", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        Console.entries.forEach { c -> games[c] = g?.optString(c.name, null) ?: Defaults.games(c) }

        val maxId = (tables.map { it.id } + services.map { it.id } + repairs.map { it.id } +
            sessions.map { it.id } + orders.map { it.id } + history.map { it.id }).maxOrNull() ?: 0L
        if (maxId >= ids.get()) ids.set(maxId + 1)
    }

    private fun round2(v: Double) = (v * 100).roundToInt() / 100.0

    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store =
            instance ?: synchronized(this) { instance ?: Store(context.applicationContext).also { instance = it } }
    }
}

private object Defaults {
    fun tables(id: () -> Long) = listOf(
        GameTable(id(), "VIP Otaq 1", Console.PS5, 8.0, vip = true, seats = 4),
        GameTable(id(), "Masa 2", Console.PS5, 6.0),
        GameTable(id(), "Masa 3", Console.PS5, 6.0),
        GameTable(id(), "Masa 4", Console.PS5, 6.0),
        GameTable(id(), "VIP Otaq 5", Console.PS4, 5.0, vip = true, seats = 4),
        GameTable(id(), "Masa 6", Console.PS4, 4.0),
        GameTable(id(), "Masa 7", Console.PS4, 4.0),
        GameTable(id(), "Masa 8", Console.PS3, 2.5),
        GameTable(id(), "Masa 9", Console.PS3, 2.5),
    )

    fun services(id: () -> Long) = listOf(
        ServiceItem(id(), "Əlavə joystik", "Masaya 3-cü / 4-cü pult", 1.0, "🎮", "Oyun xidmətləri"),
        ServiceItem(id(), "VR təcrübə", "30 dəqiqə PS VR2", 5.0, "🥽", "Oyun xidmətləri"),
        ServiceItem(id(), "Turnir iştirakı", "Həftəlik FC turniri", 10.0, "🏆", "Oyun xidmətləri"),
        ServiceItem(id(), "Gecə paketi", "22:00 – 08:00 limitsiz", 20.0, "🌙", "Oyun xidmətləri"),
        ServiceItem(id(), "Premium qulaqlıq", "Surround səs", 1.0, "🎧", "Oyun xidmətləri"),
        ServiceItem(id(), "Coca-Cola", "0.5 L, soyuq", 1.5, "🥤", "İçkilər"),
        ServiceItem(id(), "Enerji içkisi", "0.25 L", 2.5, "⚡", "İçkilər"),
        ServiceItem(id(), "Çay dəsti", "Limon və şirniyyat ilə", 3.0, "🍵", "İçkilər"),
        ServiceItem(id(), "Çips", "Böyük paket", 1.5, "🍟", "Qəlyanaltılar"),
        ServiceItem(id(), "Pizza", "1 dilim, isti", 3.5, "🍕", "Qəlyanaltılar"),
        ServiceItem(id(), "Hot-dog", "Sous seçimi ilə", 3.0, "🌭", "Qəlyanaltılar"),
    )

    fun repairs(id: () -> Long) = listOf(
        RepairService(id(), "Ekran dəyişimi", "Orijinal və keyfiyyətli ekranlar", 60.0, "1–2 saat", "📱"),
        RepairService(id(), "Batareya dəyişimi", "Tez boşalma, şişmə", 35.0, "40 dəq", "🔋"),
        RepairService(id(), "Şarj portu təmiri", "Şarj olmama problemi", 25.0, "1 saat", "🔌"),
        RepairService(id(), "Suya düşmə bərpası", "Təmizləmə və diaqnostika", 45.0, "1–2 gün", "💧"),
        RepairService(id(), "Kamera təmiri", "Ön / arxa kamera", 40.0, "2 saat", "📷"),
        RepairService(id(), "Dinamik / mikrofon", "Səs problemləri", 20.0, "1 saat", "🔊"),
        RepairService(id(), "Proqram təminatı", "Flash, sıfırlama, yeniləmə", 15.0, "30 dəq", "💾"),
        RepairService(id(), "Qoruyucu şüşə", "Quraşdırma daxil", 8.0, "10 dəq", "🛡️"),
    )

    fun games(c: Console) = when (c) {
        Console.PS5 -> "EA FC 25, Mortal Kombat 1, Tekken 8, GTA V, Spider-Man 2, UFC 5, Gran Turismo 7"
        Console.PS4 -> "FIFA 23, PES 2021, Mortal Kombat 11, GTA V, NBA 2K24, Call of Duty"
        Console.PS3 -> "PES 2013, FIFA 14, Tekken 6, Mortal Kombat 9, God of War 3"
    }
}
