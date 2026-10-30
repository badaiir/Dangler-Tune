package com.dangler.tune.dsp

/**
 * Оценка основного тона за одно окно.
 *
 * @property frequencyHz найденная частота
 * @property clarity 0..1 — насколько сигнал периодичен (амплитуда NSDF)
 */
data class PitchCandidate(
    val frequencyHz: Float,
    val clarity: Float,
)

/**
 * Одна ответственность: превратить окно сэмплов в оценку частоты.
 * Никакого сглаживания, никаких струн и строёв — только период.
 */
fun interface PitchDetector {
    fun detect(samples: FloatArray): PitchCandidate?
}
