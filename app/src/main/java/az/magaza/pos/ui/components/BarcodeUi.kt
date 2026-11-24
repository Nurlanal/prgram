package az.magaza.pos.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import az.magaza.pos.data.db.Product
import az.magaza.pos.util.Barcodes
import az.magaza.pos.util.Share

/** Məhsul kartında barkod şəkli, yaratma və etiket çapı. */
@Composable
fun BarcodeCard(product: Product, onGenerate: () -> Unit, onLabels: () -> Unit) {
    PosCard {
        Text("Barkod", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (product.barcode.isBlank()) {
            Text("Bu məhsulun barkodu yoxdur. Barkod yaradın və etiketi çap edib məhsula yapışdırın — satışda skan etmək mümkün olacaq.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.QrCode2, null); Spacer(Modifier.width(8.dp)); Text("Barkod yarat")
            }
        } else {
            val bmp = remember(product.barcode) { Barcodes.bitmap(product.barcode, 600, 200) }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White).padding(8.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    bmp?.let { Image(it.asImageBitmap(), product.barcode, Modifier.fillMaxWidth().height(90.dp)) }
                    Text(product.barcode, color = Color.Black, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLabels, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text("Barkod etiketi çap et")
            }
        }
    }
}

/** Etiket sayı seçimi: Bluetooth çek printerinə və ya A4 PDF (3×8 yapışqanlı vərəq). */
@Composable
fun LabelDialog(products: List<Product>, runner: Runner, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val printer = rememberReceiptActions(runner)
    val withCode = products.filter { it.barcode.isNotBlank() }
    var byStock by remember { mutableStateOf(false) }
    var copiesText by remember { mutableStateOf("1") }
    fun copiesOf(p: Product) = if (byStock) p.stock.coerceAtLeast(1) else (copiesText.toIntOrNull() ?: 1).coerceIn(1, 500)
    val total = withCode.sumOf { copiesOf(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Barkod etiketləri") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (withCode.size == 1) withCode[0].name else "${withCode.size} məhsul",
                    style = MaterialTheme.typography.titleSmall)
                if (products.size > withCode.size) Text("${products.size - withCode.size} məhsulun barkodu olmadığı üçün çap olunmayacaq",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(!byStock, { byStock = false }); Text("Hər məhsuldan", Modifier.weight(1f))
                    OutlinedTextField(copiesText, { copiesText = it.filter(Char::isDigit).take(3) }, Modifier.width(80.dp),
                        enabled = !byStock, singleLine = true, suffix = { Text("əd.") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(byStock, { byStock = true }); Text("Stok sayı qədər")
                }
                Text("Cəmi: $total etiket", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(enabled = withCode.isNotEmpty(), onClick = {
                    printer.printBytes("Etiketlər printerə göndərildi") {
                        withCode.fold(ByteArray(0)) { acc, p -> acc + Barcodes.escPosLabels(p, copiesOf(p)) }
                    }
                }) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(6.dp)); Text("Bluetooth printer") }
                OutlinedButton(enabled = withCode.isNotEmpty(), onClick = {
                    runner.run {
                        val f = Barcodes.labelsPdf(ctx, withCode.map { it to copiesOf(it) })
                        Share.file(ctx, f, "application/pdf", "Etiketləri paylaş / çap et")
                    }
                }) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("PDF (A4, 3×8)") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bağla") } },
    )
}
