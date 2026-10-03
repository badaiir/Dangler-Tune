package com.dangler.tune.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nu-metal задник v2 — пошарпанная табличка на двигателе.
 * Шлифовка + клёпаная рамка-плата с болтами по углам + царапины +
 * масляные потёки + виньетка. Всё статично, рисуется один раз под пейджером.
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

        // ── шлифовка металла ──
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

        // ── плата-табличка ──
        val m = 20.dp.toPx()
        val radius = 26.dp.toPx()
        val plate = Rect(m, m, w - m, h - m)
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.055f),
                    Color.White.copy(alpha = 0.015f),
                    Color.Black.copy(alpha = 0.10f),
                )
            ),
            topLeft = plate.topLeft,
            size = plate.size,
            cornerRadius = CornerRadius(radius, radius),
        )
        // кромка платы: стальной градиент + внутренняя тёмная линия (глубина)
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF4A4A52), Color(0xFF17171B), Color(0xFF3C3C44), Color(0xFF101013)),
                start = plate.topLeft,
                end = plate.bottomRight,
            ),
            topLeft = plate.topLeft,
            size = plate.size,
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 2.5f),
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.55f),
            topLeft = Offset(plate.left + 4f, plate.top + 4f),
            size = Size(plate.width - 8f, plate.height - 8f),
            cornerRadius = CornerRadius(radius - 4f, radius - 4f),
            style = Stroke(width = 1.5f),
        )

        // ── болты по углам ──
        val boltR = 8.dp.toPx()
        val bx = 20.dp.toPx()
        val corners = listOf(
            Offset(plate.left + bx, plate.top + bx),
            Offset(plate.right - bx, plate.top + bx),
            Offset(plate.left + bx, plate.bottom - bx),
            Offset(plate.right - bx, plate.bottom - bx),
        )
        corners.forEach { c ->
            // гнездо
            drawCircle(color = Color.Black.copy(alpha = 0.7f), radius = boltR + 2f, center = c)
            // шляпка
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF8A8A93), Color(0xFF2A2A2E)),
                    center = c + Offset(-boltR * 0.3f, -boltR * 0.3f),
                    radius = boltR * 1.4f,
                ),
                radius = boltR,
                center = c,
            )
            // шлиц
            drawLine(
                color = Color.Black.copy(alpha = 0.8f),
                start = c + Offset(-boltR * 0.55f, 0f),
                end = c + Offset(boltR * 0.55f, 0f),
                strokeWidth = 2.5f,
            )
        }

        // ── царапины (детерминированный псевдорандом — стабильны между кадрами) ──
        fun hash(i: Int, k: Int): Float = abs(sin(i * 12.9898f + k * 78.233f)) % 1f
        val scratchClip = Path().apply {
            addRoundRect(RoundRect(plate, CornerRadius(radius, radius)))
        }
        clipPath(scratchClip) {
            for (i in 0 until 46) {
                val sx = plate.left + hash(i, 1) * plate.width
                val sy = plate.top + hash(i, 2) * plate.height
                val len = 20f + hash(i, 3) * 70f
                val ang = (30f + hash(i, 4) * 60f) * Math.PI.toFloat() / 180f
                val light = hash(i, 5) > 0.45f
                drawLine(
                    color = if (light) Color.White.copy(alpha = 0.05f + hash(i, 6) * 0.05f)
                    else Color.Black.copy(alpha = 0.10f + hash(i, 6) * 0.10f),
                    start = Offset(sx, sy),
                    end = Offset(sx + cos(ang) * len, sy + sin(ang) * len),
                    strokeWidth = 1f + hash(i, 7) * 1.2f,
                )
            }
            // ── масляные потёки ──
            val stains = listOf(
                Triple(0.18f, 0.82f, 150.dp.toPx()),
                Triple(0.86f, 0.22f, 120.dp.toPx()),
                Triple(0.62f, 0.58f, 190.dp.toPx()),
            )
            stains.forEach { (fx, fy, r) ->
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(w * fx, h * fy),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(w * fx, h * fy),
                )
            }
        }

        // ── виньетка ──
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
