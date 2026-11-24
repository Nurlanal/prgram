package az.magaza.pos.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

object ImageStore {
    fun dir(context: Context) = File(context.filesDir, "images").apply { mkdirs() }

    fun file(context: Context, name: String?): File? =
        name?.let { File(dir(context), it) }?.takeIf { it.exists() }

    /** Seçilmiş şəkli kiçildib proqramın daxili yaddaşına köçürür. Fayl adını qaytarır. */
    fun importImage(context: Context, uri: Uri, maxSize: Int = 800): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxSize * 2 || bounds.outHeight / sample > maxSize * 2) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = minOf(1f, maxSize.toFloat() / maxOf(bmp.width, bmp.height))
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val name = "p_${UUID.randomUUID()}.jpg"
        File(dir(context), name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }.getOrNull()

    fun delete(context: Context, name: String?) {
        name?.let { File(dir(context), it).delete() }
    }
}
