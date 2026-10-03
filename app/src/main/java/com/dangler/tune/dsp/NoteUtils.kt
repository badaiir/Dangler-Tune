package com.dangler.tune.dsp

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Ноты, центы, MIDI-утилиты */
object NoteUtils {
    val NOTE_NAMES_SHARP = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    data class NoteInfo(
        val midi: Int,
        val name: String,      // "B"
        val octave: Int,       // 1
        val display: String,   // "B1"
        val targetFreq: Float, // идеальная частота ноты
        val cents: Float,      // отклонение -50..+50
    )

    fun freqToNote(freqHz: Float): NoteInfo {
        val midiFloat = 69 + 12 * log2(freqHz / 440.0)
        val midi = midiFloat.roundToInt()
        val target = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
        val cents = (1200 * log2(freqHz / target)).toFloat()
        val name = NOTE_NAMES_SHARP[(midi % 12 + 12) % 12]
        val octave = midi / 12 - 1
        return NoteInfo(midi, name, octave, "$name$octave", target, cents)
    }

    /** Ближайшая струна строя к услышанной частоте (в пределах +-6 полутонов) */
    fun nearestString(freqHz: Float, tuning: com.dangler.tune.model.Tuning): com.dangler.tune.model.Tuning.StringNote? {
        return tuning.strings.minByOrNull {
            abs(12 * log2(freqHz / it.frequencyHz))
        }
    }

    /** Центы относительно конкретной струны (для режима "по струнам") */
    fun centsRelativeTo(freqHz: Float, targetHz: Float): Float =
        (1200 * log2(freqHz / targetHz)).toFloat()
}
