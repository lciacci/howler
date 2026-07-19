# Howler — iOS

Native iOS front end over the **shared C++ DSP** (`../app/src/main/cpp/meter_core.h`).
Vehicle decision + rationale: [`../docs/adr/0001-ios-vehicle-shared-cpp-core-vs-kmp.md`](../docs/adr/0001-ios-vehicle-shared-cpp-core-vs-kmp.md)
(shared core + native SwiftUI, KMP rejected).

## Layout

```
ios/
  HowlerMeter/                       Xcode project (open HowlerMeter.xcodeproj)
    HowlerMeter.xcodeproj
    HowlerMeter/                     app target — a file-system-synchronized group:
      HowlerMeterApp.swift           @main entry            EVERY file in this folder is
      MeterEngine.swift              tap → bridge, engine    auto-compiled into the target,
      ContentView.swift              placeholder debug UI    so keep ONLY app sources here
      Calibration.swift              dBFS→SPL model + store   (host checks live in ../checks).
      meter_bridge.h / .cpp          C ABI over MeterCore
      HowlerMeter-Bridging-Header.h  exposes the bridge to Swift
  checks/                            standalone host checks (NOT in the target)
    test_host.cpp                    DSP numeric check (macOS host)
    calibration_check.swift          Calibration model + store check (macOS host)
  README.md · .gitignore
```

The DSP header `meter_core.h` is **not copied** — the bridge `#include "meter_core.h"`
resolves via **Header Search Paths** = `/Users/lorenzociacci/Claude/howler/app/src/main/cpp`
(target Build Settings). One header, both platforms.

## Build config that lives in the project (already set)

- **Header Search Paths** → the absolute `app/src/main/cpp` path above (finds `meter_core.h`).
- **Objective-C Bridging Header** → `HowlerMeter-Bridging-Header.h` (`#import "meter_bridge.h"`).
- **Info** → `NSMicrophoneUsageDescription` = `Howler measures sound level`.
- The app folder is a **synchronized group**: dropping a `.swift`/`.cpp` in it auto-adds it to
  the target (no manual add). The flip side — anything with its own `main()`/`@main` (the host
  checks) must stay in `ios/checks/`, or it collides with the app's `@main`.

## Headless checks (no Xcode, no device — run anytime, from `ios/checks/`)

```sh
cd ios/checks
# DSP:
clang++ -std=c++17 -O2 -Wall -I ../../app/src/main/cpp -I ../HowlerMeter/HowlerMeter \
    test_host.cpp ../HowlerMeter/HowlerMeter/meter_bridge.cpp \
    -o /tmp/howler_meter_test && /tmp/howler_meter_test
# Calibration:
swiftc ../HowlerMeter/HowlerMeter/Calibration.swift calibration_check.swift \
    -o /tmp/howler_cal_check && /tmp/howler_cal_check
```
Expected: `PASS: meter_core.h ...` and `PASS: calibration model + store`.

## Status

- ✅ Xcode project builds; bridge compiles + links; the AVAudioEngine tap fires and feeds
  `MeterCore` (DSP runs on iOS). Both host checks pass.
- ✅ **Calibration ported (ADR step 2):** `Calibration.swift` — tier-2 manual offset
  (`SPL = dBFS + offset`) + UserDefaults store + resolver, wired into `MeterEngine`
  (`splFromDbfs`, `saveManualCalibration`, `clearCalibration`). Mirrors Android
  `Calibration.kt`/`CalibrationStore.kt`; tier-1 device-sensitivity omitted (no iOS API, and
  Android doesn't auto-trust it either). Capture UI deferred to step 3.
- ✅ **UI ported (ADR step 3, `0c69cd2`):** the CRT/DSEG7 SwiftUI front end + first-run and
  manual-calibration dialogs — a layer-for-layer port of Android `MainActivity.kt` over
  `MeterEngine`. `ContentView.swift` + `HowlerColors.swift` (Phosphor palette); DSEG7 ttf
  registered at launch; `howler_head` imageset. Deployment target 17.0. Builds + renders live in
  the simulator; reviewed (mic-revoke stop + font-register assert fixed).
- ⚠️ **iOS Simulator mic delivers silence on this machine** (`peak=0.0`) — a simulator
  audio-input quirk, not the app (the identical path ran live in the spike). Live-audio
  re-confirmation is folded into the real-device pass.
- ⏳ **Real-device pass — the only remaining item.** Hardware `.measurement`/unprocessed parity +
  live-audio confirm + calibrate vs a reference (BAFX). Needs a wired iPhone + free Apple ID
  signing (already configured). Full checklist in `../docs/HANDOFF.md`.
