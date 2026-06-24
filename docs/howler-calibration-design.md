# Howler — calibration design (design point 1)

_Captured 2026-06-23 from the Howler design run. Drop into the Howler repo (docs/), and
mirror the "#11 carry-forward" section into claude-workbench #11's entry._

## Decision: no static profile table — build a calibration resolver

A per-device offset table keyed on phone model is the wrong architecture, and it's the
approach the authorities abandoned:

- NIOSH evaluated 192 SLM apps and only validated on **iOS**; they state verifying an
  Android app's accuracy is not currently feasible, due to device fragmentation.
- Microphone sensitivity varies **unit-to-unit even within the same model**, so a
  model-name lookup is wrong even when it "matches." (NIOSH guidance: calibrate each device
  individually.)

So Howler does not ship or maintain an offset table. It ships a **resolver** that picks the
best available calibration source per device.

## The calibration math

`getSensitivity()` returns the level in dBFS produced by a 1000 Hz tone at 94 dB SPL
(94 dB is the standard acoustic reference level). Therefore:

    SPL = measured_dBFS + (94 - sensitivity)

dBFS scales 1:1 with dB SPL (both are 20·log10 ratios), so a single offset is the whole
mapping at a given level.

## Resolver — priority order

1. **`AudioManager.getMicrophones()` → `MicrophoneInfo.getSensitivity()`** (API 28+), *if
   populated*. Zero-touch: the device reports its own offset. Best case.
2. **Manual single-point** against a reference SPL meter (Lorenzo has one). Measure a steady
   source on both, store `offset = reference_SPL - measured_dBFS`, keyed to a device profile.
   The reliable everyday path.
3. **Uncalibrated** — relative dBFS only; UI must say so. Always available, never lies.

The resolver (source selection + device-profile storage) is the **load-bearing transferable
asset**, not the metering code blob. Build it correct; keep the rest cheap.

## BUILD STEP ZERO (resolve a real decision, not a guess)

Two probes on the actual target device, same trip:

**Probe A — unprocessed input (do this first; it's the load-bearing one).** `AudioSource.UNPROCESSED`
is API 24+ but **not universally supported** — gated on
`AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)`. Without an unprocessed
source, AGC/NS/AEC float every reading and calibration is meaningless *regardless of tier* — so this
unknown outranks the sensitivity number. Fallback ladder: `UNPROCESSED` →
`VOICE_RECOGNITION` (least-processed guaranteed) → flag the reading as degraded in the UI. The probe
decides which rung this device lands on.

**Probe B — `getSensitivity()`.** OEM population is inconsistent — many devices return an
unknown/placeholder. The result decides whether tier 1 is real here or whether tier 2 (manual) is
the de facto path. Caveat: `getSensitivity()` is the transducer spec and may **not** describe digital
gain downstream in the AudioRecord path — so tier 1 can be populated *and* wrong. Tier 2 (reference
meter) is the real validator. Do not design the UI flow around tier 1 until the probe confirms a real
value *and* it agrees with the reference meter.
*(Bias flagged in design: the getSensitivity() API is elegant and easy to overweight;
realistically tier 2 manual is likely the workhorse. Build the manual path first regardless.)*

## Honest accuracy ceiling (UI-honesty decision, not just a number)

- Calibrated **internal** phone mic: ≈ ±2 dB(A) vs a type-1 reference (NIOSH).
- Only with an **external calibrated** mic do you reach ≈ ±1 dB.
- Howler's truthful claim on the internal mic is "≈ ±2 dB(A) when calibrated," never better.
  The signal path also requires the **unprocessed input** (AGC/NS/AEC off), or readings
  float and every number is meaningless — this is the cheap beginner-mistake lesson to get
  right here.
- **The ±2 dB claim is gated on the reference meter's class.** Tier-2 manual calibration
  inherits the reference's own error — calibrate against a ±3 dB hobby meter and the honest
  claim is no longer ±2. Pin the on-hand meter's spec/class before quoting an accuracy number.

## Clip / over-range (v1 scope)

The meter maxes at 0 dBFS. A source louder than full-scale **clips and reads low**, silently — a
quiet lie at exactly the moment the number matters most (app is named *Howler*; loud is the use
case). Detect samples at/near full-scale and surface an **"OVER" / over-range** indicator like a
real SLM, rather than reporting a confidently-wrong low number. In v1 scope, not deferred.

## Locked design-point-1 decisions

1. Calibration model: c-shaped, single-point default, **keyed to a device profile** (one
   profile in v1). Resolver chooses the source.
2. Weighting: A + Z in v1.
3. Reference: on-hand SPL meter → tier 2 manual is feasible from day one; Howler ships
   calibrated, not relative-only.

## #11 carry-forward (field recording / IR capture)

- The **calibration-source resolver** transfers directly — #11 needs device calibration for
  IR-capture level calibration, same per-device-variance problem.
- First contact with the **`MicrophoneInfo` audio-device API** happens here, on the small
  surface — the explicit onramp purpose. #11 inherits the learning, not a code blob.
- The phone-specific **offset value** does NOT transfer (different device/unit); the
  resolver architecture does.
