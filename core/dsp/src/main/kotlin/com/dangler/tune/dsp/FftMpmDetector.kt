package com.dangler.tune.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2

/**
 * Тот же MPM, но NSDF считается через FFT (теорема Винера–Хинчина):
 *
 *   ACF = IFFT(|FFT(x)|²)
 *   NSDF(τ) = 2·ACF(τ) / (Σx² − P(τ) + P(N−τ))
 *
 * где P — префиксные суммы энергий. Результат математически тот же,
 * что у [MpmPitchDetector], но стоимость O(N log N) вместо O(N²).
 *
 * Второй проход (только для низких нот): струна негармонична — обертоны
 * растянуты (f_h ≈ h·f0·√(1+B·h²)), и автокорреляция «уезжает» вверх
 * на 3–6 центов на басу. Поэтому после грубой оценки включаем low-pass
 * на 4·f0: остаются первые 4 гармоники, растяжение почти не влияет.
 * На синтетике B1 это даёт ~0.7¢ средних вместо ~1.6¢ и убирает +5.7¢
 * на атаке.
 *
 * Буферы переиспользуются: на кадр — ноль аллокаций.
 */
class FftMpmDetector(
    private val sampleRate: Int = 22050,
    private val bufferSize: Int = 2048,
    private val cutoff: Double = 0.97,
    private val smallCutoff: Double = 0.5,
    private val lowerPitchCutoffHz: Float = 50f,
    private val upperPitchCutoffHz: Float = 800f,
    /** Сколько гармоник оставляем на уточняющем проходе. */
    private val refineHarmonics: Float = 4f,
    /** Выше этой частоты уточнение не нужно: гармоник и так мало. */
    private val refineBelowHz: Float = 250f,
    /**
     * Второй проход с адаптивным low-pass. Отключается только для проверки
     * эквивалентности базового NSDF классической автокорреляции.
     */
    private val adaptiveRefine: Boolean = true,
) : PitchDetector {

    // Для линейной (не циклической) ACF нужен размер ≥ 2·N.
    private val fftSize = Integer.highestOneBit(bufferSize * 2 - 1) * 2
    private val fft = Radix2Fft(fftSize)
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val nsdf = FloatArray(bufferSize)
    /** P[k] = Σ input[i]² для i < k. */
    private val prefixEnergy = DoubleArray(bufferSize + 1)
    /** Копия окна для второго прохода: вход детектора не мутируем. */
    private val scratch = FloatArray(bufferSize)

    override fun detect(input: FloatArray): PitchCandidate? {
        val coarse = detectOnce(input) ?: return null
        if (!adaptiveRefine || coarse.frequencyHz >= refineBelowHz) return coarse

        val refineCutoff = (coarse.frequencyHz * refineHarmonics)
            .coerceIn(MIN_REFINE_HZ, MAX_REFINE_HZ)
        val alpha = (1.0 - exp(-2.0 * PI * refineCutoff / sampleRate)).toFloat()
        var y = input[0]
        for (i in 0 until bufferSize) {
            y += alpha * (input[i] - y)
            scratch[i] = y
        }

        val refined = detectOnce(scratch) ?: return coarse
        val cents = abs(1200 * log2((refined.frequencyHz / coarse.frequencyHz).toDouble()))
        return when {
            // обычное уточнение в пределах полутона
            cents < 100.0 -> refined
            // грубая оценка была октавой выше: фильтр вернул фундаментал
            isOctaveDown(refined.frequencyHz, coarse.frequencyHz) && refined.clarity >= 0.80f -> refined
            else -> coarse
        }
    }

    private fun detectOnce(input: FloatArray): PitchCandidate? {
        if (input.size < bufferSize) return null

        java.util.Arrays.fill(re, 0.0)
        java.util.Arrays.fill(im, 0.0)

        prefixEnergy[0] = 0.0
        for (i in 0 until bufferSize) {
            val x = input[i].toDouble()
            re[i] = x
            prefixEnergy[i + 1] = prefixEnergy[i] + x * x
        }
        val totalEnergy = prefixEnergy[bufferSize]
        if (totalEnergy <= 0.0) return null

        fft.forward(re, im)
        for (i in 0 until fftSize) {
            val power = re[i] * re[i] + im[i] * im[i]
            re[i] = power
            im[i] = 0.0
        }
        fft.inverse(re, im)

        for (tau in 0 until bufferSize) {
            // m(τ) = Σ_{j=0}^{N-τ-1} (x[j]² + x[j+τ]²) = E − P(τ) + P(N−τ)
            val divisor = totalEnergy - prefixEnergy[tau] + prefixEnergy[bufferSize - tau]
            nsdf[tau] = if (divisor > 0.0) (2.0 * re[tau] / divisor).toFloat() else 0f
        }

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

    private fun isOctaveDown(low: Float, high: Float): Boolean =
        abs(high / low - 2f) < 0.12f

    private companion object {
        const val MIN_REFINE_HZ = 120f
        const val MAX_REFINE_HZ = 1000f
    }
}
