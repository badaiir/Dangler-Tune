package com.dangler.tune.dsp

/**
 * Даунсемплинг усреднением групп из [factor] сэмплов.
 * Состояние (непарный остаток) переносится между чанками — дыр на границе нет.
 *
 * Это не «настоящий» FIR-anti-alias, а мягкий 2-tap фильтр: для этапа
 * «поведение не меняем» сохраняем ровно старую математику.
 */
class Decimator(private val factor: Int = 2) {

    init {
        require(factor > 0) { "factor must be positive" }
    }

    private var pending = 0
    private var sum = 0f

    fun push(chunk: FloatArray): FloatArray {
        if (factor == 1) return chunk
        if (chunk.isEmpty()) return FloatArray(0)

        val out = FloatArray((chunk.size + factor - 1) / factor)
        var n = 0
        for (v in chunk) {
            sum += v
            pending++
            if (pending == factor) {
                out[n++] = sum / factor
                sum = 0f
                pending = 0
            }
        }
        return if (n == out.size) out else out.copyOf(n)
    }

    fun reset() {
        pending = 0
        sum = 0f
    }
}
