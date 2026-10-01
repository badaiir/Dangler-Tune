package com.dangler.tune.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Захват микрофона: 44100 Гц, mono, 16-bit.
 * Отдает float-кадры [-1, 1] размером frameSize в реальном времени.
 */
class AudioRecorder(
    val sampleRate: Int = 44100,
    val frameSize: Int = 4096,
) {
    private var record: AudioRecord? = null
    private var job: Job? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, onFrame: (FloatArray) -> Unit) {
        stop()
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(frameSize * 2)
        record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )
        record?.startRecording()
        job = scope.launch(Dispatchers.Default) {
            val shortBuf = ShortArray(frameSize)
            val floatBuf = FloatArray(frameSize)
            while (isActive) {
                val rec = record ?: break
                val read = rec.read(shortBuf, 0, frameSize)
                if (read > 0) {
                    for (i in 0 until read) floatBuf[i] = shortBuf[i] / 32768f
                    // копия, т.к. буфер переиспользуется
                    onFrame(floatBuf.copyOf(read))
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
    }
}
