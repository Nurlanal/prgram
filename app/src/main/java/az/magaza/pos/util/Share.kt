package az.magaza.pos.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object Share {
    fun file(context: Context, f: File, mime: String, title: String = "Paylaş") {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun text(context: Context, text: String, title: String = "Paylaş") {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Excel-də düzgün açılan CSV (UTF-8 BOM, nöqtəli vergül ayırıcı). */
class Csv {
    private val sb = StringBuilder("﻿")
    fun row(vararg cells: Any?) {
        sb.append(cells.joinToString(";") { c ->
            val s = c?.toString() ?: ""
            if (s.contains(';') || s.contains('"') || s.contains('\n')) "\"" + s.replace("\"", "\"\"") + "\"" else s
        }).append("\r\n")
    }
    fun blank() { sb.append("\r\n") }
    fun save(context: Context, name: String): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        return File(dir, name).apply { writeText(sb.toString(), Charsets.UTF_8) }
    }
}
