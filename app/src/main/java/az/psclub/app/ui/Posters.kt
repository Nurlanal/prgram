package az.psclub.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.psclub.app.data.Console

private fun posterColors(c: Console) = when (c) {
    Console.PS5 -> listOf(Color(0xFF0A1A55), Color(0xFF1E50FF), Color(0xFF050518))
    Console.PS4 -> listOf(Color(0xFF14093A), Color(0xFF4B23C8), Color(0xFF05040F))
    Console.PS3 -> listOf(Color(0xFF2A0710), Color(0xFFA0153A), Color(0xFF070409))
}

fun glowColor(c: Console) = when (c) {
    Console.PS5 -> Color(0xFF5AC8FF)
    Console.PS4 -> Color(0xFF8C6BFF)
    Console.PS3 -> Color(0xFFFF3D6E)
}

/**
 * 3D console poster: gradient backdrop, neon glow, floating console drawn in
 * perspective, controller symbols, and (when interactive) drag-to-rotate tilt.
 */
@Composable
fun ConsolePoster(
    console: Console,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    animated: Boolean = true,
    interactive: Boolean = false,
    showLabel: Boolean = big,
) {
    val glow = glowColor(console)
    val inf = rememberInfiniteTransition(label = "poster")
    val floatAnim = if (animated) inf.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float"
    ) else null
    val pulseAnim = if (animated) inf.animateFloat(
        0.35f, 0.75f, infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Reverse), label = "pulse"
    ) else null
    val spinAnim = if (animated) inf.animateFloat(
        0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "spin"
    ) else null
    val f = floatAnim?.value ?: 0f
    val pulse = pulseAnim?.value ?: 0.5f
    val spin = spinAnim?.value ?: 20f

    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val tiltX by animateFloatAsState(dragX, spring(dampingRatio = 0.45f, stiffness = 120f), label = "tx")
    val tiltY by animateFloatAsState(dragY, spring(dampingRatio = 0.45f, stiffness = 120f), label = "ty")

    val shape = RoundedCornerShape(if (big) 32.dp else 26.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(posterColors(console), start = Offset.Zero, end = Offset(900f, 1400f)))
            .then(
                if (interactive) Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { dragX = 0f; dragY = 0f },
                        onDragCancel = { dragX = 0f; dragY = 0f },
                    ) { change, d ->
                        change.consume()
                        dragX = (dragX + d.x / 6f).coerceIn(-40f, 40f)
                        dragY = (dragY - d.y / 8f).coerceIn(-25f, 25f)
                    }
                } else Modifier
            ),
    ) {
        // giant faint model name behind the console
        Text(
            console.short,
            fontSize = if (big) 128.sp else 64.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.07f),
            modifier = Modifier.align(Alignment.Center).offset(y = if (big) (-18).dp else (-8).dp),
            maxLines = 1,
            softWrap = false,
        )
        // glow, grid floor, symbols, shadow
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = pulse), Color.Transparent), center = Offset(w / 2, h * 0.48f), radius = size.minDimension * 0.62f),
                radius = size.minDimension * 0.62f, center = Offset(w / 2, h * 0.48f),
            )
            // perspective grid floor
            val horizon = h * 0.74f
            val lineCol = glow.copy(alpha = 0.18f)
            for (i in -8..8) {
                drawLine(lineCol, Offset(w / 2 + i * w * 0.06f, horizon), Offset(w / 2 + i * w * 0.32f, h), strokeWidth = 1.2f)
            }
            var y = horizon; var step = h * 0.02f
            while (y < h) { drawLine(lineCol, Offset(0f, y), Offset(w, y), strokeWidth = 1.2f); y += step; step *= 1.45f }
            // floating controller symbols
            val s = size.minDimension * (if (big) 0.07f else 0.075f)
            symbols(spin, s, w, h)
            // contact shadow
            val sh = 1f - (f + 1f) * 0.15f
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), center = Offset(w / 2, h * 0.80f), radius = w * 0.34f * sh),
                topLeft = Offset(w / 2 - w * 0.34f * sh, h * 0.80f - h * 0.035f), size = Size(w * 0.68f * sh, h * 0.07f),
            )
        }
        // the console itself, rendered in a 3D layer
        Canvas(
            Modifier
                .fillMaxSize(if (big) 0.74f else 0.80f)
                .align(Alignment.Center)
                .offset(y = if (big) (-6).dp else (-4).dp)
                .graphicsLayer {
                    rotationY = f * 10f + tiltX
                    rotationX = 6f + tiltY
                    translationY = f * 5.dp.toPx()
                    cameraDistance = 14f * density
                }
        ) {
            when (console) {
                Console.PS5 -> drawPs5(glow)
                Console.PS4 -> drawPs4(glow)
                Console.PS3 -> drawPs3(glow)
            }
        }
        if (showLabel) {
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text(
                    console.label,
                    style = TextStyle(
                        brush = Brush.horizontalGradient(listOf(Color.White, glow)),
                        fontSize = 24.sp, fontWeight = FontWeight.Black,
                        shadow = Shadow(glow.copy(alpha = 0.8f), Offset(0f, 0f), 18f),
                    ),
                )
                Text(
                    when (console) {
                        Console.PS5 -> "4K · 120 FPS · DualSense"
                        Console.PS4 -> "Full HD · Klassik hitlər"
                        Console.PS3 -> "Retro · Əfsanəvi oyunlar"
                    },
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun DrawScope.symbols(spin: Float, s: Float, w: Float, h: Float) {
    val stroke = Stroke(width = s * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // triangle
    rotate(spin, Offset(w * 0.14f, h * 0.18f)) {
        val c = Offset(w * 0.14f, h * 0.18f)
        drawPath(Path().apply {
            moveTo(c.x, c.y - s); lineTo(c.x + s * 0.9f, c.y + s * 0.6f); lineTo(c.x - s * 0.9f, c.y + s * 0.6f); close()
        }, Color(0xFF2EE5C9).copy(alpha = 0.55f), style = stroke)
    }
    // circle
    drawCircle(Color(0xFFFF4D6D).copy(alpha = 0.55f), s * 0.85f, Offset(w * 0.87f, h * 0.2f), style = stroke)
    // cross
    rotate(-spin, Offset(w * 0.86f, h * 0.62f)) {
        val c = Offset(w * 0.86f, h * 0.62f); val k = s * 0.75f
        drawLine(Color(0xFF7DB4FF).copy(alpha = 0.55f), Offset(c.x - k, c.y - k), Offset(c.x + k, c.y + k), s * 0.22f, StrokeCap.Round)
        drawLine(Color(0xFF7DB4FF).copy(alpha = 0.55f), Offset(c.x + k, c.y - k), Offset(c.x - k, c.y + k), s * 0.22f, StrokeCap.Round)
    }
    // square
    rotate(spin * 0.6f, Offset(w * 0.13f, h * 0.62f)) {
        val c = Offset(w * 0.13f, h * 0.62f); val k = s * 0.75f
        drawRect(Color(0xFFE29BFF).copy(alpha = 0.55f), Offset(c.x - k, c.y - k), Size(k * 2, k * 2), style = stroke)
    }
}

/** PS5: tall white curved side plates around a black core with blue LED edges. */
private fun DrawScope.drawPs5(glow: Color) {
    val w = size.width; val h = size.height
    val cx = w / 2
    val top = h * 0.06f; val bottom = h * 0.86f
    // black core
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFF0B0B12), Color(0xFF26262F), Color(0xFF0B0B12)), startX = cx - w * 0.09f, endX = cx + w * 0.09f),
        Offset(cx - w * 0.085f, top + h * 0.02f), Size(w * 0.17f, bottom - top - h * 0.02f), CornerRadius(w * 0.03f),
    )
    // LED edges glow
    for (i in 4 downTo 1) {
        val a = 0.12f * (5 - i)
        drawLine(glow.copy(alpha = a), Offset(cx - w * 0.085f, top + h * 0.04f), Offset(cx - w * 0.085f, bottom - h * 0.02f), strokeWidth = i * 3f)
        drawLine(glow.copy(alpha = a), Offset(cx + w * 0.085f, top + h * 0.04f), Offset(cx + w * 0.085f, bottom - h * 0.02f), strokeWidth = i * 3f)
    }
    // left plate
    val plate = Path().apply {
        moveTo(cx - w * 0.075f, top)
        cubicTo(cx - w * 0.20f, top - h * 0.01f, cx - w * 0.30f, top + h * 0.05f, cx - w * 0.27f, top + h * 0.22f)
        cubicTo(cx - w * 0.235f, top + h * 0.42f, cx - w * 0.23f, top + h * 0.60f, cx - w * 0.29f, bottom)
        lineTo(cx - w * 0.075f, bottom)
        close()
    }
    val plateBrush = Brush.horizontalGradient(
        listOf(Color(0xFFBFC6D6), Color(0xFFFFFFFF), Color(0xFFE9ECF3)),
        startX = cx - w * 0.30f, endX = cx - w * 0.075f,
    )
    drawPath(plate, plateBrush)
    drawPath(plate, Color.Black.copy(alpha = 0.12f), style = Stroke(1.5f))
    // right plate (mirror)
    scale(-1f, 1f, pivot = Offset(cx, h / 2)) {
        drawPath(plate, Brush.horizontalGradient(
            listOf(Color(0xFF9AA2B4), Color(0xFFF4F6FA), Color(0xFFDDE1EA)),
            startX = cx - w * 0.30f, endX = cx - w * 0.075f,
        ))
        drawPath(plate, Color.Black.copy(alpha = 0.12f), style = Stroke(1.5f))
    }
    // specular highlight on left plate
    drawLine(Color.White.copy(alpha = 0.9f), Offset(cx - w * 0.2f, top + h * 0.05f), Offset(cx - w * 0.235f, top + h * 0.3f), strokeWidth = w * 0.012f, cap = StrokeCap.Round)
    // stand
    drawOval(Brush.verticalGradient(listOf(Color(0xFF2A2A33), Color(0xFF0B0B10))), Offset(cx - w * 0.17f, bottom - h * 0.01f), Size(w * 0.34f, h * 0.06f))
    // power button LEDs
    drawCircle(glow, w * 0.008f, Offset(cx, top + h * 0.12f))
}

/** PS4: angled slab in 3/4 view with split top and blue light bar. */
private fun DrawScope.drawPs4(glow: Color) {
    val w = size.width; val h = size.height
    fun o(x: Float, y: Float) = Offset(w * x, h * y)
    val a = o(0.04f, 0.46f); val b = o(0.58f, 0.30f); val c = o(0.97f, 0.42f); val d = o(0.42f, 0.60f)
    val thick = h * 0.12f
    val a2 = a.copy(y = a.y + thick); val d2 = d.copy(y = d.y + thick); val c2 = c.copy(y = c.y + thick)
    // left side face
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(d.x, d.y); lineTo(d2.x, d2.y); lineTo(a2.x, a2.y); close() },
        Brush.verticalGradient(listOf(Color(0xFF1C1C24), Color(0xFF09090D)), startY = a.y, endY = d2.y))
    // front face
    drawPath(Path().apply { moveTo(d.x, d.y); lineTo(c.x, c.y); lineTo(c2.x, c2.y); lineTo(d2.x, d2.y); close() },
        Brush.linearGradient(listOf(Color(0xFF15151C), Color(0xFF050508)), start = d, end = c2))
    // front groove (slot) line
    val gm = 0.5f
    drawLine(Color.White.copy(alpha = 0.18f), Offset(d.x, d.y + thick * gm), Offset(c.x, c.y + thick * gm), strokeWidth = 2f)
    // top: split into matte and glossy halves
    val ab = Offset((a.x + b.x) / 2, (a.y + b.y) / 2); val dc = Offset((d.x + c.x) / 2, (d.y + c.y) / 2)
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(ab.x, ab.y); lineTo(dc.x, dc.y); lineTo(d.x, d.y); close() },
        Brush.linearGradient(listOf(Color(0xFF2E2E3A), Color(0xFF17171F)), start = a, end = dc))
    drawPath(Path().apply { moveTo(ab.x, ab.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(dc.x, dc.y); close() },
        Brush.linearGradient(listOf(Color(0xFF3B3B4A), Color(0xFF0C0C12), Color(0xFF262633)), start = b, end = dc))
    // gloss reflection
    drawLine(Color.White.copy(alpha = 0.35f), Offset(ab.x + w * 0.06f, ab.y + h * 0.01f), Offset(b.x - w * 0.02f, b.y + h * 0.035f), strokeWidth = w * 0.01f, cap = StrokeCap.Round)
    // light bar along the split
    for (i in 5 downTo 1) {
        drawLine(glow.copy(alpha = 0.16f * (6 - i)), ab, dc, strokeWidth = i * 2.6f, cap = StrokeCap.Round)
    }
    // edges
    drawLine(Color.White.copy(alpha = 0.25f), a, d, 1.5f); drawLine(Color.White.copy(alpha = 0.25f), d, c, 1.5f)
}

/** PS3: glossy black curved-top body with silver band and red power LED. */
private fun DrawScope.drawPs3(glow: Color) {
    val w = size.width; val h = size.height
    val l = w * 0.08f; val r = w * 0.92f
    val topY = h * 0.40f; val baseY = h * 0.68f
    val body = Path().apply {
        moveTo(l, topY + h * 0.06f)
        quadraticBezierTo(w * 0.5f, topY - h * 0.12f, r, topY + h * 0.06f)
        lineTo(r, baseY)
        quadraticBezierTo(w * 0.5f, baseY + h * 0.05f, l, baseY)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFF3A3A44), Color(0xFF0A0A0E), Color(0xFF16161C)), startY = topY - h * 0.05f, endY = baseY))
    // top gloss
    val gloss = Path().apply {
        moveTo(l + w * 0.06f, topY + h * 0.045f)
        quadraticBezierTo(w * 0.5f, topY - h * 0.085f, r - w * 0.06f, topY + h * 0.045f)
        quadraticBezierTo(w * 0.5f, topY - h * 0.03f, l + w * 0.06f, topY + h * 0.045f)
        close()
    }
    drawPath(gloss, Color.White.copy(alpha = 0.32f))
    // silver band
    val band = Path().apply {
        moveTo(l, baseY - h * 0.05f)
        quadraticBezierTo(w * 0.5f, baseY, r, baseY - h * 0.05f)
        lineTo(r, baseY - h * 0.03f)
        quadraticBezierTo(w * 0.5f, baseY + h * 0.02f, l, baseY - h * 0.03f)
        close()
    }
    drawPath(band, Brush.horizontalGradient(listOf(Color(0xFF8C8F99), Color(0xFFF2F3F6), Color(0xFF8C8F99))))
    // disc slot
    drawLine(Color.Black, Offset(w * 0.22f, topY + h * 0.11f), Offset(w * 0.62f, topY + h * 0.11f), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.12f), Offset(w * 0.22f, topY + h * 0.122f), Offset(w * 0.62f, topY + h * 0.122f), strokeWidth = 1.2f)
    // power LED
    for (i in 4 downTo 1) drawCircle(glow.copy(alpha = 0.2f * (5 - i)), w * 0.006f * i, Offset(w * 0.80f, topY + h * 0.11f))
    drawCircle(Color.White, w * 0.004f, Offset(w * 0.80f, topY + h * 0.11f))
    // outline
    drawPath(body, Color.White.copy(alpha = 0.12f), style = Stroke(1.5f))
}
