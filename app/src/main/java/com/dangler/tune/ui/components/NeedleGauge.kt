package com.dangler.tune.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Шкала-стрелка -50..+50 центов */
@Composable
fun NeedleGauge(
    cents: Float,
    inTune: Boolean,
    hasSignal: Boolean,
    accent: Color,
    sharp: Color,
    flat: Color,
    dim: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h * 0.92f
        val radius = h * 0.78f

        // дуга-шкала: тики каждые 10 центов
        for (c in -50..50 step 5) {
            val angle = (c / 50f) * 45f // -45..+45 град
            val isMajor = c % 10 == 0
            val r1 = radius * if (isMajor) 0.82f else 0.90f
            val r2 = radius
            val rad = Math.toRadians((angle - 90).toDouble())
            val cos = kotlin.math.cos(rad).toFloat()
            val sin = kotlin.math.sin(rad).toFloat()
            val col = when {
                abs(c) <= 5 -> accent.copy(alpha = 0.9f)
                c < 0 -> flat.copy(alpha = 0.7f)
                else -> sharp.copy(alpha = 0.7f)
            }
            drawLine(
                color = col,
                start = Offset(cx + cos * r1, cy + sin * r1),
                end = Offset(cx + cos * r2, cy + sin * r2),
                strokeWidth = if (isMajor) 5f else 2.5f,
            )
        }

        // стрелка
        if (hasSignal) {
            val a = ((cents.coerceIn(-50f, 50f) / 50f) * 45f)
            rotate(a, pivot = Offset(cx, cy)) {
                drawLine(
                    color = if (inTune) accent else Color.White,
                    start = Offset(cx, cy),
                    end = Offset(cx, cy - radius * 0.95f),
                    strokeWidth = 7f,
                )
            }
            // пятка
            drawCircle(
                color = if (inTune) accent else dim,
                radius = 14f,
                center = Offset(cx, cy),
            )
        } else {
            drawCircle(color = dim.copy(alpha = 0.5f), radius = 14f, center = Offset(cx, cy))
        }
    }
}
