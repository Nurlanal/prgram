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
    version = 2,
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

        val MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                // Məlumat itkisinin qarşısını almaq üçün destructive migration İSTİFADƏ EDİLMİR.
                .build()
    }
}
