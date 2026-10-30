package com.dangler.tune.dsp

/** Результат одного шага пайплайна: частота и периодичность. */
data class PitchReading(val frequencyHz: Float, val clarity: Float)

/**
 * Источник оценок высоты. Абстракция нужна двум вещам:
 * тестам (подсунуть заранее известные частоты) и будущим детекторам
 * (FFT/нейросеть) — тюнеру всё равно, откуда пришла частота.
 */
interface PitchStream {
    fun push(chunk: FloatArray): PitchReading?
    fun reset()
}
