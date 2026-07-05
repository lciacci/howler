# ADR-0001 — iOS vehicle: shared C++ core + native UIs (not KMP)

- **Status:** Accepted
- **Date:** 2026-07-05
- **Deciders:** Lorenzo (with Claude Code)
- **Supersedes:** the informal "iOS via KMP later" assumption in FEATURES.md / early README.

## Context

Howler is a single-screen decibel meter, shipping on Android (closed testing,
14-day clock running). The original plan named **Kotlin Multiplatform (KMP)** as
the iOS vehicle. Before committing to it we ran a build spike (`ios-spike/`, ADR
input below) to replace assumption with data.

Two facts reframe the decision:

1. **The shared surface that carries real cost is the C++ DSP, not Kotlin.** All
   the hard, correctness-critical code — IEC 61672 weighting filters, RMS→dBFS,
   Fast/Slow smoothing, Max/Min/Leq, clip detect — lives in
   `app/src/main/cpp/meter_core.h`, pure stdlib C++. The Kotlin layer is a thin
   JNI wrapper + a one-screen UI state model.
2. **The UI is bespoke and per-platform by nature.** CRT amber-phosphor screen,
   DSEG7 LED readout via a bundled font, reactive backlight glow, head-PNG
   `SrcIn` compositing, segmented controls. This is custom canvas/shader/asset
   work with near-zero cross-platform reuse *even under KMP* unless we also adopt
   Compose Multiplatform — which is exactly the riskiest thing to port.

### Spike evidence (all done, 2026-07-05)

- DSP split into platform-agnostic `meter_core.h` (commit `eb1daad`), verified
  live on the Pixel — no behavior change.
- C-shim bridge `ios-spike/meter_bridge.{h,cpp}` wraps `MeterCore` behind a flat
  C ABI; `#include`s the Android-tree header directly (one header, both
  platforms, nothing copied). Commit `9d02cb1`.
- Compiles unchanged for `arm64-apple-ios` (Xcode 26.5 SDK); Swift AVAudioEngine
  tap type-checks against the bridge (commit `6a6de8e`).
- **Runs live in the iOS Simulator** feeding the Mac mic through the shared DSP:
  tracks sound (quiet ~−75 dB(A), speech −21), `max/min/Leq` correct, `over` and
  `onStopped()` behaving (commit `1704f2f`).

So the audio path — the only genuine iOS unknown — is proven. The remaining
choice is purely about **UI/logic reuse**.

## Options

### A. Shared C++ core + native UIs (Compose on Android, SwiftUI on iOS) — CHOSEN
The DSP is shared as a plain C++ header behind a thin bridge (JNI on Android,
C-shim on iOS — both already exist and work). Each platform's UI is native.

- **+** This is the architecture the spike already validated end-to-end.
- **+** Zero new build-system or toolchain risk; the shipping Android module is
  untouched (it's on the production track — don't destabilize it).
- **+** Platform-idiomatic UI tooling; the bespoke visuals get written in the
  tool each platform actually renders well.
- **−** The one-screen UI state (toggles, calibration store, first-run dialog,
  OVER states) is written twice. Small surface; acceptable.

### B. Kotlin Multiplatform (shared Kotlin logic; Compose MP or SwiftUI on top)
Share the Kotlin meter-state/calibration layer across platforms; DSP still C++
underneath via cinterop (iOS) / JNI (Android). Optionally share the UI via
Compose Multiplatform.

- **+** Share the Kotlin logic layer once; with Compose MP, potentially the UI.
- **−** Large new build-system + toolchain cost: KMP Gradle restructure, iOS
  framework packaging, cinterop for the C++ bridge, SPM/CocoaPods glue — landing
  on a **shipping** Android app.
- **−** The reuse payoff is thin: the shared Kotlin logic is a single screen's
  worth of state.
- **−** Compose Multiplatform on iOS is still maturing; the CRT/DSEG7/glow
  visuals (custom drawing + bundled font + PNG `SrcIn`) are the highest-risk
  things to port to it — the reuse we'd be buying is the reuse least likely to
  hold.

## Decision

**Option A — shared C++ core + native UIs.** The costly shared surface (DSP) is
already C++ and already proven portable. KMP's upside (shared Kotlin/UI) is small
for a single-screen app with bespoke visuals, and its cost is high and falls on a
production Android artifact. Native SwiftUI on iOS, keep Compose on Android, both
over the same `meter_core.h`.

## Consequences

- iOS work = a fresh SwiftUI app over the existing C bridge; no changes to the
  Android build. `ios-spike/` becomes the seed of the real iOS target.
- Two UI state layers to keep in sync (Android Compose, iOS SwiftUI). Keep the
  shared contract in the C bridge surface (`meter_bridge.h`), which both mirror.
- The `meter_bridge.{h,cpp}` C-shim graduates from spike to shipping bridge.

## Re-evaluate if

- Howler grows **multi-screen** or gains substantial non-DSP shared logic
  (history graph, dose tracking, export, cloud sync) — then shared Kotlin starts
  to pay for itself.
- Compose Multiplatform on iOS matures **and** the visual system gets simpler.
- Maintaining two UI state layers actually produces drift bugs in practice (watch
  for it; it's the main cost of this choice).
