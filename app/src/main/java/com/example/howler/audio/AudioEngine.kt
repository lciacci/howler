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

    /** Most recent block RMS level in dBFS (≤ 0; ~-160 at silence). */
    external fun nativeLevelDbfs(): Float

    /** True if the last block hit/exceeded full scale (clipping → reading is low). */
    external fun nativeOverRange(): Boolean
}
