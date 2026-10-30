package com.dangler.tune.tuner

import com.dangler.tune.dsp.PitchReading
import com.dangler.tune.dsp.PitchStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Музыкальные решения тюнера, без звука и без Android. */
class TunerEngineTest {

    private class FakePitchStream : PitchStream {
        var reading: PitchReading? = null
        var resets: Int = 0

        override fun push(chunk: FloatArray): PitchReading? = reading
        override fun reset() {
            resets++
        }
    }

    private val stream = FakePitchStream()
    private val engine = TunerEngine(pitchStream = stream)

    private fun feed(freq: Float, clarity: Float = 0.9f): TunerEngine.Reading? {
        stream.reading = PitchReading(freq, clarity)
        return engine.push(FloatArray(4096))
    }

    private fun feedSilence(): TunerEngine.Reading? {
        stream.reading = null
        return engine.push(FloatArray(4096))
    }

    private fun detuned(freq: Float, cents: Float) = freq * 2f.pow(cents / 1200f)

    @Test
    fun guitarMode_locksStringAndShowsCents() {
        val r = feed(164.81f)!!
        assertEquals("E3", r.noteName)
        assertEquals(0f, r.cents, 0.5f)
        assertTrue(r.inTune)
        assertTrue(r.noteInTuning)
        assertEquals(3, r.activeString!!.stringNumber)
    }

    @Test
    fun guitarMode_outOfTune_isNotInTune() {
        val r = feed(detuned(164.81f, 20f))!!
        assertEquals("E3", r.noteName)
        assertEquals(20f, r.cents, 1f)
        assertFalse(r.inTune)
    }

    @Test
    fun guitarMode_toleranceIsRespected() {
        engine.setTolerance(5f)
        assertTrue(feed(detuned(164.81f, 4f))!!.inTune)
        assertFalse(feed(detuned(164.81f, 7f))!!.inTune)
    }

    @Test
    fun guitarMode_lowClarity_isNotInTune() {
        assertFalse(feed(164.81f, clarity = 0.3f)!!.inTune)
    }

    @Test
    fun guitarMode_garbageBetweenStrings_yieldsNoReading() {
        feed(164.81f) // lock E3
        // 230 Гц: 5.75 полутона от E3 и 1.77 от G#3 — мусор.
        // Раньше экран навсегда залипал на E3 с hasSignal=true; теперь сигнал гаснет.
        assertNull(feed(230f))
    }

    @Test
    fun guitarMode_mainsHum_isNotAString() {
        // 50 Гц — сетевой фон, а не струна Drop B (B1 = 61.74 Гц).
        assertNull(feed(50f))
    }

    @Test
    fun guitarMode_silence_yieldsNoReading() {
        feed(164.81f)
        assertNull(feedSilence())
    }

    @Test
    fun chromaticMode_foreignNote_isAmberAndChromaticCents() {
        engine.setStringLock(false)
        val r = feed(261.63f)!! // C4 — нет в Drop B
        assertEquals("C4", r.noteName)
        assertFalse(r.noteInTuning)
        assertEquals(0f, r.cents, 0.5f)
        assertTrue(r.inTune)
    }

    @Test
    fun chromaticMode_stringNote_isBlood() {
        engine.setStringLock(false)
        val r = feed(61.74f)!! // B1
        assertEquals("B1", r.noteName)
        assertTrue(r.noteInTuning)
    }

    @Test
    fun selectTuning_resetsLocatorAndUsesNewStrings() {
        feed(164.81f) // E3 в Drop B
        engine.selectTuning(Tunings.STANDARD_E)
        // В Standard E 164.81 Гц — на 2 полутона выше D3, вне захвата.
        assertNull(feed(164.81f))
    }

    @Test
    fun setStringLock_resetsHysteresis() {
        feed(164.81f)
        engine.setStringLock(false)
        engine.setStringLock(true)
        // гистерезис сброшен: 230 Гц не в пределах захвата ни одной струны
        assertNull(feed(230f))
    }

    @Test
    fun reset_clearsPipelineAndLocator() {
        feed(164.81f)
        val before = stream.resets
        engine.reset()
        assertEquals(before + 1, stream.resets)
        assertNull(feed(230f))
    }
}
