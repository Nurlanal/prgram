package az.magaza.pos

import android.app.Application
import az.magaza.pos.data.BackupManager
import az.magaza.pos.data.PosRepository
import az.magaza.pos.data.SettingsStore
import az.magaza.pos.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Sadə asılılıq konteyneri (DI). */
class PosApp : Application() {
    lateinit var repo: PosRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var backup: BackupManager
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        repo = PosRepository(AppDatabase.create(this))
        settings = SettingsStore(this)
        backup = BackupManager(this, repo)
        runAutoBackupIfNeeded()
        az.magaza.pos.notify.DebtReminders.createChannel(this)
        runCatching { az.magaza.pos.notify.DebtReminders.schedule(this) }
    }

    /** Gündə bir dəfə avtomatik backup (daxili qovluq + Download/MagazaPOS). */
    fun runAutoBackupIfNeeded() {
        val s = settings.state.value
        if (!s.autoBackup) return
        if (System.currentTimeMillis() - s.lastAutoBackupAt < 20L * 60 * 60 * 1000) return
        appScope.launch {
            runCatching {
                if (repo.dao.allProducts().isEmpty() && repo.dao.allSales().isEmpty()) return@launch
                backup.internalAutoBackup()
                runCatching { backup.saveToDownloads("auto") }
                settings.markBackup(auto = true)
            }
        }
    }
}
