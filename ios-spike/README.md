# iOS build spike

Goal: prove the shared DSP (`../app/src/main/cpp/meter_core.h`) runs on iOS,
fed by an AVAudioEngine input tap. This is a **throwaway derisk**, not the real
port — it answers one question before we commit to a vehicle (KMP vs shared C++
core + native UIs): *does the exact Android DSP produce real dB on iOS?*

## What's here

| file | role | verified |
|------|------|----------|
| `meter_bridge.h/.cpp` | C ABI over `howler::MeterCore` (Swift can't call C++ classes portably) | ✅ host-compiled |
| `test_host.cpp` | headless numeric check through the bridge | ✅ passes on macOS |
| `MeterSpike.swift` | AVAudioEngine input tap → bridge → prints dB | ⏳ needs device + Xcode |

The bridge `#include`s `meter_core.h` from the Android tree directly — one header,
both platforms. Nothing is copied.

## Headless host check (no Xcode, no device — run this now)

```sh
cd ios-spike
clang++ -std=c++17 -O2 -Wall test_host.cpp meter_bridge.cpp -o /tmp/howler_meter_test
/tmp/howler_meter_test
```

Expected:

```
Z level = -9.03 dBFS (expect -9.03)
A level = -9.03 dBFS (expect -9.03)
over=1 maxClipped=1
PASS: meter_core.h ports clean + numerically sane via C bridge
```

This proves the DSP is portable stdlib C++ (no Oboe/JNI/android leakage) and
numerically correct. The iOS target differs only by SDK sysroot + target triple.

## On-device build (needs full Xcode.app — Command Line Tools is not enough)

Command Line Tools has no `iphoneos` SDK (`xcrun --sdk iphoneos` errors). To run
`MeterSpike.swift` on a phone:

1. Install **Xcode.app**, then `sudo xcode-select -s /Applications/Xcode.app`.
2. New Xcode project → iOS App (SwiftUI or Storyboard, does not matter).
3. Add to the target: `meter_bridge.h`, `meter_bridge.cpp`, `MeterSpike.swift`.
   Set the file type of `meter_bridge.cpp` to Objective-C++ / C++ so it sees
   `meter_core.h`. Add `$(SRCROOT)/../app/src/main/cpp` (adjust) to **Header
   Search Paths**, or copy `meter_core.h` beside the bridge for the spike.
4. Bridging header: create `Spike-Bridging-Header.h` with
   `#import "meter_bridge.h"` and set it as **Objective-C Bridging Header**.
5. **Info.plist:** add `NSMicrophoneUsageDescription` ("Howler measures sound
   level"). Without it the app is killed on mic access.
6. Call `try MeterSpike().start()` from your app's `onAppear` / `viewDidLoad`,
   run on a wired device, watch the Xcode console for live dB.

## What a green on-device run proves

- `meter_core.h` compiles for `arm64-apple-ios` unchanged.
- AVAudioEngine `.measurement` mode is a workable analog of Oboe
  `InputPreset::Unprocessed` (the raw, un-AGC'd input the meter needs).
- Same three platform hooks — `configure(fs)` / `process(samples,n)` /
  `onStopped()` — carry the whole DSP on both platforms.

If that holds, the vehicle decision (KMP vs shared-C++-core + SwiftUI) is about
UI reuse only, not audio risk — which is the point of running the spike first.
