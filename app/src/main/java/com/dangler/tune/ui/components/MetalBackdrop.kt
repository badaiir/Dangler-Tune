package com.dangler.tune.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.sin

/**
 * Nu-metal задник: почти чёрный градиент + едва видимая шлифовка металла
 * + виньетка по краям. Рисуется один раз под пейджером, ничего не весит.
 */
@Composable
fun MetalBackdrop(
    baseTop: Color,
    baseBottom: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(listOf(baseTop, baseBottom)),
            size = size,
        )

        // шлифовка: вертикальные волоски металла
        val lines = 110
        for (i in 0 until lines) {
            val x = w * i / lines
            val a = 0.012f + 0.02f * abs(sin(i * 12.9898f))
            drawLine(
                color = Color.White.copy(alpha = a),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f,
            )
        }

        // виньетка
        drawRect(
            brush = Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                center = Offset(w / 2f, h / 2f),
                radius = maxOf(w, h) * 0.62f,
            ),
            size = size,
        )
    }
}
