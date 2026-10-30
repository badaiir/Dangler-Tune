package com.dangler.tune.dsp

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Radix2FftTest {

    @Test
    fun forward_matchesNaiveDft() {
        val n = 16
        val rnd = Random(1)
        val re0 = DoubleArray(n) { rnd.nextDouble() * 2 - 1 }
        val im0 = DoubleArray(n) { rnd.nextDouble() * 2 - 1 }
        val re = re0.copyOf()
        val im = im0.copyOf()

        Radix2Fft(n).forward(re, im)

        for (k in 0 until n) {
            var sr = 0.0
            var si = 0.0
            for (j in 0 until n) {
                val a = -2.0 * PI * k * j / n
                sr += re0[j] * cos(a) - im0[j] * sin(a)
                si += re0[j] * sin(a) + im0[j] * cos(a)
            }
            assertEquals("re[$k]", sr, re[k], 1e-9)
            assertEquals("im[$k]", si, im[k], 1e-9)
        }
    }

    @Test
    fun inverse_roundTrip() {
        val n = 64
        val rnd = Random(2)
        val re0 = DoubleArray(n) { rnd.nextDouble() * 2 - 1 }
        val im0 = DoubleArray(n) { rnd.nextDouble() * 2 - 1 }
        val re = re0.copyOf()
        val im = im0.copyOf()

        val fft = Radix2Fft(n)
        fft.forward(re, im)
        fft.inverse(re, im)

        for (i in 0 until n) {
            assertEquals("re[$i]", re0[i], re[i], 1e-12)
            assertEquals("im[$i]", im0[i], im[i], 1e-12)
        }
    }
}
