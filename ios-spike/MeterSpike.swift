// iOS audio-path spike: drive howler::MeterCore (via meter_bridge.h) from an
// AVAudioEngine input tap — the iOS analog of the Android Oboe input callback.
// This is the half of the spike that needs a real device (mic) + Xcode; it does
// not compile under Command Line Tools alone. See README.md.
//
// Swift imports meter_bridge.h through a bridging header (README step 3).

import AVFoundation

final class MeterSpike {
    private let engine = AVAudioEngine()
    private let meter = meter_create()

    deinit { meter_destroy(meter) }

    func start() throws {
        let session = AVAudioSession.sharedInstance()
        // .measurement mode is the iOS analog of Oboe InputPreset::Unprocessed:
        // it disables AGC / noise suppression / EQ so the meter sees raw signal.
        // Without this the readings are processed and uncalibratable — the single
        // most important iOS-parity setting to prove out.
        try session.setCategory(.record, mode: .measurement)
        try session.setActive(true)

        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        meter_configure(meter, format.sampleRate)

        input.installTap(onBus: 0, bufferSize: 4096, format: format) { [meter] buffer, _ in
            guard let ch = buffer.floatChannelData else { return }
            // Channel 0 only (Android path is mono; a stereo route just ignores R).
            meter_process(meter, ch[0], Int32(buffer.frameLength))
        }

        try engine.start()
        meter_set_weighting(meter, 1)  // A, matching the Android default
        meter_set_fast(meter, true)

        // Poll + print, same surface the Android UI reads over JNI.
        Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [meter] _ in
            print(String(format: "%.1f dB(A)  max %.1f  min %.1f  Leq %.1f  over=%d",
                         meter_level_dbfs(meter), meter_max_dbfs(meter),
                         meter_min_dbfs(meter), meter_leq_dbfs(meter),
                         meter_over_range(meter) ? 1 : 0))
        }
    }

    func stop() {
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        meter_on_stopped(meter)
    }
}
