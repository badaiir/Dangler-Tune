package com.dangler.tune.tuner

/**
 * Залипание на струнах строя (гитарный режим — основной).
 *
 * Хроматика скачет между соседними нотами (E2/D#2 на границе) — на ней ничего
 * не настроишь. Локатор один раз уверенно берёт струну и ДЕРЖИТ её:
 * - захват: нота в пределах [lockSemitones] от струны;
 * - удержание: своя струна держится до [holdSemitones], чужая перехватывает
 *   только если явно ближе [switchSemitones] — гистерезис против дребезга;
 * - мусор между струнами (далеко от всех) — null, экран не прыгает.
 */
class StringLocator(
    private val lockSemitones: Float = 1.5f,
    private val holdSemitones: Float = 2.5f,
    private val switchSemitones: Float = 1.0f,
) {

    data class Tracked(val string: Tuning.StringNote, val cents: Float)

    private var locked: Tuning.StringNote? = null

    fun reset() {
        locked = null
    }

    /** Струна + центы относительно неё, или null (мусор — держать прошлое). */
    fun update(freqHz: Float, tuning: Tuning): Tracked? {
        val nearest = tuning.nearestString(freqHz)
        val dNearest = NoteMath.semitonesBetween(freqHz, nearest.frequencyHz)
        val current = locked

        if (current == null) {
            if (dNearest > lockSemitones) return null
            locked = nearest
            return Tracked(nearest, NoteMath.centsRelativeTo(freqHz, nearest.frequencyHz))
        }

        val dLocked = NoteMath.semitonesBetween(freqHz, current.frequencyHz)
        val target = when {
            dLocked <= holdSemitones -> current
            dNearest <= switchSemitones -> nearest.also { locked = it }
            else -> return null // между струнами — стоим, не прыгаем
        }
        return Tracked(target, NoteMath.centsRelativeTo(freqHz, target.frequencyHz))
    }
}
