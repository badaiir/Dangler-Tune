package com.dangler.tune.dsp

import kotlin.math.max

/**
 * Общая часть MPM: выбор периода по готовому NSDF.
 *
 * Вынесено из детектора, потому что NSDF можно считать по-разному
 * (напрямую или через FFT), а политика выбора пика должна быть ровно одна.
 */
internal object MpmSupport {

    fun pickCandidate(
        nsdf: FloatArray,
        size: Int,
        sampleRate: Int,
        cutoff: Double,
        smallCutoff: Double,
        lowerHz: Float,
        upperHz: Float,
    ): PitchCandidate? {
        val maxPositions = peakPicking(nsdf, size)

        var highest = Double.NEGATIVE_INFINITY
        val periods = ArrayList<Double>(16)
        val amps = ArrayList<Double>(16)
        val last = size - 1

        for (tau in maxPositions) {
            highest = max(highest, nsdf[tau].toDouble())
            if (nsdf[tau] > smallCutoff && tau > 0 && tau < last) {
                val (px, py) = parabolic(nsdf, tau)
                amps.add(py)
                periods.add(px)
                highest = max(highest, py)
            }
        }
        if (periods.isEmpty()) return null

        // Кандидаты по порядку (короткий период = высокая частота — первым).
        // Проверка двойного периода: у настоящего фундаментала NSDF(2T) тоже силён
        // (периодичность!), у дробного призрака вроде 2/3·T0 — слаб.
        val actualCutoff = cutoff * highest
        for (idx in periods.indices) {
            if (amps[idx] < actualCutoff) continue
            val period = periods[idx]
            if (period <= 0.0) continue
            if (doublePeriodOk(nsdf, size, period, amps[idx])) {
                val freq = (sampleRate / period).toFloat()
                if (freq < lowerHz || freq > upperHz) return null
                return PitchCandidate(freq, highest.coerceIn(0.0, 1.0).toFloat())
            }
        }
        return null // все кандидаты — призраки: честная тишина лучше вранья
    }

    /** NSDF(2T) линейной интерполяцией; true, если ≥40% амплитуды пика. */
    private fun doublePeriodOk(nsdf: FloatArray, size: Int, period: Double, peakAmp: Double): Boolean {
        val t2 = period * 2
        val last = (size - 1).toDouble()
        if (t2 >= last) return true // не проверяемо — доверяем
        val i = t2.toInt()
        val frac = t2 - i
        val v = nsdf[i] * (1 - frac) + nsdf[i + 1] * frac
        return v >= 0.4 * peakAmp
    }

    /**
     * Пики между парами positive zero-crossing (код Tartini, general/mytransforms.cpp).
     * Первый максимум (при tau=0) игнорируется — пропускаем начальную положительную долю.
     */
    private fun peakPicking(nsdf: FloatArray, size: Int): MutableList<Int> {
        val positions = ArrayList<Int>(16)
        val last = size - 1
        var pos = 0
        var curMaxPos = 0

        // пропускаем первую положительную долю (там сидит тривиальный максимум tau=0)
        while (pos < last / 3 && nsdf[pos] > 0) pos++
        // пропускаем всё ниже нуля
        while (pos < last && nsdf[pos] <= 0f) pos++
        if (pos == 0) pos = 1

        while (pos < last) {
            if (nsdf[pos] > nsdf[pos - 1] && nsdf[pos] >= nsdf[pos + 1]) {
                if (curMaxPos == 0) {
                    curMaxPos = pos
                } else if (nsdf[pos] > nsdf[curMaxPos]) {
                    curMaxPos = pos
                }
            }
            pos++
            // отрицательный zero-crossing — фиксируем максимум доли
            if (pos < last && nsdf[pos] <= 0) {
                if (curMaxPos > 0) {
                    positions.add(curMaxPos)
                    curMaxPos = 0
                }
                while (pos < last && nsdf[pos] <= 0f) pos++
            }
        }
        if (curMaxPos > 0) positions.add(curMaxPos)
        return positions
    }

    /** Параболическая доводка пика NSDF → суб-сэмпловый период + амплитуда. */
    private fun parabolic(nsdf: FloatArray, tau: Int): Pair<Double, Double> {
        val a = nsdf[tau - 1].toDouble()
        val b = nsdf[tau].toDouble()
        val c = nsdf[tau + 1].toDouble()
        val bottom = c + a - 2 * b
        if (bottom == 0.0) return tau.toDouble() to b
        val delta = a - c
        val x = tau + delta / (2 * bottom)
        val y = b - delta * delta / (8 * bottom)
        return x to y
    }
}
