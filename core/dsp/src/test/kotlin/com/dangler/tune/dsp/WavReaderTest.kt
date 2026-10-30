package com.dangler.tune.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WavReaderTest {

    @Test
    fun reads16BitMono() {
        val src = floatArrayOf(0f, 0.25f, -0.5f, 0.75f)
        val wav = WavReader.parse(pcm16(src, 44100, 1), "mono16")

        assertEquals(44100, wav.sampleRate)
        assertEquals(1, wav.channels)
        assertEquals(16, wav.bitsPerSample)
        for (i in src.indices) assertEquals(src[i], wav.samples[i], 1f / 32000f)
    }

    @Test
    fun reads16BitStereo_andDownmixes() {
        // кадры: (0.5, -0.5) -> 0.0, (0.25, 0.75) -> 0.5
        val src = floatArrayOf(0.5f, -0.5f, 0.25f, 0.75f)
        val wav = WavReader.parse(pcm16(src, 48000, 2), "stereo16")

        assertEquals(48000, wav.sampleRate)
        assertEquals(2, wav.channels)
        assertEquals(2, wav.samples.size)
        assertEquals(0f, wav.samples[0], 1f / 32000f)
        assertEquals(0.5f, wav.samples[1], 1f / 32000f)
    }

    @Test
    fun readsFloat32_withoutQuantization() {
        val src = floatArrayOf(0f, 0.123456f, -0.987654f, 1f)
        val wav = WavReader.parse(float32(src, 44100, 1), "float32")

        assertEquals(32, wav.bitsPerSample)
        assertArrayEquals(src, wav.samples, 1e-7f)
    }

    @Test
    fun reads24Bit() {
        val src = floatArrayOf(0f, 0.5f, -0.5f)
        val wav = WavReader.parse(pcm24(src, 44100, 1), "mono24")

        assertEquals(24, wav.bitsPerSample)
        for (i in src.indices) assertEquals(src[i], wav.samples[i], 1f / 8_000_000f)
    }

    @Test
    fun rejectsGarbage() {
        assertThrows(IllegalArgumentException::class.java) {
            WavReader.parse(ByteArray(100), "garbage")
        }
    }

    private fun pcm16(samples: FloatArray, sampleRate: Int, channels: Int): ByteArray {
        val bytesPerSample = 2
        val dataSize = samples.size * bytesPerSample
        val out = header(sampleRate, channels, 16, 1, dataSize)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
            put16(out, 44 + i * 2, v)
        }
        return out
    }

    private fun pcm24(samples: FloatArray, sampleRate: Int, channels: Int): ByteArray {
        val bytesPerSample = 3
        val dataSize = samples.size * bytesPerSample
        val out = header(sampleRate, channels, 24, 1, dataSize)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 8388607).toInt()
            val o = 44 + i * 3
            out[o] = (v and 0xFF).toByte()
            out[o + 1] = ((v ushr 8) and 0xFF).toByte()
            out[o + 2] = ((v ushr 16) and 0xFF).toByte()
        }
        return out
    }

    private fun float32(samples: FloatArray, sampleRate: Int, channels: Int): ByteArray {
        val dataSize = samples.size * 4
        val out = header(sampleRate, channels, 32, 3, dataSize)
        for (i in samples.indices) put32(out, 44 + i * 4, samples[i].toRawBits())
        return out
    }

    private fun header(sampleRate: Int, channels: Int, bits: Int, format: Int, dataSize: Int): ByteArray {
        val out = ByteArray(44 + dataSize)
        ascii(out, 0, "RIFF")
        put32(out, 4, 36 + dataSize)
        ascii(out, 8, "WAVE")
        ascii(out, 12, "fmt ")
        put32(out, 16, 16)
        put16(out, 20, format)
        put16(out, 22, channels)
        put32(out, 24, sampleRate)
        put32(out, 28, sampleRate * channels * (bits / 8))
        put16(out, 32, channels * (bits / 8))
        put16(out, 34, bits)
        ascii(out, 36, "data")
        put32(out, 40, dataSize)
        return out
    }

    private fun ascii(b: ByteArray, o: Int, s: String) {
        for (i in s.indices) b[o + i] = s[i].code.toByte()
    }

    private fun put16(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 0xFF).toByte()
        b[o + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun put32(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 0xFF).toByte()
        b[o + 1] = ((v ushr 8) and 0xFF).toByte()
        b[o + 2] = ((v ushr 16) and 0xFF).toByte()
        b[o + 3] = ((v ushr 24) and 0xFF).toByte()
    }
}
