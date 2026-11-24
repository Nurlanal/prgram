package az.magaza.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import az.magaza.pos.ui.PosNavRoot
import az.magaza.pos.ui.lock.LockScreen
import az.magaza.pos.ui.theme.PosTheme

class MainActivity : ComponentActivity() {
    private val locked = mutableStateOf(false)
    /** Bildirişə toxunduqda açılacaq müştəri. */
    private val openCustomer = mutableStateOf<Long?>(null)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PosApp
        locked.value = app.settings.state.value.pinEnabled && savedInstanceState?.getBoolean("unlocked") != true
        readIntent(intent)
        setContent {
            val settings by app.settings.state.collectAsState()
            PosTheme(settings.themeMode) {
                if (locked.value && settings.pinEnabled) {
                    LockScreen(storeName = settings.storeName, check = { app.settings.checkPin(it) }, onUnlock = { locked.value = false })
                } else {
                    PosNavRoot(openCustomer = openCustomer.value, onCustomerOpened = { openCustomer.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: android.content.Intent?) {
        val id = intent?.getLongExtra(az.magaza.pos.notify.DebtReminders.EXTRA_CUSTOMER, 0L) ?: 0L
        if (id > 0) openCustomer.value = id
        intent?.removeExtra(az.magaza.pos.notify.DebtReminders.EXTRA_CUSTOMER)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unlocked", !locked.value)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val app = application as PosApp
        // Proqramdan 1 dəqiqədən çox çıxıldıqda yenidən PIN tələb olunur.
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 60_000 && app.settings.state.value.pinEnabled) {
            locked.value = true
        }
        app.runAutoBackupIfNeeded()
        runCatching { az.magaza.pos.notify.DebtReminders.checkNow(this) }
    }
}
