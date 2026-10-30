package com.dangler.tune.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Синтетический «гитарный» корпус для замеров точности.
 *
 * Это НЕ замена реальным записям: комната, тело гитары, AGC телефона и кривые
 * руки сюда не влезают. Но корпус ловит то, что можно проверить машинно:
 * слабую фундаменталку, негармоничность струны, затухание обертонов, шум,
 * расстройку и негативы (шум/фон/стук), на которых тюнер обязан молчать.
 *
 * Все сигналы — 44100 Гц mono float, как отдаёт AudioRecorder.
 */
internal object PitchCorpus {

    const val SR = 44100

    data class Note(val name: String, val hz: Float, val tuning: String)

    val DROP_B = listOf(
        Note("B1", 61.74f, "Drop B"),
        Note("F#2", 92.50f, "Drop B"),
        Note("B2", 123.47f, "Drop B"),
        Note("E3", 164.81f, "Drop B"),
        Note("G#3", 207.65f, "Drop B"),
        Note("C#4", 277.18f, "Drop B"),
    )

    val STANDARD_E = listOf(
        Note("E2", 82.41f, "Standard E"),
        Note("A2", 110.00f, "Standard E"),
        Note("D3", 146.83f, "Standard E"),
        Note("G3", 196.00f, "Standard E"),
        Note("B3", 246.94f, "Standard E"),
        Note("E4", 329.63f, "Standard E"),
    )

    val ALL = DROP_B + STANDARD_E + Note("A4", 440f, "reference")

    fun detuned(hz: Float, cents: Float): Float =
        (hz * 2.0.pow(cents / 1200.0)).toFloat()

    /**
     * Щипок: гармоники 1/h^0.9, высокие затухают быстрее, лёгкая негармоничность
     * струны (B ≈ 1e-4), короткий шумовой щелчок атаки, белый шум с заданным SNR.
     */
    fun pluck(
        hz: Float,
        seconds: Double = 2.2,
        levelRms: Double = 0.08,
        snrDb: Double = 30.0,
        fundamentalGain: Double = 1.0,
        inharmonicity: Double = 1e-4,
        harmonics: Int = 16,
        seed: Int = 1,
        /**
         * Позиция щипка как доля длины струны от бриджа (0..1).
         * 0 — выключено. 0.1 — у бриджа (ярко, часть гармоник задавлена),
         * 0.5 — по центру (чётные гармоники в нуле). Реальный медиатор ~0.15–0.25.
         */
        pluckPosition: Double = 0.0,
        /** Обрезка низа микрофоном телефона: типично 80–120 Гц, 2-й порядок. */
        micHighPassHz: Double = 0.0,
        /** Обрезка верха микрофоном (для полноты картины). */
        micLowPassHz: Double = 0.0,
        /** Резонанс корпуса: +4 дБ, Q=1. */
        bodyPeakHz: Double = 0.0,
    ): FloatArray {
        val n = (SR * seconds).toInt()
        val out = DoubleArray(n)
        val rnd = Random(seed)
        val phases = DoubleArray(harmonics + 1) { rnd.nextDouble() * 2 * PI }

        for (h in 1..harmonics) {
            val comb = if (pluckPosition > 0.0) abs(sin(PI * h * pluckPosition)) else 1.0
            val amp = (if (h == 1) fundamentalGain else 1.0 / h.toDouble().pow(0.9)) * comb
            val tau = 2.6 / (1.0 + 0.5 * (h - 1))
            val fh = hz * h * sqrt(1.0 + inharmonicity * h * h)
            for (i in 0 until n) {
                val t = i.toDouble() / SR
                out[i] += amp * exp(-t / tau) * sin(2 * PI * fh * t + phases[h])
            }
        }

        // медиатор: короткий шумовой щелчок
        val clickN = (0.004 * SR).toInt()
        for (i in 0 until clickN) {
            out[i] += (rnd.nextDouble() * 2 - 1) * 0.2 * exp(-i / (0.0008 * SR))
        }

        normalizeRms(out, target = levelRms, window = (0.5 * SR).toInt())
        addWhiteNoise(out, snrDb = snrDb, rnd = rnd)

        // Микрофон/корпус фильтруют уже сумму сигнала и шума.
        if (micHighPassHz > 0.0) applyFilter(out, Biquad.highPass(SR.toDouble(), micHighPassHz))
        if (micLowPassHz > 0.0) applyFilter(out, Biquad.lowPass(SR.toDouble(), micLowPassHz))
        if (bodyPeakHz > 0.0) applyFilter(out, Biquad.peaking(SR.toDouble(), bodyPeakHz, 1.0, 4.0))

        return FloatArray(n) { out[it].toFloat() }
    }

    /**
     * Розовый шум (Paul Kellet): больше энергии внизу, чем у белого.
     * Кулер/вентилятор/улица звучат именно так и чаще всего дают ложные ноты.
     */
    fun pinkNoise(seconds: Double = 2.0, rms: Double = 0.05, seed: Int = 4): FloatArray {
        val n = (SR * seconds).toInt()
        val rnd = Random(seed)
        val out = DoubleArray(n)
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        var b3 = 0.0
        var b4 = 0.0
        var b5 = 0.0
        var b6 = 0.0
        for (i in 0 until n) {
            val white = rnd.nextDouble() * 2 - 1
            b0 = 0.99886 * b0 + white * 0.0555179
            b1 = 0.99332 * b1 + white * 0.0750759
            b2 = 0.96900 * b2 + white * 0.1538520
            b3 = 0.86650 * b3 + white * 0.3104856
            b4 = 0.55000 * b4 + white * 0.5329522
            b5 = -0.7616 * b5 - white * 0.0168980
            out[i] = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
            b6 = white * 0.115926
        }
        normalizeRms(out, target = rms, window = n)
        return FloatArray(n) { out[it].toFloat() }
    }

    /** Белый шум — тюнер обязан молчать. */
    fun whiteNoise(seconds: Double = 2.0, rms: Double = 0.05, seed: Int = 2): FloatArray {
        val rnd = Random(seed)
        val amp = rms * sqrt(3.0)
        return FloatArray((SR * seconds).toInt()) {
            ((rnd.nextDouble() * 2 - 1) * amp).toFloat()
        }
    }

    /** Сетевой фон 50 Гц с гармониками — типичная наводка у компа/зарядки. */
    fun hum(seconds: Double = 2.0, hz: Double = 50.0, rms: Double = 0.05): FloatArray {
        val n = (SR * seconds).toInt()
        val out = DoubleArray(n)
        val amps = doubleArrayOf(1.0, 0.5, 0.3, 0.15)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            for (h in amps.indices) {
                out[i] += amps[h] * sin(2 * PI * hz * (h + 1) * t)
            }
        }
        normalizeRms(out, target = rms, window = n)
        return FloatArray(n) { out[it].toFloat() }
    }

    /** Постукивание по корпусу/грифу: широкополосные импульсы. */
    fun taps(seconds: Double = 2.0, rms: Double = 0.05, seed: Int = 3): FloatArray {
        val n = (SR * seconds).toInt()
        val out = DoubleArray(n)
        val rnd = Random(seed)
        var tap = (0.15 * SR).toInt()
        while (tap < n - (0.05 * SR)) {
            for (i in 0 until (0.03 * SR).toInt()) {
                out[tap + i] += (rnd.nextDouble() * 2 - 1) * exp(-i / (0.004 * SR))
            }
            tap += (0.45 * SR).toInt()
        }
        normalizeRms(out, target = rms, window = n)
        return FloatArray(n) { out[it].toFloat() }
    }

    private fun normalizeRms(buf: DoubleArray, target: Double, window: Int) {
        val n = minOf(window, buf.size)
        var energy = 0.0
        for (i in 0 until n) energy += buf[i] * buf[i]
        val rms = sqrt(energy / n)
        if (rms <= 1e-12) return
        val gain = target / rms
        for (i in buf.indices) buf[i] *= gain
    }

    private fun addWhiteNoise(buf: DoubleArray, snrDb: Double, rnd: Random) {
        val n = minOf((0.5 * SR).toInt(), buf.size)
        var energy = 0.0
        for (i in 0 until n) energy += buf[i] * buf[i]
        val signalRms = sqrt(energy / n)
        val noiseRms = signalRms / 10.0.pow(snrDb / 20.0)
        val amp = noiseRms * sqrt(3.0)
        for (i in buf.indices) buf[i] += (rnd.nextDouble() * 2 - 1) * amp
    }

    private fun applyFilter(buf: DoubleArray, filter: Biquad) {
        for (i in buf.indices) buf[i] = filter.process(buf[i])
    }

    /** RBJ cookbook biquad; нужен только тестовому корпусу. */
    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double,
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        companion object {
            private fun normalized(
                b0: Double, b1: Double, b2: Double,
                a0: Double, a1: Double, a2: Double,
            ) = Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

            fun highPass(fs: Double, f0: Double, q: Double = 0.7071): Biquad {
                val w0 = 2 * PI * f0 / fs
                val cosW = cos(w0)
                val alpha = sin(w0) / (2 * q)
                return normalized(
                    b0 = (1 + cosW) / 2, b1 = -(1 + cosW), b2 = (1 + cosW) / 2,
                    a0 = 1 + alpha, a1 = -2 * cosW, a2 = 1 - alpha,
                )
            }

            fun lowPass(fs: Double, f0: Double, q: Double = 0.7071): Biquad {
                val w0 = 2 * PI * f0 / fs
                val cosW = cos(w0)
                val alpha = sin(w0) / (2 * q)
                return normalized(
                    b0 = (1 - cosW) / 2, b1 = 1 - cosW, b2 = (1 - cosW) / 2,
                    a0 = 1 + alpha, a1 = -2 * cosW, a2 = 1 - alpha,
                )
            }

            fun peaking(fs: Double, f0: Double, q: Double, gainDb: Double): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2 * PI * f0 / fs
                val cosW = cos(w0)
                val alpha = sin(w0) / (2 * q)
                return normalized(
                    b0 = 1 + alpha * a, b1 = -2 * cosW, b2 = 1 - alpha * a,
                    a0 = 1 + alpha / a, a1 = -2 * cosW, a2 = 1 - alpha / a,
                )
            }
        }
    }
}
