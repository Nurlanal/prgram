package az.magaza.pos.data

import az.magaza.pos.data.db.CustomerDebtTx
import az.magaza.pos.data.db.DebtType
import az.magaza.pos.util.Dates

/** Hələ ödənilməmiş borc hissəsi. */
data class OpenDebt(
    val tx: CustomerDebtTx,
    val remaining: Long,
) {
    val dueAt: Long? get() = tx.dueAt
    /** Ödəniş gününə qalan gün sayı: 0 — bu gün, mənfi — gecikib. null — tarix yoxdur. */
    fun daysLeft(now: Long = System.currentTimeMillis()): Int? =
        dueAt?.let { ((Dates.startOfDay(it) - Dates.startOfDay(now)) / 86_400_000L).toInt() }
}

/**
 * Hansı borcların açıq qaldığını hesablayır:
 *  - satış ləğvi əvvəlcə həmin satışın borcunu bağlayır;
 *  - ödənişlər isə ən köhnə borcdan başlayaraq (FIFO) bağlanır.
 */
object DebtSchedule {
    fun open(txs: List<CustomerDebtTx>): List<OpenDebt> {
        val sorted = txs.sortedWith(compareBy({ it.createdAt }, { it.id }))
        val remaining = LinkedHashMap<Long, Long>()
        sorted.filter { it.amount > 0 }.forEach { remaining[it.id] = it.amount }
        var credit = 0L
        sorted.filter { it.amount < 0 }.forEach { t ->
            var left = -t.amount
            if (t.type == DebtType.CANCEL && t.saleId != null) {
                sorted.firstOrNull { it.amount > 0 && it.saleId == t.saleId && it.type == DebtType.SALE }?.let { d ->
                    val r = remaining[d.id] ?: 0
                    val use = minOf(r, left)
                    remaining[d.id] = r - use
                    left -= use
                }
            }
            credit += left
        }
        val byId = sorted.associateBy { it.id }
        val out = mutableListOf<OpenDebt>()
        remaining.forEach { (id, r) ->
            val use = minOf(r, credit)
            credit -= use
            if (r - use > 0) out += OpenDebt(byId.getValue(id), r - use)
        }
        return out
    }

    /** Müştəri üzrə qruplaşdırılmış açıq borclar. */
    fun openByCustomer(all: List<CustomerDebtTx>): Map<Long, List<OpenDebt>> =
        all.groupBy { it.customerId }.mapValues { open(it.value) }.filterValues { it.isNotEmpty() }

    /** Ən yaxın (və ya ən çox gecikmiş) ödəniş tarixi. */
    fun nextDue(open: List<OpenDebt>): Long? = open.mapNotNull { it.dueAt }.minOrNull()

    fun dueLabel(days: Int?): String = when {
        days == null -> "Tarix yoxdur"
        days < 0 -> "${-days} gün gecikib"
        days == 0 -> "Bu gün ödəməlidir"
        days == 1 -> "Sabah"
        else -> "$days gün qalıb"
    }
}
