package az.magaza.pos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PosDao {

    // ---------------- Məhsullar ----------------
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeProduct(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProduct(id: Long): Product?

    @Query("SELECT * FROM products WHERE barcode = :code AND barcode != '' LIMIT 1")
    suspend fun findByBarcode(code: String): Product?

    @Query("SELECT * FROM products WHERE (barcode = :code OR sku = :code) AND :code != '' AND id != :exceptId LIMIT 1")
    suspend fun findDuplicateCode(code: String, exceptId: Long): Product?

    @Insert
    suspend fun insertProduct(p: Product): Long

    @Update
    suspend fun updateProduct(p: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    @Query("SELECT COALESCE(SUM(stock),0) FROM products WHERE stock > 0")
    fun observeTotalStock(): Flow<Long>

    @Query("SELECT COALESCE(SUM(stock * purchasePrice),0) FROM products WHERE stock > 0")
    fun observeStockCostValue(): Flow<Long>

    @Query("SELECT COALESCE(SUM(stock * salePrice),0) FROM products WHERE stock > 0")
    fun observeStockSaleValue(): Flow<Long>

    @Query("SELECT COUNT(*) FROM products WHERE stock <= minStock")
    fun observeLowStockCount(): Flow<Int>

    // ---------------- Müştərilər ----------------
    @Query(
        """
        SELECT c.id, c.name, c.phone, c.note, c.createdAt,
          COALESCE((SELECT SUM(amount) FROM customer_debt_tx d WHERE d.customerId = c.id),0) AS debt,
          COALESCE((SELECT -SUM(amount) FROM customer_debt_tx d WHERE d.customerId = c.id AND d.type = 'PAYMENT'),0) AS paid,
          (SELECT MIN(createdAt) FROM customer_debt_tx d WHERE d.customerId = c.id AND d.amount > 0) AS firstDebtAt
        FROM customers c ORDER BY c.name COLLATE NOCASE
        """
    )
    fun observeCustomersWithDebt(): Flow<List<CustomerWithDebt>>

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun observeCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observeCustomer(id: Long): Flow<Customer?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomer(id: Long): Customer?

    @Insert
    suspend fun insertCustomer(c: Customer): Long

    @Update
    suspend fun updateCustomer(c: Customer)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: Long)

    @Query("SELECT COALESCE(SUM(amount),0) FROM customer_debt_tx WHERE customerId = :id")
    suspend fun customerDebt(id: Long): Long

    @Query("SELECT COALESCE(SUM(amount),0) FROM customer_debt_tx WHERE customerId = :id")
    fun observeCustomerDebt(id: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(b),0) FROM (SELECT SUM(amount) AS b FROM customer_debt_tx GROUP BY customerId) WHERE b > 0")
    fun observeTotalCustomerDebt(): Flow<Long>

    @Query("SELECT * FROM customer_debt_tx WHERE customerId = :id ORDER BY createdAt DESC, id DESC")
    fun observeDebtTx(id: Long): Flow<List<CustomerDebtTx>>

    @Query("SELECT * FROM customer_debt_tx WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    suspend fun debtTxInRange(from: Long, to: Long): List<CustomerDebtTx>

    @Insert
    suspend fun insertDebtTx(t: CustomerDebtTx): Long

    // ---------------- Təchizatçılar ----------------
    @Query(
        """
        SELECT s.id, s.name, s.phone, s.note, s.createdAt,
          COALESCE((SELECT SUM(amount) FROM supplier_tx t WHERE t.supplierId = s.id),0) AS debt
        FROM suppliers s ORDER BY s.name COLLATE NOCASE
        """
    )
    fun observeSuppliersWithDebt(): Flow<List<SupplierWithDebt>>

    @Query("SELECT * FROM suppliers ORDER BY name COLLATE NOCASE")
    fun observeSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getSupplier(id: Long): Supplier?

    @Query("SELECT * FROM suppliers WHERE id = :id")
    fun observeSupplier(id: Long): Flow<Supplier?>

    @Insert
    suspend fun insertSupplier(s: Supplier): Long

    @Update
    suspend fun updateSupplier(s: Supplier)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun deleteSupplier(id: Long)

    @Query("SELECT COALESCE(SUM(amount),0) FROM supplier_tx WHERE supplierId = :id")
    suspend fun supplierDebt(id: Long): Long

    @Query("SELECT COALESCE(SUM(amount),0) FROM supplier_tx WHERE supplierId = :id")
    fun observeSupplierDebt(id: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(b),0) FROM (SELECT SUM(amount) AS b FROM supplier_tx GROUP BY supplierId) WHERE b > 0")
    fun observeTotalSupplierDebt(): Flow<Long>

    @Query("SELECT * FROM supplier_tx WHERE supplierId = :id ORDER BY createdAt DESC, id DESC")
    fun observeSupplierTx(id: Long): Flow<List<SupplierTx>>

    @Insert
    suspend fun insertSupplierTx(t: SupplierTx): Long

    // ---------------- Satışlar ----------------
    @Insert
    suspend fun insertSale(s: Sale): Long

    @Update
    suspend fun updateSale(s: Sale)

    @Insert
    suspend fun insertSaleItems(items: List<SaleItem>)

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSale(id: Long): Sale?

    @Query("SELECT * FROM sales WHERE id = :id")
    fun observeSale(id: Long): Flow<Sale?>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItems(saleId: Long): List<SaleItem>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun observeSaleItems(saleId: Long): Flow<List<SaleItem>>

    @Query("SELECT * FROM sale_items WHERE saleId IN (:ids)")
    suspend fun itemsForSales(ids: List<Long>): List<SaleItem>

    @Query("SELECT * FROM sales WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    fun observeSalesInRange(from: Long, to: Long): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    suspend fun salesInRange(from: Long, to: Long): List<Sale>

    @Query("SELECT * FROM sales WHERE customerId = :id ORDER BY createdAt DESC")
    fun observeCustomerSales(id: Long): Flow<List<Sale>>

    @Query("SELECT COALESCE(SUM(total),0) FROM sales WHERE status = 'ACTIVE' AND createdAt BETWEEN :from AND :to")
    fun observeSalesTotal(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(profit),0) FROM sales WHERE status = 'ACTIVE' AND createdAt BETWEEN :from AND :to")
    fun observeProfitTotal(from: Long, to: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM sales WHERE status = 'ACTIVE' AND createdAt BETWEEN :from AND :to")
    fun observeSalesCount(from: Long, to: Long): Flow<Int>

    @Query(
        """SELECT COALESCE(SUM(i.quantity),0) FROM sale_items i JOIN sales s ON s.id = i.saleId
           WHERE s.status = 'ACTIVE' AND s.createdAt BETWEEN :from AND :to"""
    )
    fun observeItemsSold(from: Long, to: Long): Flow<Long>

    @Query(
        """SELECT i.productId AS productId, i.productName AS productName, SUM(i.quantity) AS qty,
                  SUM(i.lineTotal) AS revenue, SUM(i.unitCost * i.quantity) AS cost
           FROM sale_items i JOIN sales s ON s.id = i.saleId
           WHERE s.status = 'ACTIVE' AND s.createdAt BETWEEN :from AND :to
           GROUP BY i.productId, i.productName ORDER BY revenue DESC"""
    )
    suspend fun productSales(from: Long, to: Long): List<ProductSalesRow>

    @Query(
        """SELECT i.productId AS productId, i.productName AS productName, SUM(i.quantity) AS qty,
                  SUM(i.lineTotal) AS revenue, SUM(i.unitCost * i.quantity) AS cost
           FROM sale_items i JOIN sales s ON s.id = i.saleId
           WHERE s.status = 'ACTIVE' AND s.createdAt BETWEEN :from AND :to
           GROUP BY i.productId, i.productName ORDER BY qty DESC LIMIT :limit"""
    )
    fun observeTopProducts(from: Long, to: Long, limit: Int): Flow<List<ProductSalesRow>>

    @Query(
        """SELECT s.id AS saleId, s.createdAt AS createdAt, i.quantity AS quantity, i.unitPrice AS unitPrice,
                  i.lineTotal AS lineTotal, s.status AS status
           FROM sale_items i JOIN sales s ON s.id = i.saleId
           WHERE i.productId = :productId ORDER BY s.createdAt DESC"""
    )
    fun observeProductSales(productId: Long): Flow<List<SaleItemWithSale>>

    // ---------------- Kassa ----------------
    @Insert
    suspend fun insertCashTx(t: CashTransaction): Long

    @Query("SELECT * FROM cash_transactions WHERE refId = :refId AND type = :type")
    suspend fun cashTxFor(refId: Long, type: String): List<CashTransaction>

    @Query("SELECT COALESCE(SUM(amount),0) FROM cash_transactions")
    fun observeCashBalance(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount),0) FROM cash_transactions WHERE method = :method")
    fun observeCashBalanceBy(method: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount),0) FROM cash_transactions WHERE method = :method")
    suspend fun cashBalanceBy(method: String): Long

    @Query("SELECT COALESCE(SUM(amount),0) FROM cash_transactions WHERE createdAt < :before")
    suspend fun cashBalanceBefore(before: Long): Long

    @Query("SELECT COALESCE(SUM(amount),0) FROM cash_transactions WHERE method = :method AND createdAt < :before")
    suspend fun cashBalanceBeforeBy(method: String, before: Long): Long

    @Query("SELECT * FROM cash_transactions WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC, id DESC")
    fun observeCashTxInRange(from: Long, to: Long): Flow<List<CashTransaction>>

    @Query(
        """SELECT type, method, COALESCE(SUM(amount),0) AS total FROM cash_transactions
           WHERE createdAt BETWEEN :from AND :to GROUP BY type, method"""
    )
    suspend fun cashSumsByType(from: Long, to: Long): List<TypeMethodSum>

    @Query(
        """SELECT type, method, COALESCE(SUM(amount),0) AS total FROM cash_transactions
           WHERE createdAt BETWEEN :from AND :to GROUP BY type, method"""
    )
    fun observeCashSumsByType(from: Long, to: Long): Flow<List<TypeMethodSum>>

    @Insert
    suspend fun insertSession(s: CashSession): Long

    @Update
    suspend fun updateSession(s: CashSession)

    @Query("SELECT * FROM cash_sessions WHERE closedAt IS NULL ORDER BY openedAt DESC LIMIT 1")
    fun observeOpenSession(): Flow<CashSession?>

    @Query("SELECT * FROM cash_sessions WHERE closedAt IS NULL ORDER BY openedAt DESC LIMIT 1")
    suspend fun openSession(): CashSession?

    @Query("SELECT * FROM cash_sessions ORDER BY openedAt DESC LIMIT 30")
    fun observeSessions(): Flow<List<CashSession>>

    // ---------------- Məxaric ----------------
    @Insert
    suspend fun insertExpense(e: Expense): Long

    @Update
    suspend fun updateExpense(e: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpense(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC, id DESC")
    fun observeExpensesInRange(from: Long, to: Long): Flow<List<Expense>>

    @Query("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE cancelled = 0 AND createdAt BETWEEN :from AND :to")
    fun observeExpenseTotal(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE cancelled = 0 AND createdAt BETWEEN :from AND :to")
    suspend fun expenseTotal(from: Long, to: Long): Long

    @Query(
        """SELECT category, COALESCE(SUM(amount),0) AS total FROM expenses
           WHERE cancelled = 0 AND createdAt BETWEEN :from AND :to GROUP BY category ORDER BY total DESC"""
    )
    suspend fun expenseByCategory(from: Long, to: Long): List<CategorySum>

    // ---------------- Mədaxil ----------------
    @Insert
    suspend fun insertPurchase(p: Purchase): Long

    @Update
    suspend fun updatePurchase(p: Purchase)

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getPurchase(id: Long): Purchase?

    @Query("SELECT * FROM purchases WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC, id DESC")
    fun observePurchasesInRange(from: Long, to: Long): Flow<List<Purchase>>

    @Query("SELECT COALESCE(SUM(total),0) FROM purchases WHERE cancelled = 0 AND createdAt BETWEEN :from AND :to")
    fun observePurchaseTotal(from: Long, to: Long): Flow<Long>

    // ---------------- Stok hərəkətləri ----------------
    @Insert
    suspend fun insertStockMove(m: StockMove): Long

    @Query("SELECT * FROM stock_moves WHERE productId = :id ORDER BY createdAt DESC, id DESC LIMIT 200")
    fun observeStockMoves(id: Long): Flow<List<StockMove>>

    // ---------------- Əməliyyat jurnalı ----------------
    @Insert
    suspend fun insertLog(l: ActivityLog): Long

    @Query("SELECT * FROM activity_log ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeLog(limit: Int): Flow<List<ActivityLog>>

    // ---------------- Backup üçün ----------------
    @Query("SELECT * FROM products") suspend fun allProducts(): List<Product>
    @Query("SELECT * FROM customers") suspend fun allCustomers(): List<Customer>
    @Query("SELECT * FROM suppliers") suspend fun allSuppliers(): List<Supplier>
    @Query("SELECT * FROM sales") suspend fun allSales(): List<Sale>
    @Query("SELECT * FROM sale_items") suspend fun allSaleItems(): List<SaleItem>
    @Query("SELECT * FROM cash_transactions") suspend fun allCashTx(): List<CashTransaction>
    @Query("SELECT * FROM expenses") suspend fun allExpenses(): List<Expense>
    @Query("SELECT * FROM purchases") suspend fun allPurchases(): List<Purchase>
    @Query("SELECT * FROM customer_debt_tx") suspend fun allDebtTx(): List<CustomerDebtTx>
    @Query("SELECT * FROM supplier_tx") suspend fun allSupplierTx(): List<SupplierTx>
    @Query("SELECT * FROM stock_moves") suspend fun allStockMoves(): List<StockMove>
    @Query("SELECT * FROM cash_sessions") suspend fun allSessions(): List<CashSession>
    @Query("SELECT * FROM activity_log") suspend fun allLogs(): List<ActivityLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProducts(l: List<Product>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCustomers(l: List<Customer>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSuppliers(l: List<Supplier>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSales(l: List<Sale>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSaleItems(l: List<SaleItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCashTx(l: List<CashTransaction>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putExpenses(l: List<Expense>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPurchases(l: List<Purchase>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDebtTx(l: List<CustomerDebtTx>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSupplierTx(l: List<SupplierTx>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putStockMoves(l: List<StockMove>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSessions(l: List<CashSession>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLogs(l: List<ActivityLog>)
}
