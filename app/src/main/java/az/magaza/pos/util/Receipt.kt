package az.magaza.pos.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import az.magaza.pos.data.AppSettings
import az.magaza.pos.data.PosRepository
import az.magaza.pos.data.db.Sale
import az.magaza.pos.data.db.SaleItem
import az.magaza.pos.data.db.SaleStatus
import java.io.File

/** Çek mətnini 58 mm (32 simvol) və ya 80 mm (48 simvol) eninə görə formalaşdırır. */
object Receipt {
    fun width(paper: Int) = if (paper >= 80) 48 else 32

    sealed class Line {
        data class Center(val text: String, val bold: Boolean = false) : Line()
        data class Text(val text: String, val bold: Boolean = false) : Line()
        object Sep : Line()
    }

    fun build(sale: Sale, items: List<SaleItem>, s: AppSettings): List<Line> {
        val w = width(s.paperWidth)
        fun lr(l: String, r: String): String {
            val space = w - r.length
            val left = if (l.length > space - 1) l.take(maxOf(0, space - 1)) else l
            return left + " ".repeat(maxOf(1, w - left.length - r.length)) + r
        }
        val out = mutableListOf<Line>()
        out += Line.Center(s.storeName, bold = true)
        if (s.storeAddress.isNotBlank()) out += Line.Center(s.storeAddress)
        if (s.storePhone.isNotBlank()) out += Line.Center("Tel: ${s.storePhone}")
        out += Line.Sep
        out += Line.Text(lr("Çek #${sale.id}", Dates.dateTime(sale.createdAt)))
        if (sale.customerName.isNotBlank()) out += Line.Text("Müştəri: ${sale.customerName}")
        if (sale.status == SaleStatus.CANCELLED) out += Line.Center("*** LƏĞV EDİLİB ***", bold = true)
        out += Line.Sep
        items.forEach { i ->
            i.productName.chunked(w).forEach { out += Line.Text(it) }
            out += Line.Text(lr("  ${i.quantity} x ${Money.plain(i.unitPrice)}", Money.plain(i.lineTotal)))
        }
        out += Line.Sep
        out += Line.Text(lr("Ara cəm:", Money.formatAzn(sale.subtotal)))
        if (sale.discount > 0) out += Line.Text(lr("Endirim:", "-" + Money.formatAzn(sale.discount)))
        out += Line.Text(lr("CƏMİ:", Money.formatAzn(sale.total)), bold = true)
        if (sale.paidCash > 0) out += Line.Text(lr("Nağd:", Money.formatAzn(sale.paidCash)))
        if (sale.paidCard > 0) out += Line.Text(lr("Kart:", Money.formatAzn(sale.paidCard)))
        if (sale.debtAmount > 0) out += Line.Text(lr("Borc:", Money.formatAzn(sale.debtAmount)))
        out += Line.Sep
        if (s.receiptFooter.isNotBlank()) out += Line.Center(s.receiptFooter)
        return out
    }

    fun plainText(lines: List<Line>, paper: Int): String {
        val w = width(paper)
        return lines.joinToString("\n") {
            when (it) {
                is Line.Center -> center(it.text, w)
                is Line.Text -> it.text
                Line.Sep -> "-".repeat(w)
            }
        }
    }

    fun center(t: String, w: Int): String =
        t.chunked(w).joinToString("\n") { " ".repeat(maxOf(0, (w - it.length) / 2)) + it }

    /** Çeki PDF faylı kimi yaradır (cache/receipts qovluğunda). */
    fun pdf(context: Context, sale: Sale, items: List<SaleItem>, s: AppSettings): File {
        val lines = build(sale, items, s)
        val w = width(s.paperWidth)
        val text = plainText(lines, s.paperWidth).split("\n")
        val paint = Paint().apply { typeface = Typeface.MONOSPACE; textSize = 9f; isAntiAlias = true }
        val bold = Paint(paint).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val charW = paint.measureText("M")
        val pageW = (charW * w + 24).toInt()
        val lineH = 12
        val pageH = text.size * lineH + 30
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        var y = 20f
        val boldTexts = lines.filter { (it is Line.Text && it.bold) || (it is Line.Center && it.bold) }
            .flatMap { l -> plainText(listOf(l), s.paperWidth).split("\n") }.toSet()
        text.forEach { line ->
            page.canvas.drawText(line, 12f, y, if (line in boldTexts) bold else paint)
            y += lineH
        }
        doc.finishPage(page)
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val f = File(dir, "cek_${sale.id}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun methodSummary(sale: Sale): String = buildList {
        if (sale.paidCash > 0) add(PosRepository.methodName("CASH"))
        if (sale.paidCard > 0) add(PosRepository.methodName("CARD"))
        if (sale.debtAmount > 0) add("Borc")
    }.joinToString(" + ").ifEmpty { "-" }
}
