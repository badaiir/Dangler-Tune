package com.dangler.tune.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Golden-тест рефакторинга: новый пайплайн обязан выдавать РОВНО те же числа,
 * что и старый монолит. Если этот тест падает — мы случайно поменяли DSP.
 */
class LegacyEquivalenceTest {

    private fun sine(freq: Float, sr: Int = 44100, secs: Double = 1.0): FloatArray {
        val n = (sr * secs).toInt()
        return FloatArray(n) { sin(2 * PI * freq * it / sr).toFloat() * 0.5f }
    }

    private fun pluck(freq: Float, sr: Int = 44100, secs: Double = 1.2): FloatArray {
        val n = (sr * secs).toInt()
        val rnd = Random(7)
        val amps = floatArrayOf(1f, 0.4f, 0.2f, 0.09f)
        val decays = floatArrayOf(2.2f, 5.0f, 8.0f, 12.0f)
        return FloatArray(n) { i ->
            val t = i.toFloat() / sr
            var s = 0f
            for (h in amps.indices) {
                s += (amps[h] * kotlin.math.exp(-decays[h] * t) *
                        sin(2 * PI * freq * (1.0 + 0.0012 * (h + 1)) * (h + 1) * i / sr)).toFloat()
            }
            s * 0.5f + (rnd.nextFloat() * 2 - 1f) * 0.006f + 0.008f
        }
    }

    private fun noise(secs: Double = 0.5f.toDouble()): FloatArray {
        val rnd = Random(3)
        return FloatArray((44100 * secs).toInt()) { (rnd.nextFloat() * 2 - 1f) * 0.02f }
    }

    /** Golden-тест про рефакторинг, а не про FFT: фиксируем классический детектор. */
    private fun classicPipeline() = PitchPipeline(
        detector = MpmPitchDetector(sampleRate = 22050, bufferSize = 2048),
        // Ровно тот же коэффициент, что в LegacyPitchPipeline: golden-тест бит в бит.
        conditioner = SignalConditioner(lowPassA = SignalConditioner.LOWPASS_A),
    )

    private fun assertSameStream(legacy: LegacyPitchPipeline, modern: PitchPipeline, signal: FloatArray, chunk: Int): Int {
        var i = 0
        var compared = 0
        while (i < signal.size) {
            val end = minOf(i + chunk, signal.size)
            val piece = signal.copyOfRange(i, end)
            val a = legacy.push(piece)
            val b = modern.push(piece)
            if (a == null) {
                assertNull("chunk=$chunk at $i: legacy=null, modern=$b", b)
            } else {
                assertNotNull("chunk=$chunk at $i: legacy=$a, modern=null", b)
                assertEquals("frequency at $i", a.frequencyHz, b!!.frequencyHz, 0f)
                assertEquals("clarity at $i", a.clarity, b.clarity, 0f)
                compared++
            }
            i = end
        }
        return compared
    }

    @Test
    fun matchesLegacy_onSine() {
        assertTrue(assertSameStream(LegacyPitchPipeline(), classicPipeline(), sine(61.74f), 4096) > 0)
    }

    @Test
    fun matchesLegacy_onPluck_oddChunks() {
        assertTrue(assertSameStream(LegacyPitchPipeline(), classicPipeline(), pluck(110f), 1003) > 0)
    }

    @Test
    fun matchesLegacy_onDetunedString() {
        val f = (110f * Math.pow(2.0, -18.0 / 1200)).toFloat()
        assertTrue(assertSameStream(LegacyPitchPipeline(), classicPipeline(), pluck(f), 4096) > 0)
    }

    @Test
    fun matchesLegacy_onNoiseAndSilence() {
        // Оба обязаны одинаково молчать: сравнение внутри, читать нечего.
        assertSameStream(LegacyPitchPipeline(), classicPipeline(), noise(), 777)
        assertSameStream(LegacyPitchPipeline(), classicPipeline(), FloatArray(44100), 4096)
    }

    @Test
    fun matchesLegacy_onSingleSampleChunks() {
        // Самый злой случай для склейки carry через границу чанка.
        assertTrue(
            assertSameStream(
                LegacyPitchPipeline(),
                classicPipeline(),
                sine(164.81f, secs = 0.12),
                1,
            ) > 0
        )
    }
}
