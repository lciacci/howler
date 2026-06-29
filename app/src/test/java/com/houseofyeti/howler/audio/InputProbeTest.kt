package com.houseofyeti.howler.audio

import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the pure confirmedRung verdict (no device needed). */
class InputProbeTest {

    private fun ok(name: String) = OpenAttempt(name, true, 48000, 1, "PCM_FLOAT", 480)
    private fun fail(name: String) = OpenAttempt(name, false, 0, 0, "PCM_FLOAT", 0, "fail")

    @Test fun unprocessedOpens_reportsBestPath() {
        val r = confirmedRung(listOf(ok("UNPROCESSED"), ok("VOICE_RECOGNITION")))
        assertTrue(r, r.contains("UNPROCESSED opens"))
    }

    @Test fun onlyVoiceRecognitionOpens_reportsFloor() {
        val r = confirmedRung(listOf(fail("UNPROCESSED"), ok("VOICE_RECOGNITION")))
        assertTrue(r, r.contains("least-processed floor"))
    }

    @Test fun nothingOpens_reportsDegraded() {
        val r = confirmedRung(listOf(fail("UNPROCESSED"), fail("VOICE_RECOGNITION")))
        assertTrue(r, r.contains("DEGRADED"))
    }
}
