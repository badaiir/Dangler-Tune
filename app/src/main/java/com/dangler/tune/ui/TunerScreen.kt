package com.dangler.tune.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dangler.tune.model.Tuning
import com.dangler.tune.model.Tunings
import com.dangler.tune.ui.components.BloodNote
import com.dangler.tune.ui.components.DanglerSliderRow
import com.dangler.tune.ui.components.DanglerSwitchRow
import com.dangler.tune.ui.components.MetalBackdrop
import com.dangler.tune.ui.components.MonoLabel
import com.dangler.tune.ui.components.SettingsSectionLabel
import com.dangler.tune.ui.theme.ALL_THEMES
import com.dangler.tune.ui.theme.DanglerTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * v2 — ландшафтный металлический минимализм.
 * Навигация только свайпами: [0] СТРОЙ | [1] ТЮНЕР | [2] НАСТРОЙКИ.
 * На тюнере — ничего лишнего: нота на весь экран, кровь внутри неё и есть прибор.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TunerScreen(vm: TunerViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val tilt by vm.tilt.collectAsState()
    val theme = ALL_THEMES[state.themeIndex.coerceIn(ALL_THEMES.indices)]
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 1) { 3 }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        vm.checkPermission(ctx)
        vm.checkForUpdate(manual = false) // молча, только бейдж
    }

    var wasInTune by remember { mutableStateOf(false) }
    LaunchedEffect(state.inTune) {
        if (state.inTune && !wasInTune && state.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasInTune = state.inTune
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .systemBarsPadding()
    ) {
        MetalBackdrop(
            baseTop = theme.background,
            baseBottom = Color.Black,
            modifier = Modifier.fillMaxSize()
        )
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> TuningsPage(
                    theme = theme,
                    current = state.tuning,
                    onSelect = {
                        vm.selectTuning(it)
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
                1 -> TunerPage(
                    vm = vm,
                    state = state,
                    theme = theme,
                    pagerState = pagerState,
                    tiltRoll = if (state.gyroEnabled) tilt.rollDeg else 0f,
                    tiltPitch = if (state.gyroEnabled) tilt.pitchDeg else 0f,
                    onRequestMic = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
                2 -> SettingsPage(vm = vm, state = state, theme = theme)
            }
        }
    }
}

// ── стр. 1: ТЮНЕР ─────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TunerPage(
    vm: TunerViewModel,
    state: TunerState,
    theme: DanglerTheme,
    pagerState: PagerState,
    tiltRoll: Float,
    tiltPitch: Float,
    onRequestMic: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val update by vm.update.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // нота — 70% ширины, вся высота
                BloodNote(
                    note = state.noteName.ifBlank { "—" },
                    cents = state.cents,
                    inTune = state.inTune,
                    hasSignal = state.hasSignal,
                    tiltRollDeg = tiltRoll,
                    tiltPitchDeg = tiltPitch,
                    blood = theme.blood,
                    bloodDark = theme.bloodDark,
                    accent = theme.accent,
                    steel = theme.steel,
                    steelDark = theme.steelDark,
                    dim = theme.textDim,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                // правая колонка: струна + центы, и больше ничего
                Column(
                    Modifier.width(118.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (!state.permissionGranted) {
                        Button(
                            onClick = onRequestMic,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.accent,
                                contentColor = Color.Black
                            )
                        ) { Text("МИК", fontWeight = FontWeight.Black) }
                    } else {
                        val sideName = state.activeString?.name
                            ?: state.tuning.strings.maxBy { it.stringNumber }.name
                        Text(
                            text = sideName,
                            color = if (state.activeString != null) theme.textMain else theme.textDim,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                        )
                        MonoLabel(
                            text = if (state.activeString != null)
                                "СТРУНА ${state.activeString!!.stringNumber}" else "—",
                            color = theme.textDim,
                            fontSize = 10,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = when {
                                !state.hasSignal -> ""
                                state.inTune -> "OK"
                                state.cents > 0 -> "+${abs(state.cents).toInt()}"
                                else -> "−${abs(state.cents).toInt()}"
                            },
                            color = when {
                                !state.hasSignal -> Color.Transparent
                                state.inTune -> theme.accent
                                state.cents > 0 -> theme.sharp
                                else -> theme.flat
                            },
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                        MonoLabel(
                            text = if (state.hasSignal)
                                String.format("%.1f/%.1f", state.frequencyHz, state.targetFreq)
                            else "ТИШИНА",
                            color = theme.textDim,
                            fontSize = 10,
                        )
                    }
                }
            }
            // струны — тонкая полоска снизу, в порядке строя
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.tuning.strings.sortedBy { it.stringNumber }.forEach { s ->
                    val active = state.activeString?.stringNumber == s.stringNumber
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    active && state.inTune -> theme.accent.copy(alpha = 0.9f)
                                    active -> theme.blood.copy(alpha = 0.35f)
                                    else -> Color.White.copy(alpha = 0.05f)
                                }
                            )
                            .border(
                                1.dp,
                                if (active) theme.blood else Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            s.name,
                            color = if (active) Color.White else theme.textDim,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // оверлеи: строй слева вверху, точки справа вверху
        MonoLabel(
            text = state.tuning.name.uppercase(),
            color = theme.blood,
            fontSize = 11,
            bold = true,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 12.dp)
        )
        PageDots(
            page = pagerState.currentPage,
            color = theme.textDim,
            active = theme.blood,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 14.dp)
        )
        // бейдж обновы
        if (update is UpdateUiState.Available) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp, top = 30.dp)
                    .clip(CircleShape)
                    .background(theme.blood)
                    .clickable {
                        scope.launch { pagerState.animateScrollToPage(2) }
                    }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("ОБНОВА ↓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun PageDots(page: Int, color: Color, active: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (i in 0..2) {
            Box(
                Modifier
                    .size(if (i == page) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (i == page) active else color.copy(alpha = 0.35f))
            )
        }
    }
}

// ── стр. 0: СТРОЙ ─────────────────────────────────────────

@Composable
private fun TuningsPage(
    theme: DanglerTheme,
    current: Tuning,
    onSelect: (Tuning) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("СТРОЙ", color = theme.textMain, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        MonoLabel("свайп вправо → тюнер", color = theme.textDim, fontSize = 10)
        Tunings.ALL.forEach { t ->
            val selected = t.id == current.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) theme.blood.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                    .border(
                        1.dp,
                        if (selected) theme.blood else Color.White.copy(alpha = 0.10f),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(t) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t.name,
                        fontWeight = FontWeight.Black,
                        color = if (selected) theme.blood else theme.textMain,
                        fontSize = 19.sp
                    )
                    Text(t.subtitle, color = theme.textDim, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                        t.strings.sortedByDescending { it.stringNumber }.forEach { s ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(s.name, color = theme.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (selected) Text("●", color = theme.blood, fontSize = 16.sp)
            }
        }
    }
}

// ── стр. 2: НАСТРОЙКИ + ОБНОВА ────────────────────────────

@Composable
private fun SettingsPage(
    vm: TunerViewModel,
    state: TunerState,
    theme: DanglerTheme,
) {
    val update by vm.update.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("НАСТРОЙКИ", color = theme.textMain, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        MonoLabel("свайп влево → тюнер", color = theme.textDim, fontSize = 10)

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

        SettingsSectionLabel("ЭФФЕКТЫ", theme.textDim)
        DanglerSwitchRow(
            title = "Гироскоп",
            subtitle = "Кровь наклоняется + уровень",
            icon = Icons.Default.ScreenRotation,
            isChecked = state.gyroEnabled,
            onCheckedChange = { vm.setGyro(it) },
            surface = Color.White.copy(alpha = 0.05f),
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
            surface = Color.White.copy(alpha = 0.05f),
            textMain = theme.textMain,
            textDim = theme.textDim,
            accent = theme.blood,
        )

        SettingsSectionLabel("ОБНОВЛЕНИЕ", theme.textDim)
        UpdateBlock(vm = vm, theme = theme)
    }
}

@Composable
private fun UpdateBlock(vm: TunerViewModel, theme: DanglerTheme) {
    val update by vm.update.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel("ТЕКУЩАЯ: v${vm.currentVersion()}", color = theme.textDim, fontSize = 11)
            if (update !is UpdateUiState.Checking && update !is UpdateUiState.Downloading) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.blood.copy(alpha = 0.2f))
                        .border(1.dp, theme.blood.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable { vm.checkForUpdate(manual = true) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Refresh, null, tint = theme.blood, modifier = Modifier.size(16.dp))
                    Text("ПРОВЕРИТЬ", color = theme.textMain, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        when (val u = update) {
            is UpdateUiState.Idle -> {}
            is UpdateUiState.Checking -> MonoLabel("стучусь на гитхаб…", color = theme.textDim, fontSize = 11)
            is UpdateUiState.UpToDate -> MonoLabel("ты на свежаке ★", color = theme.accent, fontSize = 11, bold = true)
            is UpdateUiState.Available -> {
                MonoLabel("ЕСТЬ ${u.info.version.uppercase()} — ${u.info.name}", color = theme.blood, fontSize = 12, bold = true)
                if (u.info.notes.isNotBlank()) {
                    Text(
                        u.info.notes.take(220),
                        color = theme.textDim,
                        fontSize = 12.sp,
                        maxLines = 3,
                    )
                }
                BigRedButton("СКАЧАТЬ И УСТАНОВИТЬ", theme) { vm.downloadUpdate(u.info) }
            }
            is UpdateUiState.Downloading -> {
                MonoLabel("качаю… ${(u.progress * 100).toInt()}%", color = theme.textMain, fontSize = 11, bold = true)
                LinearProgressIndicator(
                    progress = { u.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = theme.blood,
                    trackColor = Color.White.copy(alpha = 0.10f),
                )
            }
            is UpdateUiState.NeedsUnknownSources -> {
                MonoLabel("разреши установку из неизвестных источников", color = theme.flat, fontSize = 11)
                BigRedButton("ОТКРЫТЬ НАСТРОЙКИ", theme) { vm.openUnknownSourcesSettings() }
                MonoLabel("потом жми установку ещё раз", color = theme.textDim, fontSize = 10)
                BigRedButton("УСТАНОВИТЬ", theme) { vm.fireInstall() }
            }
            is UpdateUiState.Installing -> MonoLabel("ставлю… подтверди в системе", color = theme.textDim, fontSize = 11)
            is UpdateUiState.Error -> MonoLabel(u.message, color = theme.sharp, fontSize = 11)
        }
    }
}

@Composable
private fun BigRedButton(text: String, theme: DanglerTheme, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.blood)
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
    }
}
