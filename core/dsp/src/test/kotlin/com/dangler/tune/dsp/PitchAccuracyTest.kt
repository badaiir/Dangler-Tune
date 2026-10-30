package com.dangler.tune.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Измерительный стенд тюнера. Печатает таблицу и валит сборку, если метрики
 * деградировали. Порог p95 — в центах; октавные ошибки для тюнера недопустимы
 * вообще: это настройка не той струны.
 */
class PitchAccuracyTest {

    private val fft = FftMpmDetector(sampleRate = 22050, bufferSize = 2048)

    @Test
    fun report_allStrings_detector() {
        println("=== detector: all strings, SNR 30 dB ===")
        for (note in PitchCorpus.ALL) {
            val signal = PitchCorpus.pluck(note.hz)
            val d = PitchMetrics.detector(fft, signal, note.hz)
            println(d.line("${note.name} ${note.hz}Hz"))
            assertEquals("${note.name}: octave errors", 0, d.octaveErrors)
            assertEquals("${note.name}: gross errors", 0, d.grossErrors)
            assertTrue("${note.name}: p95=${d.p95AbsCents}", d.p95AbsCents < 2.0)
            assertTrue("${note.name}: miss=${d.missRate}", d.missRate < 0.10)
        }
    }

    @Test
    fun report_allStrings_pipeline() {
        println("=== pipeline (detector + tracker) ===")
        for (note in PitchCorpus.ALL) {
            val signal = PitchCorpus.pluck(note.hz)
            val p = PitchMetrics.pipeline(signal, note.hz)
            println(p.line("${note.name} ${note.hz}Hz"))
            assertEquals("${note.name}: octave errors", 0, p.octaveErrors)
            assertTrue("${note.name}: p95=${p.p95AbsCents}", p.p95AbsCents < 2.0)
            assertTrue("${note.name}: miss=${p.missRate}", p.missRate < 0.20)
        }
    }

    @Test
    fun weakFundamental_neverTunesOctaveUp() {
        // Самая опасная ошибка тюнера: играешь B1, а он показывает B2.
        // Фундаменталка задавлена до 3% — как на дешёвом звукоснимателе.
        for (gain in doubleArrayOf(0.10, 0.03)) {
            val signal = PitchCorpus.pluck(61.74f, fundamentalGain = gain)
            val d = PitchMetrics.detector(fft, signal, 61.74f)
            println(d.line("B1 fund=${(gain * 100).toInt()}%"))
            assertEquals("fund=$gain: octave errors", 0, d.octaveErrors)
            assertTrue("fund=$gain: p95=${d.p95AbsCents}", d.p95AbsCents < 3.0)
        }
    }

    @Test
    fun noisy_10dB_stillTracksRightString() {
        println("=== SNR 10 dB ===")
        for (note in listOf(PitchCorpus.DROP_B[0], PitchCorpus.STANDARD_E[0], PitchCorpus.STANDARD_E[1], PitchCorpus.STANDARD_E[5])) {
            val signal = PitchCorpus.pluck(note.hz, snrDb = 10.0)
            val d = PitchMetrics.detector(fft, signal, note.hz)
            println(d.line("${note.name} 10dB"))
            assertEquals("${note.name}: octave errors", 0, d.octaveErrors)
            assertTrue("${note.name}: p95=${d.p95AbsCents}", d.p95AbsCents < 5.0)
            assertTrue("${note.name}: miss=${d.missRate}", d.missRate < 0.35)
        }
    }

    @Test
    fun quietPluck_stillAboveGate() {
        // Тихий щипок у грифа: RMS 0.012 при гейте 0.006.
        for (note in listOf(PitchCorpus.DROP_B[0], PitchCorpus.DROP_B[3])) {
            val signal = PitchCorpus.pluck(note.hz, levelRms = 0.012)
            val d = PitchMetrics.detector(fft, signal, note.hz)
            println(d.line("${note.name} quiet"))
            assertEquals("${note.name}: octave errors", 0, d.octaveErrors)
            assertTrue("${note.name}: miss=${d.missRate}", d.missRate < 0.25)
        }
    }

    @Test
    fun negatives_whiteNoiseAndTaps_areIgnored() {
        val negatives = listOf(
            "white noise" to PitchCorpus.whiteNoise(),
            "taps" to PitchCorpus.taps(),
        )
        for ((name, signal) in negatives) {
            val fp = PitchMetrics.falsePositiveRate(signal)
            val raw = PitchMetrics.detectorFalsePositiveRate(fft, signal)
            println(String.format(Locale.ROOT, "negatives: %-14s pipelineFP=%5.1f%% detectorFP=%5.1f%%", name, fp * 100, raw * 100))
            assertTrue("$name: pipeline FP=$fp", fp < 0.10)
        }
    }

    @Test
    fun realisticPluckPositions_andPhoneMic() {
        // Позиция щипка вырезает гармоники (у 0.5 — все чётные), микрофон телефона
        // режет низ, корпус поднимает 200 Гц. Тюнер не должен ни врать, ни терять ноту.
        data class Variant(val label: String, val position: Double, val hp: Double, val body: Double)
        val variants = listOf(
            Variant("p=0.10 HP90", 0.10, 90.0, 0.0),
            Variant("p=0.18 HP90", 0.18, 90.0, 0.0),
            Variant("p=0.50 HP90", 0.50, 90.0, 0.0),
            Variant("p=0.18 HP90 body200", 0.18, 90.0, 200.0),
        )
        val notes = listOf(
            PitchCorpus.DROP_B[0], PitchCorpus.STANDARD_E[0], PitchCorpus.STANDARD_E[1],
            PitchCorpus.STANDARD_E[3], PitchCorpus.STANDARD_E[5],
        )
        for (note in notes) {
            for (v in variants) {
                val signal = PitchCorpus.pluck(
                    note.hz,
                    snrDb = 25.0,
                    pluckPosition = v.position,
                    micHighPassHz = v.hp,
                    bodyPeakHz = v.body,
                )
                val d = PitchMetrics.detector(fft, signal, note.hz)
                println(d.line("${note.name} ${v.label}"))
                assertEquals("${note.name} ${v.label}: octave", 0, d.octaveErrors)
                assertTrue("${note.name} ${v.label}: p95=${d.p95AbsCents}", d.p95AbsCents < 3.0)
                assertTrue("${note.name} ${v.label}: miss=${d.missRate}", d.missRate < 0.25)
            }
        }
    }

    @Test
    fun detunedNotes_areMeasuredPrecisely() {
        // Тюнер обязан показывать реальную расстройку, а не притягивать к ноте.
        for (cents in intArrayOf(-25, -10, 0, 10, 25)) {
            val target = PitchCorpus.detuned(110f, cents.toFloat())
            val signal = PitchCorpus.pluck(target)
            val d = PitchMetrics.detector(fft, signal, target)
            println(d.line("A2 ${cents} cents"))
            assertEquals("$cents: octave errors", 0, d.octaveErrors)
            assertTrue("$cents: p95=${d.p95AbsCents}", d.p95AbsCents < 2.0)
            assertTrue("$cents: max=${d.maxAbsCents}", d.maxAbsCents < 3.0)
        }
    }

    @Test
    fun pinkNoise_isIgnored() {
        // Розовый шум — кулер, улица, вентилятор. Низких частот много, соблазн «услышать ноту» велик.
        val signal = PitchCorpus.pinkNoise()
        val fp = PitchMetrics.falsePositiveRate(signal)
        val raw = PitchMetrics.detectorFalsePositiveRate(fft, signal)
        println(String.format(Locale.ROOT, "negatives: pink noise     pipelineFP=%5.1f%% detectorFP=%5.1f%%", fp * 100, raw * 100))
        assertTrue("pink noise: pipeline FP=$fp", fp < 0.10)
    }

    @Test
    fun speed_fftVsDirect() {
        val signal = PitchCorpus.pluck(110f)
        val decimated = Decimator(2).push(signal)
        val conditioner = SignalConditioner()
        val window = FloatArray(2048)
        System.arraycopy(decimated, 4096, window, 0, 2048)
        conditioner.process(window)

        val direct = MpmPitchDetector(sampleRate = 22050, bufferSize = 2048)
        val fast = FftMpmDetector(sampleRate = 22050, bufferSize = 2048)

        // прогрев JIT
        repeat(30) { direct.detect(window); fast.detect(window) }

        val iterations = 300
        val t0 = System.nanoTime()
        repeat(iterations) { direct.detect(window) }
        val directMs = (System.nanoTime() - t0) / 1e6 / iterations

        val t1 = System.nanoTime()
        repeat(iterations) { fast.detect(window) }
        val fftMs = (System.nanoTime() - t1) / 1e6 / iterations

        println(String.format(Locale.ROOT, "speed: direct=%.3f ms/window  fft=%.3f ms/window  x%.1f", directMs, fftMs, directMs / fftMs))
        assertTrue("FFT should be faster: direct=$directMs fft=$fftMs", fftMs < directMs)
    }
}
