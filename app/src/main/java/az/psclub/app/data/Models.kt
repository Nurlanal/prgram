package az.psclub.app.data

import java.util.Locale

enum class Console(val label: String, val short: String) {
    PS5("PlayStation 5", "PS5"),
    PS4("PlayStation 4", "PS4"),
    PS3("PlayStation 3", "PS3"),
}

data class GameTable(
    val id: Long,
    val name: String,
    val console: Console,
    val pricePerHour: Double,
    val vip: Boolean = false,
    val seats: Int = 2,
)

data class Session(
    val id: Long,
    val tableId: Long,
    val startedAt: Long,
    /** 0 = open time (billed per minute) */
    val plannedMinutes: Int,
    val customer: String,
    val rate: Double,
    val extrasTotal: Double = 0.0,
    val extrasNote: String = "",
)

data class ServiceItem(
    val id: Long,
    val name: String,
    val description: String,
    val price: Double,
    val emoji: String,
    val category: String,
)

data class RepairService(
    val id: Long,
    val name: String,
    val description: String,
    val price: Double,
    val duration: String,
    val emoji: String,
)

data class RepairOrder(
    val id: Long,
    val number: Int,
    val customer: String,
    val phone: String,
    val device: String,
    val serviceName: String,
    val price: Double,
    val note: String,
    val createdAt: Long,
    /** 0 = new, 1 = in repair, 2 = ready / paid */
    val status: Int,
)

data class HistoryEntry(
    val id: Long,
    val kind: String, // table | service | repair
    val title: String,
    val detail: String,
    val amount: Double,
    val time: Long,
    /** cash | credit (nisyə) | debtpay (borc ödənişi) */
    val method: String = "cash",
)

data class Debtor(
    val id: Long,
    val name: String,
    val phone: String = "",
    val createdAt: Long,
)

/** amount > 0 = new debt, amount < 0 = payment */
data class DebtRecord(
    val id: Long,
    val debtorId: Long,
    val amount: Double,
    val note: String,
    val time: Long,
)

data class DayReport(
    val cash: Double,
    val credit: Double,
    val debtPaid: Double,
    val byKind: Map<String, Double>,
    val entries: List<HistoryEntry>,
) {
    val sales get() = cash + credit
    val cashInRegister get() = cash + debtPaid
}

fun money(v: Double): String =
    if (v % 1.0 == 0.0) "${v.toLong()} ₼" else String.format(Locale.US, "%.2f ₼", v)

fun plain(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

fun parsePrice(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

fun clock(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
}

fun durationLabel(min: Int): String = when {
    min == 0 -> "Açıq vaxt"
    min < 60 -> "$min dəq"
    min % 60 == 0 -> "${min / 60} saat"
    else -> "${min / 60} saat ${min % 60} dəq"
}
