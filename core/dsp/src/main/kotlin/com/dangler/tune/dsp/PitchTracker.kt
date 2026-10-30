package com.dangler.tune.dsp

import kotlin.math.abs
import kotlin.math.log2

/**
 * Временная стабилизация частоты: медиана против выбросов, быстрая атака,
 * медленный релиз и «заморозка» тихого хвоста.
 *
 * Ничего не знает ни о струнах, ни о строе — на входе частота, на выходе частота.
 */
class PitchTracker(
    private val clarityGate: Float = 0.55f,
    private val attackCents: Float = 20f,
    private val releaseAlpha: Float = 0.45f,
    private val medianSize: Int = 3,
    private val holdFloor: Float = 0.025f,
    private val holdWidthCents: Float = 15f,
) {

    data class Smoothed(val frequencyHz: Float, val clarity: Float)

    private val medians = ArrayDeque<Float>()
    private var locked = 0f

    /**
     * @param rmsFiltered энергия фундамента после LPF — определяет «догорает ли» нота
     */
    fun update(candidate: PitchCandidate, rmsFiltered: Double): Smoothed? {
        if (candidate.clarity < clarityGate) {
            medians.clear()
            return null
        }

        // тихий хвост: та же нота догорает — стоим; новая — забываем старое
        if (rmsFiltered < holdFloor && locked > 0f) {
            val drift = 1200 * log2(candidate.frequencyHz / locked)
            if (abs(drift) < holdWidthCents) {
                return Smoothed(locked, candidate.clarity)
            }
            medians.clear()
        }

        medians.addLast(candidate.frequencyHz)
        if (medians.size > medianSize) medians.removeFirst()
        val sorted = medians.sorted()
        val median = sorted[sorted.size / 2]

        if (locked <= 0f) {
            locked = median
        } else {
            val diffCents = 1200 * log2(median / locked)
            locked = if (abs(diffCents) > attackCents) median
            else locked + (median - locked) * releaseAlpha
        }
        return Smoothed(locked, candidate.clarity)
    }

    /** Окно не дало валидной оценки (тишина, шум, сбой детектора). */
    fun onDropout() {
        medians.clear()
    }

    fun reset() {
        medians.clear()
        locked = 0f
    }
}
