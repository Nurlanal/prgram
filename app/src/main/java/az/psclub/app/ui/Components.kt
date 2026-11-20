package az.psclub.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Press feedback: springy scale-down while pressed. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    leadingPlus: Boolean = false,
    color: Color? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .bounceClick(onClick = onClick)
            .clip(shape)
            .then(if (filled) Modifier.background(p.glow) else Modifier.background(color ?: p.pill))
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tc = if (filled) Color.White else p.text
        if (leadingPlus) {
            Icon(Icons.Default.Add, null, tint = tc, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = tc, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .bounceClick(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (!enabled) Brush.horizontalGradient(listOf(p.cardAlt, p.cardAlt))
                else if (danger) Brush.horizontalGradient(listOf(p.danger, Color(0xFFFF7A45)))
                else p.glow
            )
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else p.dim, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Box(
        modifier
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(p.glow) else Modifier.background(p.pill))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.White else p.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun Stepper(qty: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(p.glow).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepBtn("−", onMinus)
        Text("$qty", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.widthIn(min = 26.dp), textAlign = TextAlign.Center)
        StepBtn("+", onPlus)
    }
}

@Composable
private fun StepBtn(s: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(s, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
}

@Composable
fun FavButton(active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s by animateFloatAsState(if (active) 1.15f else 1f, spring(dampingRatio = 0.35f), label = "fav")
    Box(
        modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (active) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
            tint = if (active) Color(0xFFFF3B5C) else Color.White,
            modifier = Modifier.size(18.dp).graphicsLayer { scaleX = s; scaleY = s },
        )
    }
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.9f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, sub: String? = null) {
    val p = LocalPalette.current
    Column(modifier) {
        Text(text, color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = p.dim, fontSize = 14.sp)
    }
}

@Composable
fun NeonCard(modifier: Modifier = Modifier, radius: Dp = 26.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .background(p.card)
            .then(
                if (p.dark) Modifier.border(1.dp, Brush.linearGradient(listOf(p.accent2.copy(alpha = 0.45f), p.border, p.accent.copy(alpha = 0.35f))), shape)
                else Modifier
            )
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun EmojiTile(emoji: String, seed: Int, modifier: Modifier = Modifier, size: Int = 64) {
    val p = LocalPalette.current
    val palettes = if (p.dark) listOf(
        listOf(Color(0xFF241046), Color(0xFF5B21B6)),
        listOf(Color(0xFF062A3A), Color(0xFF0E7490)),
        listOf(Color(0xFF3A0A22), Color(0xFFBE185D)),
        listOf(Color(0xFF0B1E4D), Color(0xFF1D4ED8)),
        listOf(Color(0xFF0B2E22), Color(0xFF047857)),
    ) else listOf(listOf(p.card, p.card))
    val colors = palettes[Math.floorMod(seed, palettes.size)]
    Box(
        modifier.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        // soft floor shadow + glow make the emoji look like a 3D object
        Box(
            Modifier.align(Alignment.Center).offset(y = (size * 0.55).dp).size((size * 0.9).dp, (size * 0.16).dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = if (p.dark) 0.45f else 0.12f))
        )
        Text(emoji, fontSize = size.sp, modifier = Modifier.graphicsLayer { rotationZ = -6f; shadowElevation = 0f })
    }
}
