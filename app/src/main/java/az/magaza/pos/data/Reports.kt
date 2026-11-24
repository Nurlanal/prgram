package az.magaza.pos.data

import az.magaza.pos.data.db.*
import az.magaza.pos.util.DateRange

/** Kassa hesabatı: açılış (dövrün əvvəlinə qalıq), dövr ərzində hərəkət, bağlanış. */
data class CashReport(
    val opening: Long,
    val openingCash: Long,
    val openingCard: Long,
    val cashSales: Long,
    val cardSales: Long,
    val debtPayments: Long,
    val cashIn: Long,
    val cashOut: Long,
    val expenses: Long,
    val supplierPayments: Long,
    val purchasePayments: Long,
    val cancellations: Long,
    val adjustments: Long,
    val closing: Long,
    val closingCash: Long,
    val closingCard: Long,
) {
    val totalIn get() = cashSales + cardSales + debtPayments + cashIn
    val totalOut get() = cashOut + expenses + supplierPayments + purchasePayments
}

object Reports {
    suspend fun cash(dao: PosDao, r: DateRange): CashReport {
        val openingCash = dao.cashBalanceBeforeBy(PayMethod.CASH, r.from)
        val openingCard = dao.cashBalanceBeforeBy(PayMethod.CARD, r.from)
        val sums = dao.cashSumsByType(r.from, r.to)
        fun sum(type: String, method: String? = null) =
            sums.filter { it.type == type && (method == null || it.method == method) }.sumOf { it.total }
        val netCash = sums.filter { it.method == PayMethod.CASH }.sumOf { it.total }
        val netCard = sums.filter { it.method == PayMethod.CARD }.sumOf { it.total }
        return CashReport(
            opening = openingCash + openingCard,
            openingCash = openingCash,
            openingCard = openingCard,
            cashSales = sum(CashType.SALE, PayMethod.CASH),
            cardSales = sum(CashType.SALE, PayMethod.CARD),
            debtPayments = sum(CashType.DEBT_PAYMENT),
            cashIn = sum(CashType.CASH_IN),
            cashOut = -sum(CashType.CASH_OUT),
            expenses = -(sum(CashType.EXPENSE) + sum(CashType.EXPENSE_CANCEL)),
            supplierPayments = -sum(CashType.SUPPLIER_PAYMENT),
            purchasePayments = -(sum(CashType.PURCHASE) + sum(CashType.PURCHASE_CANCEL)),
            cancellations = sum(CashType.SALE_CANCEL),
            adjustments = sum(CashType.ADJUSTMENT),
            closing = openingCash + openingCard + netCash + netCard,
            closingCash = openingCash + netCash,
            closingCard = openingCard + netCard,
        )
    }

    fun cashTypeName(t: String) = when (t) {
        CashType.SALE -> "Satış"
        CashType.SALE_CANCEL -> "Satış ləğvi"
        CashType.DEBT_PAYMENT -> "Borc ödənişi"
        CashType.EXPENSE -> "Məxaric"
        CashType.EXPENSE_CANCEL -> "Məxaric ləğvi"
        CashType.PURCHASE -> "Mədaxil ödənişi"
        CashType.PURCHASE_CANCEL -> "Mədaxil ləğvi"
        CashType.SUPPLIER_PAYMENT -> "Təchizatçıya ödəniş"
        CashType.CASH_IN -> "Kassa mədaxili"
        CashType.CASH_OUT -> "Kassa məxarici"
        CashType.ADJUSTMENT -> "Kassa düzəlişi"
        else -> t
    }
}
