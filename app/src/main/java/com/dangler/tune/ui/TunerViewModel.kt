package com.dangler.tune.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dangler.tune.audio.AudioRecorder
import com.dangler.tune.dsp.NoteUtils
import com.dangler.tune.dsp.YinPitchDetector
import com.dangler.tune.model.Tuning
import com.dangler.tune.model.Tunings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class TunerState(
    val frequencyHz: Float = 0f,
    val clarity: Float = 0f,
    val noteDisplay: String = "—",
    val noteName: String = "—",
    val cents: Float = 0f,          // -50..+50 относительно ближайшей ноты/струны
    val centsRelativeString: Float = 0f,
    val targetFreq: Float = 0f,
    val activeString: Tuning.StringNote? = null,
    val tuning: Tuning = Tunings.DEFAULT,
    val hasSignal: Boolean = false,
    val inTune: Boolean = false,    // |cents| <= tolerance
    val toleranceCents: Float = 5f,
    val isListening: Boolean = false,
    val permissionGranted: Boolean = false,
    val themeIndex: Int = 0,
    val strobePhase: Float = 0f,    // 0..1 фаза движения строба
)

class TunerViewModel : ViewModel() {

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private val recorder = AudioRecorder()
    private val yin = YinPitchDetector()
    private var noSignalJob: Job? = null

    // медианный фильтр по частоте — убирает джиттер/перескоки на октаву
    private val freqWindow = ArrayDeque<Float>()
    private val strobeOffset = MutableStateFlow(0f)

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted) }
        if (granted) start()
    }

    fun checkPermission(context: android.content.Context) {
        val g = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        _state.update { it.copy(permissionGranted = g) }
        if (g) start()
    }

    fun selectTuning(t: Tuning) {
        freqWindow.clear()
        _state.update { it.copy(tuning = t, activeString = null, hasSignal = false) }
    }

    fun selectTheme(i: Int) { _state.update { it.copy(themeIndex = i) } }
    fun setTolerance(c: Float) { _state.update { it.copy(toleranceCents = c) } }

    fun start() {
        if (_state.value.isListening) return
        _state.update { it.copy(isListening = true) }
        recorder.start(viewModelScope) { frame ->
            // фрейм уже 4096; если меньше — пропускаем
            if (frame.size < 4096) return@start
            val res = yin.detect(frame) ?: run { markNoSignalSoon(); return@start }
            onPitch(res.frequencyHz, res.clarity)
        }
    }

    fun stop() {
        recorder.stop()
        _state.update { it.copy(isListening = false, hasSignal = false) }
    }

    private fun markNoSignalSoon() {
        // дебаунс: не моргаем при одном пустом кадре
        if (noSignalJob?.isActive == true) return
        noSignalJob = viewModelScope.launch {
            delay(180)
            _state.update { it.copy(hasSignal = false, inTune = false) }
            freqWindow.clear()
        }
    }

    private fun onPitch(freq: Float, clarity: Float) {
        noSignalJob?.cancel()

        // медиана по 5 кадрам против выбросов
        freqWindow.addLast(freq)
        if (freqWindow.size > 5) freqWindow.removeFirst()
        val sorted = freqWindow.sorted()
        val med = sorted[sorted.size / 2]

        val tuning = _state.value.tuning
        val nearestString = NoteUtils.nearestString(med, tuning)
        val centsStr = if (nearestString != null)
            NoteUtils.centsRelativeTo(med, nearestString.frequencyHz) else 0f
        // clamp для дисплея ±50
        val clamped = centsStr.coerceIn(-50f, 50f)

        val note = NoteUtils.freqToNote(med)
        val tol = _state.value.toleranceCents
        val inTune = abs(clamped) <= tol && clarity > 0.55f

        // фаза строба: скорость пропорциональна расстройке, направление = знак центов
        // в точке — стоит (Peterson-эффект)
        val cur = _state.value.strobePhase
        val speed = (abs(clamped) / 50f) * 0.06f // на кадр
        val dir = if (clamped > 0) 1f else -1f
        val next = if (inTune) cur else (cur + speed * dir + 1f) % 1f

        _state.update {
            it.copy(
                frequencyHz = med,
                clarity = clarity,
                noteDisplay = nearestString?.name ?: note.display,
                noteName = nearestString?.name ?: note.display,
                cents = clamped,
                centsRelativeString = centsStr,
                targetFreq = nearestString?.frequencyHz ?: note.targetFreq,
                activeString = nearestString,
                hasSignal = true,
                inTune = inTune,
                strobePhase = next,
            )
        }
    }

    override fun onCleared() {
        recorder.stop()
        super.onCleared()
    }
}
