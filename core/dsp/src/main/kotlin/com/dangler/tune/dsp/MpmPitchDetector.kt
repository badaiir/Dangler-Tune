package com.dangler.tune.dsp

/**
 * McLeod Pitch Method (MPM) — "A Smarter Way to Find Pitch" (McLeod & Wyvill, Tartini).
 * Выбор вместо YIN осознанно:
 * - специальная нормализованная SDF + peak-picking с cutoff — почти не даёт октавных ошибок
 *   на гитаре с сильным обертонным рядом;
 * - точность ~1 цент из коробки, без пост-обработки (заявление из статьи);
 * - не требует low-pass фильтра, работает на скрипке — гитарные гармоники не страшны.
 *
 * Это классическая реализация: NSDF считается напрямую, O(N·L). Она остаётся
 * эталоном для тестов; в проде по умолчанию используется [FftMpmDetector]
 * с той же политикой выбора пика, но быстрым NSDF через FFT.
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
) : PitchDetector {
    private val nsdf = FloatArray(bufferSize)

    override fun detect(input: FloatArray): PitchCandidate? {
        if (input.size < bufferSize) return null
        normalizedSquareDifference(input)
        return MpmSupport.pickCandidate(
            nsdf = nsdf,
            size = bufferSize,
            sampleRate = sampleRate,
            cutoff = cutoff,
            smallCutoff = smallCutoff,
            lowerHz = lowerPitchCutoffHz,
            upperHz = upperPitchCutoffHz,
        )
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
}
