package com.dangler.tune.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class DanglerTheme(
    val name: String,
    val background: Color,
    val surface: Color,
    val accent: Color,      // цвет "в точке"
    val sharp: Color,       // диез/выше
    val flat: Color,        // ниже
    val blood: Color,       // заливка ноты
    val bloodDark: Color,
    val textMain: Color,
    val textDim: Color,
    val strobe: Color,
    val steel: Color,
    val steelDark: Color,
    val chroma: Color,      // нота НЕ из строя
    val chromaDark: Color,
)

val ThemeBrutal = DanglerTheme(
    name = "Brutal Black",
    background = Color(0xFF0A0A0B),
    surface = Color(0xFF141416),
    accent = Color(0xFF3DFF88),  // кислотно-зелёный
    sharp = Color(0xFFFF3B3B),
    flat = Color(0xFFFFB020),
    blood = Color(0xFFFF2B2B),
    bloodDark = Color(0xFFB3001B),
    textMain = Color(0xFFF2F2F2),
    textDim = Color(0xFF8A8A93),
    strobe = Color(0xFFE8E8E8),
    steel = Color(0xFFEDEDEF),
    steelDark = Color(0xFF55555C),
    chroma = Color(0xFFFFB020),
    chromaDark = Color(0xFF7A4D00),
)

val ThemeBlood = DanglerTheme(
    name = "Blood Stage",
    background = Color(0xFF120607),
    surface = Color(0xFF1E0A0B),
    accent = Color(0xFF3DFF88),
    sharp = Color(0xFFFF4D4D),
    flat = Color(0xFFFFB020),
    blood = Color(0xFFFF3B30),
    bloodDark = Color(0xFF8F0E14),
    textMain = Color(0xFFF5EDED),
    textDim = Color(0xFFA07E7E),
    strobe = Color(0xFFFF2E2E),
    steel = Color(0xFFF5E8E8),
    steelDark = Color(0xFF5C4A4A),
    chroma = Color(0xFFFFB020),
    chromaDark = Color(0xFF7A4D00),
)

val ThemeAcid = DanglerTheme(
    name = "Acid Rehearsal",
    background = Color(0xFF070B06),
    surface = Color(0xFF0E150C),
    accent = Color(0xFFB6FF2E),
    sharp = Color(0xFFFF3B3B),
    flat = Color(0xFF2EC4FF),
    blood = Color(0xFFFF4D2E),
    bloodDark = Color(0xFF9E1B0E),
    textMain = Color(0xFFEFFFE0),
    textDim = Color(0xFF7E9070),
    strobe = Color(0xFFB6FF2E),
    steel = Color(0xFFEFFFE0),
    steelDark = Color(0xFF4E5C43),
    chroma = Color(0xFFFFB020),
    chromaDark = Color(0xFF7A4D00),
)

val ALL_THEMES = listOf(ThemeBrutal, ThemeBlood, ThemeAcid)

val MaterialBrutal = darkColorScheme(
    background = ThemeBrutal.background,
    surface = ThemeBrutal.surface,
    primary = ThemeBrutal.accent,
    onBackground = ThemeBrutal.textMain,
    onSurface = ThemeBrutal.textMain,
)
