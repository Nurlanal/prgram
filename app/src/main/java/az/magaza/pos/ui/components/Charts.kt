package az.magaza.pos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.magaza.pos.util.Money

data class ChartBar(val label: String, val value: Long, val secondary: Long = 0)

/**
 * Sadə sütun qrafiki. İkinci seriya (məs. qazanc) əsas sütunun içində tünd rənglə göstərilir.
 * Sütuna toxunduqda dəyəri görünür.
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    primaryLabel: String = "Satış",
    secondaryLabel: String? = "Qazanc",
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    var selected by remember(bars) { mutableIntStateOf(bars.indexOfLast { it.value > 0 }.coerceAtLeast(bars.lastIndex)) }
    val max = (bars.maxOfOrNull { maxOf(it.value, it.secondary) } ?: 0L).coerceAtLeast(1L)

    Column(modifier) {
        if (bars.isNotEmpty() && selected in bars.indices) {
            val b = bars[selected]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Legend(primary, "$primaryLabel: ${Money.format(b.value)}")
                if (secondaryLabel != null) {
                    Spacer(Modifier.width(10.dp))
                    Legend(secondary, "$secondaryLabel: ${Money.format(b.secondary)}")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).pointerInput(bars) {
                detectTapGestures { pos ->
                    if (bars.isNotEmpty()) {
                        val slot = size.width / bars.size
                        selected = (pos.x / slot).toInt().coerceIn(0, bars.lastIndex)
                    }
                }
            }
        ) {
            val n = bars.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtMost(36.dp.toPx())
            for (i in 1..3) {
                val y = size.height * i / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            bars.forEachIndexed { i, b ->
                val x = slot * i + (slot - barW) / 2
                val h = size.height * (b.value.toFloat() / max)
                val alpha = if (i == selected) 1f else 0.55f
                if (b.value > 0) {
                    drawRoundRect(primary.copy(alpha = alpha), Offset(x, size.height - h), Size(barW, h), CornerRadius(6f, 6f))
                }
                if (secondaryLabel != null && b.secondary > 0) {
                    val h2 = size.height * (b.secondary.toFloat() / max)
                    val w2 = barW * 0.5f
                    drawRoundRect(secondary.copy(alpha = alpha), Offset(x + (barW - w2) / 2, size.height - h2), Size(w2, h2), CornerRadius(4f, 4f))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            val step = when {
                bars.size <= 8 -> 1
                bars.size <= 16 -> 2
                else -> (bars.size + 6) / 7
            }
            bars.forEachIndexed { i, b ->
                Text(
                    if (i % step == 0) (if (bars.size > 8) b.label.substringBefore(".") else b.label) else "",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** Üfüqi pay zolağı (məs. kateqoriya üzrə xərclər). */
@Composable
fun ShareBar(label: String, value: Long, max: Long, color: Color, valueText: String = Money.format(value)) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(valueText, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth((value.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}
