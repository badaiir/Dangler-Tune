package com.dangler.tune.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dangler.tune.model.Tunings
import com.dangler.tune.ui.components.BloodNote
import com.dangler.tune.ui.components.DanglerGlassCard
import com.dangler.tune.ui.components.DanglerHeaderPill
import com.dangler.tune.ui.components.DanglerSliderRow
import com.dangler.tune.ui.components.DanglerStatusPill
import com.dangler.tune.ui.components.DanglerStyle
import com.dangler.tune.ui.components.DanglerSwitchRow
import com.dangler.tune.ui.components.MonoLabel
import com.dangler.tune.ui.components.SettingsSectionLabel
import com.dangler.tune.ui.components.StrobeDisplay
import com.dangler.tune.ui.theme.ALL_THEMES
import kotlin.math.abs

@Composable
fun TunerScreen(vm: TunerViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val tilt by vm.tilt.collectAsState()
    val theme = ALL_THEMES[state.themeIndex.coerceIn(ALL_THEMES.indices)]
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.onPermissionResult(granted) }

    LaunchedEffect(Unit) { vm.checkPermission(ctx) }

    var wasInTune by remember { mutableStateOf(false) }
    LaunchedEffect(state.inTune) {
        if (state.inTune && !wasInTune && state.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasInTune = state.inTune
    }

    var drawerOpen by remember { mutableStateOf(false) }

    // свайп влево по экрану = открыть меню
    var openAccum by remember { mutableFloatStateOf(0f) }
    val swipeOpenModifier = if (!drawerOpen) {
        Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (openAccum < -90f) drawerOpen = true
                    openAccum = 0f
                }
            ) { _, dragAmount ->
                if (dragAmount < 0) openAccum += dragAmount else openAccum = 0f
            }
        }
    } else Modifier

    Box(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .then(swipeOpenModifier)
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── шапка: пилюля + видная шестерёнка ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DanglerHeaderPill {
                    Text(
                        "DANGLER",
                        color = theme.textMain,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                    Text(
                        "• ${state.tuning.name}",
                        color = theme.blood,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                // шестерёнка: фиксированное место, контрастный кружок с кровавым бордером
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(theme.surface)
                        .border(1.2.dp, theme.blood.copy(alpha = 0.6f), CircleShape)
                        .clickable { drawerOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Settings, "Меню", tint = theme.textMain, modifier = Modifier.size(24.dp))
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
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

                // статус-пилюля
                DanglerStatusPill(
                    text = when {
                        !state.hasSignal -> "СЛУШАЮ…"
                        state.inTune -> "★ В ТОЧКЕ ★"
                        state.cents > 0 -> "ВЫШЕ ▲"
                        else -> "НИЖЕ ▼"
                    },
                    isActive = state.inTune && state.hasSignal,
                    activeColor = theme.accent,
                    textDim = theme.textDim,
                )

                Spacer(Modifier.height(10.dp))

                // ── строб (Peterson vibe), отключаемый ──
                if (state.showStrobe) {
                    DanglerGlassCard(Modifier.fillMaxWidth()) {
                        StrobeDisplay(
                            cents = state.cents,
                            phase = state.strobePhase,
                            inTune = state.inTune,
                            hasSignal = state.hasSignal,
                            strobeColor = theme.strobe,
                            inTuneColor = theme.accent,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // ── HERO: кровавая нота ──
                BloodNote(
                    note = state.noteName.ifBlank { "—" },
                    cents = state.cents,
                    inTune = state.inTune,
                    hasSignal = state.hasSignal,
                    tiltRollDeg = if (state.gyroEnabled) tilt.rollDeg else 0f,
                    tiltPitchDeg = if (state.gyroEnabled) tilt.pitchDeg else 0f,
                    blood = theme.blood,
                    bloodDark = theme.bloodDark,
                    accent = theme.accent,
                    dim = theme.textDim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // частота + подсказка (моно, как в Gloryhole)
                MonoLabel(
                    text = if (state.hasSignal)
                        String.format("%.1f Hz → %.1f Hz", state.frequencyHz, state.targetFreq)
                    else "сыграй струну…",
                    color = theme.textDim,
                    fontSize = 13,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        !state.hasSignal -> "кровь ждёт звука"
                        state.inTune -> "★ В ТОЧКЕ ★"
                        state.cents > 0 -> "▲ +${abs(state.cents).toInt()} — ослабь"
                        else -> "▼ −${abs(state.cents).toInt()} — подтяни"
                    },
                    color = when {
                        !state.hasSignal -> theme.textDim
                        state.inTune -> theme.accent
                        state.cents > 0 -> theme.sharp
                        else -> theme.flat
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(Modifier.height(10.dp))

                // ── струны строя, в порядке строя ──
                MonoLabel(text = "СТРУНЫ • ${state.tuning.subtitle}", color = theme.textDim, fontSize = 10)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    state.tuning.strings.sortedBy { it.stringNumber }.forEach { s ->
                        val active = state.activeString?.stringNumber == s.stringNumber
                        val bg = if (active && state.inTune) theme.accent
                        else if (active) theme.textMain else theme.surface
                        val fg = if (active) Color.Black else theme.textMain
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
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
                                Text(s.name, color = fg, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                Text(
                                    "${s.stringNumber}",
                                    color = if (active) fg.copy(alpha = 0.7f) else theme.textDim,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        // ── кромка-подсказка свайпа ──
        if (!drawerOpen) {
            SwipeEdgeHint(
                modifier = Modifier.align(Alignment.CenterEnd),
                blood = theme.blood,
                onClick = { drawerOpen = true }
            )
        }

        // ── шторка справа ──
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(DanglerStyle.scrim)
                    .clickable { drawerOpen = false }
            )
        }
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SettingsDrawer(
                vm = vm,
                onClose = { drawerOpen = false }
            )
        }
    }
}

/** Дышащая кромка справа — намекает, что меню открывается свайпом влево */
@Composable
private fun SwipeEdgeHint(
    modifier: Modifier = Modifier,
    blood: Color,
    onClick: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "edge")
    val glow by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "edge_glow"
    )
    Box(
        modifier = modifier
            .width(20.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, blood.copy(alpha = 0.35f * glow + 0.15f))
                )
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.KeyboardArrowLeft,
            contentDescription = "Меню — свайп влево",
            tint = Color.White.copy(alpha = 0.5f + 0.4f * glow),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Шторка настроек: строи, эффекты, темы, точность */
@Composable
private fun SettingsDrawer(
    vm: TunerViewModel,
    onClose: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val theme = ALL_THEMES[state.themeIndex.coerceIn(ALL_THEMES.indices)]

    var closeAccum by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(DanglerStyle.panelBg)
            .border(1.dp, theme.blood.copy(alpha = 0.35f))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (closeAccum > 90f) onClose()
                        closeAccum = 0f
                    }
                ) { _, dragAmount ->
                    if (dragAmount > 0) closeAccum += dragAmount else closeAccum = 0f
                }
            }
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // шапка шторки + крестик как в Gloryhole (36dp circle)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("НАСТРОЙКИ", color = theme.textMain, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x1FFFFFFF))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "Закрыть", tint = theme.textDim, modifier = Modifier.size(18.dp))
            }
        }

        SettingsSectionLabel("СТРОЙ", theme.textDim)
        Tunings.ALL.forEach { t ->
            val selected = t.id == state.tuning.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) theme.blood.copy(alpha = 0.16f) else theme.surface
                    )
                    .border(
                        1.dp,
                        if (selected) theme.blood else theme.textDim.copy(alpha = 0.25f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { vm.selectTuning(t) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t.name,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) theme.blood else theme.textMain,
                        fontSize = 15.sp
                    )
                    Text(t.subtitle, color = theme.textDim, fontSize = 11.sp)
                }
                if (selected) Text("●", color = theme.blood, fontSize = 14.sp)
            }
        }

        SettingsSectionLabel("ЭФФЕКТЫ", theme.textDim)
        DanglerSwitchRow(
            title = "Стробоскоп",
            subtitle = "Peterson-полоса над нотой",
            icon = Icons.Default.Movie,
            isChecked = state.showStrobe,
            onCheckedChange = { vm.setShowStrobe(it) },
            surface = theme.surface,
            textMain = theme.textMain,
            textDim = theme.textDim,
            accent = theme.blood,
        )
        DanglerSwitchRow(
            title = "Гироскоп",
            subtitle = "Кровь наклоняется + уровень",
            icon = Icons.Default.ScreenRotation,
            isChecked = state.gyroEnabled,
            onCheckedChange = { vm.setGyro(it) },
            surface = theme.surface,
            textMain = theme.textMain,
            textDim = theme.textDim,
            accent = theme.blood,
        )
        DanglerSwitchRow(
            title = "Вибрация",
            subtitle = "Отклик при попадании в точку",
            icon = Icons.Default.Vibration,
            isChecked = state.hapticsEnabled,
            onCheckedChange = { vm.setHaptics(it) },
            surface = theme.surface,
            textMain = theme.textMain,
            textDim = theme.textDim,
            accent = theme.blood,
        )

        SettingsSectionLabel("ТЕМА", theme.textDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ALL_THEMES.forEachIndexed { i, th ->
                FilterChip(
                    selected = state.themeIndex == i,
                    onClick = { vm.selectTheme(i) },
                    label = { Text(th.name, fontSize = 12.sp) }
                )
            }
        }

        DanglerSliderRow(
            label = "ТОЧНОСТЬ, CENTS",
            valueText = "±${state.toleranceCents.toInt()}",
            value = state.toleranceCents,
            valueRange = 2f..12f,
            steps = 9,
            onValueChange = { vm.setTolerance(it) },
            accent = theme.blood,
            textMain = theme.textMain,
            textDim = theme.textDim,
        )

        Spacer(Modifier.height(4.dp))
        MonoLabel("свайп вправо — закрыть", color = theme.textDim, fontSize = 10)
        Spacer(Modifier.height(12.dp))
    }
}
