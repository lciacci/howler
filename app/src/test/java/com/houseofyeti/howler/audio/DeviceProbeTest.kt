package com.houseofyeti.howler.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the pure probe verdicts (no device needed). */
class DeviceProbeTest {

    @Test fun unprocessedSupported_landsOnUnprocessedRung() {
        assertEquals(InputRung.UNPROCESSED, inputRung(true))
    }

    @Test fun unprocessedUnsupported_fallsToVoiceRecognition() {
        assertEquals(InputRung.VOICE_RECOGNITION, inputRung(false))
    }

    @Test fun noSensitivities_pointToTier2Manual() {
        val verdict = sensitivityVerdict(listOf(null, null))
        assertTrue(verdict, verdict.contains("tier-2"))
    }

    @Test fun populatedSensitivity_demandsReferenceValidation() {
        val verdict = sensitivityVerdict(listOf(-37.5f, null))
        assertTrue(verdict, verdict.contains("-37.5"))
        assertTrue(verdict, verdict.contains("reference meter"))
    }
}
