package com.dangler.tune.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * HERO v5 — настоящая жидкость.
 *
 * - Двухслойная волна: задний слой (прозрачный, сдвиг фазы) + передний +
 *   светлый блик-мениск по кромке — читается как жидкость, а не полоски.
 * - Волна медленная и гладкая (шаг 4px, две гармоники синуса, период 3.2с).
 * - Направление СТРЕЛКАМИ: flat (низко) — ▲ подтяни, sharp (высоко) — ▼ ослабь.
 *   Стрелки плывут в свою сторону и дышат. В точке их нет — там вспышка.
 * - Глиф дышит и плющится на пружине, буквы вдавлены штампом.
 * - Заливка строго внутри глифов: saveLayer + маска текста + SrcIn.
 */
@Composable
fun BloodNote(
    note: String,
    cents: Float,
    inTune: Boolean,
    hasSignal: Boolean,
    inTuning: Boolean,
    blood: Color,
    bloodDark: Color,
    chroma: Color,
    chromaDark: Color,
    accent: Color,
    steel: Color,
    steelDark: Color,
    dim: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val infinite = rememberInfiniteTransition(label = "blood")

    val smoothCents by animateFloatAsState(
        targetValue = cents,
        animationSpec = tween(120),
        label = "cents"
    )
    var side by remember { mutableFloatStateOf(-1f) }
    if (abs(cents) > 2.5f) side = if (cents < 0) -1f else 1f

    val fillAmt = if (inTune) 1f else (abs(smoothCents) / 50f).coerceIn(0f, 1f)

    val squashX by animateFloatAsState(
        targetValue = 1f + 0.045f * fillAmt,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
        label = "sqx"
    )
    val squashY by animateFloatAsState(
        targetValue = 1f - 0.05f * fillAmt,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
        label = "sqy"
    )
    val pop by animateFloatAsState(
        targetValue = if (inTune && hasSignal) 1.045f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 260f),
        label = "pop"
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (hasSignal) 1f else 0f,
        animationSpec = tween(250),
        label = "fillA"
    )

    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "wave"
    )
    val breath by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    val wobble = if (hasSignal) sin(wavePhase) * 1.2f * fillAmt else 0f
    // цвет жидкости одним взглядом: кислота / кровь / янтарь
    val liquidTop = when {
        inTune -> Color(0xFF8DFFB9)
        inTuning -> blood
        else -> chroma
    }
    val liquidBottom = when {
        inTune -> Color(0xFF1FBF5F)
        inTuning -> bloodDark
        else -> chromaDark
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // глиф НА ВЕСЬ ЭКРАН: 66% высоты, ужатие только по ширине
        val fontSp = (h * 0.66f).toSp()
        val style = TextStyle(
            fontSize = fontSp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-6).sp,
        )
        val layout = measurer.measure(note.ifBlank { "—" }, style)
        val tw = layout.size.width.toFloat()
        val th = layout.size.height.toFloat()
        val fit = (w * 0.96f / tw).coerceAtMost(1f)

        rotate(wobble, pivot = Offset(cx, cy)) {
            scale(fit * squashX * pop, fit * squashY * pop, pivot = Offset(cx, cy)) {
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

                // штамп в металле
                drawText(
                    textLayoutResult = layout,
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = origin + Offset(2.5f, 3.5f),
                )
                drawText(
                    textLayoutResult = layout,
                    color = Color.Black.copy(alpha = 0.55f),
                    topLeft = origin + Offset(-2.5f, -2.5f),
                )
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
                        color = dim.copy(alpha = 0.32f),
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

                if (fillAlpha > 0.01f && fillAmt > 0.005f) {
                    val fillBrush = if (inTune) {
                        Brush.verticalGradient(
                            listOf(liquidTop, accent, liquidBottom),
                            startY = top,
                            endY = bottom,
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(liquidTop, liquidBottom),
                            startY = top,
                            endY = bottom,
                        )
                    }
                    val anchor = if (side < 0) left + fillAmt * tw else right - fillAmt * tw
                    val waveAmp = (th * 0.035f).coerceIn(4f, 10f)
                    val waveLen = th / 1.5f
                    val topExt = top - 80f
                    val botExt = bottom + 80f

                    // живая поверхность: две гармоники — гладко, без граней
                    fun surfaceX(y: Float, phase: Float): Float =
                        anchor +
                                waveAmp * sin(2 * PI.toFloat() * (y - cy) / waveLen + phase) * 0.7f +
                                waveAmp * 0.6f * sin(2 * PI.toFloat() * (y - cy) / (waveLen * 0.55f) - phase * 0.7f + 1.3f) * 0.3f

                    fun fillPath(phase: Float, edgeShift: Float): Path {
                        val path = Path()
                        val farX = if (side < 0) left - 100f else right + 100f
                        path.moveTo(farX, topExt)
                        var y = topExt
                        while (y <= botExt) {
                            path.lineTo(surfaceX(y, phase) + edgeShift, y)
                            y += 4f
                        }
                        path.lineTo(farX, botExt)
                        path.close()
                        return path
                    }

                    // кромка-мениск: светлая линия ровно по волне
                    fun meniscusPath(phase: Float): Path {
                        val path = Path()
                        var y = topExt
                        path.moveTo(surfaceX(y, phase), y)
                        y += 4f
                        while (y <= botExt) {
                            path.lineTo(surfaceX(y, phase), y)
                            y += 4f
                        }
                        return path
                    }

                    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                    drawText(textLayoutResult = layout, color = Color.White, topLeft = origin)

                    if (fillAmt >= 0.999f) {
                        drawRect(brush = fillBrush, alpha = fillAlpha, blendMode = BlendMode.SrcIn)
                    } else {
                        // задний слой волны — прозрачный, со сдвигом (глубина жидкости)
                        drawPath(
                            path = fillPath(wavePhase + PI.toFloat(), if (side < 0) 26f else -26f),
                            brush = fillBrush,
                            alpha = fillAlpha * 0.35f,
                            blendMode = BlendMode.SrcIn,
                        )
                        // передний слой
                        drawPath(
                            path = fillPath(wavePhase, 0f),
                            brush = fillBrush,
                            alpha = fillAlpha,
                            blendMode = BlendMode.SrcIn,
                        )
                        // блик по кромке
                        drawPath(
                            path = meniscusPath(wavePhase),
                            color = Color.White.copy(alpha = 0.5f * fillAlpha),
                            style = Stroke(width = 4f),
                            blendMode = BlendMode.SrcIn,
                        )
                    }
                    drawContext.canvas.restore()
                }
            }
        }

        // ── СТРЕЛКИ: flat — ▲ подтяни, sharp — ▼ ослабь. Плывут и дышат ──
        if (hasSignal && !inTune) {
            val up = smoothCents < 0
            val arrowCol = if (inTuning) blood else chroma
            val slide = ((wavePhase / (2 * PI).toFloat()) % 1f + 1f) % 1f
            for (k in 0..1) {
                val t = (slide + k * 0.5f) % 1f
                val a = sin(t * PI.toFloat()).coerceIn(0f, 1f)
                val travel = 46f
                // якоря в полях над/под глифом: пересчитываем грубо от центра канваса
                val baseY = if (up) h * 0.10f else h * 0.90f
                val ay = if (up) baseY + t * travel else baseY - t * travel
                val s = 26f // полуразмер стрелки
                val tri = Path().apply {
                    if (up) {
                        moveTo(cx - s, ay + s * 0.7f)
                        lineTo(cx + s, ay + s * 0.7f)
                        lineTo(cx, ay - s * 0.7f)
                    } else {
                        moveTo(cx - s, ay - s * 0.7f)
                        lineTo(cx + s, ay - s * 0.7f)
                        lineTo(cx, ay + s * 0.7f)
                    }
                    close()
                }
                drawPath(
                    path = tri,
                    color = arrowCol.copy(alpha = (0.20f + 0.50f * a) * fillAlpha),
                )
            }
        }
    }
}
