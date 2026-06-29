package com.houseofyeti.howler.audio

/**
 * Kotlin bridge to the native Oboe input engine (src/main/cpp/audio_engine.cpp).
 * The engine computes one active frequency weighting at a time (see [setWeighting]);
 * all levels are dBFS. SPL calibration is applied upstream.
 */
object AudioEngine {
    init { System.loadLibrary("howler_audio") }

    /** Weighting codes passed to [nativeSetWeighting]. */
    const val WEIGHTING_Z = 0
    const val WEIGHTING_A = 1
    const val WEIGHTING_C = 2

    /** Opens + starts the unprocessed input stream. Requires RECORD_AUDIO. */
    external fun nativeStart(): Boolean

    external fun nativeStop()

    /** Most recent time-weighted level in dBFS, active weighting (≤ 0; ~-160 at silence). */
    external fun nativeLevelDbfs(): Float

    /** True if the last block hit/exceeded full scale (clipping → reading is low). */
    external fun nativeOverRange(): Boolean

    /** Time-weighting: true = Fast (125 ms), false = Slow (1 s). IEC 61672. */
    external fun nativeSetFast(fast: Boolean)

    /** Active frequency weighting: [WEIGHTING_Z]/[WEIGHTING_A]/[WEIGHTING_C]. Resets stats. */
    external fun nativeSetWeighting(weighting: Int)

    /** Running peak of the time-weighted level since last reset. */
    external fun nativeMaxDbfs(): Float

    /** True if the peak came from a clipped block — true level is ≥ the shown Max. */
    external fun nativeMaxClipped(): Boolean

    /** Running minimum of the time-weighted level since last reset. */
    external fun nativeMinDbfs(): Float

    /** Equivalent continuous level (Leq) since last reset. */
    external fun nativeLeqDbfs(): Float

    /** Clear all since-reset stats (max, min, Leq). */
    external fun nativeResetStats()
}
