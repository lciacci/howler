import Foundation

/// Maps measured dBFS → estimated dB SPL. iOS mirror of the Android
/// `Calibration.kt` (see docs/howler-calibration-design.md).
///
/// Math: `SPL = dBFS + offset`, tier-2 manual single-point:
///   offset = reference_SPL − measured_dBFS
///
/// The Android tier-1 "device-reported" source (offset = 94 − sensitivity) is
/// intentionally omitted: iOS exposes no transducer-sensitivity API, and Android
/// never auto-trusts it at runtime either (MainActivity resolves with a null
/// device value), so manual-or-uncalibrated matches real behavior on both.
enum Calibration: Equatable {
    /// No mapping; report relative dBFS only. Always available, never lies.
    case uncalibrated
    /// Tier-2 manual single-point against a reference meter.
    case manual(offsetDb: Double, referenceMeterClass: String)

    /// dB SPL for a measured dBFS, or nil when uncalibrated.
    func splFromDbfs(_ dbfs: Float) -> Float? {
        switch self {
        case .uncalibrated: return nil
        case .manual(let offsetDb, _): return dbfs + Float(offsetDb)
        }
    }
}

/// Tier-2 offset from one calibration point: reference_SPL − measured_dBFS.
func manualOffset(referenceSpl: Double, measuredDbfs: Double) -> Double {
    referenceSpl - measuredDbfs
}

/// Pick the best calibration source: a stored manual point, else uncalibrated.
/// (Kept as a seam — if a future iOS sensitivity source appears, it slots here.)
func resolveCalibration(storedManual: Calibration?) -> Calibration {
    storedManual ?? .uncalibrated
}

/// Persists the tier-2 manual calibration for this install (one device profile,
/// v1) in UserDefaults. Mirror of Android `CalibrationStore` (SharedPreferences).
struct CalibrationStore {
    private let defaults: UserDefaults
    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    func loadManual() -> Calibration? {
        // Absence of the offset key = never calibrated (distinct from offset 0).
        guard defaults.object(forKey: Keys.offset) != nil else { return nil }
        let offset = defaults.double(forKey: Keys.offset)
        let meter = defaults.string(forKey: Keys.meter) ?? ""
        return .manual(offsetDb: offset, referenceMeterClass: meter)
    }

    func saveManual(offsetDb: Double, referenceMeterClass: String) {
        defaults.set(offsetDb, forKey: Keys.offset)
        defaults.set(referenceMeterClass, forKey: Keys.meter)
    }

    func clear() {
        defaults.removeObject(forKey: Keys.offset)
        defaults.removeObject(forKey: Keys.meter)
    }

    private enum Keys {
        static let offset = "howler_calibration.offset_db"
        static let meter = "howler_calibration.meter_class"
    }
}
