# Howler — session handoff

How to pick this repo up cold. Pair with `CLAUDE.md` (conventions) and
`FEATURES.md` (what's built). Git is the source of truth; everything below is the
stuff git alone won't tell you.

## Status (2026-07-05)

**In closed testing on Google Play — 14-day clock running.** 12 testers enrolled;
the 14-day closed-testing period (Play's gate before you can apply for production
access) is counting down. Opt-in URL in Play Console → Testing → Closed testing.
Nothing to ship on Android until the clock clears — then apply for production.

**1.0.2 (versionCode 3) uploaded — in-app help reachable.** The first-run dialog
(accuracy + calibration guidance, LEARN MORE link) was only reachable once, on first launch —
no way back to it. Added a `?` next to the OVER indicator that reopens it (`2538668`).
AAB at `app/build/outputs/bundle/release/app-release.aab`.

1.0.1 (versionCode 2, Jun 30) shipped the crash-on-open fix: package rename (`7b0feaf`) left the
C++ JNI symbols as `com_example_howler` → `UnsatisfiedLinkError` on `nativeStart()`, fixed in
`c7c6335` (also Oboe 1.9.0→1.10.0 + 16KB-page link flag). See FINDINGS F-004.

- Package: `com.houseofyeti.howler`
- Version: 1.0.2 (versionCode 3)
- Signed with upload keystore at `~/howler-upload.keystore` (keep safe — not in repo)
- Signing config reads from `keystore.properties` (gitignored) in repo root

**What shipped:**
- Meter: native Oboe input, unprocessed; Z/A/C weighting, Fast/Slow, Max/Min/Leq,
  OVER, tier-2 manual calibration. See `FEATURES.md`.
- UI: CRT amber-phosphor screen — head + reactive backlight glow, DSEG7 LED
  readout, segmented controls, OVER/clipped-Max/mic-busy states, phosphor dialog.
- First-run calibration notice dialog (SharedPreferences flag `first_run_seen`),
  reopenable anytime via the `?` by the OVER indicator.
- Howler dark-variant adaptive launcher icon.
- All verified on a physical **Pixel 10 Pro XL** (API 36).

**Web (houseofyeti.com/howler/):**
- `index.html` — help page (calibration first, all features covered)
- `privacy.html` — privacy policy (effective 2026-06-27)
- Both cross-link; privacy URL submitted to Play Console.

**Next:** gather feedback from closed testers, then promote to production once the
14-day clock clears.

### iOS — spike in progress (derisking the audio path)

Decision on record: **don't commit to KMP yet.** Howler's shared surface is the C++
DSP, not Kotlin — so before picking a vehicle (KMP vs shared-C++-core + native UIs)
we derisk the one real unknown: does the exact Android DSP run on iOS?

- **DSP split — done (`eb1daad`).** `audio_engine.cpp` was Oboe I/O + DSP welded
  together. Pulled all platform-agnostic DSP (Biquad, WeightingFilter, MeterCore) into
  `app/src/main/cpp/meter_core.h` — pure stdlib C++, zero Oboe/JNI/android headers.
  `audio_engine.cpp` is now the Android I/O + JNI shell wrapping a `MeterCore`. JNI
  symbols byte-identical (F-004 class avoided). No behavior change; verified live on the
  Pixel (30.4 dB(A), Max/Min/Leq tracking). Three platform hooks carry everything:
  `configure(fs)` / `process(samples,n)` / `onStopped()`.
- **iOS spike scaffold — done (`9d02cb1`), in `ios-spike/`.** C-shim bridge
  (`meter_bridge.{h,cpp}`) wraps `MeterCore` behind a flat C ABI and `#include`s
  `meter_core.h` from the Android tree directly — one header, both platforms, nothing
  copied. `test_host.cpp` proves the header ports to plain `clang++` and is numerically
  exact (Z/A −9.03 dBFS, over-range + clip flags). `MeterSpike.swift` feeds it from an
  AVAudioEngine input tap (`.measurement` mode = iOS analog of Oboe `Unprocessed`).
- **Compiles against the real iOS SDK — done (Xcode 26.5, headless).** Both halves build
  for `arm64-apple-ios`, no device:
  - host numeric check: `cd ios-spike && clang++ -std=c++17 -O2 -Wall test_host.cpp
    meter_bridge.cpp -o /tmp/t && /tmp/t` → `PASS` (Z/A −9.03 dBFS).
  - iOS C++ compile: `SDK=$(xcrun --sdk iphoneos --show-sdk-path); xcrun --sdk iphoneos
    clang++ -std=c++17 -target arm64-apple-ios15.0 -isysroot "$SDK" -c meter_bridge.cpp -o
    /tmp/o` → arm64 Mach-O object.
  - Swift tap type-check: `xcrun --sdk iphoneos swiftc -typecheck -target arm64-apple-ios15.0
    -sdk "$SDK" -import-objc-header meter_bridge.h MeterSpike.swift` → exit 0.

  **Toolchain + compile risk is dead.** `meter_core.h` builds unchanged for iOS; the
  AVAudioEngine glue type-checks against the bridge.

- **Runs live in the iOS Simulator — done (Mac mic).** Wired the spike into a SwiftUI app
  (Start button → `MeterSpike().start()`), ran in the simulator, which feeds the Mac's mic to
  AVAudioEngine. The console tracks sound live: quiet room ~−75 dB(A), speech climbed to −21,
  decayed back; `max`/`min`/`Leq` held correctly, `over=0` at speech levels, Stop floored the
  live readout via `onStopped()`. The exact Android DSP runs unchanged on iOS.

  **Audio risk is dead. The KMP-vs-native decision is now UI-only.**

**Only remaining iOS check (optional, low-risk):** a real-device run to confirm hardware
`.measurement`/unprocessed-input parity (the simulator uses macOS CoreAudio, not the phone's
raw mic path). Not a blocker for the vehicle decision — the pipeline and DSP are proven. When
wanted: `ios-spike/README.md` → same project, wired iPhone, free Apple ID signs it, trust the
dev cert on the phone.

**iOS vehicle — decided (`adr/0001-ios-vehicle-shared-cpp-core-vs-kmp.md`):** shared C++ DSP
core + **native SwiftUI**, KMP rejected. Rationale: the costly shared surface (DSP) is already
C++ and proven portable; KMP's upside is thin for a single-screen app with bespoke visuals, and
its cost lands on a shipping Android artifact. No Android build changes.

**iOS foundation — done (`27d935e`), in `ios/`.** The spike graduated to a real in-repo target:
`ios/Bridge/` (shipping C shim `meter_bridge.{h,cpp}` finding `meter_core.h` via header search
path + `test_host.cpp` headless check), `ios/App/` (`MeterEngine` ObservableObject, placeholder
debug view, `@main` entry). Verified: host test PASS, C++ compiles for `arm64-apple-ios`, all
Swift type-checks (Xcode 26.5). `ios-spike/` removed.

**Next iOS work (all feedback-independent, none blocked by the Android trial):**
1. **Create `HowlerMeter.xcodeproj`** — the one-time Xcode-GUI step (6 steps in `ios/README.md`;
   can't be scripted headless). Then it builds + runs in the Simulator.
2. **Calibration port** — iOS mic offset → UserDefaults (ADR step 2).
3. **Real-device pass** — hardware `.measurement` parity + calibrate vs a reference; needs a
   physical iPhone.
- **Held:** the real CRT/DSEG7 SwiftUI UI (ADR step 3) until closed-testing feedback lands.

Parked out-of-v1 extras (unchanged): history graph, dose, Ln percentiles, octave bands, export.

## Toolchain

Installed but not on PATH; system `java` is 8 (breaks AGP). Each session:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
ADB="$ANDROID_HOME/platform-tools/adb"
```

## Dev / verify loop

The pattern used all session — build, flash, screenshot, look:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am force-stop com.houseofyeti.howler
$ADB shell am start -n com.houseofyeti.howler/.MainActivity
$ADB exec-out screencap -p > shot.png      # then Read shot.png
```

- If the image tool **rejects a large PNG**, downscale: `sips -Z 1400 shot.png --out small.png`.
- To tap a control: `$ADB shell input tap <x> <y>` (screencap is 1080x2404).
- Check focus / that the app is foreground: `$ADB shell dumpsys window | grep mCurrentFocus`.

## Gotchas that cost time

- **Device sleeps mid-verify.** The app now holds the screen on while foreground
  (`FLAG_KEEP_SCREEN_ON`), but if it's backgrounded the device can lock and the
  next `screencap` catches the lock screen. The phone unlock is the user's
  (fingerprint) — ask them.
- **Clip / OVER states need ~126 dB acoustic** (0 dBFS, with the ~126 dB
  calibration offset). You cannot reach digital clip by shouting. To verify
  OVER-red / clipped-Max `≥` / the mic-busy retry screen, **force-test**: drop
  `kOverThreshold` in `audio_engine.cpp`, or `return JNI_FALSE` from
  `nativeStart`, screenshot, then **revert** (grep `TEMP` before committing).
- **Reactive glow** is driven by the displayed SPL via `smoothstep(50, 88, level)`
  — invisible below ~55 dB, so it won't show in a quiet room screenshot. It was
  confirmed live at 97 dB(A).
- **Calibration persists on-device** in `CalibrationStore` (offset ≈ 126 dB,
  Pixel 10 Pro XL + BAFX ±1.5). Reinstalls keep it; `CLEAR` in the dialog removes it.
- **Huge single-file writes can trip an output content filter.** If a write gets
  "blocked by content filtering policy," split it into smaller edits.

## Where things live

- Native engine + DSP: `app/src/main/cpp/audio_engine.cpp` (single active
  weighting; stats reset via an atomic flag applied on the audio thread).
- JNI bridge: `app/src/main/java/com/houseofyeti/howler/audio/AudioEngine.kt`. The JNI
  exports in `audio_engine.cpp` must mirror this package (`Java_com_houseofyeti_howler_...`) —
  a rename that misses the C++ symbols compiles fine but crashes on open (see FINDINGS F-004).
- UI + state: `app/src/main/java/com/houseofyeti/howler/MainActivity.kt`.
- Phosphor palette: `ui/theme/Color.kt`. LED font + license: `res/font/`, `assets/`.
- Head/glow assets: `res/drawable-nodpi/howler_{head,glow}.png`. Current art is a
  roaring head dropped in as PNGs directly (the earlier `design/` SVG comp is no
  longer the source). On API 31+ the head PNG drives the glow, black mask, and
  phosphor face via `SrcIn`, so swapping the head needs no code change; `howler_glow.png`
  is only the API<31 pre-blurred fallback. Same 900x1209 dims keep `HEAD_RATIO` valid.
- Weighting spec-guard test: `app/src/test/.../AWeightingTest.kt` (asserts the A
  and C analog response vs the IEC 61672 tables — the native filter can't run
  host-side, so this guards the corner-frequency constants).

## Dogfooding

This is the Tessera dogfood project. Capture framework friction in
`docs/FINDINGS.md` (runtime) or `docs/SCAFFOLD-NOTES.md` (setup); fix in
`../tessera` only when asked. Suggestion-gates get logged via
`scripts/gate/emit.py`.
