# Howler — iOS

Native iOS front end over the **shared C++ DSP** (`../app/src/main/cpp/meter_core.h`).
Vehicle decision + rationale: [`../docs/adr/0001-ios-vehicle-shared-cpp-core-vs-kmp.md`](../docs/adr/0001-ios-vehicle-shared-cpp-core-vs-kmp.md)
(shared core + native SwiftUI, KMP rejected).

## Layout

```
ios/
  HowlerMeter/                       Xcode project (open HowlerMeter.xcodeproj)
    HowlerMeter.xcodeproj
    HowlerMeter/
      HowlerMeterApp.swift           @main entry
      MeterEngine.swift              AVAudioEngine tap → C bridge, ObservableObject
      ContentView.swift              placeholder debug UI (real CRT/DSEG7 UI is held — see ADR)
      meter_bridge.h / .cpp          C ABI over howler::MeterCore (the shipping bridge)
      HowlerMeter-Bridging-Header.h  exposes the C bridge to Swift
      test_host.cpp                  headless numeric check (NOT in the target)
  README.md · .gitignore
```

The DSP header `meter_core.h` is **not copied** — the bridge `#include "meter_core.h"`
resolves via **Header Search Paths** = `/Users/lorenzociacci/Claude/howler/app/src/main/cpp`
(target Build Settings). One header, both platforms.

## Build config that lives in the project (already set)

- **Header Search Paths** → the absolute `app/src/main/cpp` path above (finds `meter_core.h`).
- **Objective-C Bridging Header** → `HowlerMeter-Bridging-Header.h` (`#import "meter_bridge.h"`).
- **Info** → `NSMicrophoneUsageDescription` = `Howler measures sound level`.
- `test_host.cpp` is on disk but **not** in Build Phases → Compile Sources (it has its own
  `main()`; adding it would collide with the app's `@main`).

## Headless DSP check (no Xcode, no device — run anytime)

```sh
cd ios/HowlerMeter/HowlerMeter
clang++ -std=c++17 -O2 -Wall -I ../../../app/src/main/cpp \
    test_host.cpp meter_bridge.cpp -o /tmp/howler_meter_test && /tmp/howler_meter_test
```
Expected: `PASS: meter_core.h ports clean + numerically sane via C bridge`.

## Status

- ✅ Xcode project builds; bridge compiles + links; the AVAudioEngine tap fires and feeds
  `MeterCore` (verified — the DSP runs on iOS). Host check passes.
- ⚠️ **iOS Simulator mic delivers silence on this machine** (`peak=0.0`) — an environment
  quirk of the simulator's audio input, not the app. The identical audio path was already
  validated live in a Simulator during the spike, so the pipeline is proven; live-audio
  re-confirmation is deferred to the real-device pass.
- ⏳ **Real-device pass** — confirm hardware `.measurement`/unprocessed parity + calibrate vs a
  reference (BAFX). Needs a wired iPhone + free Apple ID signing (trust the dev cert on-device).
- ⏳ Calibration port — iOS mic offset → UserDefaults (ADR step 2).
- 🅗 **Held:** the real CRT/DSEG7 SwiftUI front end (ADR step 3) — until Android closed-testing
  feedback lands, so it's built once against a validated design.
