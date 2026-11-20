package az.psclub.app

import androidx.test.core.app.ApplicationProvider
import az.psclub.app.data.Session
import az.psclub.app.data.Store
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BillingTest {
    private val store = Store.get(ApplicationProvider.getApplicationContext())
    private fun s(planned: Int) = Session(1, 1, 0L, planned, "", rate = 2.5)

    @Test fun openTime25Minutes() {
        assertEquals(25, store.billedMinutes(s(0), 25 * 60_000L + 30_000L))
        assertEquals(1.04, store.timeCost(s(0), 25 * 60_000L), 0.001) // 2.50/60*25
    }
    @Test fun plannedClosedEarlyPaysPlayedOnly() = assertEquals(1.04, store.timeCost(s(60), 25 * 60_000L), 0.001)
    @Test fun fullHour() = assertEquals(2.5, store.timeCost(s(0), 60 * 60_000L), 0.001)
    @Test fun overtime() = assertEquals(3.75, store.timeCost(s(60), 90 * 60_000L), 0.001)
}
