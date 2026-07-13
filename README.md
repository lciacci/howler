# Howler

A sound level (decibel) meter. Native Android app today; the DSP core is
proven running on iOS as well, with the SwiftUI front end still to come.

Live help / privacy pages: https://houseofyeti.com/howler/

## What it does

- Real-time SPL readout from unprocessed microphone input (AGC, noise
  suppression, and echo cancellation disabled — those "enhancements" distort
  a measurement).
- IEC 61672-style frequency weighting: **Z** (flat), **A**, **C** — one tap to
  switch.
- Time weighting: **Fast** (125 ms) / **Slow** (1 s) exponential averaging.
- **Max** hold, **Min** hold, and **Leq** (equivalent continuous level), with
  a shared reset.
- **OVER** (clipping) indicator; Max is flagged when the recorded peak came
  from a clipped block, since the true level was higher.
- Single-point manual calibration against a reference meter, persisted
  on-device (~±2 dB(A) once calibrated; uncalibrated readings are relative,
  not absolute).
- CRT amber-phosphor themed UI (Jetpack Compose): reactive backlight glow,
  DSEG7 LED-style readout, segmented A/C/Z and Fast/Slow controls.

Howler is not a certified Type 1/2 sound level meter and isn't intended for
regulatory or occupational-compliance measurements.

## Status

Android is in Google Play closed testing (v1.0.2), calibrated and verified on
physical hardware.

iOS has its hardest risk retired: the same C++ DSP core runs live on a
physical iPhone/iPad through a thin C bridge and an AVAudioEngine input tap,
with calibration ported and unit-tested. The native SwiftUI front end is
deliberately held until feedback from Android testers lands, so it's built
once against a validated design — see `docs/adr/0001-ios-vehicle-shared-cpp-core-vs-kmp.md`.

Parked, out of scope for v1: history graph, dose/exposure tracking, Ln
percentiles, octave-band analysis, export.

See `docs/FEATURES.md` for the full feature breakdown and `docs/HANDOFF.md`
for current session-to-session status.

## Architecture

- **Android app** (`app/`) — Kotlin + Jetpack Compose UI. Native audio engine
  in C++ (`app/src/main/cpp/audio_engine.cpp`) using
  [Oboe](https://github.com/google/oboe) for low-latency, unprocessed mic
  input, wrapped in JNI (`app/src/main/java/com/houseofyeti/howler/audio/AudioEngine.kt`).
- **Shared DSP core** (`app/src/main/cpp/meter_core.h`) — platform-agnostic
  C++ (biquad weighting filters, level tracking) with no Android/Oboe/JNI
  dependency. The iOS bridge includes this header directly; nothing is
  duplicated between platforms.
- **iOS** (`ios/`) — Xcode project. `meter_bridge.{h,cpp}` exposes the shared
  core over a flat C ABI to Swift; `MeterEngine`/`Calibration.swift` drive it
  from an AVAudioEngine tap. See `ios/README.md` for build config and the
  headless checks in `ios/checks/` (no Xcode or device required).

## Build & run

Android:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

iOS: open `ios/HowlerMeter/HowlerMeter.xcodeproj` in Xcode, or run the
headless DSP/calibration checks described in `ios/README.md`.

## Docs

- `docs/FEATURES.md` — what's implemented, what's deferred
- `docs/HANDOFF.md` — status and how to pick the repo up cold
- `docs/adr/` — architecture decisions

## Dogfooding

Howler is also the first real dogfood project for [Tessera](https://github.com/lciacci/tessera),
an AI-agent development framework — see `CLAUDE.md` for repo conventions and
`docs/FINDINGS.md`/`docs/SCAFFOLD-NOTES.md` for the framework friction it surfaced.
