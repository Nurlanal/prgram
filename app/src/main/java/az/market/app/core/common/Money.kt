package az.market.app.core.common

import kotlin.math.absoluteValue

/**
 * Money is handled as an integer number of qəpik (1 AZN = 100 qəpik) to avoid floating-point
 * rounding errors. Authoritative totals are always computed on the server; this type is only
 * used for display and for mapping server values.
 */
@JvmInline
value class Money(val qepik: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(qepik + other.qepik)
    operator fun minus(other: Money) = Money(qepik - other.qepik)
    operator fun times(quantity: Int) = Money(qepik * quantity)
    override fun compareTo(other: Money) = qepik.compareTo(other.qepik)

    /** Azerbaijani formatting: "1 234,50 ₼" (space thousands separator, comma decimal). */
    fun format(withSymbol: Boolean = true): String {
        val abs = qepik.absoluteValue
        val manat = (abs / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
        val cents = (abs % 100).toString().padStart(2, '0')
        val sign = if (qepik < 0) "-" else ""
        return if (withSymbol) "$sign$manat,$cents ₼" else "$sign$manat,$cents"
    }

    companion object {
        val ZERO = Money(0)

        /** Parses a server decimal string such as "12.50" without going through Double. */
        fun fromDecimalString(value: String): Money {
            val trimmed = value.trim()
            require(trimmed.matches(Regex("-?\\d+(\\.\\d{1,2})?"))) { "Invalid money value: $value" }
            val negative = trimmed.startsWith("-")
            val parts = trimmed.removePrefix("-").split('.')
            val manat = parts[0].toLong()
            val cents = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L
            val total = manat * 100 + cents
            return Money(if (negative) -total else total)
        }
    }
}
