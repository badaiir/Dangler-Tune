package com.dangler.tune.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DanglerKit — дизайн-кит в духе GloryholeVPN (glass, пилюли, дыхание),
 * но с брутальной кровавой палитрой Dangler.
 *
 * Канон Gloryhole, который портируем 1-в-1 по механике:
 * - AutumnGlassCard: clip 24dp + glass-bg + border 1dp, без теней
 * - AutumnHeaderPill: капсула 14dp + горизонтальный градиент-бордер
 * - AutumnStatusPill: CircleShape + animateColorAsState бордера
 * - SettingsItemSwitch: ряд 16dp + иконка 32dp + Switch
 * - Slider: thumb/active в акцент, моно-подписи
 * - Dialog: 24dp, тёмный фон ~#F50F0B08, тонкий бордер
 */

// ── токены ──────────────────────────────────────────────
object DanglerStyle {
    const val CARD_RADIUS = 24
    const val PILL_RADIUS = 14
    const val SHEET_TOP_RADIUS = 28
    const val DIALOG_RADIUS = 24
    const val ROW_RADIUS = 16

    val glassBg = Color(0xAB100B0B)
    val glassBorder = Color(0x40FF2E2E)       // кровавый glass-бордер
    val glassBorderSubtle = Color(0x1FFFFFFF)
    val panelBg = Color(0xF5140B0B)           // фон шторки/диалога
    val scrim = Color(0x8C000000)

    /** Градиент-бордер для хедер-пилюли (кровь → тьма → кровь) */
    val headerBorder = Brush.horizontalGradient(
        listOf(
            Color(0xCCFF2E2E),
            Color(0x66410D0D),
            Color(0x44330000),
            Color(0x66FF2E2E),
            Color(0xCCFF2E2E),
        )
    )
}

/** Моно-подпись в духе Gloryhole label* (цифры — моноширинные) */
@Composable
fun MonoLabel(
    text: String,
    color: Color,
    fontSize: Int = 12,
    bold: Boolean = false,
    letterSpacing: Float = 1.5f,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        letterSpacing = letterSpacing.sp,
        modifier = modifier,
    )
}

/** Порт AutumnGlassCard: базовый glass-контейнер всего */
@Composable
fun DanglerGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = DanglerStyle.glassBorder,
    backgroundColor: Color = DanglerStyle.glassBg,
    shapeRadius: Int = DanglerStyle.CARD_RADIUS,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(shapeRadius.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(shapeRadius.dp))
    ) {
        content()
    }
}

/** Порт AutumnHeaderPill: flagship glass-капсула шапки */
@Composable
fun DanglerHeaderPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .wrapContentWidth(Alignment.CenterHorizontally)
            .clip(RoundedCornerShape(DanglerStyle.PILL_RADIUS.dp))
            .background(DanglerStyle.glassBg)
            .border(1.dp, DanglerStyle.headerBorder, RoundedCornerShape(DanglerStyle.PILL_RADIUS.dp))
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.wrapContentWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Порт AutumnStatusPill: пилюля статуса с дышащим бордером */
@Composable
fun DanglerStatusPill(
    text: String,
    isActive: Boolean,
    activeColor: Color,
    inactiveBorder: Color = DanglerStyle.glassBorder,
    textDim: Color,
    modifier: Modifier = Modifier,
) {
    val border by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.55f) else inactiveBorder,
        label = "pill_border"
    )
    val dot by animateColorAsState(
        targetValue = if (isActive) activeColor else textDim,
        label = "pill_dot"
    )
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0x991B0F0F))
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = if (isActive) activeColor else textDim,
            )
        }
    }
}

/** Заголовок секции настроек */
@Composable
fun SettingsSectionLabel(text: String, dim: Color) {
    MonoLabel(text = text, color = dim, fontSize = 11, letterSpacing = 2f)
}

/** Порт SettingsItemSwitch: ряд 16dp + иконка + свитч */
@Composable
fun DanglerSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    surface: Color,
    textMain: Color,
    textDim: Color,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DanglerStyle.ROW_RADIUS.dp))
            .background(surface)
            .clickable { onCheckedChange(!isChecked) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textMain)
            Text(subtitle, fontSize = 12.sp, color = textDim)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            )
        )
    }
}

/** Слайдер в духе DevTuningDialog: акцентный thumb + моно-значение */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DanglerSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    accent: Color,
    textMain: Color,
    textDim: Color,
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel(text = label, color = textDim, fontSize = 11, letterSpacing = 2f)
            MonoLabel(text = valueText, color = accent, fontSize = 13, bold = true)
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            )
        )
    }
}
