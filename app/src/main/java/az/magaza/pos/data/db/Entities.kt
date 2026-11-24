package az.magaza.pos.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Bütün pul məbləğləri qəpiklə (Long) saxlanılır: 1 AZN = 100 qəpik.
 * Bu, kəsr xətalarının qarşısını alır.
 */

@Serializable
@Entity(tableName = "products", indices = [Index("barcode"), Index("name"), Index("category")])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val imagePath: String? = null,
    val barcode: String = "",
    val sku: String = "",
    val category: String = "Digər",
    val brand: String = "",
    val purchasePrice: Long = 0,
    val salePrice: Long = 0,
    val stock: Int = 0,
    val minStock: Int = 0,
    val supplierId: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Anbardakı bütün ədədlərin DƏQİQ alış dəyəri (qəpiklə). Anbar dəyəri bu sahədən hesablanır,
     * "stok × orta qiymət" kimi yuvarlaqlaşdırılmış hasildən yox. purchasePrice isə göstərmək üçün orta qiymətdir.
     */
    @ColumnInfo(defaultValue = "0") val stockCost: Long = 0,
)

@Serializable
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

object SaleStatus {
    const val ACTIVE = "ACTIVE"
    const val CANCELLED = "CANCELLED"
}

@Serializable
@Entity(tableName = "sales", indices = [Index("createdAt"), Index("customerId")])
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val customerId: Long? = null,
    val customerName: String = "",
    val itemCount: Int,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val costTotal: Long,
    val profit: Long,
    val paidCash: Long,
    val paidCard: Long,
    val debtAmount: Long,
    val status: String = SaleStatus.ACTIVE,
    val cancelReason: String = "",
    val cancelledAt: Long? = null,
    val note: String = "",
)

@Serializable
@Entity(tableName = "sale_items", indices = [Index("saleId"), Index("productId")])
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long,
    val unitCost: Long,
    val lineTotal: Long,
    /** Satılan ədədlərin dəqiq maya dəyəri (anbardan çıxan qəpiklər). */
    @ColumnInfo(defaultValue = "0") val lineCost: Long = 0,
)

object PayMethod {
    const val CASH = "CASH"
    const val CARD = "CARD"
}

/** Kassa əməliyyatının növləri. Kassa tarixçəsi heç vaxt silinmir; ləğvlər əks yazılışla edilir. */
object CashType {
    const val SALE = "SALE"
    const val SALE_CANCEL = "SALE_CANCEL"
    const val DEBT_PAYMENT = "DEBT_PAYMENT"
    const val EXPENSE = "EXPENSE"
    const val EXPENSE_CANCEL = "EXPENSE_CANCEL"
    const val PURCHASE = "PURCHASE"
    const val PURCHASE_CANCEL = "PURCHASE_CANCEL"
    const val SUPPLIER_PAYMENT = "SUPPLIER_PAYMENT"
    const val CASH_IN = "CASH_IN"
    const val CASH_OUT = "CASH_OUT"
    const val ADJUSTMENT = "ADJUSTMENT"
}

@Serializable
@Entity(tableName = "cash_transactions", indices = [Index("createdAt"), Index("type")])
data class CashTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val type: String,
    val method: String = PayMethod.CASH,
    /** İşarəli məbləğ: + kassaya daxil olur, − kassadan çıxır. */
    val amount: Long,
    val refId: Long? = null,
    val note: String = "",
)

@Serializable
@Entity(tableName = "expenses", indices = [Index("createdAt")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val amount: Long,
    val category: String,
    val description: String = "",
    val method: String = PayMethod.CASH,
    val note: String = "",
    val cancelled: Boolean = false,
)

object PurchaseStatus {
    const val PAID = "PAID"
    const val DEBT = "DEBT"
    const val PARTIAL = "PARTIAL"
}

@Serializable
@Entity(tableName = "purchases", indices = [Index("createdAt"), Index("productId")])
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitCost: Long,
    val total: Long,
    val supplierId: Long? = null,
    val supplierName: String = "",
    val paymentStatus: String,
    val paidAmount: Long,
    val method: String = PayMethod.CASH,
    val note: String = "",
    val cancelled: Boolean = false,
)

object DebtType {
    const val SALE = "SALE"
    const val MANUAL = "MANUAL"
    const val PAYMENT = "PAYMENT"
    const val CANCEL = "CANCEL"
}

@Serializable
@Entity(tableName = "customer_debt_tx", indices = [Index("customerId"), Index("createdAt")])
data class CustomerDebtTx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val createdAt: Long,
    val type: String,
    /** + borcu artırır, − borcu azaldır. */
    val amount: Long,
    val saleId: Long? = null,
    val method: String? = null,
    val note: String = "",
    /** Borcun ödənilməli olduğu gün (yalnız + borc yazılışları üçün). null — tarix təyin edilməyib. */
    val dueAt: Long? = null,
)

object SupplierTxType {
    const val PURCHASE = "PURCHASE"
    const val PAYMENT = "PAYMENT"
    const val CANCEL = "CANCEL"
}

@Serializable
@Entity(tableName = "supplier_tx", indices = [Index("supplierId")])
data class SupplierTx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val createdAt: Long,
    val type: String,
    /** + mağazanın təchizatçıya borcu artır, − azalır. */
    val amount: Long,
    val purchaseId: Long? = null,
    val note: String = "",
)

object StockType {
    const val INITIAL = "INITIAL"
    const val SALE = "SALE"
    const val SALE_CANCEL = "SALE_CANCEL"
    const val PURCHASE = "PURCHASE"
    const val PURCHASE_CANCEL = "PURCHASE_CANCEL"
    const val ADJUST = "ADJUST"
}

@Serializable
@Entity(tableName = "stock_moves", indices = [Index("productId"), Index("createdAt")])
data class StockMove(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val createdAt: Long,
    val type: String,
    val qtyChange: Int,
    val stockAfter: Int,
    val refId: Long? = null,
    val note: String = "",
)

@Serializable
@Entity(tableName = "cash_sessions")
data class CashSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val openedAt: Long,
    val openingBalance: Long,
    val closedAt: Long? = null,
    val expectedBalance: Long? = null,
    val countedBalance: Long? = null,
    val note: String = "",
)

@Serializable
@Entity(tableName = "activity_log", indices = [Index("createdAt")])
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val action: String,
    val details: String,
)

// ---- Sorğu nəticələri (entity deyil) ----

data class CustomerWithDebt(
    val id: Long,
    val name: String,
    val phone: String,
    val note: String,
    val createdAt: Long,
    val debt: Long,
    val paid: Long,
    val firstDebtAt: Long?,
)

data class SupplierWithDebt(
    val id: Long,
    val name: String,
    val phone: String,
    val note: String,
    val createdAt: Long,
    val debt: Long,
)

data class ProductSalesRow(
    val productId: Long,
    val productName: String,
    val qty: Long,
    val revenue: Long,
    val cost: Long,
)

data class SaleItemWithSale(
    val saleId: Long,
    val createdAt: Long,
    val quantity: Int,
    val unitPrice: Long,
    val lineTotal: Long,
    val status: String,
)

data class TypeMethodSum(
    val type: String,
    val method: String,
    val total: Long,
)

data class CategorySum(
    val category: String,
    val total: Long,
)
