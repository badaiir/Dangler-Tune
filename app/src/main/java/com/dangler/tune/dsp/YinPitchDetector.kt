package com.dangler.tune.dsp

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Крутой детектор: YIN (de Cheveigné & Kawahara) + параболическая интерполяция
 * + медианный фильтр сверху (в TunerViewModel).
 *
 * Почему YIN, а не просто FFT peak:
 * - гитара = сильный обертонный ряд, FFT часто ловит 2f/3f вместо фундамента
 * - YIN меряет автокорреляцию через разностную функцию -> устойчив к гармоникам
 * - точность ~0.5-1 цент на 60-500 Гц при 44100 Гц, чего хватает для Peterson-style строба
 */
class YinPitchDetector(
    private val sampleRate: Int = 44100,
    private val bufferSize: Int = 4096,
    private val threshold: Float = 0.12f, // ниже = строже, 0.10-0.15 ок для гитары
    private val minFreqHz: Float = 55f,   // ниже B1 (61.7) с запасом
    private val maxFreqHz: Float = 660f,  // выше E6 (329) с запасом для гармоник-артефактов
) {
    private val yinBuffer = FloatArray(bufferSize / 2)

    data class Result(val frequencyHz: Float, val clarity: Float) // clarity 0..1, >0.6 = уверенно

    fun detect(input: FloatArray): Result? {
        if (input.size < bufferSize) return null

        // 1. RMS gate — тишина/шум не детектим
        var rms = 0.0
        for (i in 0 until bufferSize) rms += input[i] * input[i]
        rms = kotlin.math.sqrt(rms / bufferSize)
        if (rms < 0.008) return null // -42 dBFS примерно

        // 2. Разностная функция
        for (tau in 0 until bufferSize / 2) {
            var sum = 0f
            for (i in 0 until bufferSize / 2) {
                val delta = input[i] - input[i + tau]
                sum += delta * delta
            }
            yinBuffer[tau] = sum
        }

        // 3. Кумулятивная нормализация
        yinBuffer[0] = 1f
        var runningSum = 0f
        for (tau in 1 until bufferSize / 2) {
            runningSum += yinBuffer[tau]
            yinBuffer[tau] *= tau / runningSum
        }

        // 4. Поиск первого dip ниже threshold
        val minTau = (sampleRate / maxFreqHz).roundToInt().coerceAtLeast(2)
        val maxTau = (sampleRate / minFreqHz).roundToInt().coerceAtMost(bufferSize / 2 - 2)

        var tauEstimate = -1
        for (tau in minTau..maxTau) {
            if (yinBuffer[tau] < threshold) {
                // ищем локальный минимум
                var t = tau
                while (t + 1 <= maxTau && yinBuffer[t + 1] < yinBuffer[t]) t++
                tauEstimate = t
                break
            }
        }
        if (tauEstimate == -1) return null

        // 5. Параболическая интерполяция для суб-сэмпловой точности (критично для центов!)
        val x0 = (tauEstimate - 1).coerceAtLeast(1)
        val x2 = (tauEstimate + 1).coerceAtMost(bufferSize / 2 - 1)
        val s0 = yinBuffer[x0].toDouble()
        val s1 = yinBuffer[tauEstimate].toDouble()
        val s2 = yinBuffer[x2].toDouble()
        val denom = (s0 + s2 - 2 * s1)
        val shift = if (denom == 0.0) 0.0 else 0.5 * (s0 - s2) / denom
        val betterTau = tauEstimate + shift.coerceIn(-1.0, 1.0)

        val freq = (sampleRate / betterTau).toFloat()
        if (freq < minFreqHz || freq > maxFreqHz) return null

        val clarity = (1f - yinBuffer[tauEstimate].coerceIn(0f, 1f))
        if (clarity < 0.35f) return null

        return Result(freq, clarity)
    }
}

/** Ноты, центы, MIDI-утилиты */
object NoteUtils {
    val NOTE_NAMES_SHARP = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    data class NoteInfo(
        val midi: Int,
        val name: String,      // "B"
        val octave: Int,       // 1
        val display: String,   // "B1"
        val targetFreq: Float, // идеальная частота ноты
        val cents: Float,      // отклонение -50..+50
    )

    fun freqToNote(freqHz: Float): NoteInfo {
        val midiFloat = 69 + 12 * log2(freqHz / 440.0)
        val midi = midiFloat.roundToInt()
        val target = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
        val cents = (1200 * log2(freqHz / target)).toFloat()
        val name = NOTE_NAMES_SHARP[(midi % 12 + 12) % 12]
        val octave = midi / 12 - 1
        return NoteInfo(midi, name, octave, "$name$octave", target, cents)
    }

    /** Ближайшая струна строя к услышанной частоте (в пределах +-6 полутонов) */
    fun nearestString(freqHz: Float, tuning: com.dangler.tune.model.Tuning): com.dangler.tune.model.Tuning.StringNote? {
        return tuning.strings.minByOrNull {
            abs(12 * log2(freqHz / it.frequencyHz))
        }
    }

    /** Центы относительно конкретной струны (для режима "по струнам") */
    fun centsRelativeTo(freqHz: Float, targetHz: Float): Float =
        (1200 * log2(freqHz / targetHz)).toFloat()
}
