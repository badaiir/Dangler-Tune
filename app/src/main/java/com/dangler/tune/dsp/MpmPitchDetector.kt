package com.dangler.tune.dsp

import kotlin.math.max

/**
 * McLeod Pitch Method (MPM) — "A Smarter Way to Find Pitch" (McLeod & Wyvill, Tartini).
 * Выбор вместо YIN осознанно:
 * - специальная нормализованная SDF + peak-picking с cutoff — почти не даёт октавных ошибок
 *   на гитаре с сильным обертонным рядом;
 * - точность ~1 цент из коробки, без пост-обработки (заявление из статьи);
 * - не требует low-pass фильтра, работает на скрипке — гитарные гармоники не страшны.
 *
 * Структура повторяет TarsosDSP McLeodPitchMethod (Joren Six, Tartini by Philip McLeod):
 * NSDF → peak picking между zero-crossing → параболическая доводка пиков →
 * первый пик выше cutoff * max. Своя реализация на Kotlin, без зависимостей.
 */
class MpmPitchDetector(
    private val sampleRate: Int = 22050,
    private val bufferSize: Int = 2048,
    /**
     * Доля от сильнейшего пика: первый пик выше cutoff*max и есть период.
     * TarsosDSP дефолт 0.97 (строже Tartini 0.93) — жёстче режет верхние призраки
     * (квинтовые/октавные) ценой чуть большего числа пропусков на шуме.
     * Для тюнера у грифа это правильный трейд-офф: лучше пропуск, чем врёт.
     */
    private val cutoff: Double = 0.97,
    /** Пики ниже даже не рассматриваем (TarsosDSP: 0.5). */
    private val smallCutoff: Double = 0.5,
    private val lowerPitchCutoffHz: Float = 50f, // ниже B1 (61.7) с запасом
    private val upperPitchCutoffHz: Float = 800f,
) {
    private val nsdf = FloatArray(bufferSize)

    data class Result(val frequencyHz: Float, val clarity: Float) // clarity 0..1 (амплитуда NSDF)

    fun detect(input: FloatArray): Result? {
        if (input.size < bufferSize) return null

        normalizedSquareDifference(input)
        val maxPositions = peakPicking()

        var highest = Double.NEGATIVE_INFINITY
        val periods = ArrayList<Double>(16)
        val amps = ArrayList<Double>(16)
        val last = bufferSize - 1
        for (tau in maxPositions) {
            highest = max(highest, nsdf[tau].toDouble())
            if (nsdf[tau] > smallCutoff && tau > 0 && tau < last) {
                val (px, py) = parabolic(tau)
                amps.add(py)
                periods.add(px)
                highest = max(highest, py)
            }
        }
        if (periods.isEmpty()) return null

        // Кандидаты по порядку (короткий период = высокая частота — первым).
        // Проверка двойного периода: у настоящего фундаментала NSDF(2T) тоже силён
        // (периодичность!), у дробного призрака вроде 2/3·T0 — слаб. Иначе врём квинту вверх.
        val actualCutoff = cutoff * highest
        val candidates = amps.indices.filter { amps[it] >= actualCutoff }
        for (idx in candidates) {
            val period = periods[idx]
            if (period <= 0.0) continue
            if (doublePeriodOk(period, amps[idx])) {
                val freq = (sampleRate / period).toFloat()
                if (freq < lowerPitchCutoffHz || freq > upperPitchCutoffHz) return null
                return Result(freq, highest.coerceIn(0.0, 1.0).toFloat())
            }
        }
        return null // все кандидаты — призраки: честная тишина лучше вранья
    }

    /** NSDF(2T) линейной интерполяцией; true, если ≥40% амплитуды пика. */
    private fun doublePeriodOk(period: Double, peakAmp: Double): Boolean {
        val t2 = period * 2
        val last = (bufferSize - 1).toDouble()
        if (t2 >= last) return true // не проверяемо — доверяем
        val i = t2.toInt()
        val frac = t2 - i
        val v = nsdf[i] * (1 - frac) + nsdf[i + 1] * frac
        return v >= 0.4 * peakAmp
    }

    /** Нормализованная SDF: nsdf[tau] = 2*ACF / (E[x²]+E[y²]), в [-1, 1]. */
    private fun normalizedSquareDifference(input: FloatArray) {
        for (tau in 0 until bufferSize) {
            var acf = 0.0
            var divisor = 0.0
            val n = bufferSize - tau
            var i = 0
            while (i < n) {
                val x = input[i].toDouble()
                val y = input[i + tau].toDouble()
                acf += x * y
                divisor += x * x + y * y
                i++
            }
            nsdf[tau] = if (divisor > 0.0) (2 * acf / divisor).toFloat() else 0f
        }
    }

    /**
     * Пики между парами positive zero-crossing (код Tartini, general/mytransforms.cpp).
     * Первый максимум (при tau=0) игнорируется — пропускаем начальную положительную долю.
     */
    private fun peakPicking(): MutableList<Int> {
        val positions = ArrayList<Int>(16)
        val last = bufferSize - 1
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
    private fun parabolic(tau: Int): Pair<Double, Double> {
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
