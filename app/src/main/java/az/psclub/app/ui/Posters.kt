package az.psclub.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import az.psclub.app.R
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

fun consoleImage(c: Console): Int = when (c) {
    Console.PS5 -> R.drawable.console_ps5
    Console.PS4 -> R.drawable.console_ps4
    Console.PS3 -> R.drawable.console_ps3
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
            val sy = if (big) 0.66f else 0.735f
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), center = Offset(w / 2, h * sy), radius = w * 0.34f * sh),
                topLeft = Offset(w / 2 - w * 0.34f * sh, h * sy - h * 0.035f), size = Size(w * 0.68f * sh, h * 0.07f),
            )
        }
        // the real console photo, rendered in a 3D layer with a floor reflection
        val img = painterResource(consoleImage(console))
        Column(
            Modifier
                .fillMaxSize(if (big) 0.78f else 0.84f)
                .align(Alignment.Center)
                .offset(y = if (big) (-14).dp else 6.dp)
                .graphicsLayer {
                    rotationY = f * 12f + tiltX
                    rotationX = 4f + tiltY
                    translationY = f * 5.dp.toPx()
                    cameraDistance = 14f * density
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                img, console.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(0.78f).fillMaxWidth()
                    .graphicsLayer { shadowElevation = 0f },
            )
            // mirrored, fading reflection
            Image(
                img, null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopCenter,
                modifier = Modifier.weight(0.22f).fillMaxWidth()
                    .graphicsLayer {
                        scaleY = -1f
                        alpha = 0.35f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
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
