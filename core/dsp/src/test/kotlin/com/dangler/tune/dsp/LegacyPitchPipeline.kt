package com.dangler.tune.dsp

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sqrt

/**
 * Дословная копия PitchPipeline до SRP-разбиения (v1.12).
 * Живёт только в тестах: доказывает, что рефакторинг не изменил математику.
 * После этапа точности (когда поведение меняем осознанно) этот файл удаляется.
 */
internal class LegacyPitchPipeline(
    inputSampleRate: Int = 44100,
    private val downsample: Int = 2,
    private val windowSize: Int = 2048,
    private val hopSize: Int = 1024,
    private val rmsGate: Float = 0.006f,
    private val clarityGate: Float = 0.55f,
    private val attackCents: Float = 20f,
    private val releaseAlpha: Float = 0.45f,
    private val medianSize: Int = 3,
) {
    data class Reading(val frequencyHz: Float, val clarity: Float)

    private val effectiveRate = inputSampleRate / downsample
    private val mpm = MpmPitchDetector(sampleRate = effectiveRate, bufferSize = windowSize)
    private val ring = ArrayDeque<Float>()
    private var carry: Float? = null
    private val window = FloatArray(windowSize)
    private val medians = ArrayDeque<Float>()
    private var locked = 0f
    private val holdFloor = 0.025f
    private val holdWidthCents = 15f

    fun push(chunk: FloatArray): Reading? {
        var i = 0
        val c = carry
        if (c != null && chunk.isNotEmpty()) {
            ring.addLast((c + chunk[0]) / 2f)
            carry = null
            i = 1
        }
        while (i + 1 < chunk.size) {
            ring.addLast((chunk[i] + chunk[i + 1]) / 2f)
            i += 2
        }
        if (i < chunk.size) carry = chunk[i]

        var latest: Reading? = null
        while (ring.size >= windowSize) {
            for (k in 0 until windowSize) window[k] = ring[k]
            repeat(hopSize) { ring.removeFirst() }
            processWindow()?.let { latest = it }
        }
        return latest
    }

    private fun processWindow(): Reading? {
        var mean = 0.0
        for (v in window) mean += v
        mean /= windowSize
        var energy = 0.0
        for (k in 0 until windowSize) {
            val centered = window[k] - mean
            window[k] = centered.toFloat()
            energy += centered * centered
        }
        val rms = sqrt(energy / windowSize)
        if (rms < rmsGate) {
            medians.clear()
            return null
        }
        var y = window[0]
        for (k in window.indices) {
            y += LOWPASS_A * (window[k] - y)
            window[k] = y
        }
        var energyF = 0.0
        for (k in 0 until windowSize) energyF += window[k] * window[k]
        val rmsF = sqrt(energyF / windowSize)

        val res = mpm.detect(window) ?: run {
            medians.clear()
            return null
        }
        if (res.clarity < clarityGate) {
            medians.clear()
            return null
        }

        if (rmsF < holdFloor && locked > 0f) {
            val drift = 1200 * log2(res.frequencyHz / locked)
            if (abs(drift) < holdWidthCents) {
                return Reading(locked, res.clarity)
            }
            medians.clear()
        }

        medians.addLast(res.frequencyHz)
        if (medians.size > medianSize) medians.removeFirst()
        val sorted = medians.sorted()
        val med = sorted[sorted.size / 2]

        if (locked <= 0f) {
            locked = med
        } else {
            val diffCents = 1200 * log2(med / locked)
            locked = if (abs(diffCents) > attackCents) med
            else locked + (med - locked) * releaseAlpha
        }
        return Reading(locked, res.clarity)
    }

    companion object {
        const val LOWPASS_A = 0.248f
    }
}
