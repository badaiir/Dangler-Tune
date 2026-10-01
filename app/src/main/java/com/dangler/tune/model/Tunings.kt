package com.dangler.tune.model

/** Одна струна строя */
data class GuitarString(
    val label: String,   // "1", "2"... или "B1"
    val noteName: String,// "B"
    val octave: Int,
    val frequencyHz: Float,
)

data class Tuning(
    val id: String,
    val name: String,        // "Drop B"
    val subtitle: String,    // "B1 – F#2 – B2 – E3 – G#3 – C#4"
    val strings: List<StringNote>,
) {
    data class StringNote(
        val stringNumber: Int, // 6..1 (6 = самая толстая)
        val name: String,      // "B1"
        val frequencyHz: Float,
    )
}

private fun s(num: Int, name: String, freq: Float) = Tuning.StringNote(num, name, freq)

object Tunings {
    // База группы — Drop B: B1 F#2 B2 E3 G#3 C#4
    val DROP_B = Tuning(
        "drop_b", "Drop B ★",
        "B1 – F#2 – B2 – E3 – G#3 – C#4 — строй Dangler",
        listOf(
            s(6, "B1", 61.74f),
            s(5, "F#2", 92.50f),
            s(4, "B2", 123.47f),
            s(3, "E3", 164.81f),
            s(2, "G#3", 207.65f),
            s(1, "C#4", 277.18f),
        )
    )
    val STANDARD_E = Tuning(
        "standard_e", "Standard E",
        "E2 – A2 – D3 – G3 – B3 – E4",
        listOf(
            s(6, "E2", 82.41f),
            s(5, "A2", 110.00f),
            s(4, "D3", 146.83f),
            s(3, "G3", 196.00f),
            s(2, "B3", 246.94f),
            s(1, "E4", 329.63f),
        )
    )
    val DROP_D = Tuning(
        "drop_d", "Drop D",
        "D2 – A2 – D3 – G3 – B3 – E4",
        listOf(
            s(6, "D2", 73.42f),
            s(5, "A2", 110.00f),
            s(4, "D3", 146.83f),
            s(3, "G3", 196.00f),
            s(2, "B3", 246.94f),
            s(1, "E4", 329.63f),
        )
    )
    val D_STANDARD = Tuning(
        "d_standard", "D Standard",
        "D2 – G2 – C3 – F3 – A3 – D4",
        listOf(
            s(6, "D2", 73.42f),
            s(5, "G2", 98.00f),
            s(4, "C3", 130.81f),
            s(3, "F3", 174.61f),
            s(2, "A3", 220.00f),
            s(1, "D4", 293.66f),
        )
    )
    val HALF_STEP = Tuning(
        "eb", "E♭ Half-Step Down",
        "E♭2 – A♭2 – D♭3 – G♭3 – B♭3 – E♭4",
        listOf(
            s(6, "D#2", 77.78f),
            s(5, "G#2", 103.83f),
            s(4, "C#3", 138.59f),
            s(3, "F#3", 185.00f),
            s(2, "A#3", 233.08f),
            s(1, "D#4", 311.13f),
        )
    )
    val DROP_C = Tuning(
        "drop_c", "Drop C",
        "C2 – G2 – C3 – F3 – A3 – D4",
        listOf(
            s(6, "C2", 65.41f),
            s(5, "G2", 98.00f),
            s(4, "C3", 130.81f),
            s(3, "F3", 174.61f),
            s(2, "A3", 220.00f),
            s(1, "D4", 293.66f),
        )
    )

    val ALL = listOf(DROP_B, STANDARD_E, DROP_D, D_STANDARD, HALF_STEP, DROP_C)
    val DEFAULT = DROP_B
}
