package az.magaza.pos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Product::class, Customer::class, Supplier::class, Sale::class, SaleItem::class,
        CashTransaction::class, Expense::class, Purchase::class, CustomerDebtTx::class,
        SupplierTx::class, StockMove::class, CashSession::class, ActivityLog::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PosDao

    companion object {
        const val NAME = "magaza_pos.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // Məlumat itkisinin qarşısını almaq üçün destructive migration İSTİFADƏ EDİLMİR.
                .build()
    }
}
