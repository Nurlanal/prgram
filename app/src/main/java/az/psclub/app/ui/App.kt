package az.psclub.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.psclub.app.data.Store
import az.psclub.app.data.money
import kotlinx.coroutines.delay

enum class Tab { MENU, ACTIVE, DEBT, ADMIN }

@Composable
fun PsClubApp(store: Store, startCat: Cat = Cat.PS5, startTab: Tab = Tab.MENU, startTable: Long? = null) {
    val nowState = produceState(System.currentTimeMillis()) {
        while (true) { delay(1000); value = System.currentTimeMillis() }
    }
    PsTheme(store.darkMode) {
        CompositionLocalProvider(LocalNow provides nowState) {
            val p = LocalPalette.current
            var tab by rememberSaveable { mutableStateOf(startTab) }
            var tableSheet by remember { mutableStateOf(startTable) }
            var repairSheet by remember { mutableStateOf<Long?>(null) }
            var showCart by remember { mutableStateOf(false) }
            var showProfile by remember { mutableStateOf(false) }
            var showPin by remember { mutableStateOf(false) }
            var splash by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(Unit) { delay(1700); splash = false }
            BackHandler(enabled = tab != Tab.MENU) { tab = Tab.MENU }

            Box(Modifier.fillMaxSize().background(p.bg)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 14 }) togetherWith fadeOut(tween(180))
                    },
                    label = "tab",
                ) { t ->
                    when (t) {
                        Tab.MENU -> MenuScreen(
                            store,
                            startCat = startCat,
                            onTable = { tableSheet = it.id },
                            onRepair = { repairSheet = it.id },
                            onProfile = { showProfile = true },
                        )
                        Tab.ACTIVE -> ActiveScreen(store, onTable = { tableSheet = it })
                        Tab.DEBT -> DebtScreen(store)
                        Tab.ADMIN -> AdminScreen(store, onBack = { tab = Tab.MENU })
                    }
                }
                AnimatedVisibility(
                    visible = tab != Tab.ADMIN,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    BottomBar(store, tab, onTab = { tab = it }, onCart = { showCart = true })
                }
                AnimatedVisibility(splash, enter = EnterTransition.None, exit = fadeOut(tween(500)) + scaleOut(tween(500), targetScale = 1.15f)) {
                    Splash(store.clubName)
                }
            }

            tableSheet?.let { id ->
                val t = store.table(id)
                if (t != null) TableSheet(store, t, onDismiss = { tableSheet = null })
            }
            repairSheet?.let { id ->
                val r = store.repairs.firstOrNull { it.id == id }
                if (r != null) RepairSheet(store, r, onDismiss = { repairSheet = null })
            }
            if (showCart) CartSheet(store, onDismiss = { showCart = false })
            if (showProfile) ProfileSheet(
                store,
                onDismiss = { showProfile = false },
                onAdmin = { showProfile = false; showPin = true },
            )
            if (showPin) PinDialog(store, onDismiss = { showPin = false }, onSuccess = { showPin = false; tab = Tab.ADMIN })
        }
    }
}

@Composable
private fun BottomBar(store: Store, tab: Tab, onTab: (Tab) -> Unit, onCart: () -> Unit) {
    val p = LocalPalette.current
    val total = store.cartTotal()
    val active = store.sessions.size
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, p.bg.copy(alpha = 0.92f), p.bg)))
        )
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            AnimatedVisibility(total > 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(
                    Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(50)).background(p.card)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⭐", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("${store.pointsFor(total)} bonus xal qazanacaqsınız!", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.shadow(if (p.dark) 0.dp else 10.dp, RoundedCornerShape(50), clip = false)
                        .clip(RoundedCornerShape(50)).background(if (p.dark) p.cardAlt else p.pill).padding(5.dp),
                ) {
                    Seg("Menyu", tab == Tab.MENU) { onTab(Tab.MENU) }
                    Seg(if (active > 0) "Aktiv·$active" else "Aktiv", tab == Tab.ACTIVE) { onTab(Tab.ACTIVE) }
                    Seg("Borc", tab == Tab.DEBT) { onTab(Tab.DEBT) }
                }
                Spacer(Modifier.weight(1f))
                val bump by animateFloatAsState(if (total > 0) 1f else 0.96f, spring(dampingRatio = 0.4f), label = "cart")
                Row(
                    Modifier.graphicsLayer { scaleX = bump; scaleY = bump }
                        .shadow(if (p.dark) 18.dp else 8.dp, RoundedCornerShape(50), ambientColor = p.accent, spotColor = p.accent2)
                        .clip(RoundedCornerShape(50)).background(p.glow)
                        .bounceClick(onClick = onCart)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    AnimatedContent(money(total), label = "total", transitionSpec = {
                        (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                    }) { Text(it, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                }
            }
        }
    }
}

@Composable
private fun Seg(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg by animateColorAsState(
        if (selected) (if (p.dark) Color(0xFF2E2E46) else Color.White) else Color.Transparent, label = "seg"
    )
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).bounceClick(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 12.dp),
    ) {
        Text(
            text,
            color = if (selected && p.dark) p.accent else p.text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp, maxLines = 1,
        )
    }
}

@Composable
private fun Splash(name: String) {
    val inf = rememberInfiniteTransition(label = "splash")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "rot")
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val s by animateFloatAsState(if (started) 1f else 0.4f, spring(dampingRatio = 0.45f, stiffness = 120f), label = "s")
    Box(Modifier.fillMaxSize().background(DarkPalette.bg), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(320.dp).background(
                Brush.radialGradient(listOf(Color(0xFF6A1BFF).copy(alpha = 0.45f), Color.Transparent))
            )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("△" to Color(0xFF2EE5C9), "○" to Color(0xFFFF4D6D), "✕" to Color(0xFF7DB4FF), "□" to Color(0xFFE29BFF))
                    .forEachIndexed { i, (sym, c) ->
                        Text(sym, color = c, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.graphicsLayer { rotationY = rot + i * 45f })
                    }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                name,
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFB026FF), Color(0xFF00E5FF))),
                    fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                ),
            )
            Text("GAMING LOUNGE", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, letterSpacing = 6.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).size(8.dp).clip(CircleShape).background(Color(0xFF00E5FF)))
    }
}
