package az.magaza.pos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import az.magaza.pos.data.*
import az.magaza.pos.data.db.*
import az.magaza.pos.util.Barcodes
import az.magaza.pos.util.DateRange
import az.magaza.pos.util.Money
import az.magaza.pos.util.RangePreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: PosRepository
    private lateinit var dao: PosDao
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = PosRepository(db)
        dao = repo.dao
    }

    @After
    fun tearDown() = db.close()

    private fun azn(v: String) = Money.parse(v)!!
    private val all = DateRange.of(RangePreset.ALL)

    private suspend fun product(buy: String = "5", sell: String = "10", stock: Int = 20, name: String = "Kabel") =
        repo.saveProduct(Product(name = name, purchasePrice = azn(buy), salePrice = azn(sell), stock = stock, minStock = 2))

    /** Tapşırıqdakı nümunə: 5 ədəd × 10 AZN, 30 AZN nağd, 20 AZN borc. */
    @Test
    fun specExample_mixedCashAndDebt() = runBlocking {
        val pid = product()
        val cid = repo.saveCustomer(Customer(name = "Əli"))
        val saleId = repo.completeSale(listOf(CartLine(pid, "Kabel", 5, azn("10"))), 0, azn("30"), 0, cid)

        val sale = dao.getSale(saleId)!!
        assertEquals(azn("50"), sale.total)          // Satış: 50 AZN
        assertEquals(azn("25"), sale.costTotal)      // Maya dəyəri: 25 AZN
        assertEquals(azn("25"), sale.profit)         // Brüt qazanc: 25 AZN
        assertEquals(15, dao.getProduct(pid)!!.stock) // Anbar qalığı: 15
        assertEquals(azn("30"), dao.observeCashBalance().first()) // Kassa: 30 AZN
        assertEquals(azn("20"), dao.customerDebt(cid))            // Borc: 20 AZN
        assertEquals(azn("20"), dao.observeTotalCustomerDebt().first())
        assertEquals(azn("50"), dao.observeSalesTotal(all.from, all.to).first())
        assertEquals(5L, dao.observeItemsSold(all.from, all.to).first())
    }

    @Test
    fun cannotSellMoreThanStock() = runBlocking {
        val pid = product(stock = 3)
        val e = runCatching { repo.completeSale(listOf(CartLine(pid, "Kabel", 4, azn("10"))), 0, azn("40"), 0, null) }.exceptionOrNull()
        assertTrue(e is PosException)
        assertEquals(3, dao.getProduct(pid)!!.stock)
        assertEquals(0L, dao.observeCashBalance().first())
    }

    @Test
    fun debtWithoutCustomerIsRejected() = runBlocking {
        val pid = product()
        val e = runCatching { repo.completeSale(listOf(CartLine(pid, "Kabel", 1, azn("10"))), 0, azn("5"), 0, null) }.exceptionOrNull()
        assertTrue(e is PosException)
        assertTrue(dao.allSales().isEmpty())
    }

    @Test
    fun discountAndCardPayment() = runBlocking {
        val pid = product()
        val id = repo.completeSale(listOf(CartLine(pid, "Kabel", 2, azn("10"))), azn("3"), 0, azn("17"), null)
        val s = dao.getSale(id)!!
        assertEquals(azn("17"), s.total)
        assertEquals(azn("7"), s.profit) // 17 - 10
        assertEquals(azn("17"), dao.cashBalanceBy(PayMethod.CARD))
        assertEquals(0L, dao.cashBalanceBy(PayMethod.CASH))
    }

    @Test
    fun cancelSaleRevertsEverything() = runBlocking {
        val pid = product()
        val cid = repo.saveCustomer(Customer(name = "Vəli"))
        val id = repo.completeSale(listOf(CartLine(pid, "Kabel", 5, azn("10"))), 0, azn("20"), azn("10"), cid)
        repo.cancelSale(id, "Müştəri imtina etdi")

        assertEquals(20, dao.getProduct(pid)!!.stock)
        assertEquals(0L, dao.observeCashBalance().first())
        assertEquals(0L, dao.customerDebt(cid))
        val s = dao.getSale(id)!!
        assertEquals(SaleStatus.CANCELLED, s.status)
        assertEquals("Müştəri imtina etdi", s.cancelReason)
        assertEquals(0L, dao.observeSalesTotal(all.from, all.to).first())
        assertEquals(0L, dao.observeProfitTotal(all.from, all.to).first())
        // Kassa tarixçəsi silinmir: 2 satış + 2 əks yazılış
        assertEquals(4, dao.allCashTx().size)
        assertTrue(runCatching { repo.cancelSale(id, "yenə") }.exceptionOrNull() is PosException)
    }

    @Test
    fun debtPaymentGoesToCash() = runBlocking {
        val pid = product()
        val cid = repo.saveCustomer(Customer(name = "Aysel"))
        repo.completeSale(listOf(CartLine(pid, "Kabel", 5, azn("10"))), 0, 0, 0, cid)
        assertEquals(azn("50"), dao.customerDebt(cid))
        repo.payDebt(cid, azn("15"), PayMethod.CASH, "qismən")
        assertEquals(azn("35"), dao.customerDebt(cid))
        assertEquals(azn("15"), dao.observeCashBalance().first())
        assertTrue(runCatching { repo.payDebt(cid, azn("100"), PayMethod.CASH, "") }.exceptionOrNull() is PosException)
        repo.payDebt(cid, azn("35"), PayMethod.CARD, "tam")
        assertEquals(0L, dao.customerDebt(cid))
        val c = dao.observeCustomersWithDebt().first().single()
        assertEquals(azn("50"), c.paid)
        // Satış qazancı ödənişdən asılı deyil
        assertEquals(azn("25"), dao.observeProfitTotal(all.from, all.to).first())
    }

    @Test
    fun purchaseUpdatesStockCashAndSupplierDebt() = runBlocking {
        val pid = product(buy = "5", stock = 10)
        val sid = repo.saveSupplier(Supplier(name = "Topdan MMC"))
        repo.cashIn(azn("100"), PayMethod.CASH, "açılış")
        // 10 ədəd × 7 AZN = 70, 30 ödənilir, 40 borc
        val puId = repo.addPurchase(pid, 10, azn("7"), sid, azn("30"), PayMethod.CASH, System.currentTimeMillis(), "")
        val p = dao.getProduct(pid)!!
        assertEquals(20, p.stock)
        assertEquals(azn("6"), p.purchasePrice) // orta çəkili: (10*5 + 10*7)/20
        assertEquals(azn("70"), dao.observeCashBalance().first())
        assertEquals(azn("40"), dao.supplierDebt(sid))
        assertEquals(PurchaseStatus.PARTIAL, dao.getPurchase(puId)!!.paymentStatus)

        repo.paySupplier(sid, azn("40"), PayMethod.CASH, "")
        assertEquals(0L, dao.supplierDebt(sid))
        assertEquals(azn("30"), dao.observeCashBalance().first())

        // Borcla alış təchizatçısız olmaz
        assertTrue(runCatching { repo.addPurchase(pid, 1, azn("1"), null, 0, PayMethod.CASH, 0, "") }.exceptionOrNull() is PosException)
    }

    @Test
    fun cancelPurchase() = runBlocking {
        val pid = product(stock = 0)
        val puId = repo.addPurchase(pid, 5, azn("4"), null, azn("20"), PayMethod.CASH, System.currentTimeMillis(), "")
        assertEquals(-azn("20"), dao.observeCashBalance().first())
        repo.cancelPurchase(puId, "səhv")
        assertEquals(0, dao.getProduct(pid)!!.stock)
        assertEquals(0L, dao.observeCashBalance().first())
        assertTrue(dao.getPurchase(puId)!!.cancelled)
    }

    @Test
    fun expenseReducesCashAndProfitReport() = runBlocking {
        val pid = product()
        repo.completeSale(listOf(CartLine(pid, "Kabel", 4, azn("10"))), 0, azn("40"), 0, null)
        val eid = repo.addExpense(azn("12.50"), "Elektrik", "Noyabr", PayMethod.CASH, System.currentTimeMillis(), "")
        assertEquals(azn("27.50"), dao.observeCashBalance().first())
        assertEquals(azn("12.50"), dao.expenseTotal(all.from, all.to))
        val report = Reports.cash(dao, all)
        assertEquals(azn("40"), report.cashSales)
        assertEquals(azn("12.50"), report.expenses)
        assertEquals(azn("27.50"), report.closing)
        repo.cancelExpense(eid)
        assertEquals(azn("40"), dao.observeCashBalance().first())
        assertEquals(0L, dao.expenseTotal(all.from, all.to))
    }

    @Test
    fun cashSessionRecordsDifference() = runBlocking {
        repo.cashIn(azn("50"), PayMethod.CASH, "")
        repo.openCashSession(azn("50"), "")
        assertTrue(runCatching { repo.openCashSession(0, "") }.exceptionOrNull() is PosException)
        repo.closeCashSession(azn("48"), "2 AZN əskik")
        assertEquals(azn("48"), dao.cashBalanceBy(PayMethod.CASH))
        val s = dao.allSessions().single()
        assertEquals(azn("50"), s.expectedBalance)
        assertEquals(azn("48"), s.countedBalance)
        assertTrue(runCatching { repo.cashOut(azn("100"), PayMethod.CASH, "") }.exceptionOrNull() is PosException)
    }

    @Test
    fun stockAdjustAndDuplicateBarcode() = runBlocking {
        val pid = repo.saveProduct(Product(name = "Qab", barcode = "4760001", salePrice = 100, stock = 5))
        assertTrue(runCatching { repo.saveProduct(Product(name = "Başqa", barcode = "4760001")) }.exceptionOrNull() is PosException)
        repo.adjustStock(pid, 3, "sayım")
        assertEquals(3, dao.getProduct(pid)!!.stock)
        assertEquals("Qab", dao.findByBarcode("4760001")!!.name)
        val moves = dao.allStockMoves()
        assertEquals(listOf(StockType.INITIAL, StockType.ADJUST), moves.sortedBy { it.id }.map { it.type })
    }

    @Test
    fun backupRoundTrip() = runBlocking {
        val pid = product()
        val cid = repo.saveCustomer(Customer(name = "Leyla", phone = "+994501234567"))
        repo.completeSale(listOf(CartLine(pid, "Kabel", 2, azn("10"))), 0, azn("5"), 0, cid)
        repo.addExpense(azn("3"), "Digər", "", PayMethod.CASH, System.currentTimeMillis(), "")

        val bm = BackupManager(ctx, repo)
        val out = ByteArrayOutputStream()
        bm.exportZip(out)
        val before = bm.snapshot()

        bm.wipeAll()
        assertTrue(dao.allProducts().isEmpty())
        assertEquals(0L, dao.observeCashBalance().first())

        val (data, images) = bm.readBackup(out.toByteArray().inputStream())
        bm.restore(data, images)
        val after = bm.snapshot()
        assertEquals(before.products, after.products)
        assertEquals(before.sales, after.sales)
        assertEquals(before.saleItems, after.saleItems)
        assertEquals(before.cashTransactions, after.cashTransactions)
        assertEquals(before.customerDebtTx, after.customerDebtTx)
        assertEquals(azn("2"), dao.observeCashBalance().first())
        assertEquals(azn("15"), dao.customerDebt(cid))

        // JSON formatı da qəbul edilir
        val json = ByteArrayOutputStream()
        bm.exportJson(after, json)
        assertEquals(after.products, bm.readBackup(json.toByteArray().inputStream()).first.products)
        // Səhv fayl rədd edilir
        assertTrue(runCatching { bm.readBackup("salam".byteInputStream()) }.exceptionOrNull() is PosException)
    }

    @Test
    fun assignAndGenerateBarcodes() = runBlocking {
        val a = repo.saveProduct(Product(name = "Qab A", salePrice = 500, stock = 5))
        val b = repo.saveProduct(Product(name = "Qab B", salePrice = 500, stock = 5))
        val c = repo.saveProduct(Product(name = "Kabel", barcode = "4801234567890", salePrice = 900, stock = 5))

        // Satışda skan edilmiş naməlum barkod mövcud məhsula təyin edilir
        repo.assignBarcode(a, "123456")
        assertEquals(a, dao.findByBarcode("123456")!!.id)
        assertTrue(runCatching { repo.assignBarcode(b, "123456") }.exceptionOrNull() is PosException)
        assertTrue(runCatching { repo.assignBarcode(b, "4801234567890") }.exceptionOrNull() is PosException)
        // Təyin edildikdən sonra həmin barkodla satış
        val sale = repo.completeSale(listOf(CartLine(dao.findByBarcode("123456")!!.id, "", 2, 500)), 0, 1000, 0, null)
        assertEquals(3, dao.getProduct(a)!!.stock)
        assertEquals(1000L, dao.getSale(sale)!!.total)

        // Barkodsuz məhsullara daxili EAN-13
        assertEquals(1, repo.generateMissingBarcodes())
        val code = dao.getProduct(b)!!.barcode
        assertTrue(Barcodes.isValidEan13(code))
        assertTrue(code.startsWith("20"))
        assertEquals("4801234567890", dao.getProduct(c)!!.barcode) // mövcud barkod dəyişmir
        assertEquals(0, repo.generateMissingBarcodes())
        assertTrue(Barcodes.isValidEan13("4006381333931"))
        assertFalse(Barcodes.isValidEan13("4006381333932"))
    }

    @Test
    fun moneyParsing() {
        assertEquals(1250L, Money.parse("12,5"))
        assertEquals(1250L, Money.parse("12.50"))
        assertEquals(100000L, Money.parse("1 000"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertEquals("1 250.50 ₼", Money.format(125050))
        assertEquals("12.5", Money.toInput(1250))
    }
}
