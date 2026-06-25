package com.example.howler.audio

/**
 * Kotlin bridge to the native Oboe input engine (src/main/cpp/audio_engine.cpp).
 * Uncalibrated: returns relative dBFS. SPL calibration is applied upstream.
 */
object AudioEngine {
    init { System.loadLibrary("howler_audio") }

    /** Opens + starts the unprocessed input stream. Requires RECORD_AUDIO. */
    external fun nativeStart(): Boolean

    external fun nativeStop()

    /** Most recent block RMS level in dBFS, flat/Z-weighted (≤ 0; ~-160 at silence). */
    external fun nativeLevelDbfs(): Float

    /** Most recent block RMS level in dBFS, A-weighted (IEC 61672). */
    external fun nativeLevelDbfsA(): Float

    /** True if the last block hit/exceeded full scale (clipping → reading is low). */
    external fun nativeOverRange(): Boolean

    /** Time-weighting: true = Fast (125 ms), false = Slow (1 s). IEC 61672. */
    external fun nativeSetFast(fast: Boolean)

    /** Running peak of the time-weighted level since last reset, flat/Z-weighted. */
    external fun nativeMaxDbfs(): Float

    /** Running peak of the time-weighted level since last reset, A-weighted. */
    external fun nativeMaxDbfsA(): Float

    /** Clear the max-hold (both weightings). */
    external fun nativeResetMax()
}
