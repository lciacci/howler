package com.houseofyeti.howler.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {

    @Test fun uncalibrated_hasNoSpl() {
        assertNull(Calibration.Uncalibrated.splFromDbfs(-40f))
    }

    @Test fun manual_appliesStoredOffset() {
        val cal = Calibration.Manual(offsetDb = 130.0, referenceMeterClass = "class-2")
        // -40 dBFS + 130 = 90 dB SPL
        assertEquals(90f, cal.splFromDbfs(-40f)!!, 1e-3f)
    }

    @Test fun deviceReported_usesNinetyFourMinusSensitivity() {
        val cal = Calibration.DeviceReported(sensitivityDbFs = -37.0)
        // -40 dBFS + (94 - (-37)) = -40 + 131 = 91 dB SPL
        assertEquals(91f, cal.splFromDbfs(-40f)!!, 1e-3f)
    }

    @Test fun manualOffset_isReferenceMinusMeasured() {
        // reference meter reads 84 dB SPL while phone measures -46 dBFS → offset 130
        assertEquals(130.0, manualOffset(referenceSpl = 84.0, measuredDbfs = -46.0), 1e-9)
    }

    @Test fun plausibleSensitivity_band() {
        assertTrue(isPlausibleSensitivity(-37.0))
        assertTrue(!isPlausibleSensitivity(37.0))   // the Pixel's garbage value
        assertTrue(!isPlausibleSensitivity(-10.0))
    }

    @Test fun resolver_manualWinsOverDevice() {
        val manual = Calibration.Manual(130.0, "class-2")
        val r = resolveCalibration(storedManual = manual, deviceSensitivityDbFs = -37.0)
        assertEquals(manual, r)
    }

    @Test fun resolver_plausibleDeviceUsedWhenNoManual() {
        val r = resolveCalibration(storedManual = null, deviceSensitivityDbFs = -37.0)
        assertEquals(Calibration.DeviceReported(-37.0), r)
    }

    @Test fun resolver_garbageDeviceFallsToUncalibrated() {
        val r = resolveCalibration(storedManual = null, deviceSensitivityDbFs = 37.0)
        assertEquals(Calibration.Uncalibrated, r)
    }

    @Test fun resolver_nothingIsUncalibrated() {
        assertEquals(Calibration.Uncalibrated, resolveCalibration(null, null))
    }
}
