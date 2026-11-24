package az.magaza.pos.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest

enum class ThemeMode(val label: String) { SYSTEM("Sistem"), LIGHT("İşıqlı"), DARK("Qaranlıq") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pinEnabled: Boolean = false,
    val storeName: String = "Mağaza",
    val storeAddress: String = "",
    val storePhone: String = "",
    val receiptFooter: String = "Alış-verişiniz üçün təşəkkür edirik!",
    val printerAddress: String = "",
    val printerName: String = "",
    val paperWidth: Int = 58,
    val autoBackup: Boolean = true,
    val lastBackupAt: Long = 0,
    val lastAutoBackupAt: Long = 0,
    val debtReminders: Boolean = true,
    val reminderHour: Int = 10,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state

    private fun read() = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        pinEnabled = prefs.getString("pin", null) != null,
        storeName = prefs.getString("storeName", "Mağaza")!!,
        storeAddress = prefs.getString("storeAddress", "")!!,
        storePhone = prefs.getString("storePhone", "")!!,
        receiptFooter = prefs.getString("receiptFooter", "Alış-verişiniz üçün təşəkkür edirik!")!!,
        printerAddress = prefs.getString("printerAddress", "")!!,
        printerName = prefs.getString("printerName", "")!!,
        paperWidth = prefs.getInt("paperWidth", 58),
        autoBackup = prefs.getBoolean("autoBackup", true),
        lastBackupAt = prefs.getLong("lastBackupAt", 0),
        lastAutoBackupAt = prefs.getLong("lastAutoBackupAt", 0),
        debtReminders = prefs.getBoolean("debtReminders", true),
        reminderHour = prefs.getInt("reminderHour", 10),
    )

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = read()
    }

    fun setTheme(m: ThemeMode) = edit { putString("theme", m.name) }
    fun setStoreInfo(name: String, address: String, phone: String, footer: String) = edit {
        putString("storeName", name.trim().ifBlank { "Mağaza" })
        putString("storeAddress", address.trim())
        putString("storePhone", phone.trim())
        putString("receiptFooter", footer.trim())
    }
    fun setPrinter(address: String, name: String) = edit { putString("printerAddress", address); putString("printerName", name) }
    fun setPaperWidth(w: Int) = edit { putInt("paperWidth", w) }
    fun setAutoBackup(on: Boolean) = edit { putBoolean("autoBackup", on) }
    fun setDebtReminders(on: Boolean) = edit { putBoolean("debtReminders", on) }
    fun setReminderHour(h: Int) = edit { putInt("reminderHour", h.coerceIn(0, 23)) }

    /** Eyni borc üçün eyni gündə yalnız bir bildiriş göndərmək üçün. */
    fun wasReminded(key: String): Boolean = prefs.getStringSet("reminded", emptySet())!!.contains(key)
    fun markReminded(keys: Collection<String>, today: Long) {
        val keep = prefs.getStringSet("reminded", emptySet())!!.filter { it.substringAfter('@').toLongOrNull() == today }
        prefs.edit().putStringSet("reminded", (keep + keys).toSet()).apply()
    }
    fun markBackup(auto: Boolean) = edit {
        val now = System.currentTimeMillis()
        putLong("lastBackupAt", now)
        if (auto) putLong("lastAutoBackupAt", now)
    }

    fun setPin(pin: String?) = edit {
        if (pin == null) remove("pin") else putString("pin", hash(pin))
    }

    fun checkPin(pin: String): Boolean = prefs.getString("pin", null) == hash(pin)

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(("magaza-pos:" + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }
}
