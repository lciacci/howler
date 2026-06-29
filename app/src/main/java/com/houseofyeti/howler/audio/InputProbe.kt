package com.houseofyeti.howler.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Authoritative Probe A — see docs/howler-calibration-design.md.
 *
 * The PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED flag is an unreliable hint; the
 * real test is whether an AudioRecord actually opens with the source. Try the
 * least-processed source first, fall down the ladder.
 */

data class OpenAttempt(
    val sourceName: String,
    val opened: Boolean,
    val sampleRate: Int,
    val channels: Int,
    val encoding: String,
    val framesRead: Int,
    val error: String? = null,
)

private const val SAMPLE_RATE = 48000

/** Opens each source in ladder order. Requires RECORD_AUDIO. */
fun runInputProbe(): List<OpenAttempt> = listOf(
    tryOpen(MediaRecorder.AudioSource.UNPROCESSED, "UNPROCESSED"),
    tryOpen(MediaRecorder.AudioSource.VOICE_RECOGNITION, "VOICE_RECOGNITION"),
)

private fun tryOpen(source: Int, name: String): OpenAttempt {
    val channel = AudioFormat.CHANNEL_IN_MONO
    val encoding = AudioFormat.ENCODING_PCM_FLOAT
    val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, channel, encoding)
    if (minBuf <= 0) return OpenAttempt(name, false, 0, 0, "PCM_FLOAT", 0, "getMinBufferSize=$minBuf")
    var record: AudioRecord? = null
    return try {
        record = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(channel)
                    .setEncoding(encoding)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .build()
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            OpenAttempt(name, false, 0, 0, "PCM_FLOAT", 0, "state=${record.state}")
        } else {
            record.startRecording()
            val buf = FloatArray(SAMPLE_RATE / 100) // ~10 ms
            val n = record.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
            record.stop()
            OpenAttempt(name, n >= 0, record.sampleRate, record.channelCount, "PCM_FLOAT", maxOf(n, 0))
        }
    } catch (e: Exception) {
        OpenAttempt(name, false, 0, 0, "PCM_FLOAT", 0, e.message ?: "$e")
    } finally {
        record?.release()
    }
}

/** Pure verdict: which rung the device actually lands on, from real opens. */
fun confirmedRung(attempts: List<OpenAttempt>): String =
    when (attempts.firstOrNull { it.opened }?.sourceName) {
        "UNPROCESSED" -> "UNPROCESSED opens → best path (the property's 'false' was a false negative)"
        "VOICE_RECOGNITION" -> "UNPROCESSED failed; VOICE_RECOGNITION opens → least-processed floor"
        else -> "no low-processing source opened → DEGRADED; readings must be flagged"
    }
