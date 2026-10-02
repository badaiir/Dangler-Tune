package com.dangler.tune.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dangler.tune.audio.AudioRecorder
import com.dangler.tune.dsp.NoteUtils
import com.dangler.tune.dsp.YinPitchDetector
import com.dangler.tune.model.Tuning
import com.dangler.tune.model.Tunings
import com.dangler.tune.sensor.TiltSensor
import com.dangler.tune.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    val gyroEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
)

/** Состояние самообновления из GitHub Releases */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateManager.ReleaseInfo) : UpdateUiState
    data class Downloading(val progress: Float) : UpdateUiState
    /** APK скачан, но системе запрещено ставить из неизвестных источников */
    data object NeedsUnknownSources : UpdateUiState
    data object Installing : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private val tiltSensor = TiltSensor(application)
    val tilt: StateFlow<TiltSensor.Tilt> = tiltSensor.tilt

    private val _update = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()

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
    fun setHaptics(v: Boolean) { _state.update { it.copy(hapticsEnabled = v) } }
    fun setGyro(v: Boolean) {
        _state.update { it.copy(gyroEnabled = v) }
        if (v && _state.value.isListening) tiltSensor.start() else tiltSensor.stop()
    }

    fun start() {
        if (_state.value.isListening) return
        _state.update { it.copy(isListening = true) }
        if (_state.value.gyroEnabled) tiltSensor.start()
        recorder.start(viewModelScope) { frame ->
            // фрейм уже 4096; если меньше — пропускаем
            if (frame.size < 4096) return@start
            val res = yin.detect(frame) ?: run { markNoSignalSoon(); return@start }
            onPitch(res.frequencyHz, res.clarity)
        }
    }

    fun stop() {
        recorder.stop()
        tiltSensor.stop()
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
            )
        }
    }

    // ── самообновление ──

    private fun updateApkFile() = File(getApplication<Application>().cacheDir, "dangler-update.apk")

    fun currentVersion(): String = try {
        val app = getApplication<Application>()
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    private fun appVersion(): String = currentVersion()

    /** manual=true → показываем UpToDate/Error, иначе молча (только бейдж при Available) */
    fun checkForUpdate(manual: Boolean = false) {
        if (_update.value is UpdateUiState.Checking || _update.value is UpdateUiState.Downloading) return
        _update.value = UpdateUiState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rel = UpdateManager.checkLatest()
                val next: UpdateUiState = when {
                    rel == null ->
                        if (manual) UpdateUiState.Error("Релизов пока нет") else UpdateUiState.Idle
                    UpdateManager.isNewer(rel.version, appVersion()) ->
                        UpdateUiState.Available(rel)
                    else ->
                        if (manual) UpdateUiState.UpToDate else UpdateUiState.Idle
                }
                withContext(Dispatchers.Main) { _update.value = next }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _update.value =
                        if (manual) UpdateUiState.Error("Нет связи: ${e.message}") else UpdateUiState.Idle
                }
            }
        }
    }

    fun downloadUpdate(info: UpdateManager.ReleaseInfo) {
        _update.value = UpdateUiState.Downloading(0f)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                UpdateManager.downloadApk(info.apkUrl, updateApkFile()) { p ->
                    _update.value = UpdateUiState.Downloading(p)
                }
                withContext(Dispatchers.Main) { fireInstall() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _update.value = UpdateUiState.Error("Скачивание сорвалось: ${e.message}")
                }
            }
        }
    }

    fun canInstallUnknown(): Boolean =
        getApplication<Application>().packageManager.canRequestPackageInstalls()

    /** Установка скачанного APK; если нет права — ведём в настройки системы */
    fun fireInstall() {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            _update.value = UpdateUiState.NeedsUnknownSources
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", updateApkFile())
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _update.value = UpdateUiState.Installing
        app.startActivity(intent)
    }

    fun openUnknownSourcesSettings() {
        val app = getApplication<Application>()
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${app.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        app.startActivity(intent)
    }

    fun resetUpdateState() {
        if (_update.value !is UpdateUiState.Downloading) _update.value = UpdateUiState.Idle
    }

    override fun onCleared() {
        recorder.stop()
        tiltSensor.stop()
        super.onCleared()
    }
}
