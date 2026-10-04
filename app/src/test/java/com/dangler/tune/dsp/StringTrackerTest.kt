package com.dangler.tune.dsp

import com.dangler.tune.model.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Трекер струн: взял струну — держит, мусор между струнами — игнорит. */
class StringTrackerTest {

    private val dropB = Tunings.DROP_B // B1 F#2 B2 E3 G#3 C#4

    @Test
    fun locksNearestString() {
        val t = StringTracker()
        val r = t.update(164.81f, dropB)!!
        assertEquals("E3", r.string.name)
        assertEquals(3, r.string.stringNumber) // 3-я струна
    }

    @Test
    fun holdsStringThroughBoundaryWobble() {
        // E3 (−51¢ ≈ 160Гц) и D#2 (+49¢ ≈ 157Гц) — граница, где хроматика скачет.
        // Трекер обязан держать E3 во всех трёх точках.
        val t = StringTracker()
        assertEquals("E3", t.update(164.81f, dropB)!!.string.name)
        val flat = t.update(160.0f, dropB)!!
        assertEquals("E3", flat.string.name)
        val sharpSide = t.update(157.0f, dropB)!!
        assertEquals("E3", sharpSide.string.name)
    }

    @Test
    fun garbageBetweenStrings_isIgnored() {
        val t = StringTracker()
        t.update(164.81f, dropB) // lock E3
        // 230Гц: до E3 — 5.75 полутона, до G#3 — 1.77: ни удержание, ни перехват
        assertNull(t.update(230f, dropB))
    }

    @Test
    fun clearMoveToAnotherString_switches() {
        val t = StringTracker()
        t.update(164.81f, dropB) // lock E3
        // явная G#3 — перехват
        val r = t.update(207.65f, dropB)!!
        assertEquals("G#3", r.string.name)
    }

    @Test
    fun farFromAllStrings_noLock() {
        val t = StringTracker()
        // 440Гц далеко от всех струн Drop B
        assertNull(t.update(440f, dropB))
    }

    @Test
    fun reset_releasesLock() {
        val t = StringTracker()
        t.update(164.81f, dropB)
        t.reset()
        assertNull(t.update(230f, dropB))
    }
}
