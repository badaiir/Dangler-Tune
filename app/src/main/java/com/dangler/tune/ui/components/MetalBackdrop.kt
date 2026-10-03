package com.dangler.tune.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Задник v3 — чистое железо + PS3-ленты.
 * Никаких рамок и болтов: градиент, шлифовка, три медленные полупрозрачные
 * волны-ленты через весь экран (как фон PS3, только кровавые) и виньетка.
 */
@Composable
fun MetalBackdrop(
    baseTop: Color,
    baseBottom: Color,
    waveTint: Color,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "ps3")
    val p1 by infinite.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(9000, easing = LinearEasing)), "r1"
    )
    val p2 by infinite.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(13000, easing = LinearEasing)), "r2"
    )
    val p3 by infinite.animateFloat(
        (2 * PI).toFloat(), 0f,
        infiniteRepeatable(tween(17000, easing = LinearEasing)), "r3"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(listOf(baseTop, baseBottom)),
            size = size,
        )

        // шлифовка металла
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

        // PS3-ленты: медленные, едва видимые
        fun ribbon(baseY: Float, amp: Float, len: Float, phase: Float, color: Color, alpha: Float, width: Float) {
            val path = Path()
            var x = -40f
            path.moveTo(x, baseY + amp * sin(x / len + phase))
            x += 10f
            while (x <= w + 40f) {
                path.lineTo(x, baseY + amp * sin(x / len + phase))
                x += 10f
            }
            drawPath(
                path = path,
                color = color.copy(alpha = alpha),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = width),
            )
        }
        ribbon(h * 0.28f, h * 0.05f, w / 3.2f, p1, waveTint, 0.055f, 130f)
        ribbon(h * 0.28f, h * 0.05f, w / 3.2f, p1 + 0.5f, Color.White, 0.03f, 46f)
        ribbon(h * 0.58f, h * 0.07f, w / 2.6f, p2, waveTint, 0.045f, 170f)
        ribbon(h * 0.58f, h * 0.07f, w / 2.6f, p2 + 0.5f, Color.White, 0.025f, 60f)
        ribbon(h * 0.84f, h * 0.05f, w / 3.8f, p3, waveTint, 0.05f, 110f)

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
