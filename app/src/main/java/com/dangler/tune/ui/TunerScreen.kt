package com.dangler.tune.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dangler.tune.model.Tunings
import com.dangler.tune.ui.components.NeedleGauge
import com.dangler.tune.ui.components.StrobeDisplay
import com.dangler.tune.ui.theme.ALL_THEMES
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(vm: TunerViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val theme = ALL_THEMES[state.themeIndex.coerceIn(ALL_THEMES.indices)]
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.onPermissionResult(granted) }

    LaunchedEffect(Unit) { vm.checkPermission(ctx) }

    // вау-эффект при попадании в точку: вибрация один раз на переход
    var wasInTune by remember { mutableStateOf(false) }
    LaunchedEffect(state.inTune) {
        if (state.inTune && !wasInTune) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        wasInTune = state.inTune
    }

    var showSettings by remember { mutableStateOf(false) }

    val noteColor by animateColorAsState(
        targetValue = when {
            !state.hasSignal -> theme.textDim
            state.inTune -> theme.accent
            state.cents > 0 -> theme.sharp
            else -> theme.flat
        },
        label = "noteColor"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (state.inTune && state.hasSignal) 0.55f else 0f,
        label = "glow"
    )

    Scaffold(
        containerColor = theme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "DANGLER",
                        color = theme.textMain,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )
                    Text(
                        "TUNE • ${state.tuning.name}",
                        color = theme.textDim,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, "Настройки", tint = theme.textDim)
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .background(theme.background)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!state.permissionGranted) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = Color.Black)
                ) { Text("Разрешить микрофон") }
                Spacer(Modifier.height(12.dp))
            }

            // --- СТРОБ (Peterson vibe) ---
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.textDim.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(vertical = 10.dp, horizontal = 12.dp)
            ) {
                StrobeDisplay(
                    cents = state.cents,
                    phase = state.strobePhase,
                    inTune = state.inTune,
                    hasSignal = state.hasSignal,
                    strobeColor = theme.strobe,
                    inTuneColor = theme.accent
                )
            }

            Spacer(Modifier.height(8.dp))

            // --- БОЛЬШАЯ НОТА с glow ---
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(210.dp)) {
                // glow-подложка
                Text(
                    text = state.noteName.ifBlank { "—" },
                    fontSize = 170.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.accent.copy(alpha = glowAlpha * 0.35f),
                    modifier = Modifier.blur(42.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = state.noteName.ifBlank { "—" },
                    fontSize = 150.sp,
                    fontWeight = FontWeight.Black,
                    color = noteColor,
                    textAlign = TextAlign.Center
                )
            }

            // частота + цель + центы
            Text(
                if (state.hasSignal) String.format("%.1f Hz  →  %.1f Hz", state.frequencyHz, state.targetFreq)
                else "сыграй струну…",
                color = theme.textDim, fontSize = 15.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    !state.hasSignal -> "слушаю…"
                    state.inTune -> "★ В ТОЧКЕ ★"
                    state.cents > 0 -> "▲  +${abs(state.cents).toInt()} cents — ослабь"
                    else -> "▼  −${abs(state.cents).toInt()} cents — подтяни"
                },
                color = noteColor, fontWeight = FontWeight.Bold, fontSize = 17.sp
            )

            Spacer(Modifier.height(6.dp))

            NeedleGauge(
                cents = state.cents,
                inTune = state.inTune,
                hasSignal = state.hasSignal,
                accent = theme.accent,
                sharp = theme.sharp,
                flat = theme.flat,
                dim = theme.textDim
            )

            Spacer(Modifier.weight(1f))

            // --- струны строя ---
            Text("СТРУНЫ • ${state.tuning.subtitle}", color = theme.textDim, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                state.tuning.strings.sortedBy { it.stringNumber }.forEach { s ->
                    val active = state.activeString?.stringNumber == s.stringNumber
                    val bg = if (active && state.inTune) theme.accent
                    else if (active) theme.textMain else theme.surface
                    val fg = if (active && state.inTune) Color.Black
                    else if (active) Color.Black else theme.textMain
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .border(
                                1.dp,
                                if (active) theme.accent else theme.textDim.copy(alpha = 0.3f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.name, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${s.stringNumber}", color = if (active) fg.copy(alpha = 0.7f) else theme.textDim, fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            containerColor = theme.surface,
            contentColor = theme.textMain
        ) {
            Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("СТРОЙ", color = theme.textDim, fontSize = 12.sp, letterSpacing = 2.sp)
                Tunings.ALL.forEach { t ->
                    val selected = t.id == state.tuning.id
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) theme.accent.copy(alpha = 0.15f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) theme.accent else theme.textDim.copy(alpha = 0.25f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { vm.selectTuning(t) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(t.name, fontWeight = FontWeight.Bold, color = if (selected) theme.accent else theme.textMain)
                            Text(t.subtitle, color = theme.textDim, fontSize = 12.sp)
                        }
                        if (selected) Text("●", color = theme.accent)
                    }
                }

                Text("ТЕМА", color = theme.textDim, fontSize = 12.sp, letterSpacing = 2.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ALL_THEMES.forEachIndexed { i, th ->
                        FilterChip(
                            selected = state.themeIndex == i,
                            onClick = { vm.selectTheme(i) },
                            label = { Text(th.name) }
                        )
                    }
                }

                Text("ТОЧНОСТЬ: ±${state.toleranceCents.toInt()} cents", color = theme.textDim, fontSize = 12.sp)
                Slider(
                    value = state.toleranceCents,
                    onValueChange = { vm.setTolerance(it) },
                    valueRange = 2f..12f, steps = 9
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
