package com.dangler.tune.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Peterson-style строб-полоса: диагональные штрихи едут влево/вправо
 * со скоростью ~ расстройке. В точке — замирают + зеленеют.
 */
@Composable
fun StrobeDisplay(
    cents: Float,
    phase: Float,
    inTune: Boolean,
    hasSignal: Boolean,
    strobeColor: Color,
    inTuneColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(54.dp)) {
        val w = size.width
        val h = size.height
        val stripeW = 26f
        val gap = 26f
        val period = stripeW + gap

        val speedFactor = (abs(cents) / 50f).coerceIn(0f, 1f)
        // чем больше расстройка — тем контрастнее/быстрее; при hasSignal=false — тускло
        val alpha = if (!hasSignal) 0.15f else 0.35f + 0.65f * speedFactor.coerceAtLeast(if (inTune) 1f else 0f)
        val color = if (inTune) inTuneColor else strobeColor

        // сдвиг: phase 0..1 * period, направление по знаку центов
        val dir = if (cents >= 0) 1f else -1f
        val offset = if (inTune) 0f else phase * period * dir

        var x = -period + (offset % period)
        while (x < w + period) {
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(x, h),
                end = Offset(x + 18f, 0f),
                strokeWidth = stripeW * 0.42f,
            )
            x += period
        }
        // центральная риска
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(w / 2, 0f),
            end = Offset(w / 2, h),
            strokeWidth = 3f,
        )
    }
}
