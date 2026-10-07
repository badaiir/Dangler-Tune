package com.dangler.tune.dsp

/**
 * Полный pitch-пайплайн гитарного тюнера (чистый Kotlin, без Android-зависимостей —
 * поэтому покрыт юнит-тестами, см. PitchPipelineTest).
 *
 * Цепочка (выводы из TarsosDSP + практики тюнеров):
 * 1. Аккумулятор: чанки микрофона любых размеров складываются в очередь —
 *    никаких пропусков кадров и дыр в анализе.
 * 2. Даунсемплинг ×2 (44100 → 22050, среднее пар = заодно мягкий anti-alias low-pass):
 *    окно 2048 покрывает 93мс — для B1 (61.7Гц, период 16мс) влезает ~5.7 периодов,
 *    а MPM стоит те же 2М операций, что и 46мс на полной частоте.
 * 3. Окно 2048 / hop 1024 (46мс) — баланс задержки и стабильности.
 * 4. Удаление DC (вычет среднего): смещение микрофона иначе косит NSDF.
 * 5. RMS-gate против тишины/шума.
 * 6. MPM + clarity-gate.
 * 7. Трекер: медиана-3 против выбросов, быстрая атака (скачок >20¢ — сразу),
 *    живой релиз (EMA 0.45) — нота переключается мгновенно, игла не дрожит.
 */
class PitchPipeline(
    private val inputSampleRate: Int = 44100,
    private val downsample: Int = 2,
    private val windowSize: Int = 2048,   // в даунсемплированных сэмплах
    private val hopSize: Int = 1024,      // в даунсемплированных сэмплах
    private val rmsGate: Float = 0.006f,
    private val clarityGate: Float = 0.55f,
    private val attackCents: Float = 20f,
    private val releaseAlpha: Float = 0.45f,
    private val medianSize: Int = 3,
) {
    data class Reading(val frequencyHz: Float, val clarity: Float)

    private val effectiveRate = inputSampleRate / downsample
    private val mpm = MpmPitchDetector(sampleRate = effectiveRate, bufferSize = windowSize)
    private val ring = ArrayDeque<Float>()
    private var carry: Float? = null // непарный остаток даунсемплинга между чанками
    private val window = FloatArray(windowSize)
    private val medians = ArrayDeque<Float>()
    private var locked = 0f
    /**
     * Пол sustain: ниже этого уровня нота уже догорает в шуме — оценку не гоним,
     * держим последнее уверенное значение (иначе игла ползёт по шумовому хвосту).
     */
    private val holdFloor = 0.025f
    /** Если тихая рамка дальше этого от lock — это уже новая нота, перехватываем. */
    private val holdWidthCents = 15f

    companion object {
        /** Однополюсный LPF ~1000Гц @22050: a = 1 - exp(-2π·1000/22050). */
        const val LOWPASS_A = 0.248f
    }

    /** Скормить чанк [-1, 1] с микрофона. Возвращает свежее чтение или null. */
    fun push(chunk: FloatArray): Reading? {
        // даунсемплинг средним пар, со склейкой через границу чанков
        var i = 0
        val c = carry
        if (c != null && chunk.isNotEmpty()) {
            ring.addLast((c + chunk[0]) / 2f)
            carry = null
            i = 1
        }
        while (i + 1 < chunk.size) {
            ring.addLast((chunk[i] + chunk[i + 1]) / 2f)
            i += 2
        }
        if (i < chunk.size) carry = chunk[i]

        var latest: Reading? = null
        while (ring.size >= windowSize) {
            for (k in 0 until windowSize) window[k] = ring[k]
            repeat(hopSize) { ring.removeFirst() }
            processWindow()?.let { latest = it }
        }
        return latest
    }

    private fun processWindow(): Reading? {
        // DC removal + RMS одним проходом
        var mean = 0.0
        for (v in window) mean += v
        mean /= windowSize
        var energy = 0.0
        for (k in 0 until windowSize) {
            val centered = window[k] - mean
            window[k] = centered.toFloat()
            energy += centered * centered
        }
        // однополюсный low-pass ~1000Гц: душит верхние гармоники/хэш, фундамент цел.
        // MPM от этого только стабильнее (периодичность фильтр не трогает).
        // Гейты — по СЫРОМУ уровню (до фильтра), детект — по фильтрованному.
        val rms = kotlin.math.sqrt(energy / windowSize)
        if (rms < rmsGate) {
            medians.clear()
            return null
        }
        var y = window[0]
        for (k in window.indices) {
            y += LOWPASS_A * (window[k] - y)
            window[k] = y
        }
        var energyF = 0.0
        for (k in 0 until windowSize) energyF += window[k] * window[k]
        val rmsF = kotlin.math.sqrt(energyF / windowSize)

        val res = mpm.detect(window) ?: run {
            medians.clear()
            return null
        }
        if (res.clarity < clarityGate) {
            medians.clear()
            return null
        }

        // тихий хвост: та же нота догорает — стоим, новая — перехватываем
        // (уровень хвоста меряем по фильтрованному окну — честная энергия фундамента)
        if (rmsF < holdFloor && locked > 0f) {
            val drift = 1200 * kotlin.math.log2(res.frequencyHz / locked)
            if (kotlin.math.abs(drift) < holdWidthCents) {
                return Reading(locked, res.clarity)
            }
            medians.clear() // чужая тихая нота — забываем старое, захватываем заново
        }

        // медиана против выбросов
        medians.addLast(res.frequencyHz)
        if (medians.size > medianSize) medians.removeFirst()
        val sorted = medians.sorted()
        val med = sorted[sorted.size / 2]

        // быстрая атака / медленный релиз
        if (locked <= 0f) {
            locked = med
        } else {
            val diffCents = 1200 * kotlin.math.log2(med / locked)
            locked = if (kotlin.math.abs(diffCents) > attackCents) med
            else locked + (med - locked) * releaseAlpha
        }
        return Reading(locked, res.clarity)
    }

    fun reset() {
        ring.clear()
        medians.clear()
        locked = 0f
        carry = null
    }
}
