# Howler — iOS

Native iOS front end over the **shared C++ DSP** (`../app/src/main/cpp/meter_core.h`).
Vehicle decision + rationale: [`../docs/adr/0001-ios-vehicle-shared-cpp-core-vs-kmp.md`](../docs/adr/0001-ios-vehicle-shared-cpp-core-vs-kmp.md)
(shared core + native SwiftUI, KMP rejected).

## Layout

```
ios/
  Bridge/
    meter_bridge.h / .cpp            C ABI over howler::MeterCore (the shipping bridge)
    HowlerMeter-Bridging-Header.h    exposes the C bridge to Swift
    test_host.cpp                    headless numeric check (runs on macOS, no Xcode)
  App/
    HowlerMeterApp.swift             @main entry
    MeterEngine.swift                AVAudioEngine tap → bridge, ObservableObject
    ContentView.swift                placeholder debug UI (real CRT/DSEG7 UI is held — see ADR)
  README.md (this file)
  .gitignore
```

The DSP header is **not copied** — the bridge `#include "meter_core.h"` resolves via a
Header Search Path to the Android tree. One header, both platforms.

## Headless check (do this anytime — no Xcode, no device)

```sh
cd ios/Bridge
clang++ -std=c++17 -O2 -Wall -I ../../app/src/main/cpp \
    test_host.cpp meter_bridge.cpp -o /tmp/howler_meter_test && /tmp/howler_meter_test
```

Expected: `PASS: meter_core.h ports clean + numerically sane via C bridge`.

## Create the Xcode project (the GUI part — ~10 min, one time)

Everything below is Xcode-GUI only; all the code already exists in `App/` and `Bridge/`.

1. **New project** → iOS → **App**. Product Name **`HowlerMeter`**, Interface **SwiftUI**,
   Language **Swift**, Organization Identifier `com.houseofyeti`. **Save it into `ios/`**
   (so the project sits at `ios/HowlerMeter.xcodeproj`). Uncheck "Create Git repository".
2. Xcode generates its own `HowlerMeterApp.swift` + `ContentView.swift` — **delete those
   generated two** from the project, then **add the real files** (drag from Finder,
   *Reference files in place*, add to the target):
   - `App/MeterEngine.swift`, `App/ContentView.swift`, `App/HowlerMeterApp.swift`
   - `Bridge/meter_bridge.h`, `Bridge/meter_bridge.cpp`, `Bridge/HowlerMeter-Bridging-Header.h`
3. **Bridging header:** Build Settings → *Objective-C Bridging Header* →
   `$(SRCROOT)/Bridge/HowlerMeter-Bridging-Header.h`
   (Xcode may also offer to create one when you add the `.cpp` + header — point it at this file.)
4. **Header Search Paths:** Build Settings → *Header Search Paths* → add
   `$(SRCROOT)/../app/src/main/cpp` (non-recursive). This is how `meter_bridge.cpp` finds
   `meter_core.h`.
5. **Mic permission:** target → Info → add key `NSMicrophoneUsageDescription` =
   `Howler measures sound level`. (Without it the app is killed on mic access.)
6. **Run** on an iPhone Simulator (Cmd+R). Allow the Simulator mic prompt (macOS) + the
   in-app prompt → tap **Start** → the debug readout tracks the Mac mic.

Commit `HowlerMeter.xcodeproj` (the `.gitignore` here excludes per-user + build cruft).

## Status / what's next

- ✅ Bridge + engine + placeholder app — this directory. Host check passes.
- ⏳ **Xcode project** — the 6 steps above (GUI, can't be scripted headless).
- ⏳ Calibration port (iOS mic offset → UserDefaults) — ADR step 2.
- ⏳ Real-device pass — hardware `.measurement` parity + calibration vs a reference.
- 🅗 **Held:** the real CRT/DSEG7 SwiftUI front end (ADR step 3) — until Android
  closed-testing feedback lands, so it's built once against a validated design.
