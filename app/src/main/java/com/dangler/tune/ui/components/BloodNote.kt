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
 * HERO тюнера: гигантская нота (F#2), которая наполняется "кровью".
 *
 * - Ниже строя (cents < 0) — кровь течёт СЛЕВА, выше — СПРАВА.
 * - Уровень = |cents| / 50. В точке — нота целиком вспыхивает кислотным.
 * - Поверхность жидкости наклоняется с гироскопом (roll), плюс лёгкая волна.
 * - Заливка строго внутри глифов: saveLayer + маска текста + SrcIn.
 * - Снизу — пузырьковый уровень: наклони телефон полубоком и найди центр.
 * - Дыхание glow — по канону TactileConnectButton (tween 1800 Reverse).
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
    dim: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val infinite = rememberInfiniteTransition(label = "blood")

    // бегущая волна поверхности
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "wave"
    )
    // дыхание glow в точке
    val breath by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bubbleZone = 44.dp.toPx()
        val cx = w / 2f
        val cy = (h - bubbleZone) / 2f

        val style = TextStyle(
            fontSize = 148.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-4).sp,
        )
        val layout = measurer.measure(note.ifBlank { "—" }, style)
        val tw = layout.size.width.toFloat()
        val th = layout.size.height.toFloat()
        // только ужатие под ширину, растягивать короткие ("—") не надо
        val fit = (w * 0.92f / tw).coerceAtMost(1f)

        scale(fit, fit, pivot = Offset(cx, cy)) {
            val left = cx - tw / 2f
            val right = cx + tw / 2f
            val top = cy - th / 2f
            val bottom = cy + th / 2f
            val origin = Offset(left, top)

            // glow-подложка в точке
            if (inTune && hasSignal) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.45f * breath), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = tw * 0.75f * (0.95f + 0.1f * breath),
                    ),
                    radius = tw * 0.75f,
                    center = Offset(cx, cy),
                )
            }

            // база: приглушённый символ
            drawText(
                textLayoutResult = layout,
                color = dim.copy(alpha = if (hasSignal) 0.45f else 0.20f),
                topLeft = origin,
            )

            // центральный датчик — тонкая риска
            drawLine(
                color = Color.White.copy(alpha = 0.16f),
                start = Offset(cx, top - 14f),
                end = Offset(cx, bottom + 14f),
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

                // маска: текст белым в отдельном слое
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                drawText(textLayoutResult = layout, color = Color.White, topLeft = origin)

                if (fillFrac >= 0.999f) {
                    // в точке — льём целиком
                    drawRect(brush = fillBrush, blendMode = BlendMode.SrcIn)
                } else {
                    // наклон поверхности от гироскопа + волна
                    val theta = tiltRollDeg.coerceIn(-20f, 20f) * PI.toFloat() / 180f
                    val slope = tan(theta)
                    val anchor = if (cents < 0) {
                        left + fillFrac * tw      // ниже — кровь слева
                    } else {
                        right - fillFrac * tw     // выше — кровь справа
                    }
                    val waveAmp = 7f
                    val waveLen = th / 1.5f
                    val topExt = top - 60f
                    val botExt = bottom + 60f

                    fun surfaceX(y: Float): Float =
                        anchor + (y - cy) * slope +
                                waveAmp * sin(2 * PI.toFloat() * (y - cy) / waveLen + wavePhase)

                    val path = Path()
                    if (cents < 0) {
                        val farX = left - 80f
                        path.moveTo(farX, topExt)
                        var y = topExt
                        while (y <= botExt) {
                            path.lineTo(surfaceX(y), y)
                            y += 10f
                        }
                        path.lineTo(farX, botExt)
                        path.close()
                    } else {
                        val farX = right + 80f
                        path.moveTo(farX, topExt)
                        var y = topExt
                        while (y <= botExt) {
                            path.lineTo(surfaceX(y), y)
                            y += 10f
                        }
                        path.lineTo(farX, botExt)
                        path.close()
                    }
                    drawPath(path = path, brush = fillBrush, blendMode = BlendMode.SrcIn)
                }
                drawContext.canvas.restore()
            }
        }

        // ── пузырьковый уровень ──
        val bc = Offset(cx, h - bubbleZone / 2f)
        val r = 17.dp.toPx()
        drawCircle(color = dim.copy(alpha = 0.5f), radius = r, center = bc, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        // центральная точка-мишень
        drawCircle(color = dim.copy(alpha = 0.6f), radius = 2.5f, center = bc)
        val dx = (tiltRollDeg / 25f).coerceIn(-1f, 1f) * r * 0.62f
        val dy = (tiltPitchDeg / 25f).coerceIn(-1f, 1f) * r * 0.62f
        val levelOk = abs(tiltRollDeg) < 4f && abs(tiltPitchDeg) < 6f
        drawCircle(
            color = if (levelOk && inTune) accent else Color.White.copy(alpha = 0.85f),
            radius = 6.dp.toPx(),
            center = Offset(bc.x + dx, bc.y + dy),
        )
    }
}
