package az.magaza.pos.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import az.magaza.pos.data.db.*
import az.magaza.pos.util.Dates
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupData(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val products: List<Product> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val sales: List<Sale> = emptyList(),
    val saleItems: List<SaleItem> = emptyList(),
    val cashTransactions: List<CashTransaction> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val customerDebtTx: List<CustomerDebtTx> = emptyList(),
    val supplierTx: List<SupplierTx> = emptyList(),
    val stockMoves: List<StockMove> = emptyList(),
    val cashSessions: List<CashSession> = emptyList(),
    val activityLog: List<ActivityLog> = emptyList(),
) {
    companion object { const val FORMAT = "magaza-pos-backup" }
}

data class BackupSummary(val products: Int, val sales: Int, val customers: Int, val createdAt: Long)

/**
 * ZIP backup: içində data.json (bütün cədvəllər) və images/ (məhsul şəkilləri).
 * Bərpa zamanı yalnız JSON faylı da qəbul edilir.
 */
class BackupManager(private val context: Context, private val repo: PosRepository) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val imagesDir get() = File(context.filesDir, "images").apply { mkdirs() }

    suspend fun snapshot(): BackupData {
        val d = repo.dao
        return repo.db.withTransaction {
            BackupData(
                products = d.allProducts(), customers = d.allCustomers(), suppliers = d.allSuppliers(),
                sales = d.allSales(), saleItems = d.allSaleItems(), cashTransactions = d.allCashTx(),
                expenses = d.allExpenses(), purchases = d.allPurchases(), customerDebtTx = d.allDebtTx(),
                supplierTx = d.allSupplierTx(), stockMoves = d.allStockMoves(), cashSessions = d.allSessions(),
                activityLog = d.allLogs(),
            )
        }
    }

    suspend fun exportZip(out: OutputStream): BackupSummary {
        val data = snapshot()
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            data.products.mapNotNull { it.imagePath }.distinct().forEach { name ->
                val f = File(imagesDir, name)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("images/$name"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return BackupSummary(data.products.size, data.sales.size, data.customers.size, data.createdAt)
    }

    fun exportJson(data: BackupData, out: OutputStream) {
        out.use { it.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)) }
    }

    /** Faylı oxuyub yoxlayır, amma bazaya yazmır. */
    fun readBackup(input: InputStream): Pair<BackupData, Map<String, ByteArray>> {
        val bytes = input.use { it.readBytes() }
        val images = mutableMapOf<String, ByteArray>()
        var data: BackupData? = null
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (isZip) {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    when {
                        e.name == "data.json" -> data = parse(zip.readBytes())
                        e.name.startsWith("images/") && !e.isDirectory -> {
                            val name = File(e.name).name
                            if (name.isNotBlank() && !name.contains("..")) images[name] = zip.readBytes()
                        }
                    }
                    e = zip.nextEntry
                }
            }
        } else {
            data = parse(bytes)
        }
        return (data ?: throw PosException("Backup faylında data.json tapılmadı")) to images
    }

    private fun parse(bytes: ByteArray): BackupData {
        val d = try {
            json.decodeFromString(BackupData.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw PosException("Backup faylı oxunmadı: fayl zədələnib və ya uyğun deyil")
        }
        if (d.format != BackupData.FORMAT) throw PosException("Bu fayl Mağaza POS backup faylı deyil")
        return d
    }

    fun summary(d: BackupData) = BackupSummary(d.products.size, d.sales.size, d.customers.size, d.createdAt)

    /** Bütün mövcud məlumatları backup-dakı məlumatlarla əvəz edir. */
    suspend fun restore(data: BackupData, images: Map<String, ByteArray>) {
        val d = repo.dao
        repo.db.withTransaction {
            clearTables()
            d.putProducts(data.products); d.putCustomers(data.customers); d.putSuppliers(data.suppliers)
            d.putSales(data.sales); d.putSaleItems(data.saleItems); d.putCashTx(data.cashTransactions)
            d.putExpenses(data.expenses); d.putPurchases(data.purchases); d.putDebtTx(data.customerDebtTx)
            d.putSupplierTx(data.supplierTx); d.putStockMoves(data.stockMoves); d.putSessions(data.cashSessions)
            d.putLogs(data.activityLog)
            d.insertLog(ActivityLog(createdAt = System.currentTimeMillis(), action = "Backup bərpa edildi",
                details = "Backup tarixi: ${Dates.dateTime(data.createdAt)}, məhsul: ${data.products.size}, satış: ${data.sales.size}"))
        }
        images.forEach { (name, bytes) -> File(imagesDir, name).writeBytes(bytes) }
    }

    private fun clearTables() {
        val w = repo.db.openHelper.writableDatabase
        listOf(
            "products", "customers", "suppliers", "sales", "sale_items", "cash_transactions", "expenses",
            "purchases", "customer_debt_tx", "supplier_tx", "stock_moves", "cash_sessions", "activity_log",
        ).forEach { w.execSQL("DELETE FROM $it") }
        runCatching { w.execSQL("DELETE FROM sqlite_sequence") }
    }

    suspend fun wipeAll() {
        repo.db.withTransaction {
            clearTables()
            repo.dao.insertLog(ActivityLog(createdAt = System.currentTimeMillis(), action = "Bütün məlumatlar silindi", details = ""))
        }
        imagesDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Backup-ı telefonun "Download/MagazaPOS" qovluğuna yazır. Bu qovluq proqram silinsə belə qalır.
     * @return faylın göstəriləcək yolu
     */
    suspend fun saveToDownloads(prefix: String = "backup"): String {
        val name = "magaza_pos_${prefix}_${Dates.fileStamp()}.zip"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MagazaPOS")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw PosException("Fayl yaradıla bilmədi")
            context.contentResolver.openOutputStream(uri)!!.use { exportZip(it) }
        } else {
            val dir = File(context.getExternalFilesDir(null), "backup").apply { mkdirs() }
            File(dir, name).outputStream().use { exportZip(it) }
            return File(dir, name).absolutePath
        }
        return "Download/MagazaPOS/$name"
    }

    /** Proqramın daxili qovluğunda son 7 avtomatik backup saxlanılır (əlavə qoruma). */
    suspend fun internalAutoBackup() {
        val dir = File(context.filesDir, "auto_backup").apply { mkdirs() }
        File(dir, "auto_${Dates.fileStamp()}.zip").outputStream().use { exportZip(it) }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(7)?.forEach { it.delete() }
    }

    fun internalBackups(): List<File> =
        File(context.filesDir, "auto_backup").listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
}
