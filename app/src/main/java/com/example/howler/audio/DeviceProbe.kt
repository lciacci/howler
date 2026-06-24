package com.example.howler.audio

/**
 * STEP ZERO device probe — see docs/howler-calibration-design.md.
 *
 * Resolves the two decisions that outrank metering code:
 *  - Probe A: does the device expose an UNPROCESSED input path? (load-bearing —
 *    without it AGC/NS/AEC float every reading and calibration is meaningless)
 *  - Probe B: does MicrophoneInfo.getSensitivity() return a real value, or is
 *    tier-2 manual calibration the de facto path here?
 *
 * The data gathering touches AudioManager (Android); the verdict functions below
 * are pure so they're unit-tested on the JVM without a device.
 */

/** Input rung per the doc's fallback ladder. DEGRADED is only confirmable at
 *  stream-open time in the native layer, so the probe reports the top two. */
enum class InputRung { UNPROCESSED, VOICE_RECOGNITION }

data class MicProbe(
    val description: String,
    val location: String,
    /** dBFS produced by a 1 kHz tone at 94 dB SPL; null = unpopulated/unknown. */
    val sensitivityDbFs: Float?,
)

data class ProbeResult(
    val device: String,
    val unprocessedSupported: Boolean,
    val outputSampleRateHz: Int?,
    val outputFramesPerBuffer: Int?,
    val microphones: List<MicProbe>,
    val micError: String? = null,
)

/** Probe A verdict: which input rung this device lands on. */
fun inputRung(unprocessedSupported: Boolean): InputRung =
    if (unprocessedSupported) InputRung.UNPROCESSED else InputRung.VOICE_RECOGNITION

/** Probe B verdict: is tier-1 getSensitivity() usable, or fall to tier-2 manual? */
fun sensitivityVerdict(sensitivities: List<Float?>): String {
    val real = sensitivities.filterNotNull()
    if (real.isEmpty()) return "unpopulated → tier-2 manual calibration is the path"
    val values = real.joinToString(", ") { "%.1f".format(it) }
    return "populated ($values dBFS) — must validate vs reference meter; " +
        "tier-1 can be populated yet wrong (transducer spec ≠ AudioRecord digital gain)"
}
