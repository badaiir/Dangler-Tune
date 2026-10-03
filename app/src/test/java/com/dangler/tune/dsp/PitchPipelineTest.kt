package com.dangler.tune.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Синтетическая валидация pitch-пайплайна (гоняется в CI: :app:testDebugUnitTest).
 * Критерии тюнера: средняя ошибка <3¢, ноль октавных ошибок, сквозной пайплайн <3¢.
 */
class PitchPipelineTest {

    private fun sine(freq: Float, sr: Int, secs: Double): FloatArray {
        val n = (sr * secs).toInt()
        return FloatArray(n) { sin(2 * PI * freq * it / sr).toFloat() * 0.5f }
    }

    /** Реалистичный щипок: гармоники затухают быстрее фундаменталки. */
    private fun pluck(freq: Float, sr: Int, secs: Double): FloatArray {
        val n = (sr * secs).toInt()
        val out = FloatArray(n)
        val rnd = Random(7)
        val amps = floatArrayOf(1f, 0.4f, 0.2f, 0.09f)
        val decays = floatArrayOf(2.2f, 5.0f, 8.0f, 12.0f)
        for (i in 0 until n) {
            val t = i.toFloat() / sr
            var s = 0f
            for (h in amps.indices) {
                val hp = 1.0 + 0.0012 * (h + 1)
                s += (amps[h] * kotlin.math.exp(-decays[h] * t) *
                        sin(2 * PI * freq * hp * (h + 1) * i / sr)).toFloat()
            }
            out[i] = s * 0.5f + (rnd.nextFloat() * 2 - 1) * 0.006f + 0.008f
        }
        return out
    }

    private data class Stats(val frames: Int, val avgErrCents: Double, val octaveErrors: Int, val pipeFreq: Float)

    private fun measure(freq: Float, sig: FloatArray): Stats {
        val sr = 44100
        val ds = FloatArray(sig.size / 2) { (sig[2 * it] + sig[2 * it + 1]) / 2f }
        val mpm = MpmPitchDetector(sampleRate = 22050, bufferSize = 2048)
        var sumErr = 0.0
        var count = 0
        var octErr = 0
        var i = 0
        while (i + 2048 <= ds.size) {
            val r = mpm.detect(ds.copyOfRange(i, i + 2048))
            if (r != null) {
                val err = 1200 * kotlin.math.log2((r.frequencyHz / freq).toDouble())
                sumErr += kotlin.math.abs(err)
                count++
                val ratio = r.frequencyHz / freq
                if (kotlin.math.abs(ratio - 2f) < 0.06f || kotlin.math.abs(ratio - 0.5f) < 0.06f) octErr++
            }
            i += 1024
        }
        val pipe = PitchPipeline()
        var lastF = 0f
        var j = 0
        while (j < sig.size) {
            pipe.push(sig.copyOfRange(j, minOf(j + 4096, sig.size)))?.let { lastF = it.frequencyHz }
            j += 4096
        }
        return Stats(count, if (count > 0) sumErr / count else Double.NaN, octErr, lastF)
    }

    @Test
    fun pureSine_isExact_noOctaveErrors() {
        for (f in floatArrayOf(61.74f, 82.41f, 110f, 246.94f, 440f)) {
            val s = measure(f, sine(f, 44100, 1.5))
            assertTrue("sine $f: frames=${s.frames}", s.frames > 5)
            assertTrue("sine $f: avgErr=${s.avgErrCents}", s.avgErrCents < 0.5)
            assertEquals("sine $f: octave errors", 0, s.octaveErrors)
        }
    }

    @Test
    fun realisticPluck_allGuitarStrings() {
        // все струны Drop B + стандарт: B1 F#2 B2 E3 G#3 C#4, E2 A2 D3 G3 B3 E4
        val freqs = floatArrayOf(
            61.74f, 92.50f, 123.47f, 164.81f, 207.65f, 277.18f,
            82.41f, 110f, 146.83f, 196f, 246.94f, 329.63f, 440f
        )
        for (f in freqs) {
            val s = measure(f, pluck(f, 44100, 1.5))
            assertTrue("pluck $f: frames=${s.frames}", s.frames > 5)
            assertTrue("pluck $f: avgErr=${s.avgErrCents}", s.avgErrCents < 3.0)
            assertEquals("pluck $f: octave errors", 0, s.octaveErrors)
            val pipeErr = kotlin.math.abs(1200 * kotlin.math.log2((s.pipeFreq / f).toDouble()))
            assertTrue("pluck $f: pipeline err=$pipeErr", pipeErr < 3.0)
        }
    }

    @Test
    fun detunedString_tracksCents() {
        // A2 на -18 центов — пайплайн должен увидеть ~-18, а не соседнюю ноту
        val f = (110f * Math.pow(2.0, -18.0 / 1200)).toFloat()
        val s = measure(f, pluck(f, 44100, 1.5))
        assertTrue("frames=${s.frames}", s.frames > 5)
        assertEquals(0, s.octaveErrors)
        val pipeErr = 1200 * kotlin.math.log2((s.pipeFreq / 110f).toDouble())
        assertTrue("expected ≈-18¢, got $pipeErr", kotlin.math.abs(pipeErr + 18.0) < 3.0)
    }

    @Test
    fun silence_givesNoReading() {
        val pipe = PitchPipeline()
        val zeros = FloatArray(44100) // 1с тишины
        var j = 0
        var got: PitchPipeline.Reading? = null
        while (j < zeros.size) {
            pipe.push(zeros.copyOfRange(j, minOf(j + 4096, zeros.size)))?.let { got = it }
            j += 4096
        }
        assertEquals(null, got)
    }
}
