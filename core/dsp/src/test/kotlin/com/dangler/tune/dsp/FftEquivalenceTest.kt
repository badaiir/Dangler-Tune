package com.dangler.tune.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sin
import kotlin.random.Random

/**
 * FFT-NSDF обязан давать ту же частоту, что и прямая автокорреляция.
 * Это не «примерно», это одна и та же математика с разной стоимостью.
 * Второй проход (адаптивный low-pass) здесь намеренно выключен: он меняет
 * результат осознанно, и его проверяет PitchAccuracyTest.
 */
class FftEquivalenceTest {

    private fun pluck(freq: Float, sr: Int = 44100, secs: Double = 1.2, fundamentalGain: Double = 1.0): FloatArray {
        val n = (sr * secs).toInt()
        val rnd = Random(7)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            var s = 0.0
            for (h in 1..12) {
                val amp = if (h == 1) fundamentalGain else 1.0 / h
                s += amp * kotlin.math.exp(-t * (1.2 + 0.4 * h)) *
                        sin(2 * PI * freq * h * t)
            }
            out[i] = (s * 0.25 + (rnd.nextDouble() * 2 - 1) * 0.004).toFloat()
        }
        return out
    }

    private fun compareOn(signal: FloatArray, label: String) {
        val decimated = Decimator(2).push(signal)
        val classic = MpmPitchDetector(sampleRate = 22050, bufferSize = 2048)
        val fft = FftMpmDetector(sampleRate = 22050, bufferSize = 2048, adaptiveRefine = false)
        val conditioner = SignalConditioner()
        val window = FloatArray(2048)

        var i = 0
        var compared = 0
        while (i + 2048 <= decimated.size) {
            System.arraycopy(decimated, i, window, 0, 2048)
            if (conditioner.process(window) != null) {
                val a = classic.detect(window)
                val b = fft.detect(window)
                if (a == null) {
                    assertNull("$label at $i: classic=null, fft=$b", b)
                } else {
                    val diffCents = abs(1200 * log2((b!!.frequencyHz / a.frequencyHz).toDouble()))
                    assertTrue("$label at $i: classic=${a.frequencyHz}, fft=${b.frequencyHz} (${diffCents}¢)", diffCents < 0.05)
                    assertEquals("$label clarity", a.clarity.toDouble(), b.clarity.toDouble(), 1e-3)
                    compared++
                }
            }
            i += 1024
        }
        assertTrue("$label: nothing compared", compared > 0)
    }

    @Test
    fun matchesClassic_onGuitarNotes() {
        for (f in floatArrayOf(61.74f, 82.41f, 110f, 164.81f, 246.94f, 329.63f, 440f)) {
            compareOn(pluck(f), "note $f")
        }
    }

    @Test
    fun matchesClassic_onWeakFundamental() {
        compareOn(pluck(82.41f, fundamentalGain = 0.03), "weak fundamental")
    }
}
