package com.dangler.tune.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Подготовка окна к детектору: DC-removal, RMS-гейт и low-pass ~1 кГц.
 *
 * Гейты считаются по СЫРОМУ уровню (до фильтра), детект — по фильтрованному:
 * иначе LPF занижает тихий хвост и глушит ещё звучащую ноту.
 */
class SignalConditioner(
    private val rmsGate: Float = 0.006f,
    private val lowPassA: Float = LOWPASS_A,
) {

    data class Conditioned(
        /** RMS до фильтра — по нему решаем «есть ли вообще звук». */
        val rms: Double,
        /** RMS после фильтра — честная энергия фундамента для hold-логики. */
        val rmsFiltered: Double,
    )

    /** Модифицирует [window] на месте. null — тишина/шум ниже порога. */
    fun process(window: FloatArray): Conditioned? {
        var mean = 0.0
        for (v in window) mean += v
        mean /= window.size

        var energy = 0.0
        for (k in window.indices) {
            val centered = window[k] - mean
            window[k] = centered.toFloat()
            energy += centered * centered
        }

        val rms = sqrt(energy / window.size)
        if (rms < rmsGate) return null

        var y = window[0]
        for (k in window.indices) {
            y += lowPassA * (window[k] - y)
            window[k] = y
        }

        var filteredEnergy = 0.0
        for (v in window) filteredEnergy += v * v
        return Conditioned(rms, sqrt(filteredEnergy / window.size))
    }

    companion object {
        /** Однополюсный LPF ~1000 Гц @22050: a = 1 - exp(-2π·1000/22050). */
        const val LOWPASS_A = 0.248f

        /** Тот же фильтр для любого sample rate: a = 1 - exp(-2π·fc/fs). */
        fun lowPassAlpha(sampleRate: Int, cutoffHz: Float = 1000f): Float =
            (1.0 - exp(-2.0 * PI * cutoffHz / sampleRate)).toFloat()
    }
}
