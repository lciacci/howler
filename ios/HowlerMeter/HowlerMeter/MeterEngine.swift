import AVFoundation
import Combine

/// Drives the shared C++ meter DSP (`meter_core.h`, via `meter_bridge.h`) from an
/// AVAudioEngine input tap — the iOS analog of the Android Oboe input callback.
/// Publishes readings for SwiftUI; the real CRT/DSEG7 UI (ADR-0001 step 3, held
/// until closed-testing feedback) binds to these same properties.
final class MeterEngine: ObservableObject {
    @Published private(set) var levelDbfs: Float = -160
    @Published private(set) var maxDbfs: Float = -160
    @Published private(set) var minDbfs: Float = 200
    @Published private(set) var leqDbfs: Float = -160
    @Published private(set) var over = false
    @Published private(set) var running = false

    /// Current dBFS→SPL mapping (tier-2 manual or uncalibrated). The DSP stays
    /// uncalibrated; calibration is applied at display, mirroring Android.
    @Published private(set) var calibration: Calibration = .uncalibrated

    private let engine = AVAudioEngine()
    private let meter = meter_create()
    private var poll: Timer?
    private let calStore = CalibrationStore()

    init() { calibration = resolveCalibration(storedManual: calStore.loadManual()) }

    deinit { meter_destroy(meter) }

    /// dB SPL for a measured dBFS under the current calibration, or nil if none.
    func splFromDbfs(_ dbfs: Float) -> Float? { calibration.splFromDbfs(dbfs) }

    /// Tier-2 single-point calibration: pin the current live level to a reference
    /// reading. Capture UI is deferred (ADR step 3); this is the mechanism.
    func saveManualCalibration(referenceSpl: Double, meterClass: String) {
        let offset = manualOffset(referenceSpl: referenceSpl, measuredDbfs: Double(levelDbfs))
        calStore.saveManual(offsetDb: offset, referenceMeterClass: meterClass)
        calibration = .manual(offsetDb: offset, referenceMeterClass: meterClass)
    }

    func clearCalibration() {
        calStore.clear()
        calibration = .uncalibrated
    }

    func start() throws {
        guard !running else { return }
        let session = AVAudioSession.sharedInstance()
        // .measurement disables AGC / noise-suppression / EQ — the iOS analog of
        // Oboe InputPreset::Unprocessed, required for honest SPL.
        try session.setCategory(.record, mode: .measurement)
        try session.setActive(true)

        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        meter_configure(meter, format.sampleRate)
        input.installTap(onBus: 0, bufferSize: 4096, format: format) { [meter] buf, _ in
            guard let ch = buf.floatChannelData else { return }
            // Channel 0 only (mono, like the Android path; stereo route ignores R).
            meter_process(meter, ch[0], Int32(buf.frameLength))
        }
        try engine.start()
        meter_set_weighting(meter, 1)  // A, matching the Android default
        meter_set_fast(meter, true)

        // Poll the atomics at ~20 Hz for the UI, same as Android reads over JNI.
        poll = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            self?.pull()
        }
        running = true
    }

    func stop() {
        poll?.invalidate(); poll = nil
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        meter_on_stopped(meter)
        running = false
        pull()
    }

    func setWeighting(_ w: Int32) { meter_set_weighting(meter, w) }  // 0=Z, 1=A, 2=C
    func setFast(_ fast: Bool) { meter_set_fast(meter, fast) }
    func resetStats() { meter_reset_stats(meter) }

    private func pull() {
        levelDbfs = meter_level_dbfs(meter)
        maxDbfs = meter_max_dbfs(meter)
        minDbfs = meter_min_dbfs(meter)
        leqDbfs = meter_leq_dbfs(meter)
        over = meter_over_range(meter)
    }
}
