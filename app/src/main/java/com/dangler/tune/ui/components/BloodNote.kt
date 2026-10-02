package com.dangler.tune.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tan

/**
 * HERO v2 — ландшафтный минимализм: нота на весь экран.
 *
 * Размер глифа считается от ВЫСОТЫ канваса, кровь течёт слева (ниже)
 * или справа (выше), уровень = |cents| / 50. В точке — кислотная вспышка.
 * Заливка строго внутри глифов (saveLayer + SrcIn), поверхность жидкости
 * наклоняется с гироскопом + бегущая волна. Пузырьковый уровень — в углу.
 * База — стальной градиент с тиснением (nu-metal).
 */
@Composable
fun BloodNote(
    note: String,
    cents: Float,
    inTune: Boolean,
    hasSignal: Boolean,
    tiltRollDeg: Float,
    tiltPitchDeg: Float,
    blood: Color,
    bloodDark: Color,
    accent: Color,
    steel: Color,
    steelDark: Color,
    dim: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val infinite = rememberInfiniteTransition(label = "blood")

    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "wave"
    )
    val breath by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // глиф ~58% высоты экрана
        val fontSp = (h * 0.52f).toSp()
        val style = TextStyle(
            fontSize = fontSp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-6).sp,
        )
        val layout = measurer.measure(note.ifBlank { "—" }, style)
        val tw = layout.size.width.toFloat()
        val th = layout.size.height.toFloat()
        val fit = (w * 0.94f / tw).coerceAtMost(1f)

        scale(fit, fit, pivot = Offset(cx, cy)) {
            val left = cx - tw / 2f
            val right = cx + tw / 2f
            val top = cy - th / 2f
            val bottom = cy + th / 2f
            val origin = Offset(left, top)

            if (inTune && hasSignal) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.40f * breath), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = tw * 0.7f * (0.95f + 0.1f * breath),
                    ),
                    radius = tw * 0.7f,
                    center = Offset(cx, cy),
                )
            }

            // тиснение: тёмный сдвиг под сталью
            drawText(
                textLayoutResult = layout,
                color = Color.Black.copy(alpha = 0.65f),
                topLeft = origin + Offset(5f, 7f),
            )
            // база: сталь, если молчит — тусклая
            if (hasSignal) {
                drawText(
                    textLayoutResult = layout,
                    brush = Brush.verticalGradient(
                        listOf(steel, steelDark),
                        startY = top,
                        endY = bottom,
                    ),
                    topLeft = origin,
                )
            } else {
                drawText(
                    textLayoutResult = layout,
                    color = dim.copy(alpha = 0.30f),
                    topLeft = origin,
                )
            }

            // центральный датчик — едва видимая риска
            drawLine(
                color = Color.White.copy(alpha = 0.13f),
                start = Offset(cx, top - 12f),
                end = Offset(cx, bottom + 12f),
                strokeWidth = 2f,
            )

            val fillFrac = if (inTune) 1f else (abs(cents) / 50f).coerceIn(0f, 1f)
            if (hasSignal && fillFrac > 0.005f) {
                val fillBrush = if (inTune) {
                    Brush.verticalGradient(
                        listOf(Color(0xFF8DFFB9), accent, Color(0xFF1FBF5F)),
                        startY = top,
                        endY = bottom,
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(blood, bloodDark),
                        startY = top,
                        endY = bottom,
                    )
                }

                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                drawText(textLayoutResult = layout, color = Color.White, topLeft = origin)

                if (fillFrac >= 0.999f) {
                    drawRect(brush = fillBrush, blendMode = BlendMode.SrcIn)
                } else {
                    val theta = tiltRollDeg.coerceIn(-20f, 20f) * PI.toFloat() / 180f
                    val slope = tan(theta)
                    val anchor = if (cents < 0) left + fillFrac * tw else right - fillFrac * tw
                    val waveAmp = 8f
                    val waveLen = th / 1.5f
                    val topExt = top - 80f
                    val botExt = bottom + 80f

                    fun surfaceX(y: Float): Float =
                        anchor + (y - cy) * slope +
                                waveAmp * sin(2 * PI.toFloat() * (y - cy) / waveLen + wavePhase)

                    val path = Path()
                    val farX = if (cents < 0) left - 100f else right + 100f
                    path.moveTo(farX, topExt)
                    var y = topExt
                    while (y <= botExt) {
                        path.lineTo(surfaceX(y), y)
                        y += 12f
                    }
                    path.lineTo(farX, botExt)
                    path.close()
                    drawPath(path = path, brush = fillBrush, blendMode = BlendMode.SrcIn)
                }
                drawContext.canvas.restore()
            }
        }

        // ── пузырьковый уровень в углу ──
        val r = 13.dp.toPx()
        val bc = Offset(w - 34.dp.toPx(), h - 30.dp.toPx())
        drawCircle(color = dim.copy(alpha = 0.5f), radius = r, center = bc, style = Stroke(width = 2f))
        drawCircle(color = dim.copy(alpha = 0.6f), radius = 2f, center = bc)
        val dx = (tiltRollDeg / 25f).coerceIn(-1f, 1f) * r * 0.6f
        val dy = (tiltPitchDeg / 25f).coerceIn(-1f, 1f) * r * 0.6f
        val levelOk = abs(tiltRollDeg) < 4f && abs(tiltPitchDeg) < 6f
        drawCircle(
            color = if (levelOk && inTune) accent else Color.White.copy(alpha = 0.85f),
            radius = 5.dp.toPx(),
            center = Offset(bc.x + dx, bc.y + dy),
        )
    }
}
