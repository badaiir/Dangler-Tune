package com.dangler.tune.dsp

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import java.util.Locale

internal data class PitchStats(
    val frames: Int,
    val readings: Int,
    val medAbsCents: Double,
    val p95AbsCents: Double,
    val maxAbsCents: Double,
    val octaveErrors: Int,
    val grossErrors: Int,
    val medClarity: Double,
) {
    val missRate: Double get() = if (frames == 0) 1.0 else 1.0 - readings.toDouble() / frames

    fun line(label: String): String = String.format(
        Locale.ROOT,
        "%-34s frames=%3d read=%3d miss=%4.1f%% med=%5.2f p95=%6.2f max=%7.2f oct=%d gross=%d clar=%.2f",
        label, frames, readings, missRate * 100, medAbsCents, p95AbsCents, maxAbsCents,
        octaveErrors, grossErrors, medClarity,
    )
}

/**
 * Замеры точности: без UI, без Android, только числа.
 * Критерии тюнера — не «красиво», а p95 в центах, октавные ошибки и ложные срабатывания.
 */
internal object PitchMetrics {

    private const val DS_SR = 22050
    private const val WINDOW = 2048

    /** Только детектор: окна с шагом 1024, атака пропускается. */
    fun detector(
        detector: PitchDetector,
        signal: FloatArray,
        trueHz: Float,
        windowSize: Int = WINDOW,
        hopSize: Int = 1024,
        startSec: Double = 0.30,
        endSec: Double = 1.90,
    ): PitchStats {
        val decimated = Decimator(2).push(signal)
        val conditioner = SignalConditioner()
        val window = FloatArray(windowSize)
        val start = (startSec * DS_SR).toInt()
        val end = minOf((endSec * DS_SR).toInt(), decimated.size)

        val errors = ArrayList<Double>()
        var frames = 0
        var octave = 0
        var gross = 0
        var claritySum = 0.0

        var i = start
        while (i + windowSize <= end) {
            System.arraycopy(decimated, i, window, 0, windowSize)
            if (conditioner.process(window) != null) {
                frames++
                val r = detector.detect(window)
                if (r != null) {
                    val error = 1200 * log2((r.frequencyHz / trueHz).toDouble())
                    val ratio = r.frequencyHz / trueHz
                    if (abs(ratio - 2f) < 0.06f || abs(ratio - 0.5f) < 0.06f) octave++
                    else if (abs(error) > 25.0) gross++
                    errors.add(abs(error))
                    claritySum += r.clarity
                }
            }
            i += hopSize
        }
        return stats(frames, errors, octave, gross, claritySum)
    }

    /** Сквозной пайплайн: детектор + трекер, как слышит приложение. */
    fun pipeline(
        signal: FloatArray,
        trueHz: Float,
        windowSize: Int = WINDOW,
        hopSize: Int = 1024,
        startSec: Double = 0.30,
    ): PitchStats {
        val pipe = PitchPipeline(
            windowSize = windowSize,
            hopSize = hopSize,
            detector = FftMpmDetector(sampleRate = DS_SR, bufferSize = windowSize),
        )
        val startSample = (startSec * PitchCorpus.SR).toInt()
        val errors = ArrayList<Double>()
        var frames = 0
        var octave = 0
        var gross = 0
        var claritySum = 0.0

        var i = 0
        while (i < signal.size) {
            val end = minOf(i + 4096, signal.size)
            val reading = pipe.push(signal.copyOfRange(i, end))
            if (end >= startSample) {
                frames++
                if (reading != null) {
                    val error = 1200 * log2((reading.frequencyHz / trueHz).toDouble())
                    val ratio = reading.frequencyHz / trueHz
                    if (abs(ratio - 2f) < 0.06f || abs(ratio - 0.5f) < 0.06f) octave++
                    else if (abs(error) > 25.0) gross++
                    errors.add(abs(error))
                    claritySum += reading.clarity
                }
            }
            i = end
        }
        return stats(frames, errors, octave, gross, claritySum)
    }

    /** Доля кадров, на которых пайплайн «услышал ноту» в негативе. */
    fun falsePositiveRate(signal: FloatArray, startSec: Double = 0.30): Double {
        val pipe = PitchPipeline()
        val startSample = (startSec * PitchCorpus.SR).toInt()
        var frames = 0
        var hits = 0
        var i = 0
        while (i < signal.size) {
            val end = minOf(i + 4096, signal.size)
            val reading = pipe.push(signal.copyOfRange(i, end))
            if (end >= startSample) {
                frames++
                if (reading != null) hits++
            }
            i = end
        }
        return if (frames == 0) 1.0 else hits.toDouble() / frames
    }

    /** Ложные срабатывания самого детектора, без трекера и hold-логики. */
    fun detectorFalsePositiveRate(detector: PitchDetector, signal: FloatArray): Double {
        val decimated = Decimator(2).push(signal)
        val conditioner = SignalConditioner()
        val window = FloatArray(WINDOW)
        var frames = 0
        var hits = 0
        var i = (0.30 * DS_SR).toInt()
        while (i + WINDOW <= decimated.size) {
            System.arraycopy(decimated, i, window, 0, WINDOW)
            if (conditioner.process(window) != null) {
                frames++
                if (detector.detect(window) != null) hits++
            }
            i += 1024
        }
        return if (frames == 0) 0.0 else hits.toDouble() / frames
    }

    private fun stats(
        frames: Int,
        errors: List<Double>,
        octave: Int,
        gross: Int,
        claritySum: Double,
    ): PitchStats {
        val sorted = errors.sorted()
        return PitchStats(
            frames = frames,
            readings = errors.size,
            medAbsCents = percentile(sorted, 0.50),
            p95AbsCents = percentile(sorted, 0.95),
            maxAbsCents = if (sorted.isEmpty()) Double.NaN else sorted.last(),
            octaveErrors = octave,
            grossErrors = gross,
            medClarity = if (errors.isEmpty()) 0.0 else claritySum / errors.size,
        )
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        val index = ((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
