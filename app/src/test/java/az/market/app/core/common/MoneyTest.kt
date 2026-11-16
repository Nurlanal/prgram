package az.market.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun formatsWithAzerbaijaniSeparators() {
        assertEquals("1 234,50 ₼", Money(123_450).format())
        assertEquals("0,05 ₼", Money(5).format())
        assertEquals("-2,00", Money(-200).format(withSymbol = false))
    }

    @Test fun parsesServerDecimalsWithoutFloatingPointErrors() {
        assertEquals(Money(1250), Money.fromDecimalString("12.50"))
        assertEquals(Money(1250), Money.fromDecimalString("12.5"))
        assertEquals(Money(1200), Money.fromDecimalString("12"))
        assertEquals(Money(-30), Money.fromDecimalString("-0.30"))
        // 0.1 + 0.2 must be exactly 0.30
        assertEquals(Money(30), Money.fromDecimalString("0.10") + Money.fromDecimalString("0.20"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThanTwoDecimals() {
        Money.fromDecimalString("1.999")
    }

    @Test fun arithmetic() {
        assertEquals(Money(750), Money(250) * 3)
        assertEquals(Money(100), Money(250) - Money(150))
    }
}
