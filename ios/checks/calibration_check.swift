// Headless check for Calibration.swift — mirrors the Android CalibrationTest.kt
// assertions. Lives outside the app's synchronized source folder so Xcode never
// sweeps its @main into the target. Runs on the macOS host, no Xcode, no device.
//
// Build + run (from ios/checks/):
//   swiftc ../HowlerMeter/HowlerMeter/Calibration.swift calibration_check.swift \
//       -o /tmp/howler_cal_check && /tmp/howler_cal_check

import Foundation

@main
struct CalibrationCheck {
    static func check(_ cond: Bool, _ msg: String) {
        if !cond { print("FAIL: \(msg)"); exit(1) }
    }

    static func main() {
        // splFromDbfs
        check(Calibration.uncalibrated.splFromDbfs(-40) == nil, "uncalibrated has no SPL")
        check(Calibration.manual(offsetDb: 130, referenceMeterClass: "class-2").splFromDbfs(-40) == 90,
              "manual applies offset (-40 + 130 = 90)")

        // manualOffset: reference meter 84 dB SPL while phone reads -46 dBFS → offset 130
        check(manualOffset(referenceSpl: 84, measuredDbfs: -46) == 130, "offset = reference - measured")

        // resolve
        check(resolveCalibration(storedManual: nil) == .uncalibrated, "nothing → uncalibrated")
        let m = Calibration.manual(offsetDb: 130, referenceMeterClass: "class-2")
        check(resolveCalibration(storedManual: m) == m, "stored manual is used")

        // store round-trip in an isolated UserDefaults suite (no pollution of .standard)
        let suiteName = "howler.caltest"
        let suite = UserDefaults(suiteName: suiteName)!
        suite.removePersistentDomain(forName: suiteName)
        let store = CalibrationStore(defaults: suite)
        check(store.loadManual() == nil, "empty store → nil")
        store.saveManual(offsetDb: 126.5, referenceMeterClass: "BAFX")
        check(store.loadManual() == .manual(offsetDb: 126.5, referenceMeterClass: "BAFX"), "save/load round-trip")
        store.clear()
        check(store.loadManual() == nil, "clear → nil")
        suite.removePersistentDomain(forName: suiteName)

        print("PASS: calibration model + store")
    }
}
