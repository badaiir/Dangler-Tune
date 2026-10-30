package com.dangler.tune.dsp

/**
 * Копит поток сэмплов и нарезает его на перекрывающиеся окна.
 * Одна ответственность: кадрирование. Что делать с окном — не его дело.
 */
class FrameBuffer(
    private val windowSize: Int,
    private val hopSize: Int,
) {

    init {
        require(windowSize > 0) { "windowSize must be positive" }
        require(hopSize in 1..windowSize) { "hopSize must be in 1..windowSize" }
    }

    private val queue = ArrayDeque<Float>()

    fun push(samples: FloatArray) {
        for (s in samples) queue.addLast(s)
    }

    /** true, если накопилось полное окно; копирует его в [out]. */
    fun next(out: FloatArray): Boolean {
        require(out.size >= windowSize) { "out is smaller than windowSize" }
        if (queue.size < windowSize) return false
        for (k in 0 until windowSize) out[k] = queue[k]
        repeat(hopSize) { queue.removeFirst() }
        return true
    }

    fun clear() = queue.clear()
}
