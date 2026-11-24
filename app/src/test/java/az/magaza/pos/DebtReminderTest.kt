package az.magaza.pos

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import az.magaza.pos.data.CartLine
import az.magaza.pos.data.DebtSchedule
import az.magaza.pos.data.db.*
import az.magaza.pos.notify.DebtReminders
import az.magaza.pos.util.Dates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebtReminderTest {
    private val day = 86_400_000L

    private fun tx(id: Long, type: String, amount: Long, at: Long, saleId: Long? = null, due: Long? = null) =
        CustomerDebtTx(id = id, customerId = 1, createdAt = at, type = type, amount = amount, saleId = saleId, dueAt = due)

    @Test
    fun paymentsCloseOldestDebtFirst() {
        val open = DebtSchedule.open(listOf(
            tx(1, DebtType.SALE, 5000, 1, saleId = 10, due = 100 * day),
            tx(2, DebtType.SALE, 3000, 2, saleId = 11, due = 50 * day),
            tx(3, DebtType.PAYMENT, -6000, 3),
        ))
        // 50 tam bağlandı, ikincidən 10 ödənildi
        assertEquals(listOf(2L), open.map { it.tx.id })
        assertEquals(2000L, open.single().remaining)
    }

    @Test
    fun cancelledSaleRemovesItsOwnDebt() {
        val open = DebtSchedule.open(listOf(
            tx(1, DebtType.SALE, 5000, 1, saleId = 10),
            tx(2, DebtType.SALE, 3000, 2, saleId = 11),
            tx(3, DebtType.CANCEL, -3000, 3, saleId = 11),
        ))
        assertEquals(listOf(1L), open.map { it.tx.id })
        assertEquals(5000L, open.single().remaining)
        assertTrue(DebtSchedule.open(listOf(tx(1, DebtType.SALE, 100, 1), tx(2, DebtType.PAYMENT, -100, 2))).isEmpty())
    }

    @Test
    fun migrationFrom1To2KeepsData() {
        val name = "migration-test"
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO customers (id, name, phone, note, createdAt) VALUES (1, 'Əli', '050', '', 1)")
            execSQL("INSERT INTO customer_debt_tx (id, customerId, createdAt, type, amount, saleId, method, note) VALUES (1, 1, 5, 'SALE', 2000, 7, NULL, 'Satış #7')")
            execSQL("INSERT INTO products (id, name, imagePath, barcode, sku, category, brand, purchasePrice, salePrice, stock, minStock, supplierId, note, createdAt) VALUES (1, 'Kabel', NULL, '111', '', 'Kabellər', '', 500, 1000, 15, 2, NULL, '', 1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, AppDatabase.MIGRATION_1_2).close()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries().build()
        runBlocking {
            val d = db.dao()
            assertEquals("Əli", d.getCustomer(1)!!.name)
            assertEquals(2000L, d.customerDebt(1))
            assertNull(d.getDebtTx(1)!!.dueAt)
            assertEquals(15, d.getProduct(1)!!.stock)
            d.setDebtDue(1, 123L)
            assertEquals(123L, d.getDebtTx(1)!!.dueAt)
        }
        db.close()
    }

    @Test
    fun reminderFiresOnDueDayOnceAndStopsWhenPaid() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PosApp>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.settings.setReminderHour(0)
        val repo = app.repo
        val pid = repo.saveProduct(Product(name = "Telefon", purchasePrice = 10000, salePrice = 15000, stock = 3))
        val cid = repo.saveCustomer(Customer(name = "Rəşad"))
        val now = System.currentTimeMillis()
        // Sabaha təyin olunmuş borc — bu gün bildiriş yoxdur
        val saleId = repo.completeSale(listOf(CartLine(pid, "", 1, 15000)), 0, 5000, 0, cid, debtDueAt = now + day)
        val t = repo.dao.allDebtTx().single()
        assertEquals(Dates.startOfDay(now + day), t.dueAt)
        assertTrue(DebtReminders.due(app, now).isEmpty())

        // Ertəsi gün: bildiriş gəlir
        val tomorrow = now + day
        val r = DebtReminders.due(app, tomorrow).single()
        assertEquals("Rəşad", r.customerName)
        assertEquals(10000L, r.amount)
        assertEquals(1, DebtReminders.run(app, tomorrow))
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        assertEquals(1, nm.allNotifications.size)
        assertEquals("Bu gün borc ödəniş günüdür", nm.allNotifications[0].extras.getString("android.title"))
        // Eyni gün ikinci dəfə göndərilmir
        assertEquals(0, DebtReminders.run(app, tomorrow))
        // Gecikmiş: 2 gün sonra yenidən xatırladır
        val late = DebtReminders.due(app, tomorrow + 2 * day).single()
        assertEquals(2, late.daysLate)

        // Tarixi dəyişmək (möhlət) və ödəmək
        repo.setDebtDueDate(t.id, tomorrow + 10 * day)
        assertTrue(DebtReminders.due(app, tomorrow + 2 * day).isEmpty())
        repo.payDebt(cid, 10000, PayMethod.CASH, "")
        assertTrue(DebtReminders.due(app, tomorrow + 20 * day).isEmpty())
        assertTrue(saleId > 0)
    }
}
