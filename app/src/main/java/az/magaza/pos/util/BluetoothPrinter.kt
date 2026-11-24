package az.magaza.pos.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

data class PrinterDevice(val name: String, val address: String)

/** ESC/POS dəstəkləyən Bluetooth çek printerləri üçün (58 mm / 80 mm). */
object BluetoothPrinter {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<PrinterDevice> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices.map { PrinterDevice(it.name ?: it.address, it.address) }
    }

    fun isBluetoothOn(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    /** Printerlərin çoxu Azərbaycan hərflərini çap etmir, ona görə latın ekvivalentlərinə çeviririk. */
    fun translit(s: String): String = buildString {
        s.forEach { c ->
            append(
                when (c) {
                    'ə' -> "e"; 'Ə' -> "E"; 'ı' -> "i"; 'İ' -> "I"; 'ğ' -> "g"; 'Ğ' -> "G"
                    'ş' -> "s"; 'Ş' -> "S"; 'ç' -> "c"; 'Ç' -> "C"; 'ö' -> "o"; 'Ö' -> "O"
                    'ü' -> "u"; 'Ü' -> "U"; '₼' -> "AZN"; '–', '—' -> "-"
                    else -> if (c.code < 128) c.toString() else "?"
                }
            )
        }
    }

    fun escPos(lines: List<Receipt.Line>, paper: Int): ByteArray {
        val w = Receipt.width(paper)
        val o = ByteArrayOutputStream()
        fun cmd(vararg b: Int) = b.forEach { o.write(it) }
        fun text(t: String) = o.write((translit(t) + "\n").toByteArray(Charsets.US_ASCII))
        cmd(0x1B, 0x40) // init
        lines.forEach { l ->
            when (l) {
                is Receipt.Line.Center -> {
                    cmd(0x1B, 0x61, 1); if (l.bold) cmd(0x1B, 0x45, 1)
                    text(l.text)
                    if (l.bold) cmd(0x1B, 0x45, 0); cmd(0x1B, 0x61, 0)
                }
                is Receipt.Line.Text -> {
                    if (l.bold) cmd(0x1B, 0x45, 1)
                    text(l.text)
                    if (l.bold) cmd(0x1B, 0x45, 0)
                }
                Receipt.Line.Sep -> text("-".repeat(w))
            }
        }
        cmd(0x1B, 0x64, 4) // 4 sətir irəli
        cmd(0x1D, 0x56, 66, 0) // kəsmə (dəstəklənirsə)
        return o.toByteArray()
    }

    @SuppressLint("MissingPermission")
    suspend fun print(context: Context, address: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IllegalStateException("Bluetooth dəstəklənmir")
        if (!adapter.isEnabled) throw IllegalStateException("Bluetooth söndürülüb")
        val device = adapter.getRemoteDevice(address)
        runCatching { adapter.cancelDiscovery() }
        val socket = device.createRfcommSocketToServiceRecord(SPP)
        try {
            socket.connect()
            socket.outputStream.write(data)
            socket.outputStream.flush()
            Thread.sleep(400)
        } finally {
            runCatching { socket.close() }
        }
    }
}
