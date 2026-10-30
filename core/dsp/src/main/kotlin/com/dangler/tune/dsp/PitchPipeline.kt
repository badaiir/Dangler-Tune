package com.dangler.tune.dsp

/**
 * Тонкий оркестратор: поток сэмплов → кадры → подготовка → детект → стабилизация.
 *
 * Сам ничего не считает. Каждый шаг живёт отдельно и тестируется отдельно,
 * поэтому детектор можно заменить (FFT, нейросеть), не трогая кадрирование
 * и трекер, а трекер — не трогая детектор.
 */
class PitchPipeline(
    inputSampleRate: Int = INPUT_SAMPLE_RATE,
    downsample: Int = 2,
    windowSize: Int = 2048,   // в даунсемплированных сэмплах
    hopSize: Int = 1024,      // в даунсемплированных сэмплах
    rmsGate: Float = 0.006f,
    clarityGate: Float = 0.55f,
    attackCents: Float = 20f,
    releaseAlpha: Float = 0.45f,
    medianSize: Int = 3,
    private val detector: PitchDetector = FftMpmDetector(
        sampleRate = inputSampleRate / downsample,
        bufferSize = windowSize,
    ),
    private val conditioner: SignalConditioner = SignalConditioner(
        rmsGate = rmsGate,
        lowPassA = SignalConditioner.lowPassAlpha(inputSampleRate / downsample),
    ),
) : PitchStream {

    private val decimator = Decimator(downsample)
    private val frames = FrameBuffer(windowSize, hopSize)
    private val tracker = PitchTracker(
        clarityGate = clarityGate,
        attackCents = attackCents,
        releaseAlpha = releaseAlpha,
        medianSize = medianSize,
    )
    private val window = FloatArray(windowSize)

    /** Скормить чанк [-1, 1] с микрофона. Возвращает свежее чтение или null. */
    override fun push(chunk: FloatArray): PitchReading? {
        frames.push(decimator.push(chunk))

        var latest: PitchReading? = null
        while (frames.next(window)) {
            latest = processWindow() ?: latest
        }
        return latest
    }

    private fun processWindow(): PitchReading? {
        val conditioned = conditioner.process(window)
        if (conditioned == null) {
            tracker.onDropout()
            return null
        }
        val candidate = detector.detect(window)
        if (candidate == null) {
            tracker.onDropout()
            return null
        }
        val smoothed = tracker.update(candidate, conditioned.rmsFiltered) ?: return null
        return PitchReading(smoothed.frequencyHz, smoothed.clarity)
    }

    override fun reset() {
        decimator.reset()
        frames.clear()
        tracker.reset()
    }

    companion object {
        const val INPUT_SAMPLE_RATE = 44100
    }
}
