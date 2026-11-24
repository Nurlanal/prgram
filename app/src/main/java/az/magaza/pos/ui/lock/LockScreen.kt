package az.magaza.pos.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LockScreen(storeName: String, check: (String) -> Boolean, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun press(d: String) {
        if (pin.length >= 8) return
        pin += d
        error = false
        if (pin.length >= 4 && check(pin)) onUnlock()
    }
    PinPad(
        title = storeName,
        subtitle = if (error) "PIN kod səhvdir" else "PIN kodu daxil edin",
        isError = error,
        pin = pin,
        onDigit = ::press,
        onBack = { pin = pin.dropLast(1); error = false },
        onOk = { if (check(pin)) onUnlock() else { error = true; pin = "" } },
    )
}

@Composable
fun PinPad(
    title: String,
    subtitle: String,
    isError: Boolean,
    pin: String,
    onDigit: (String) -> Unit,
    onBack: () -> Unit,
    onOk: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("<", "0", "OK"))
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    r.forEach { k ->
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable {
                                    when (k) {
                                        "<" -> onBack()
                                        "OK" -> onOk()
                                        else -> onDigit(k)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "<" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Sil")
                                else -> Text(k, fontSize = if (k == "OK") 18.sp else 26.sp, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
