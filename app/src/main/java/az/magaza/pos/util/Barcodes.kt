package az.magaza.pos.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import az.magaza.pos.data.db.Product
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Mağaza daxili barkodlar: "2" ilə başlayan EAN-13 (GS1 standartında daxili istifadə üçün ayrılmış diapazon),
 * buna görə zavod barkodları ilə toqquşmur.
 */
object Barcodes {
    fun ean13CheckDigit(first12: String): Int {
        require(first12.length == 12 && first12.all { it.isDigit() })
        val sum = first12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10
    }

    fun isValidEan13(code: String): Boolean =
        code.length == 13 && code.all { it.isDigit() } && ean13CheckDigit(code.take(12)) == code.last() - '0'

    /** Məhsul ID-si əsasında daxili EAN-13: 20 + 10 rəqəm + yoxlama rəqəmi. */
    fun internalFor(productId: Long, attempt: Int = 0): String {
        val body = "20" + ((productId + attempt * 7_919_000L) % 10_000_000_000L).toString().padStart(10, '0')
        return body + ean13CheckDigit(body)
    }

    fun bitmap(code: String, width: Int, height: Int): Bitmap? = runCatching {
        val format = if (isValidEan13(code)) BarcodeFormat.EAN_13 else BarcodeFormat.CODE_128
        val m = MultiFormatWriter().encode(code, format, width, height, mapOf(EncodeHintType.MARGIN to 4))
        val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until m.width) for (y in 0 until m.height) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        bmp
    }.getOrNull()

    /** ESC/POS printer üçün etiket(lər): ad, qiymət və barkod. */
    fun escPosLabels(p: Product, copies: Int): ByteArray {
        val o = ByteArrayOutputStream()
        fun cmd(vararg b: Int) = b.forEach { o.write(it) }
        fun text(t: String) = o.write((BluetoothPrinter.translit(t) + "\n").toByteArray(Charsets.US_ASCII))
        cmd(0x1B, 0x40)
        repeat(copies.coerceIn(1, 200)) {
            cmd(0x1B, 0x61, 1)
            text(p.name.take(32))
            cmd(0x1B, 0x45, 1); text(Money.formatAzn(p.salePrice)); cmd(0x1B, 0x45, 0)
            cmd(0x1D, 0x68, 70) // hündürlük
            cmd(0x1D, 0x77, 2) // en
            cmd(0x1D, 0x48, 2) // rəqəmlər barkodun altında
            if (isValidEan13(p.barcode)) {
                cmd(0x1D, 0x6B, 67, 13); o.write(p.barcode.toByteArray(Charsets.US_ASCII))
            } else {
                val data = ("{B" + p.barcode).toByteArray(Charsets.US_ASCII)
                cmd(0x1D, 0x6B, 73, data.size); o.write(data)
            }
            cmd(0x0A)
            cmd(0x1B, 0x64, 3)
        }
        cmd(0x1D, 0x56, 66, 0)
        return o.toByteArray()
    }

    const val PAGE_W = 595
    const val PAGE_H = 842
    private const val COLS = 3
    private const val ROWS = 8
    const val PER_PAGE = COLS * ROWS

    /** Bir A4 səhifəyə (595×842 pt) 3×8 etiket çəkir. PDF və önizləmə üçün ortaqdır. */
    fun drawLabelPage(c: android.graphics.Canvas, chunk: List<Product>, cache: MutableMap<String, Bitmap?> = mutableMapOf()) {
        val cellW = PAGE_W / COLS; val cellH = PAGE_H / ROWS
        val name = Paint().apply { textSize = 9f; isAntiAlias = true }
        val price = Paint().apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val digits = Paint().apply { textSize = 8f; typeface = Typeface.MONOSPACE; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val border = Paint().apply { style = Paint.Style.STROKE; color = Color.LTGRAY; strokeWidth = 0.5f }
        chunk.forEachIndexed { i, p ->
            val x = (i % COLS) * cellW.toFloat(); val y = (i / COLS) * cellH.toFloat()
            c.drawRect(x + 2, y + 2, x + cellW - 2, y + cellH - 2, border)
            c.drawText(p.name.take(30), x + 8, y + 15, name)
            c.drawText(Money.formatAzn(p.salePrice), x + 8, y + 29, price)
            val bmp = cache.getOrPut(p.barcode) { bitmap(p.barcode, 360, 120) }
            if (bmp != null) c.drawBitmap(bmp, null, android.graphics.RectF(x + 10, y + 34, x + cellW - 10, y + cellH - 16), null)
            c.drawText(p.barcode, x + cellW / 2f, y + cellH - 6, digits)
        }
    }

    fun expand(items: List<Pair<Product, Int>>): List<Product> = items.flatMap { (p, n) -> List(n.coerceIn(1, 500)) { p } }

    /** A4 vərəqdə 3×8 etiket (yapışqanlı etiket vərəqləri üçün). */
    fun labelsPdf(context: Context, items: List<Pair<Product, Int>>): File {
        val doc = PdfDocument()
        val cache = mutableMapOf<String, Bitmap?>()
        expand(items).chunked(PER_PAGE).forEachIndexed { pageIndex, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex + 1).create())
            drawLabelPage(page.canvas, chunk, cache)
            doc.finishPage(page)
        }
        val dir = File(context.cacheDir, "labels").apply { mkdirs() }
        val f = File(dir, "barkod_etiketleri_${Dates.fileStamp()}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }
}
