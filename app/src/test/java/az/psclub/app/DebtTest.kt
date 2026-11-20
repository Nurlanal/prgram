package az.psclub.app

import androidx.test.core.app.ApplicationProvider
import az.psclub.app.data.Session
import az.psclub.app.data.Store
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebtTest {
    private val store = Store.get(ApplicationProvider.getApplicationContext())
    @Before fun reset() = store.resetAll()

    @Test fun cashCreditAndDebtPaymentDayReport() {
        val now = System.currentTimeMillis()
        val t = store.tables[0]
        // 60 min @ rate 6 → 6 ₼ cash
        store.closeSession(Session(1, t.id, now - 60 * 60_000L, 0, "", 6.0), now)
        // 30 min @ 6 → 3 ₼ on credit for Elvin
        store.closeSession(Session(2, t.id, now - 30 * 60_000L, 0, "", 6.0), now, debtorName = "Elvin")
        // manual old debt 10 ₼ (no sale)
        store.addDebt("elvin", "050", 10.0, "köhnə borc")
        assertEquals(1, store.debtors.size)
        val d = store.debtors[0]
        assertEquals(13.0, store.debtOf(d.id), 0.001)
        store.payDebt(d, 5.0)
        assertEquals(8.0, store.debtOf(d.id), 0.001)
        assertEquals(8.0, store.totalDebt(), 0.001)

        val r = store.dayReport(store.startOfToday())
        assertEquals(6.0, r.cash, 0.001)
        assertEquals(3.0, r.credit, 0.001)
        assertEquals(9.0, r.sales, 0.001)
        assertEquals(5.0, r.debtPaid, 0.001)
        assertEquals(11.0, r.cashInRegister, 0.001)
        // revenue does not double-count debt payments
        assertEquals(9.0, store.revenueSince(store.startOfToday()), 0.001)
    }

    @Test fun cannotOverpay() {
        store.addDebt("Murad", "", 4.0, "")
        val d = store.debtors[0]
        store.payDebt(d, 50.0)
        assertEquals(0.0, store.debtOf(d.id), 0.001)
        assertEquals(4.0, store.dayReport(store.startOfToday()).debtPaid, 0.001)
    }
}
