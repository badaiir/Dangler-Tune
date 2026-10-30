package com.dangler.tune.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Прогон реальных записей. В CI пропускается: нужна папка с WAV.
 *
 *   DANGLER_CORPUS_DIR=~/guitar-wavs ./gradlew :core:dsp:test --tests "*CorpusEvaluationTest"
 *
 * Один файл = одна нота. Имя по ноте: B1.wav, E2_take2.wav, F#2.wav. Нота нужна
 * только для сравнения с номиналом; без неё метрики стабильности всё равно считаются.
 *
 * Что печатает:
 * - jitter p95 — разброс вокруг медианы (дрожание показаний), в центах;
 * - oct — сколько кадров улетело на октаву от медианы (для тюнера это брак);
 * - nulls — сколько кадров без ноты после первого чтения (провалы);
 * - nominal — медиана против номинала ноты (включает реальную расстройку гитары);
 * - refine — насколько второй проход сдвинул частоту (негармоничность струны).
 */
class CorpusEvaluationTest {

    @Test
    fun evaluateRealRecordings() {
        val dir = System.getenv("DANGLER_CORPUS_DIR") ?: run {
            println("DANGLER_CORPUS_DIR не задан — реальный корпус пропущен")
            return
        }
        val root = Paths.get(dir)
        if (!Files.isDirectory(root)) {
            println("DANGLER_CORPUS_DIR=$dir — не папка, пропуск")
            return
        }

        val files = mutableListOf<Path>()
        Files.list(root).use { stream ->
            stream.forEach { path ->
                if (path.fileName.toString().endsWith(".wav", ignoreCase = true)) files.add(path)
            }
        }
        files.sort()
        if (files.isEmpty()) {
            println("в $dir нет .wav — пропуск")
            return
        }

        println("=== реальный корпус: ${files.size} файл(ов), $dir ===")
        for (file in files) evaluate(file)
    }

    private fun evaluate(file: Path) {
        val wav = try {
            WavReader.read(file)
        } catch (e: Exception) {
            println("${file.fileName}: не прочитан (${e.message})")
            return
        }
        if (wav.samples.size < wav.sampleRate / 2) {
            println("${file.fileName}: короче 0.5 с, пропуск")
            return
        }

        val sr = wav.sampleRate
        val refined = PitchPipeline(inputSampleRate = sr)
        val coarse = PitchPipeline(
            inputSampleRate = sr,
            detector = FftMpmDetector(
                sampleRate = sr / 2,
                bufferSize = 2048,
                adaptiveRefine = false,
            ),
        )

        val chunk = maxOf(8192, sr / 10)
        val freqs = ArrayList<Float>()
        val coarseFreqs = ArrayList<Float>()
        val times = ArrayList<Double>()
        var nulls = 0
        var seenReading = false

        var i = 0
        while (i < wav.samples.size) {
            val end = minOf(i + chunk, wav.samples.size)
            val samples = wav.samples.copyOfRange(i, end)
            val r = refined.push(samples)
            val c = coarse.push(samples)
            if (r != null) {
                seenReading = true
                freqs.add(r.frequencyHz)
                coarseFreqs.add(c?.frequencyHz ?: Float.NaN)
                times.add(end.toDouble() / sr)
            } else if (seenReading) {
                nulls++
            }
            i = end
        }

        if (freqs.size < 3) {
            println("${file.fileName}: слишком мало чтений (${freqs.size}), пропуск")
            return
        }

        // Атака: первые 250 мс не считаем — там щелчок медиатора и нестабильная высота.
        var from = times.indexOfFirst { it >= 0.25 }
        if (from < 0) from = 0
        if (freqs.size - from < 3) from = 0

        val steady = freqs.subList(from, freqs.size)
        val median = steady.sorted()[steady.size / 2]
        val errors = steady.map { abs(1200.0 * log2((it / median).toDouble())) }.sorted()
        val p95 = percentile(errors, 0.95)
        val octaves = steady.count {
            val ratio = it / median
            abs(ratio - 2f) < 0.06f || abs(ratio - 0.5f) < 0.06f
        }
        val refineDelta = run {
            val deltas = ArrayList<Double>()
            for (k in steady.indices) {
                val idx = from + k
                val c = coarseFreqs[idx]
                if (!c.isNaN() && c > 0f) {
                    deltas.add(1200.0 * log2((steady[k] / c).toDouble()))
                }
            }
            if (deltas.isEmpty()) Double.NaN else deltas.sorted()[deltas.size / 2]
        }
        val nominal = nominalHz(file.fileName.toString())
        val nominalCents = if (nominal == null) Double.NaN else 1200.0 * log2(median / nominal)

        println(
            String.format(
                Locale.ROOT,
                "%-24s sr=%5d ch=%d  n=%3d  med=%7.2fHz  jitter p95=%5.2f¢  oct=%d  nulls=%d  nominal=%s (%+.1f¢)  refine=%+.2f¢",
                file.fileName.toString(), sr, wav.channels, steady.size, median,
                p95, octaves, nulls,
                nominal?.let { String.format(Locale.ROOT, "%.2fHz", it) } ?: "—",
                nominalCents, refineDelta,
            )
        )

        assertEquals("${file.fileName}: октавные скачки", 0, octaves)
        assertTrue("${file.fileName}: jitter p95=$p95", p95 < 50.0)
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        val index = ((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    /** B1.wav / E2_take2.wav / F#2.wav / Bb3.wav → частота 12-TET. */
    private fun nominalHz(name: String): Double? {
        val match = NOTE.find(name) ?: return null
        val letter = match.groupValues[1].uppercase(Locale.ROOT)
        val pitchClass = when (letter) {
            "C" -> 0
            "D" -> 2
            "E" -> 4
            "F" -> 5
            "G" -> 7
            "A" -> 9
            "B" -> 11
            else -> return null
        }
        val accidental = when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "#" -> 1
            "b" -> -1
            else -> 0
        }
        val octave = match.groupValues[3].toIntOrNull() ?: return null
        val midi = (octave + 1) * 12 + pitchClass + accidental
        return 440.0 * 2.0.pow((midi - 69) / 12.0)
    }

    private companion object {
        val NOTE = Regex("(?i)([a-g])([#b]?)(-?\\d)")
    }
}
