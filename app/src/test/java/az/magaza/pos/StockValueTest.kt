package az.magaza.pos

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import az.magaza.pos.data.*
import az.magaza.pos.data.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class StockValueTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: PosRepository
    private val dao get() = repo.dao
    private val now = System.currentTimeMillis()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        repo = PosRepository(db)
    }
    @After fun tearDown() = db.close()

    private suspend fun stockValue() = dao.observeStockCostValue().first()

    @Test
    fun averagingDoesNotLoseAQepik() = runBlocking {
        val id = repo.saveProduct(Product(name = "Qab", purchasePrice = 0, salePrice = 1000))
        repo.addPurchase(id, 2, 500, null, 1000, PayMethod.CASH, now, "")
        repo.addPurchase(id, 1, 600, null, 600, PayMethod.CASH, now, "")
        // Köhnə hesab: 3 × 5.33 = 15.99. İndi dəqiq 16.00
        assertEquals(1600L, stockValue())
        assertEquals(533L, dao.getProduct(id)!!.purchasePrice)
        val costs = (1..3).map { dao.getSale(repo.completeSale(listOf(CartLine(id, "", 1, 1000)), 0, 1000, 0, null))!!.costTotal }
        assertEquals(1600L, costs.sum())            // satılanların mayası cəmi = alış
        assertEquals(0L, stockValue())
        assertEquals(3000L - 1600L, dao.observeProfitTotal(0, Long.MAX_VALUE).first())
    }

    @Test
    fun purchaseByTotalAmountIsExact() = runBlocking {
        val id = repo.saveProduct(Product(name = "Kabel", salePrice = 500))
        repo.addPurchase(id, 3, 0, null, 1000, PayMethod.CASH, now, "", totalCost = 1000) // 3 ədəd cəmi 10.00
        assertEquals(1000L, stockValue())
        assertEquals(1000L, dao.allPurchases().single().total)
        assertEquals(333L, dao.allPurchases().single().unitCost)
        val s1 = repo.completeSale(listOf(CartLine(id, "", 2, 500)), 0, 1000, 0, null)
        assertEquals(667L, dao.getSale(s1)!!.costTotal)
        assertEquals(333L, stockValue())
        // Ləğv: dəyər dəqiq geri qayıdır
        repo.cancelSale(s1, "test")
        assertEquals(1000L, stockValue())
        assertEquals(3, dao.getProduct(id)!!.stock)
    }

    @Test
    fun stockCountCorrectsQuantityAndValue() = runBlocking {
        val a = repo.saveProduct(Product(name = "A", purchasePrice = 333, salePrice = 500, stock = 3))   // 9.99
        val b = repo.saveProduct(Product(name = "B", purchasePrice = 250, salePrice = 400, stock = 4))   // 10.00
        val c = repo.saveProduct(Product(name = "C", purchasePrice = 100, salePrice = 200, stock = 5))
        assertEquals(999L + 1000 + 500, stockValue())
        val r = repo.applyStockCount(mapOf(a to 2, b to 6, c to 5), "sayım")
        assertEquals(2, r.changed)
        assertEquals(1, r.qtyDiff)
        assertEquals(2, dao.getProduct(a)!!.stock)
        assertEquals(666L, dao.getProduct(a)!!.stockCost)   // 1 ədəd (333) çıxdı
        assertEquals(1500L, dao.getProduct(b)!!.stockCost)  // 2 ədəd orta qiymətlə (250) əlavə
        assertEquals(-333L + 500L, r.valueDiff)
        assertEquals(666L + 1500 + 500, stockValue())
        assertEquals(2, dao.allStockMoves().count { it.type == StockType.ADJUST })
        // Sıfıra endirmək dəyəri tam sıfırlayır
        repo.adjustStock(a, 0, "itki")
        assertEquals(0L, dao.getProduct(a)!!.stockCost)
        assertTrue(runCatching { repo.applyStockCount(mapOf(c to -1), "") }.exceptionOrNull() is PosException)
    }

    @Test
    fun manualPriceChangeRevaluesStock() = runBlocking {
        val id = repo.saveProduct(Product(name = "X", purchasePrice = 500, salePrice = 900, stock = 4))
        val p = dao.getProduct(id)!!
        repo.saveProduct(p.copy(salePrice = 950))          // yalnız satış qiyməti: dəyər dəyişmir
        assertEquals(2000L, stockValue())
        repo.saveProduct(dao.getProduct(id)!!.copy(purchasePrice = 450))
        assertEquals(1800L, stockValue())
        repo.saveProduct(dao.getProduct(id)!!.copy(stock = 2)) // redaktədə say dəyişdi
        assertEquals(900L, stockValue())
    }

    /** Təsadüfi əməliyyatlar: alışlar = satılanların mayası + anbar dəyəri (qəpiyinə qədər). */
    @Test
    fun randomOperationsKeepExactBalance() = runBlocking {
        val rnd = Random(42)
        val ids = (1..5).map { repo.saveProduct(Product(name = "P$it", salePrice = 1000 + it * 37L)) }
        val supplier = repo.saveSupplier(Supplier(name = "S"))
        val sales = mutableListOf<Long>()
        var corrections = 0L
        repeat(400) {
            val id = ids.random(rnd)
            when (rnd.nextInt(10)) {
                in 0..3 -> {
                    val q = rnd.nextInt(1, 7)
                    val byTotal = rnd.nextBoolean()
                    val total = rnd.nextLong(1, 5000)
                    repo.addPurchase(id, q, if (byTotal) 0 else total, supplier, 0, PayMethod.CASH, now, "",
                        totalCost = if (byTotal) total else null)
                }
                in 4..7 -> {
                    val p = dao.getProduct(id)!!
                    if (p.stock > 0) {
                        val q = rnd.nextInt(1, p.stock + 1)
                        sales += repo.completeSale(listOf(CartLine(id, "", q, p.salePrice)), 0, p.salePrice * q, 0, null)
                    }
                }
                8 -> sales.filter { dao.getSale(it)!!.status == SaleStatus.ACTIVE }.randomOrNull(rnd)?.let { repo.cancelSale(it, "r") }
                else -> {
                    val p = dao.getProduct(id)!!
                    corrections += repo.applyStockCount(mapOf(id to (p.stock + rnd.nextInt(-3, 4)).coerceAtLeast(0)), "r").valueDiff
                }
            }
            val bought = dao.allPurchases().filter { !it.cancelled }.sumOf { it.total }
            val soldCost = dao.allSales().filter { it.status == SaleStatus.ACTIVE }.sumOf { it.costTotal }
            val itemsCost = dao.allSaleItems().filter { i -> dao.getSale(i.saleId)!!.status == SaleStatus.ACTIVE }.sumOf { it.lineCost }
            assertEquals(soldCost, itemsCost)
            dao.allProducts().forEach { p ->
                assertTrue("stok mənfi", p.stock >= 0)
                if (p.stock == 0) assertEquals("boş anbarın dəyəri 0 olmalıdır", 0L, p.stockCost)
                val moves = dao.allStockMoves().filter { it.productId == p.id }.sumOf { it.qtyChange }
                assertEquals("stok hərəkətləri qalığa bərabər olmalıdır", p.stock, moves)
            }
            assertEquals("alış + sayım düzəlişləri = satılanların mayası + anbar dəyəri", bought + corrections, soldCost + stockValue())
        }
    }

    @Test
    fun purchaseCancelRemovesItsExactValue() = runBlocking {
        val id = repo.saveProduct(Product(name = "Y", salePrice = 900))
        val p1 = repo.addPurchase(id, 3, 0, null, 1000, PayMethod.CASH, now, "", totalCost = 1000)
        val p2 = repo.addPurchase(id, 2, 437, null, 874, PayMethod.CASH, now, "")
        assertEquals(1874L, stockValue())
        repo.cancelPurchase(p2, "səhv")
        assertEquals(1000L, stockValue())
        assertEquals(3, dao.getProduct(id)!!.stock)
        repo.cancelPurchase(p1, "səhv")
        assertEquals(0L, stockValue())
        assertEquals(0, dao.getProduct(id)!!.stock)
    }

    @Test
    fun migrationFrom2To3FillsExactValues() {
        val name = "mig23"
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO products (id, name, imagePath, barcode, sku, category, brand, purchasePrice, salePrice, stock, minStock, supplierId, note, createdAt) VALUES (1, 'Qab', NULL, '', '', 'Digər', '', 250, 800, 4, 1, NULL, '', 1)")
            execSQL("INSERT INTO products (id, name, imagePath, barcode, sku, category, brand, purchasePrice, salePrice, stock, minStock, supplierId, note, createdAt) VALUES (2, 'Bitib', NULL, '', '', 'Digər', '', 300, 800, 0, 1, NULL, '', 1)")
            execSQL("INSERT INTO sales (id, createdAt, customerId, customerName, itemCount, subtotal, discount, total, costTotal, profit, paidCash, paidCard, debtAmount, status, cancelReason, cancelledAt, note) VALUES (1, 1, NULL, '', 2, 1600, 0, 1600, 500, 1100, 1600, 0, 0, 'ACTIVE', '', NULL, '')")
            execSQL("INSERT INTO sale_items (id, saleId, productId, productName, quantity, unitPrice, unitCost, lineTotal) VALUES (1, 1, 1, 'Qab', 2, 800, 250, 1600)")
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true, AppDatabase.MIGRATION_2_3).close()
        val db2 = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(1000L, db2.dao().getProduct(1)!!.stockCost)
            assertEquals(0L, db2.dao().getProduct(2)!!.stockCost)
            assertEquals(500L, db2.dao().getSaleItems(1).single().lineCost)
            assertEquals(1000L, db2.dao().observeStockCostValue().first())
        }
        db2.close()
    }

    @Test
    fun oldBackupIsUpgradedOnRestore() = runBlocking {
        val bm = BackupManager(ApplicationProvider.getApplicationContext(), repo)
        val old = BackupData(version = 1,
            products = listOf(Product(id = 1, name = "Köhnə", purchasePrice = 250, salePrice = 500, stock = 4)),
            sales = listOf(Sale(id = 1, createdAt = 1, itemCount = 1, subtotal = 500, discount = 0, total = 500, costTotal = 250,
                profit = 250, paidCash = 500, paidCard = 0, debtAmount = 0)),
            saleItems = listOf(SaleItem(id = 1, saleId = 1, productId = 1, productName = "Köhnə", quantity = 1, unitPrice = 500, unitCost = 250, lineTotal = 500)))
        bm.restore(old, emptyMap())
        assertEquals(1000L, dao.getProduct(1)!!.stockCost)
        assertEquals(250L, dao.getSaleItems(1).single().lineCost)
    }
}
