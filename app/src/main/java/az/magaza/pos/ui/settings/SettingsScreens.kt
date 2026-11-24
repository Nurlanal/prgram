package az.magaza.pos.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import az.magaza.pos.data.BackupData
import az.magaza.pos.data.ThemeMode
import az.magaza.pos.ui.Routes
import az.magaza.pos.ui.components.*
import az.magaza.pos.util.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val app = app()
    val ctx = LocalContext.current
    val runner = rememberRunner()
    val s by app.settings.state.collectAsState()
    var storeName by remember { mutableStateOf(s.storeName) }
    var address by remember { mutableStateOf(s.storeAddress) }
    var phone by remember { mutableStateOf(s.storePhone) }
    var footer by remember { mutableStateOf(s.receiptFooter) }
    var pinDialog by remember { mutableStateOf(false) }
    var printers by remember { mutableStateOf<List<PrinterDevice>?>(null) }

    val btPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) printers = BluetoothPrinter.pairedDevices(ctx) else runner.message("Bluetooth icazəsi verilmədi")
    }
    fun choosePrinter() {
        if (!BluetoothPrinter.isBluetoothOn(ctx)) { runner.message("Bluetooth-u yandırın və printeri telefonun ayarlarında cütləşdirin"); return }
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            btPerm.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else printers = BluetoothPrinter.pairedDevices(ctx)
    }
    val receipt = rememberReceiptActions(runner)

    Scaffold(topBar = { PosTopBar("Ayarlar", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Görünüş")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(s.themeMode == m, { app.settings.setTheme(m) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(m.label) }
                }
            }

            SectionTitle("Mağaza məlumatları (çekdə görünür)")
            TextInput(storeName, { storeName = it }, "Mağaza adı")
            TextInput(address, { address = it }, "Ünvan")
            TextInput(phone, { phone = it }, "Telefon", keyboardType = KeyboardType.Phone)
            TextInput(footer, { footer = it }, "Çekin sonunda mətn")
            Button(onClick = { app.settings.setStoreInfo(storeName, address, phone, footer); runner.message("Yadda saxlanıldı") }) { Text("Yadda saxla") }

            SectionTitle("Çek printeri (Bluetooth)")
            PosCard {
                InfoRow("Printer", s.printerName.ifBlank { "Seçilməyib" })
                Text("Kağız eni", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(58, 80).forEachIndexed { i, w ->
                        SegmentedButton(s.paperWidth == w, { app.settings.setPaperWidth(w) }, SegmentedButtonDefaults.itemShape(i, 2)) { Text("$w mm") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::choosePrinter, Modifier.weight(1f)) { Text("Printer seç") }
                    OutlinedButton(enabled = s.printerAddress.isNotBlank(), modifier = Modifier.weight(1f), onClick = {
                        val now = System.currentTimeMillis()
                        receipt.print(
                            az.magaza.pos.data.db.Sale(id = 0, createdAt = now, itemCount = 1, subtotal = 100, discount = 0, total = 100,
                                costTotal = 0, profit = 100, paidCash = 100, paidCard = 0, debtAmount = 0),
                            listOf(az.magaza.pos.data.db.SaleItem(saleId = 0, productId = 0, productName = "Test çapı", quantity = 1,
                                unitPrice = 100, unitCost = 0, lineTotal = 100)),
                        )
                    }) { Text("Test çapı") }
                }
                Text("ESC/POS dəstəkləyən 58 mm və 80 mm printerlər üçündür. Printeri əvvəlcə telefonun Bluetooth ayarlarında cütləşdirin.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle("Borc xatırlatmaları")
            val askNotif = rememberNotificationPermission()
            val notifOk = az.magaza.pos.notify.DebtReminders.canNotify(ctx)
            PosCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ödəniş günü bildiriş göndər", style = MaterialTheme.typography.titleSmall)
                        Text("Borcun ödəniş günü gələndə və gecikdikcə hər gün bir dəfə", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(s.debtReminders, { app.settings.setDebtReminders(it); if (it) askNotif() })
                }
                if (s.debtReminders) {
                    Spacer(Modifier.height(6.dp))
                    Text("Bildiriş saatı (bu saatdan sonra)", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(8, 9, 10, 11, 12, 14, 16, 18).forEach { h ->
                            FilterChip(s.reminderHour == h, { app.settings.setReminderHour(h) }, { Text("%02d:00".format(h)) })
                        }
                    }
                    if (!notifOk) {
                        Text("Telefonda bu proqram üçün bildirişlər bağlıdır.", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            askNotif()
                            runCatching {
                                ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Text("Bildirişlərə icazə ver") }
                    }
                    TextButton(onClick = { az.magaza.pos.notify.DebtReminders.checkNow(ctx); runner.message("Borclar yoxlanılır") }) {
                        Text("İndi yoxla")
                    }
                }
            }

            SectionTitle("Təhlükəsizlik")
            PosCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("PIN kod ilə qoruma", style = MaterialTheme.typography.titleSmall)
                        Text(if (s.pinEnabled) "Aktivdir" else "Söndürülüb", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = s.pinEnabled, onCheckedChange = { on ->
                        if (on) pinDialog = true else { app.settings.setPin(null); runner.message("PIN kod söndürüldü") }
                    })
                }
                if (s.pinEnabled) TextButton(onClick = { pinDialog = true }) { Text("PIN kodu dəyiş") }
            }

            SectionTitle("Digər")
            PosCard(onClick = { nav.navigate(Routes.BACKUP) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Backup, null); Spacer(Modifier.width(12.dp))
                    Text("Backup, bərpa və məlumatların silinməsi", Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null)
                }
            }
            PosCard(onClick = { nav.navigate(Routes.LOG) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null); Spacer(Modifier.width(12.dp))
                    Text("Əməliyyat tarixçəsi", Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null)
                }
            }
            Text("Mağaza POS v1.3.1 · Tam offline işləyir, internet tələb etmir.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (pinDialog) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text("PIN kod təyin et") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(p1, { p1 = it.filter(Char::isDigit).take(8) }, label = { Text("Yeni PIN (4-8 rəqəm)") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    OutlinedTextField(p2, { p2 = it.filter(Char::isDigit).take(8) }, label = { Text("PIN-i təkrarlayın") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    if (p2.isNotEmpty() && p1 != p2) Text("PIN kodlar eyni deyil", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(enabled = p1.length >= 4 && p1 == p2, onClick = {
                    app.settings.setPin(p1); pinDialog = false; runner.message("PIN kod təyin edildi")
                }) { Text("Yadda saxla") }
            },
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text("İmtina") } },
        )
    }
    printers?.let { list ->
        AlertDialog(
            onDismissRequest = { printers = null },
            title = { Text("Cütləşdirilmiş cihazlar") },
            text = {
                if (list.isEmpty()) Text("Cütləşdirilmiş Bluetooth cihazı tapılmadı. Printeri telefonun Bluetooth ayarlarında cütləşdirin.")
                else LazyColumn { items(list) { d ->
                    ListItem(headlineContent = { Text(d.name) }, supportingContent = { Text(d.address) },
                        leadingContent = { Icon(Icons.Default.Print, null) },
                        trailingContent = { TextButton(onClick = { app.settings.setPrinter(d.address, d.name); printers = null }) { Text("Seç") } })
                } }
            },
            confirmButton = { TextButton(onClick = { printers = null }) { Text("Bağla") } },
        )
    }
}

@Composable
fun BackupScreen(nav: NavController) {
    val app = app()
    val ctx = LocalContext.current
    val runner = rememberRunner()
    val s by app.settings.state.collectAsState()
    var pendingRestore by remember { mutableStateOf<Pair<BackupData, Map<String, ByteArray>>?>(null) }
    var wipe by remember { mutableStateOf(false) }
    var internal by remember { mutableStateOf(app.backup.internalBackups()) }

    val createZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runner.run(success = "Backup faylı yaradıldı") {
            ctx.contentResolver.openOutputStream(uri)!!.use { app.backup.exportZip(it) }
            app.settings.markBackup(auto = false)
        }
    }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runner.run(success = "JSON backup yaradıldı (şəkillərsiz)") {
            app.backup.exportJson(app.backup.snapshot(), ctx.contentResolver.openOutputStream(uri)!!)
            app.settings.markBackup(auto = false)
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runner.run {
            pendingRestore = app.backup.readBackup(ctx.contentResolver.openInputStream(uri)!!)
        }
    }

    Scaffold(topBar = { PosTopBar("Backup və bərpa", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Info, null); Spacer(Modifier.width(10.dp))
                    Text("Bütün məlumatlar yalnız bu telefonda saxlanılır. Telefon dəyişdikdə, sıfırlandıqda və ya proqram silindikdə " +
                        "məlumatlar itməsin deyə müntəzəm backup çıxarın və faylı başqa yerdə (kompüter, flaş, digər telefon) saxlayın.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            InfoRow("Son backup", if (s.lastBackupAt > 0) Dates.dateTime(s.lastBackupAt) else "Hələ yoxdur")

            SectionTitle("Backup yarat")
            BigButton("Backup faylı yarat (ZIP)", Icons.Default.Backup, { createZip.launch("magaza_pos_backup_${Dates.fileStamp()}.zip") }, Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                runner.run(success = null) {
                    val path = app.backup.saveToDownloads("manual")
                    app.settings.markBackup(auto = false)
                    runner.message("Saxlanıldı: $path")
                }
            }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Yükləmələr (Download) qovluğuna saxla") }
            OutlinedButton(onClick = {
                runner.run {
                    val f = File(ctx.cacheDir, "magaza_pos_backup_${Dates.fileStamp()}.zip")
                    f.outputStream().use { app.backup.exportZip(it) }
                    app.settings.markBackup(auto = false)
                    Share.file(ctx, f, "application/zip", "Backup-ı göndər")
                }
            }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Backup-ı paylaş (WhatsApp, Telegram, e-poçt...)") }
            OutlinedButton(onClick = { createJson.launch("magaza_pos_backup_${Dates.fileStamp()}.json") }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DataObject, null); Spacer(Modifier.width(8.dp)); Text("JSON formatında export")
            }

            PosCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Avtomatik gündəlik backup", style = MaterialTheme.typography.titleSmall)
                        Text("Gündə bir dəfə Download/MagazaPOS qovluğuna və proqramın daxili yaddaşına (son 7 nüsxə)",
                            style = MaterialTheme.typography.bodySmall)
                        if (s.lastAutoBackupAt > 0) Text("Sonuncu: ${Dates.dateTime(s.lastAutoBackupAt)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(s.autoBackup, { app.settings.setAutoBackup(it) })
                }
            }

            SectionTitle("Bərpa et")
            BigButton("Backup faylından bərpa et", Icons.Default.Restore, { open.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) },
                Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors())
            if (internal.isNotEmpty()) {
                Text("Daxili avtomatik nüsxələr:", style = MaterialTheme.typography.labelLarge)
                internal.forEach { f ->
                    ListItem(
                        headlineContent = { Text(Dates.dateTime(f.lastModified())) },
                        supportingContent = { Text("${f.length() / 1024} KB") },
                        trailingContent = { TextButton(onClick = { runner.run { pendingRestore = app.backup.readBackup(f.inputStream()) } }) { Text("Bərpa et") } },
                    )
                }
            }

            SectionTitle("Təhlükəli zona")
            OutlinedButton(onClick = { wipe = true }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text("Bütün məlumatları sil")
            }
        }
    }

    pendingRestore?.let { (data, images) ->
        val sum = app.backup.summary(data)
        ConfirmDialog(
            "Bərpa edilsin?",
            "Backup tarixi: ${Dates.dateTime(sum.createdAt)}\nMəhsul: ${sum.products}, satış: ${sum.sales}, müştəri: ${sum.customers}\n\n" +
                "DİQQƏT: Hazırkı bütün məlumatlar silinib backup-dakı məlumatlarla əvəz ediləcək. Əvvəlcə hazırkı məlumatların backup-ı avtomatik saxlanılacaq.",
            "Bərpa et", destructive = true,
            onConfirm = {
                runner.run(success = "Məlumatlar uğurla bərpa edildi", onDone = { internal = app.backup.internalBackups() }) {
                    app.backup.internalAutoBackup() // təhlükəsizlik üçün hazırkı vəziyyəti saxla
                    app.backup.restore(data, images)
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }
    if (wipe) {
        var confirmText by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { wipe = false },
            title = { Text("Bütün məlumatları sil") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bütün məhsullar, satışlar, kassa, borclar və hesabatlar silinəcək. Bu əməliyyat geri qaytarıla bilməz " +
                        "(silinmədən əvvəl daxili backup saxlanılır). Təsdiq üçün SİL yazın.")
                    OutlinedTextField(confirmText, { confirmText = it }, label = { Text("SİL yazın") }, singleLine = true)
                    if (s.pinEnabled) OutlinedTextField(pin, { pin = it.filter(Char::isDigit) }, label = { Text("PIN kod") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                }
            },
            confirmButton = {
                Button(
                    enabled = confirmText.trim().uppercase() in setOf("SİL", "SIL") && (!s.pinEnabled || app.settings.checkPin(pin)),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        runner.run(success = "Bütün məlumatlar silindi", onDone = { wipe = false; internal = app.backup.internalBackups() }) {
                            app.backup.internalAutoBackup()
                            app.backup.wipeAll()
                        }
                    },
                ) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { wipe = false }) { Text("İmtina") } },
        )
    }
}

@Composable
fun ActivityLogScreen(nav: NavController) {
    val app = app()
    val logs by app.repo.dao.observeLog(1000).collectAsState(emptyList())
    var q by remember { mutableStateOf("") }
    val filtered = logs.filter { q.isBlank() || it.action.contains(q, true) || it.details.contains(q, true) }
    Scaffold(topBar = { PosTopBar("Əməliyyat tarixçəsi", onBack = { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item { SearchField(q, { q = it }, "Axtar") }
            if (filtered.isEmpty()) item { EmptyState(Icons.Default.History, "Əməliyyat yoxdur") }
            items(filtered, key = { it.id }) { l ->
                ListItem(
                    headlineContent = { Text(l.action, style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { Text((if (l.details.isNotBlank()) l.details + "\n" else "") + Dates.dateTime(l.createdAt)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
