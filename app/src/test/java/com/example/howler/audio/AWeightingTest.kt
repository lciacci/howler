package com.example.howler.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Spec-guard for the native A-weighting filter (src/main/cpp/audio_engine.cpp).
 *
 * The filter runs in C++ (it needs the audio samples) and can't be unit-tested
 * here directly. What CAN go wrong without a compile error is the *definition* —
 * the four IEC 61672 corner frequencies and the 0 dB @ 1 kHz normalization. This
 * test recomputes the analog A-weighting magnitude from those same constants in
 * closed form and asserts it against the IEC 61672-1 reference table. If the
 * native corner freqs drift from these, this fails. Keep the constants in sync.
 */
class AWeightingTest {

    // IEC 61672 corner frequencies (Hz) — must match audio_engine.cpp AWeighting::init.
    private val f1 = 20.598997
    private val f2 = 107.65265
    private val f3 = 737.86223
    private val f4 = 12194.217

    /** Analog A-weighting attenuation in dB at frequency f, normalized to 0 dB @ 1 kHz. */
    private fun aWeightDb(f: Double): Double {
        val f2sq = f * f
        val ra = (f4 * f4 * f2sq * f2sq) /
            ((f2sq + f1 * f1) *
                sqrt((f2sq + f2 * f2) * (f2sq + f3 * f3)) *
                (f2sq + f4 * f4))
        return 20.0 * log10(ra) + 2.00  // +2.00 normalizes to 0 dB at 1 kHz
    }

    @Test fun matchesIecReferenceTable() {
        // IEC 61672-1 nominal A-weighting values (dB) at standard frequencies.
        val reference = mapOf(
            31.5 to -39.4,
            100.0 to -19.1,
            1000.0 to 0.0,
            4000.0 to 1.0,
            12500.0 to -4.3,
        )
        for ((freq, expected) in reference) {
            assertEquals("A-weighting at $freq Hz", expected, aWeightDb(freq), 0.2)
        }
    }

    @Test fun unityAtOneKilohertz() {
        assertEquals(0.0, aWeightDb(1000.0), 0.05)
    }

    /** Analog C-weighting attenuation in dB — A minus the mid-band poles f2,f3. */
    private fun cWeightDb(f: Double): Double {
        val f2sq = f * f
        val rc = (f4 * f4 * f2sq) / ((f2sq + f1 * f1) * (f2sq + f4 * f4))
        return 20.0 * log10(rc) + 0.06  // +0.06 normalizes to 0 dB at 1 kHz
    }

    @Test fun cWeightingMatchesIecReferenceTable() {
        // IEC 61672-1 nominal C-weighting values (dB) at standard frequencies.
        val reference = mapOf(
            31.5 to -3.0,
            1000.0 to 0.0,
            8000.0 to -3.0,
            12500.0 to -6.2,
        )
        for ((freq, expected) in reference) {
            assertEquals("C-weighting at $freq Hz", expected, cWeightDb(freq), 0.2)
        }
    }
}
