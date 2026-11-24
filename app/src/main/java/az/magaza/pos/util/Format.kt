package az.magaza.pos.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Money {
    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ' '
        decimalSeparator = '.'
    }

    private fun df() = DecimalFormat("#,##0.00", symbols)

    /** 12550 -> "125.50" */
    fun plain(qepik: Long): String = df().format(BigDecimal(qepik).movePointLeft(2))

    /** 12550 -> "125.50 ₼" */
    fun format(qepik: Long): String = plain(qepik) + " ₼"

    /** Printer/CSV üçün: "125.50 AZN" */
    fun formatAzn(qepik: Long): String = plain(qepik) + " AZN"

    /** İstifadəçi daxil etdiyi mətni qəpiyə çevirir. "12,5" -> 1250. Səhv olarsa null. */
    fun parse(text: String): Long? {
        val t = text.trim().replace(" ", "").replace(',', '.')
        if (t.isEmpty()) return null
        return try {
            val bd = BigDecimal(t)
            if (bd.signum() < 0) null else bd.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    /** Redaktə sahəsi üçün: 1250 -> "12.50", 1200 -> "12" */
    fun toInput(qepik: Long): String {
        if (qepik == 0L) return ""
        val bd = BigDecimal(qepik).movePointLeft(2).stripTrailingZeros()
        return bd.toPlainString()
    }
}

fun Long.money(): String = Money.format(this)

object Dates {
    private val loc = Locale("az")
    fun dateTime(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", loc).format(Date(ms))
    fun date(ms: Long): String = SimpleDateFormat("dd.MM.yyyy", loc).format(Date(ms))
    fun time(ms: Long): String = SimpleDateFormat("HH:mm", loc).format(Date(ms))
    fun shortDay(ms: Long): String = SimpleDateFormat("dd.MM", loc).format(Date(ms))
    fun fileStamp(ms: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(ms))

    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun endOfDay(ms: Long): Long = startOfDay(ms) + 24L * 60 * 60 * 1000 - 1

    /** Tarix seçicisindən gələn UTC gün başlanğıcını lokal günə çevirir. */
    fun utcDayToLocalStart(utcMs: Long): Long {
        val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    /** Lokal vaxtı (gün) tarix seçicisinin gözlədiyi UTC formatına çevirir. */
    fun localToUtcDay(ms: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }
}

enum class RangePreset(val label: String) {
    TODAY("Bu gün"), WEEK("Həftə"), MONTH("Ay"), YEAR("İl"), ALL("Ümumi"), CUSTOM("Tarix seç")
}

data class DateRange(val from: Long, val to: Long, val preset: RangePreset) {
    val label: String
        get() = when (preset) {
            RangePreset.ALL -> "Bütün dövr"
            RangePreset.TODAY -> "Bu gün, ${Dates.date(from)}"
            else -> "${Dates.date(from)} – ${Dates.date(to)}"
        }

    val days: Int get() = (((Dates.startOfDay(to) - Dates.startOfDay(from)) / 86_400_000L) + 1).toInt()

    companion object {
        fun of(preset: RangePreset, now: Long = System.currentTimeMillis()): DateRange {
            val end = Dates.endOfDay(now)
            val cal = Calendar.getInstance().apply { timeInMillis = Dates.startOfDay(now) }
            val from = when (preset) {
                RangePreset.TODAY, RangePreset.CUSTOM -> cal.timeInMillis
                RangePreset.WEEK -> cal.apply { add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis
                RangePreset.MONTH -> cal.apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
                RangePreset.YEAR -> cal.apply { set(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                RangePreset.ALL -> 0L
            }
            return DateRange(from, end, preset)
        }

        fun custom(fromLocal: Long, toLocal: Long) =
            DateRange(Dates.startOfDay(fromLocal), Dates.endOfDay(toLocal), RangePreset.CUSTOM)
    }
}
