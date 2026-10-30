package com.dangler.tune.dsp

import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.sqrt

/**
 * Диагностика смещения на низких струнах. Не проверяет — печатает.
 * Нужна, чтобы понимать, откуда центы: негармоничность, окно, фильтр или трекер.
 */
class PitchDiagnosticsTest {

    @Test
    fun b1_inharmonicityAndWindow() {
        println("=== B1: inharmonicity × window ===")
        for (inh in doubleArrayOf(0.0, 5e-5, 1e-4, 3e-4)) {
            for (win in intArrayOf(2048, 4096)) {
                val signal = PitchCorpus.pluck(61.74f, inharmonicity = inh)
                val detector = FftMpmDetector(sampleRate = 22050, bufferSize = win)
                println(PitchMetrics.detector(detector, signal, 61.74f, windowSize = win).line("B1 inh=$inh win=$win"))
            }
        }
    }

    @Test
    fun b1_lowPassCutoff() {
        println("=== B1: LPF cutoff (window 2048) ===")
        for (cut in doubleArrayOf(150.0, 250.0, 400.0, 800.0, 1000.0)) {
            val signal = PitchCorpus.pluck(61.74f)
            val (mean, max, n) = measureWithLowPass(signal, 61.74f, cut)
            println("B1 LPF=%5.0f Hz  mean=%5.2f max=%6.2f n=%d".format(cut, mean, max, n))
        }
    }

    @Test
    fun weakFundamental_sweep() {
        println("=== B1: fundamental gain sweep (второй проход включён) ===")
        for (gain in doubleArrayOf(1.0, 0.3, 0.1, 0.03, 0.01, 0.003, 0.001, 0.0)) {
            val signal = PitchCorpus.pluck(61.74f, fundamentalGain = gain)
            val d = PitchMetrics.detector(FftMpmDetector(sampleRate = 22050, bufferSize = 2048), signal, 61.74f)
            println(d.line(String.format(java.util.Locale.ROOT, "B1 fund=%7.4f", gain)))
        }
    }

    @Test
    fun refineHarmonics_sweep() {
        println("=== refine harmonics sweep (low notes) ===")
        for (inh in doubleArrayOf(1e-4, 3e-4)) {
            for (snr in doubleArrayOf(30.0, 15.0)) {
                for (k in floatArrayOf(2f, 3f, 4f, 5f, 6f)) {
                    val detector = FftMpmDetector(
                        sampleRate = 22050,
                        bufferSize = 2048,
                        refineHarmonics = k,
                    )
                    for (f in floatArrayOf(61.74f, 82.41f, 110f)) {
                        val signal = PitchCorpus.pluck(f, inharmonicity = inh, snrDb = snr)
                        val d = PitchMetrics.detector(detector, signal, f)
                        println(d.line("inh=$inh snr=$snr k=$k f=$f"))
                    }
                }
            }
        }
    }

    @Test
    fun b1_perFrame() {
        println("=== B1: per-frame detector error ===")
        val signal = PitchCorpus.pluck(61.74f)
        val window = FloatArray(2048)
        val ds = Decimator(2).push(signal)
        val conditioner = SignalConditioner()
        var i = (0.10 * 22050).toInt()
        while (i + 2048 <= (1.9 * 22050).toInt()) {
            System.arraycopy(ds, i, window, 0, 2048)
            val c = conditioner.process(window)
            val r = FftMpmDetector(sampleRate = 22050, bufferSize = 2048).detect(window)
            if (c != null && r != null) {
                val err = 1200 * log2((r.frequencyHz / 61.74f).toDouble())
                println("t=%5.2fs  f=%7.3f  err=%+6.2f¢  clar=%.3f  rms=%.4f".format(
                    i / 22050.0, r.frequencyHz, err, r.clarity, c.rmsFiltered
                ))
            }
            i += 1024
        }
    }

    @Test
    fun b1_pipelinePerFrame() {
        println("=== B1: per-frame pipeline error ===")
        val signal = PitchCorpus.pluck(61.74f)
        val pipe = PitchPipeline()
        var i = 0
        while (i < signal.size) {
            val end = minOf(i + 4096, signal.size)
            val r = pipe.push(signal.copyOfRange(i, end))
            if (r != null && end > (0.10 * 44100).toInt()) {
                val err = 1200 * log2((r.frequencyHz / 61.74f).toDouble())
                println("t=%5.2fs  f=%7.3f  err=%+6.2f¢  clar=%.3f".format(
                    end / 44100.0, r.frequencyHz, err, r.clarity
                ))
            }
            i = end
        }
    }

    private fun measureWithLowPass(
        signal: FloatArray,
        trueHz: Float,
        cutoffHz: Double,
        windowSize: Int = 2048,
    ): Triple<Double, Double, Int> {
        val ds = Decimator(2).push(signal)
        val detector = FftMpmDetector(sampleRate = 22050, bufferSize = windowSize, adaptiveRefine = false)
        val a = (1.0 - exp(-2.0 * PI * cutoffHz / 22050.0)).toFloat()
        val window = FloatArray(windowSize)
        var sum = 0.0
        var max = 0.0
        var n = 0
        var i = (0.30 * 22050).toInt()
        while (i + windowSize <= (1.90 * 22050).toInt()) {
            System.arraycopy(ds, i, window, 0, windowSize)
            var mean = 0.0
            for (v in window) mean += v
            mean /= windowSize
            var energy = 0.0
            for (k in window.indices) {
                val x = (window[k] - mean).toFloat()
                window[k] = x
                energy += x.toDouble() * x
            }
            if (sqrt(energy / windowSize) >= 0.006) {
                var y = window[0]
                for (k in window.indices) {
                    y += a * (window[k] - y)
                    window[k] = y
                }
                val r = detector.detect(window)
                if (r != null) {
                    val err = abs(1200 * log2((r.frequencyHz / trueHz).toDouble()))
                    sum += err
                    if (err > max) max = err
                    n++
                }
            }
            i += 1024
        }
        return Triple(if (n > 0) sum / n else Double.NaN, max, n)
    }
}
