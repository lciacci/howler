package com.houseofyeti.howler.audio

/**
 * Maps measured dBFS → estimated dB SPL. See docs/howler-calibration-design.md.
 *
 * Math: SPL = measured_dBFS + offset.
 *  - tier-1 (device-reported): offset = 94 − sensitivity  (94 dB SPL reference)
 *  - tier-2 (manual single-point): offset = reference_SPL − measured_dBFS
 *
 * The resolver is the transferable asset — it picks the best source per device.
 * STEP ZERO showed tier-1 getSensitivity() can be populated-but-garbage, so a
 * validated manual offset wins, and implausible device values are rejected.
 */
sealed interface Calibration {
    /** No mapping; report relative dBFS only. Always available, never lies. */
    data object Uncalibrated : Calibration

    /** Tier-2 manual single-point against a reference meter. */
    data class Manual(val offsetDb: Double, val referenceMeterClass: String) : Calibration

    /** Tier-1 device-reported transducer sensitivity (dBFS @ 94 dB SPL, 1 kHz). */
    data class DeviceReported(val sensitivityDbFs: Double) : Calibration
}

private const val REFERENCE_SPL = 94.0

/** dB SPL for a measured dBFS, or null when uncalibrated. */
fun Calibration.splFromDbfs(dbfs: Float): Float? = when (this) {
    is Calibration.Uncalibrated -> null
    is Calibration.Manual -> (dbfs + offsetDb).toFloat()
    is Calibration.DeviceReported -> (dbfs + (REFERENCE_SPL - sensitivityDbFs)).toFloat()
}

/** Tier-2 offset from one calibration point: reference_SPL − measured_dBFS. */
fun manualOffset(referenceSpl: Double, measuredDbfs: Double): Double = referenceSpl - measuredDbfs

/** A transducer sensitivity is only believable in this band; outside it is garbage. */
fun isPlausibleSensitivity(sensitivityDbFs: Double): Boolean = sensitivityDbFs in -60.0..-20.0

/**
 * Pick the best calibration source. A stored manual offset (validated) beats the
 * device-reported value; a device value is used only if plausible; else uncalibrated.
 */
fun resolveCalibration(
    storedManual: Calibration.Manual?,
    deviceSensitivityDbFs: Double?,
): Calibration = when {
    storedManual != null -> storedManual
    deviceSensitivityDbFs != null && isPlausibleSensitivity(deviceSensitivityDbFs) ->
        Calibration.DeviceReported(deviceSensitivityDbFs)
    else -> Calibration.Uncalibrated
}
