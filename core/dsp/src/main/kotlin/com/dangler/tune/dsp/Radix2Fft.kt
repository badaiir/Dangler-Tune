package com.dangler.tune.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Итеративное radix-2 FFT (Cooley–Tukey), in-place, Double.
 *
 * Зачем: NSDF — это автокорреляция. Прямая автокорреляция стоит O(N·L),
 * FFT — O(N log N). На слабом телефоне это разница между «окно 2048 ценой
 * 2 млн умножений» и «окно 4096 ценой ~50 тыс. бабочек». Освободившийся
 * бюджет уходит на длинные окна для басовых струн и на несколько гипотез.
 */
internal class Radix2Fft(val size: Int) {

    init {
        require(size >= 2 && size and (size - 1) == 0) { "size must be a power of two: $size" }
    }

    private val cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2.0 * PI * it / size) }
    private val reverse = IntArray(size)
    private val bits = Integer.numberOfTrailingZeros(size)

    init {
        for (i in 0 until size) reverse[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = false)

    fun inverse(re: DoubleArray, im: DoubleArray) {
        transform(re, im, inverse = true)
        val scale = 1.0 / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        require(re.size >= size && im.size >= size) { "buffers are smaller than $size" }

        for (i in 0 until size) {
            val j = reverse[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (j in 0 until half) {
                    val c = cosTable[k]
                    val s = if (inverse) sinTable[k] else -sinTable[k]
                    val i0 = start + j
                    val i1 = i0 + half
                    val tr = re[i1] * c - im[i1] * s
                    val ti = re[i1] * s + im[i1] * c
                    re[i1] = re[i0] - tr
                    im[i1] = im[i0] - ti
                    re[i0] += tr
                    im[i0] += ti
                    k += step
                }
                start += len
            }
            len = len shl 1
        }
    }
}
