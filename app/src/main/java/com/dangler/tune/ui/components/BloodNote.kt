package com.dangler.tune.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * HERO v6 — вода и радость.
 *
 * Жидкость без полосок: один слой волны + мягкая пенная кромка (3 гаснущих штриха
 * вместо жёсткой белой линии) + задний слой УБРАН (он и давал параллельные полосы).
 * Волна медленная и гладкая: две гармоники синуса, период 4с, шаг 4px.
 * Направление стрелками: flat — ▲ подтяни, sharp — ▼ ослабь.
 * В точке — JOJO-ВЗРЫВ РАДОСТИ: speed lines + расходящееся кольцо + ゴゴゴ + вспышка.
 * Глиф дышит и плющится на пружине, буквы вдавлены штампом.
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
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "wave"
    )
    val breath by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    // взрыв радости: срабатывает один раз на вход в точку
    var burst by remember { mutableFloatStateOf(1f) } // 0→1 прогресс, 1 = покой
    LaunchedEffect(inTune, hasSignal) {
        if (inTune && hasSignal) {
            animate(0f, 1f, animationSpec = tween(700, easing = FastOutSlowInEasing)) { v, _ ->
                burst = v
            }
        } else {
            burst = 1f
        }
    }

    val wobble = if (hasSignal) sin(wavePhase) * 0.8f * fillAmt else 0f
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
    val foam = when {
        inTune -> Color(0xFFC9FFE0)
        inTuning -> Color(0xFFFF8080)
        else -> Color(0xFFFFD98A)
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
        val left = cx - tw * fit / 2f
        val right = cx + tw * fit / 2f
        val top = cy - th * fit / 2f
        val bottom = cy + th * fit / 2f

        // ── JOJO-ВЗРЫВ (поверх всего, не клиппится глифами) ──
        if (burst < 1f) {
            val p = burst
            val fade = 1f - p
            // вспышка
            drawRect(color = Color.White.copy(alpha = 0.14f * fade))
            // расходящееся кольцо
            drawCircle(
                color = accent.copy(alpha = 0.55f * fade),
                radius = tw * 0.35f + tw * 0.55f * p,
                center = Offset(cx, cy),
                style = Stroke(width = 10f * fade + 2f),
            )
            // speed lines
            val rays = 26
            for (i in 0 until rays) {
                val ang = 2 * PI.toFloat() * i / rays + p * 0.35f
                val r0 = tw * (0.42f + 0.10f * p)
                val r1 = r0 + 60f + 200f * p
                drawLine(
                    color = Color.White.copy(alpha = 0.50f * fade),
                    start = Offset(cx + cos(ang) * r0, cy + sin(ang) * r0),
                    end = Offset(cx + cos(ang) * r1, cy + sin(ang) * r1),
                    strokeWidth = 7f * fade + 1f,
                )
            }
            // ゴゴゴ — menacing symbols вокруг
            val goStyle = TextStyle(
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = steel.copy(alpha = 0.55f * fade),
            )
            val spots = listOf(
                Offset(left - 60f, top - 40f),
                Offset(right + 10f, top + th * fit * 0.2f),
                Offset(left - 40f, bottom - 60f),
                Offset(right - 40f, bottom + 10f),
                Offset(cx - 40f, top - 90f),
            )
            val gos = listOf("ゴ", "ゴ", "ゴ", "ド", "ン")
            spots.forEachIndexed { idx, off ->
                val gl = measurer.measure(gos[idx], goStyle)
                drawText(gl, color = steel.copy(alpha = 0.55f * fade), topLeft = off)
            }
        }

        rotate(wobble, pivot = Offset(cx, cy)) {
            scale(fit * squashX * pop, fit * squashY * pop, pivot = Offset(cx, cy)) {
                // координаты ВНУТРИ трансформа (глифные, до fit)
                val gLeft = cx - tw / 2f
                val gRight = cx + tw / 2f
                val gTop = cy - th / 2f
                val gBottom = cy + th / 2f
                val origin = Offset(gLeft, gTop)

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
                            startY = gTop,
                            endY = gBottom,
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
                    start = Offset(cx, gTop - 12f),
                    end = Offset(cx, gBottom + 12f),
                    strokeWidth = 2f,
                )

                if (fillAlpha > 0.01f && fillAmt > 0.005f) {
                    val fillBrush = if (inTune) {
                        Brush.verticalGradient(
                            listOf(liquidTop, accent, liquidBottom),
                            startY = gTop,
                            endY = gBottom,
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(liquidTop, liquidBottom),
                            startY = gTop,
                            endY = gBottom,
                        )
                    }
                    val anchor = if (side < 0) gLeft + fillAmt * tw else gRight - fillAmt * tw
                    val waveAmp = (th * 0.035f).coerceIn(4f, 10f)
                    val waveLen = th / 1.5f
                    val topExt = gTop - 80f
                    val botExt = gBottom + 80f

                    fun surfaceX(y: Float, phase: Float): Float =
                        anchor +
                                waveAmp * sin(2 * PI.toFloat() * (y - cy) / waveLen + phase) * 0.7f +
                                waveAmp * 0.6f * sin(2 * PI.toFloat() * (y - cy) / (waveLen * 0.55f) - phase * 0.7f + 1.3f) * 0.3f

                    fun surfacePath(phase: Float): Path {
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
                        // тело воды
                        val body = Path().apply {
                            val farX = if (side < 0) gLeft - 100f else gRight + 100f
                            moveTo(farX, topExt)
                            var y = topExt
                            while (y <= botExt) {
                                lineTo(surfaceX(y, wavePhase), y)
                                y += 4f
                            }
                            lineTo(farX, botExt)
                            close()
                        }
                        drawPath(path = body, brush = fillBrush, alpha = fillAlpha, blendMode = BlendMode.SrcIn)
                        // пенная кромка: 3 гаснущих штриха — мягко, без белой полосы
                        val surf = surfacePath(wavePhase)
                        drawPath(surf, color = foam.copy(alpha = 0.14f * fillAlpha), style = Stroke(13f), blendMode = BlendMode.SrcIn)
                        drawPath(surf, color = foam.copy(alpha = 0.24f * fillAlpha), style = Stroke(7f), blendMode = BlendMode.SrcIn)
                        drawPath(surf, color = foam.copy(alpha = 0.38f * fillAlpha), style = Stroke(3.5f), blendMode = BlendMode.SrcIn)
                    }
                    drawContext.canvas.restore()
                }
            }
        }

        // ── СТРЕЛКИ: flat — ▲ подтяни, sharp — ▼ ослабь ──
        if (hasSignal && !inTune) {
            val up = smoothCents < 0
            val arrowCol = if (inTuning) blood else chroma
            val slide = ((wavePhase / (2 * PI).toFloat()) % 1f + 1f) % 1f
            for (k in 0..1) {
                val t = (slide + k * 0.5f) % 1f
                val a = sin(t * PI.toFloat()).coerceIn(0f, 1f)
                val travel = 46f
                val baseY = if (up) h * 0.10f else h * 0.90f
                val ay = if (up) baseY + t * travel else baseY - t * travel
                val s = 26f
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
