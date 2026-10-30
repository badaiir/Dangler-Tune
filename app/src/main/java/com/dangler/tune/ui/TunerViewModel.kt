package com.dangler.tune.ui

import android.Manifest
import android.app.Application
import android.content.Context
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
import com.dangler.tune.tuner.TunerEngine
import com.dangler.tune.tuner.Tuning
import com.dangler.tune.tuner.Tunings
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

data class TunerState(
    val frequencyHz: Float = 0f,
    val clarity: Float = 0f,
    val noteName: String = Tunings.DEFAULT.rootString.name,
    val cents: Float = 0f,          // гитарный режим: от струны; хроматика: от ноты 12-TET
    val targetFreq: Float = 0f,
    val activeString: Tuning.StringNote? = null,  // ближайшая струна строя (для подсветки)
    val noteInTuning: Boolean = true,            // нота входит в текущий строй?
    val tuning: Tuning = Tunings.DEFAULT,
    val hasSignal: Boolean = false,
    val inTune: Boolean = false,    // |cents| <= tolerance
    val toleranceCents: Float = 5f,
    val isListening: Boolean = false,
    val permissionGranted: Boolean = false,
    val themeIndex: Int = 0,
    val hapticsEnabled: Boolean = true,
    val stringLock: Boolean = true, // гитарный режим: залипать на струнах (кровь, без хроматики)
)

/** Состояние самообновления из GitHub Releases */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateManager.ReleaseInfo) : UpdateUiState
    data class Downloading(val progress: Float) : UpdateUiState
    /** Нужно разрешение системы на установку из этого приложения. */
    data class NeedsUnknownSources(val info: UpdateManager.ReleaseInfo) : UpdateUiState
    /** APK скачан, проверен и ждёт подтверждения в системном установщике. */
    data class ReadyToInstall(val info: UpdateManager.ReleaseInfo) : UpdateUiState
    data class Installing(val info: UpdateManager.ReleaseInfo) : UpdateUiState
    data class Error(val message: String, val info: UpdateManager.ReleaseInfo? = null) : UpdateUiState
}

class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private val _update = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()
    private var lastUpdateCheckAt = 0L

    // Вся музыка и DSP — в :core:tuner / :core:dsp. Здесь только склейка.
    private val engine = TunerEngine()
    private val recorder = AudioRecorder(sampleRate = TunerEngine.INPUT_SAMPLE_RATE)
    private var noSignalJob: Job? = null

    // ── жизненный цикл ──

    /** Вызывается из Activity.onStart: вернулись в приложение — снова слушаем. */
    fun onStart() {
        if (_state.value.permissionGranted) start()
        resumeUpdate()
    }

    /** Возврат из системных настроек/установщика: докручиваем прерванный апдейт. */
    private fun resumeUpdate() {
        when (val u = _update.value) {
            is UpdateUiState.NeedsUnknownSources -> if (canInstallUnknown()) downloadUpdate(u.info)
            // Вернулись из установщика, версия не сменилась → установку отменили.
            // Если установка удалась, процесс уже новый и сюда не попадёт.
            is UpdateUiState.Installing -> _update.value = UpdateUiState.ReadyToInstall(u.info)
            else -> Unit
        }
    }

    /** Вызывается из Activity.onStop: микрофон отпускаем, батарею не жжём. */
    fun onStop() {
        stop()
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted) }
        if (granted) start()
    }

    fun checkPermission(context: Context) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        _state.update { it.copy(permissionGranted = granted) }
        if (granted) start()
    }

    fun start() {
        if (_state.value.isListening || !_state.value.permissionGranted) return
        engine.reset()
        _state.update { it.copy(isListening = true) }
        recorder.start(
            scope = viewModelScope,
            onFrame = { frame ->
                // null = нет кадра, тишина или мусор между струнами.
                // Во всех случаях сигнал должен погаснуть — иначе экран залипает.
                val reading = engine.push(frame)
                if (reading != null) onReading(reading) else markNoSignalSoon()
            },
            onError = {
                // Микрофон отобрали/сломался — честно показываем «не слушаем».
                viewModelScope.launch(Dispatchers.Main) {
                    _state.update { it.copy(isListening = false, hasSignal = false, inTune = false) }
                }
            },
        )
    }

    fun stop() {
        recorder.stop()
        noSignalJob?.cancel()
        noSignalJob = null
        _state.update { it.copy(isListening = false, hasSignal = false, inTune = false) }
    }

    // ── настройки ──

    fun selectTuning(t: Tuning) {
        engine.selectTuning(t)
        val root = t.rootString.name
        _state.update {
            it.copy(
                tuning = t,
                activeString = null,
                hasSignal = false,
                inTune = false,
                frequencyHz = 0f,
                clarity = 0f,
                cents = 0f,
                targetFreq = 0f,
                noteName = root,
                noteInTuning = true,
            )
        }
    }

    fun selectTheme(i: Int) { _state.update { it.copy(themeIndex = i) } }

    fun setTolerance(c: Float) {
        engine.setTolerance(c)
        _state.update { it.copy(toleranceCents = c) }
    }

    fun setHaptics(v: Boolean) { _state.update { it.copy(hapticsEnabled = v) } }

    fun setStringLock(v: Boolean) {
        engine.setStringLock(v)
        _state.update {
            it.copy(stringLock = v, activeString = null, hasSignal = false, inTune = false)
        }
    }

    // ── чтение с движка ──

    private fun markNoSignalSoon() {
        // дебаунс короткий — буквы меняются быстро
        if (noSignalJob?.isActive == true) return
        noSignalJob = viewModelScope.launch {
            delay(120)
            _state.update { it.copy(hasSignal = false, inTune = false) }
        }
    }

    private fun onReading(reading: TunerEngine.Reading) {
        noSignalJob?.cancel()
        _state.update {
            it.copy(
                frequencyHz = reading.frequencyHz,
                clarity = reading.clarity,
                noteName = reading.noteName,
                cents = reading.cents,
                targetFreq = reading.targetHz,
                activeString = reading.activeString,
                noteInTuning = reading.noteInTuning,
                hasSignal = true,
                inTune = reading.inTune,
            )
        }
    }

    // ── самообновление ──

    private fun updateApkFile(): File {
        val dir = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
        return File(dir, "dangler-update.apk")
    }

    private val versionName: String by lazy {
        try {
            val app = getApplication<Application>()
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    fun currentVersion(): String = versionName

    /** manual=true → показываем UpToDate/Error, иначе молча (только бейдж при Available) */
    fun checkForUpdate(manual: Boolean = false) {
        val current = _update.value
        if (current is UpdateUiState.Checking ||
            current is UpdateUiState.Downloading ||
            current is UpdateUiState.Installing
        ) return
        if (!manual && (current is UpdateUiState.Available ||
                current is UpdateUiState.ReadyToInstall ||
                current is UpdateUiState.NeedsUnknownSources)
        ) return
        // Тихая проверка не чаще раза в минуту: старт + открытие настроек не спамят GitHub.
        val now = System.currentTimeMillis()
        if (!manual && now - lastUpdateCheckAt < 60_000L) return
        lastUpdateCheckAt = now

        _update.value = UpdateUiState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rel = UpdateManager.checkLatest()
                val next: UpdateUiState = when {
                    rel == null ->
                        if (manual) UpdateUiState.Error("Релизов пока нет") else UpdateUiState.Idle
                    UpdateManager.isNewer(rel.version, currentVersion()) ->
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
        val app = getApplication<Application>()
        if (!canInstallUnknown()) {
            _update.value = UpdateUiState.NeedsUnknownSources(info)
            return
        }

        _update.value = UpdateUiState.Downloading(0f)
        viewModelScope.launch(Dispatchers.IO) {
            val apk = updateApkFile()
            try {
                UpdateManager.downloadApk(info.apkUrl, apk, info.sha256) { p ->
                    _update.value = UpdateUiState.Downloading(p)
                }
                val problem = UpdateManager.verifyApk(app, apk)
                if (problem != null) {
                    apk.delete()
                    withContext(Dispatchers.Main) {
                        _update.value = UpdateUiState.Error(problem, info)
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    _update.value = UpdateUiState.ReadyToInstall(info)
                    fireInstall()
                }
            } catch (e: Exception) {
                apk.delete()
                withContext(Dispatchers.Main) {
                    _update.value = UpdateUiState.Error("Скачивание сорвалось: ${e.message}", info)
                }
            }
        }
    }

    fun canInstallUnknown(): Boolean =
        Build.VERSION.SDK_INT < 26 ||
            getApplication<Application>().packageManager.canRequestPackageInstalls()

    /** Установка скачанного APK; если нет права — ведём в настройки системы */
    fun fireInstall() {
        val app = getApplication<Application>()
        val info = when (val u = _update.value) {
            is UpdateUiState.ReadyToInstall -> u.info
            is UpdateUiState.NeedsUnknownSources -> u.info
            is UpdateUiState.Installing -> u.info
            else -> null
        }
        if (!canInstallUnknown()) {
            if (info != null) _update.value = UpdateUiState.NeedsUnknownSources(info)
            return
        }
        if (info == null) {
            _update.value = UpdateUiState.Error("Нет данных о релизе")
            return
        }

        val apk = updateApkFile()
        val problem = UpdateManager.verifyApk(app, apk)
        if (problem != null) {
            apk.delete()
            _update.value = UpdateUiState.Error(problem, info)
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _update.value = UpdateUiState.Installing(info)
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


    override fun onCleared() {
        recorder.stop()
        super.onCleared()
    }
}
