package az.psclub.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.psclub.app.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("dd.MM HH:mm", Locale.US)

@Composable
fun ActiveScreen(store: Store, onTable: (Long) -> Unit) {
    val p = LocalPalette.current
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("Aktiv masalar", sub = "${store.sessions.size} masa oyundadır") }
        if (store.sessions.isEmpty()) item {
            NeonCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🕹️", fontSize = 48.sp)
                    Text("Hazırda aktiv masa yoxdur", color = p.dim)
                }
            }
        }
        items(store.sessions, key = { it.id }) { s -> SessionCard(store, s) { onTable(s.tableId) } }
        item {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoBox("Bugünkü gəlir", money(store.revenueSince(store.startOfToday())), Modifier.weight(1f), p.success)
                InfoBox("Boş masa", "${store.tables.count { store.sessionFor(it.id) == null }} / ${store.tables.size}", Modifier.weight(1f))
            }
        }
        if (store.history.isNotEmpty()) {
            item { Text("Son əməliyyatlar", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            items(store.history.take(20), key = { "h${it.id}" }) { HistoryRow(it) }
        }
    }
}

@Composable
private fun SessionCard(store: Store, s: Session, onClick: () -> Unit) {
    val p = LocalPalette.current
    val now = LocalNow.current.value
    val t = store.table(s.tableId) ?: return
    val elapsed = now - s.startedAt
    val planned = s.plannedMinutes * 60_000L
    val over = s.plannedMinutes > 0 && elapsed > planned
    val inf = rememberInfiniteTransition(label = "sc")
    val blink by inf.animateFloat(1f, 0.4f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "b")
    NeonCard(Modifier.fillMaxWidth().bounceClick(onClick = onClick), radius = 26.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConsolePoster(t.console, Modifier.size(92.dp), animated = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, color = p.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    listOf(t.console.short, durationLabel(s.plannedMinutes), s.customer).filter { it.isNotBlank() }.joinToString(" · "),
                    color = p.dim, fontSize = 13.sp, maxLines = 1,
                )
                Text(
                    clock(elapsed),
                    style = TextStyle(brush = p.glow, fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.graphicsLayer { alpha = if (over) blink else 1f },
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(store.totalCost(s, now)), color = p.text, fontWeight = FontWeight.Black, fontSize = 18.sp)
                if (over) Badge("Vaxt bitdi", p.danger)
            }
        }
        if (s.plannedMinutes > 0) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (elapsed.toFloat() / planned).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = if (over) p.danger else p.accent, trackColor = p.cardAlt,
            )
        }
    }
}

@Composable
fun HistoryRow(e: HistoryEntry) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).then(Modifier).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(when (e.kind) { "table" -> "🎮"; "repair" -> "🔧"; "debtpay" -> "💵"; else -> "🥤" }, fontSize = 26.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = p.text, fontWeight = FontWeight.SemiBold)
            Text(e.detail, color = p.dim, fontSize = 12.sp, maxLines = 2)
            Text(timeFmt.format(Date(e.time)), color = p.dim, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("+${money(e.amount)}", color = if (e.method == "credit") p.warn else p.success, fontWeight = FontWeight.Bold)
            when (e.method) {
                "credit" -> Badge("Nisyə", p.warn)
                "debtpay" -> Badge("Borc ödənişi", p.accent2)
                else -> Badge("Nağd", p.success.copy(alpha = 0.8f))
            }
        }
    }
}
