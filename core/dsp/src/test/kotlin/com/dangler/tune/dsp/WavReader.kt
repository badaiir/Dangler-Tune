package com.dangler.tune.dsp

import java.nio.file.Files
import java.nio.file.Path

/**
 * Минимальный RIFF/WAVE reader для тестового корпуса.
 *
 * Поддерживает PCM 8/16/24/32-bit, IEEE float 32/64-bit, mono/stereo/multichannel
 * (каналы усредняются), WAVE_FORMAT_EXTENSIBLE. Не тянет зависимостей и не лезет
 * в прод: тюнер получает уже готовый FloatArray с микрофона.
 */
internal object WavReader {

    data class Wav(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val samples: FloatArray,
    )

    fun read(path: Path): Wav = parse(Files.readAllBytes(path), path.fileName.toString())

    fun parse(bytes: ByteArray, name: String = "wav"): Wav {
        require(bytes.size >= 44) { "$name: слишком короткий WAV" }
        require(ascii(bytes, 0) == "RIFF" && ascii(bytes, 8) == "WAVE") { "$name: не RIFF/WAVE" }

        var fmtOffset = -1
        var fmtSize = 0
        var dataOffset = -1
        var dataSize = 0
        var offset = 12

        while (offset + 8 <= bytes.size) {
            val id = ascii(bytes, offset)
            val size = u32(bytes, offset + 4)
            val body = offset + 8
            if (size < 0 || body + size > bytes.size) break
            when (id) {
                "fmt " -> { fmtOffset = body; fmtSize = size }
                "data" -> { dataOffset = body; dataSize = size }
            }
            offset = body + size + (size and 1) // chunks выровнены по 2 байта
        }

        require(fmtOffset >= 0 && fmtSize >= 16) { "$name: нет fmt-чанка" }
        require(dataOffset >= 0 && dataSize > 0) { "$name: нет data-чанка" }

        var format = u16(bytes, fmtOffset)
        val channels = u16(bytes, fmtOffset + 2)
        val sampleRate = u32(bytes, fmtOffset + 4)
        val bits = u16(bytes, fmtOffset + 14)
        if (format == 0xFFFE && fmtSize >= 26) {
            // WAVE_FORMAT_EXTENSIBLE: настоящий формат — первые 2 байта SubFormat GUID.
            format = u16(bytes, fmtOffset + 24)
        }

        require(channels in 1..8) { "$name: channels=$channels" }
        require(sampleRate > 0) { "$name: sampleRate=$sampleRate" }
        require(bits % 8 == 0) { "$name: bits=$bits" }

        val bytesPerSample = bits / 8
        val frameBytes = bytesPerSample * channels
        val frames = dataSize / frameBytes
        require(frames > 0) { "$name: нет сэмплов" }

        val out = FloatArray(frames)
        for (f in 0 until frames) {
            val base = dataOffset + f * frameBytes
            var sum = 0.0
            for (c in 0 until channels) {
                val o = base + c * bytesPerSample
                sum += when (format) {
                    1 -> when (bits) {
                        8 -> ((bytes[o].toInt() and 0xFF) - 128) / 128.0
                        16 -> i16(bytes, o) / 32768.0
                        24 -> i24(bytes, o) / 8388608.0
                        32 -> i32(bytes, o) / 2147483648.0
                        else -> error("$name: PCM $bits bit не поддержан")
                    }
                    3 -> when (bits) {
                        32 -> Float.fromBits(i32(bytes, o)).toDouble()
                        64 -> Double.fromBits(i64(bytes, o))
                        else -> error("$name: float $bits bit не поддержан")
                    }
                    else -> error("$name: audioFormat=$format не поддержан")
                }
            }
            out[f] = (sum / channels).toFloat()
        }
        return Wav(sampleRate, channels, bits, out)
    }

    private fun ascii(b: ByteArray, o: Int) = String(b, o, 4, Charsets.US_ASCII)

    private fun u16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun i16(b: ByteArray, o: Int): Int = u16(b, o).toShort().toInt()

    private fun u32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun i32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            (b[o + 3].toInt() shl 24)

    private fun i24(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            (b[o + 2].toInt() shl 16)

    private fun i64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((b[o + i].toLong() and 0xFF) shl (8 * i))
        return v
    }
}
