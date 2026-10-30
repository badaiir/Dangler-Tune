package com.dangler.tune.tuner

import com.dangler.tune.dsp.PitchPipeline
import com.dangler.tune.dsp.PitchReading
import com.dangler.tune.dsp.PitchStream
import kotlin.math.abs

/**
 * Музыкальная голова тюнера: сигнал → «какая это нота/струна и насколько мимо».
 *
 * Режимы:
 * - гитарный (по умолчанию): только струны строя, залипание с гистерезисом;
 * - хроматика: любая нота 12-TET, чужая помечается [Reading.noteInTuning] = false.
 *
 * Ни Android, ни Compose, ни сглаживания — всё это снаружи.
 */
class TunerEngine(
    private val pitchStream: PitchStream = PitchPipeline(),
    private val locator: StringLocator = StringLocator(),
) {

    data class Reading(
        val frequencyHz: Float,
        val clarity: Float,
        val noteName: String,
        /** Хроматические центы: для гитарного режима — относительно струны. */
        val cents: Float,
        val targetHz: Float,
        val activeString: Tuning.StringNote?,
        /** Нота входит в текущий строй (в хроматике определяет кровь/янтарь). */
        val noteInTuning: Boolean,
        val inTune: Boolean,
    )

    @Volatile
    var tuning: Tuning = Tunings.DEFAULT
        private set

    @Volatile
    var toleranceCents: Float = 5f
        private set

    @Volatile
    var stringLock: Boolean = true
        private set

    @Synchronized
    fun selectTuning(tuning: Tuning) {
        resetInternal()
        this.tuning = tuning
    }

    fun setTolerance(cents: Float) {
        toleranceCents = cents
    }

    @Synchronized
    fun setStringLock(enabled: Boolean) {
        if (enabled == stringLock) return
        locator.reset()
        stringLock = enabled
    }

    @Synchronized
    fun reset() = resetInternal()

    private fun resetInternal() {
        pitchStream.reset()
        locator.reset()
    }

    /**
     * Скормить чанк [-1, 1]. null — кадра нет, тишина или мусор между струнами.
     * Вызывается с аудиопотока; конфигурация — с UI-потока (поэтому synchronized).
     */
    @Synchronized
    fun push(chunk: FloatArray): Reading? {
        val pitch = pitchStream.push(chunk) ?: return null
        return if (stringLock) guitarReading(pitch) else chromaticReading(pitch)
    }

    private fun guitarReading(pitch: PitchReading): Reading? {
        val tracked = locator.update(pitch.frequencyHz, tuning) ?: return null
        val cents = tracked.cents.coerceIn(-75f, 75f)
        return Reading(
            frequencyHz = pitch.frequencyHz,
            clarity = pitch.clarity,
            noteName = tracked.string.name,
            cents = cents,
            targetHz = tracked.string.frequencyHz,
            activeString = tracked.string,
            noteInTuning = true, // всегда своя — всегда кровь
            inTune = abs(cents) <= toleranceCents && pitch.clarity > CLARITY_TO_TUNE,
        )
    }

    private fun chromaticReading(pitch: PitchReading): Reading {
        val note = NoteMath.freqToNote(pitch.frequencyHz)
        val cents = note.cents.coerceIn(-50f, 50f)
        return Reading(
            frequencyHz = pitch.frequencyHz,
            clarity = pitch.clarity,
            noteName = note.display,
            cents = cents,
            targetHz = note.targetFreq,
            activeString = tuning.nearestString(pitch.frequencyHz),
            noteInTuning = tuning.strings.any { it.name == note.display },
            inTune = abs(cents) <= toleranceCents && pitch.clarity > CLARITY_TO_TUNE,
        )
    }

    companion object {
        /** Частота входа. Одна константа и для AudioRecorder, и для пайплайна. */
        const val INPUT_SAMPLE_RATE = PitchPipeline.INPUT_SAMPLE_RATE

        /** Ниже этой периодичности «в строе» не показываем — это шум. */
        const val CLARITY_TO_TUNE = 0.55f
    }
}
