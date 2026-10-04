package com.dangler.tune.dsp

import com.dangler.tune.model.Tuning
import kotlin.math.abs
import kotlin.math.log2

/**
 * Залипание на струнах строя (гитарный режим — основной).
 * Хроматика скачет между соседними нотами (E2/D#2 на границе) — на ней ничего не настроишь.
 * Трекер один раз уверенно берёт струну и ДЕРЖИТ её:
 * - захват: нота в пределах [lockSemitones] от струны;
 * - удержание: своя струна держится до [holdSemitones], чужая перехватывает
 *   только если явно ближе [switchSemitones] — гистерезис против дребезга;
 * - мусор между струнами (далеко от всех) — игнор, экран не прыгает.
 */
class StringTracker(
    private val lockSemitones: Float = 1.5f,
    private val holdSemitones: Float = 2.5f,
    private val switchSemitones: Float = 1.0f,
) {
    data class Tracked(val string: Tuning.StringNote, val cents: Float)

    private var locked: Tuning.StringNote? = null

    fun reset() {
        locked = null
    }

    /** Возвращает струну + центы относительно неё, или null (мусор — держать прошлое). */
    fun update(freqHz: Float, tuning: Tuning): Tracked? {
        val nearest = NoteUtils.nearestString(freqHz, tuning) ?: return null
        val dNearest = semitones(freqHz, nearest.frequencyHz)
        val cur = locked
        if (cur == null) {
            if (dNearest > lockSemitones) return null
            locked = nearest
            return Tracked(nearest, NoteUtils.centsRelativeTo(freqHz, nearest.frequencyHz))
        }
        val dLocked = semitones(freqHz, cur.frequencyHz)
        val target = when {
            dLocked <= holdSemitones -> cur
            dNearest <= switchSemitones -> nearest.also { locked = it }
            else -> return null // между струнами — стоим, не прыгаем
        }
        return Tracked(target, NoteUtils.centsRelativeTo(freqHz, target.frequencyHz))
    }

    private fun semitones(freqHz: Float, targetHz: Float): Float =
        abs(12 * log2(freqHz / targetHz))
}
