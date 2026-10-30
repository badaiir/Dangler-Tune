package com.dangler.tune.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Захват микрофона: mono, 16-bit.
 * Отдает float-кадры [-1, 1] в реальном времени, кусками как пришло
 * (аккумулятор и нарезка окон — в PitchPipeline, пропусков нет).
 *
 * КРИТИЧНО для тюнера: источник UNPROCESSED (сырой микрофон без шумодава/АРУ).
 * Обычный MIC на многих телефонах включает NoiseSuppressor + AGC, которые
 * модулируют сигнал и ломают периодичность — тюнер врёт и скачет.
 * Фолбэк: VOICE_RECOGNITION (тоже без обработок) → MIC.
 */
class AudioRecorder(
    val sampleRate: Int,
    val frameSize: Int = 4096,
) {
    @Volatile
    private var record: AudioRecord? = null
    private var job: Job? = null

    private fun createRecord(): AudioRecord {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(frameSize * 2)
        val sources = listOf(
            MediaRecorder.AudioSource.UNPROCESSED, // API 24+, сырец
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // без обработок
            MediaRecorder.AudioSource.MIC, // последний шанс
        )
        var lastError: Exception? = null
        for (src in sources) {
            try {
                val rec = AudioRecord(src, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
                if (rec.state == AudioRecord.STATE_INITIALIZED) return rec
                try { rec.release() } catch (_: Exception) {}
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("AudioRecord init failed")
    }

    /**
     * @param onError вызывается на фоновом потоке, если захват сорвался
     * (микрофон отобрали, гарнитуру выдернули, read вернул ошибку).
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(
        scope: CoroutineScope,
        onFrame: (FloatArray) -> Unit,
        onError: (Throwable) -> Unit = {},
    ) {
        stop()
        val rec = try {
            createRecord()
        } catch (e: Exception) {
            onError(e)
            return
        }
        record = rec
        try {
            rec.startRecording()
        } catch (e: Exception) {
            stop()
            onError(e)
            return
        }

        job = scope.launch(Dispatchers.Default) {
            val shortBuf = ShortArray(frameSize)
            val floatBuf = FloatArray(frameSize)
            var failure: Throwable? = null

            while (isActive) {
                val read = try {
                    rec.read(shortBuf, 0, frameSize)
                } catch (e: Exception) {
                    failure = e
                    break
                }
                if (read > 0) {
                    for (i in 0 until read) floatBuf[i] = shortBuf[i] / 32768f
                    // копия, т.к. буфер переиспользуется
                    onFrame(floatBuf.copyOf(read))
                } else if (read < 0) {
                    failure = IllegalStateException("AudioRecord.read error $read")
                    break
                } else {
                    // read == 0: данных пока нет. Без паузы это busy-loop,
                    // который жрёт ядро и мешает DSP-потоку.
                    delay(5)
                }
            }

            if (failure != null) {
                release(rec)
                onError(failure)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        record?.let { release(it) }
        record = null
    }

    private fun release(rec: AudioRecord) {
        try { rec.stop() } catch (_: Exception) {}
        try { rec.release() } catch (_: Exception) {}
        if (record === rec) record = null
    }
}
