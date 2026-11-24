package az.magaza.pos.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import az.magaza.pos.data.AppSettings
import az.magaza.pos.data.db.Customer
import az.magaza.pos.data.db.Sale
import az.magaza.pos.data.db.SaleItem
import az.magaza.pos.util.BluetoothPrinter
import az.magaza.pos.util.ImageStore
import az.magaza.pos.util.Receipt
import az.magaza.pos.util.Share
import coil.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun ProductThumb(imagePath: String?, name: String, size: Dp = 48.dp) {
    val ctx = LocalContext.current
    val file = remember(imagePath) { ImageStore.file(ctx, imagePath) }
    if (file != null) {
        AsyncImage(
            model = file, contentDescription = name, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" },
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Kamera ilə barkod skaneri. Kənar (USB/Bluetooth) skanerlər klaviatura kimi işlədiyi üçün axtarış sahəsi ilə işləyir. */
@Composable
fun rememberBarcodeScanner(onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { r ->
        r.contents?.trim()?.takeIf { it.isNotEmpty() }?.let(onResult)
    }
    return {
        launcher.launch(
            ScanOptions().setPrompt("Barkodu çərçivəyə yaxınlaşdırın").setBeepEnabled(true)
                .setOrientationLocked(true).setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
        )
    }
}

@Composable
fun CustomerDialog(initial: Customer?, onSave: (Customer) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni müştəri" else "Müştərini redaktə et") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(name, { name = it }, "Ad, soyad *")
                TextInput(phone, { phone = it }, "Telefon nömrəsi", keyboardType = KeyboardType.Phone)
                TextInput(note, { note = it }, "Qeyd", singleLine = false)
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onSave((initial ?: Customer(name = "")).copy(name = name, phone = phone, note = note)); onDismiss()
            }) { Text("Yadda saxla") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}

/** Müştəri seçimi: axtarış + yeni müştəri əlavə etmək. */
@Composable
fun CustomerPickerDialog(
    customers: List<Customer>,
    onPick: (Customer) -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(q, customers) {
        customers.filter { q.isBlank() || it.name.contains(q, true) || it.phone.contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Müştəri seçin") },
        text = {
            Column {
                SearchField(q, { q = it }, "Ad və ya telefon")
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { c ->
                        ListItem(
                            headlineContent = { Text(c.name) },
                            supportingContent = { if (c.phone.isNotBlank()) Text(c.phone) },
                            leadingContent = { Icon(Icons.Default.Person, null) },
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).then(Modifier),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0f)),
                            trailingContent = { TextButton(onClick = { onPick(c); onDismiss() }) { Text("Seç") } },
                        )
                    }
                    if (filtered.isEmpty()) item { Text("Müştəri tapılmadı", Modifier.padding(12.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAddNew() }) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Yeni müştəri") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bağla") } },
    )
}

/** Çek əməliyyatları: Bluetooth printerə çap və PDF kimi paylaşma. */
class ReceiptActions(
    private val context: android.content.Context,
    private val runner: Runner,
    private val settings: () -> AppSettings,
    private val requestBtPermission: (() -> Unit) -> Unit,
) {
    fun pdf(sale: Sale, items: List<SaleItem>) {
        runner.run {
            val f = Receipt.pdf(context, sale, items, settings())
            Share.file(context, f, "application/pdf", "Çeki paylaş")
        }
    }

    fun print(sale: Sale, items: List<SaleItem>) {
        val s = settings()
        if (s.printerAddress.isBlank()) {
            runner.message("Əvvəlcə Ayarlar bölməsində Bluetooth printeri seçin")
            return
        }
        requestBtPermission {
            runner.run(success = "Çek printerə göndərildi") {
                try {
                    BluetoothPrinter.print(context, s.printerAddress, BluetoothPrinter.escPos(Receipt.build(sale, items, s), s.paperWidth))
                } catch (e: Exception) {
                    throw az.magaza.pos.data.PosException("Printerə qoşulmaq mümkün olmadı: ${e.message ?: "naməlum xəta"}. Printerin açıq və cütləşdirilmiş olduğunu yoxlayın.")
                }
            }
        }
    }
}

@Composable
fun rememberReceiptActions(runner: Runner): ReceiptActions {
    val ctx = LocalContext.current
    val app = app()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke() else runner.message("Bluetooth icazəsi verilmədi")
        pending = null
    }
    return remember {
        ReceiptActions(ctx, runner, { app.settings.state.value }) { action ->
            if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                pending = action
                permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else action()
        }
    }
}
