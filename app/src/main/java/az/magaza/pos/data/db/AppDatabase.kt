package az.magaza.pos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Product::class, Customer::class, Supplier::class, Sale::class, SaleItem::class,
        CashTransaction::class, Expense::class, Purchase::class, CustomerDebtTx::class,
        SupplierTx::class, StockMove::class, CashSession::class, ActivityLog::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PosDao

    companion object {
        const val NAME = "magaza_pos.db"

        /** v1 → v2: borclara ödəniş tarixi əlavə edildi. Mövcud məlumatlar toxunulmaz qalır. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customer_debt_tx ADD COLUMN dueAt INTEGER")
            }
        }

        /** v2 → v3: dəqiq anbar dəyəri (stockCost) və satış sətrinin dəqiq mayası (lineCost). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN stockCost INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE products SET stockCost = stock * purchasePrice WHERE stock > 0")
                db.execSQL("ALTER TABLE sale_items ADD COLUMN lineCost INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE sale_items SET lineCost = unitCost * quantity")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                // Məlumat itkisinin qarşısını almaq üçün destructive migration İSTİFADƏ EDİLMİR.
                .build()
    }
}
