# Howler — feature summary

A decibel meter. Android first (Kotlin + Oboe/NDK), iOS via KMP later. This file
tracks what the app does today, what's deferred to the UI pass, and what's
deliberately out of v1 scope.

_Last updated: 2026-06-26._

## Status

Meter is **feature-complete and calibrated** on real hardware (Pixel 10 Pro XL).
UI is functional but unstyled — the next work item. iOS not started.

---

## Implemented

### Audio path
- Native **Oboe** input stream; **unprocessed** preset (AGC / noise-suppression /
  echo-cancellation off — required for honest SPL).
- Exclusive, low-latency, mono float.
- Opens at the device-**granted** sample rate (read dynamically, not hardcoded),
  so the DSP stays correct if a route grants 44.1 kHz instead of 48 kHz.
- RMS → dBFS per audio callback.

### Metering (IEC 61672)
- **Frequency weighting:** Z (flat), **A**, **C** — selectable, one active at a
  time. Biquad cascades derived by bilinear transform (no magic constants),
  normalized to 0 dB @ 1 kHz.
- **Time weighting:** Fast (125 ms) / Slow (1 s) exponential averaging.
- **Max-hold** (peak) — survives lifecycle stop/restart; includes clipped blocks
  as a lower bound on the true peak.
- **Min-hold** (quietest).
- **Leq** — equivalent continuous level (energy average since reset; independent
  of Fast/Slow).
- Shared **Reset** for Max/Min/Leq; auto-reset when weighting or time response
  changes (units change).
- **OVER** / over-range (clipping) indicator.

### Calibration
- Source **resolver**: tier-1 device sensitivity → tier-2 manual → uncalibrated.
- **Tier-2 manual single-point**, persisted across restart; implausible tier-1
  device values rejected.
- Honest accuracy caption: **≈ ±2 dB(A)** (gated on the reference meter's class).
- Calibrated result recorded — Pixel 10 Pro XL + BAFX ±1.5 dB, offset ≈ 126.2 dB
  (see `step-zero-results.md`).

### Robustness
- Stream restarts on visibility (`ON_START`/`ON_STOP`) — survives doze/lock;
  keeps running while paused-but-visible (split-screen).
- **Screen stays on** while the app is in front (`FLAG_KEEP_SCREEN_ON`), released
  automatically on background/close — you watch a meter, you don't tap it.
- Background polling halts (no battery drain when hidden).
- Mic-permission flow (RECORD_AUDIO).
- Weighting + time response persist across rotation.
- Stats reset is thread-safe (atomic request flag, applied on the audio thread).

### Dev tooling (not user-facing)
- `DeviceProbe` / `InputProbe` — the STEP ZERO device probes.

---

## Deferred to the UI pass

| Item | Note |
|------|------|
| **UI / layout redesign** | Current screen is a bare centered number + greyed-button toggles + stats row. Needs a chosen visual direction (minimal vs gauge/zones). |
| **Mic-busy error / retry state** | If another app holds the mic on resume, the meter shows a bare "failed to open" with no recovery. Needs a retry / recoverable state. _(review finding #6)_ |
| **Clipped-Max flag** | Max captures clipped blocks but doesn't visually mark them as "≥, clipped". |
| **Toggle affordance** | A/C/Z and Fast/Slow use enabled/disabled TextButtons (active reads as greyed). Replace with a real segmented control. |

---

## Considered — out of v1 scope

Decide later; not blocking v1.

- **History graph / level-over-time**
- **Hold / freeze** display
- **Dose / exposure time** (OSHA/NIOSH), **SEL**
- **Ln percentiles** (L10 / L50 / L90)
- **Octave-band** frequency analysis
- **Logging / export**

---

## Build & verify

Toolchain (no PATH config; system `java` is 8, unusable for AGP):

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME=~/Library/Android/sdk
./gradlew :app:assembleDebug :app:testDebugUnitTest
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
