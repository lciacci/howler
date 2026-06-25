# Howler — STEP ZERO probe results

Resolves the two decisions the calibration design doc ranks above metering code.
See `docs/howler-calibration-design.md` § "BUILD STEP ZERO".

**Device:** Google Pixel 10 Pro XL · Android 16 · API 36
**Date:** 2026-06-24
**Method:** on-device probe (`app/.../audio/DeviceProbe.kt`, `InputProbe.kt`), captured via Logcat.

## Probe A — unprocessed input (load-bearing)

- `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` → **false** (the unreliable hint).
- **Authoritative open test → UNPROCESSED opens and records** (48000 Hz, mono, PCM_FLOAT, 480 frames read). VOICE_RECOGNITION also opens.
- **Verdict: rung = UNPROCESSED.** The property's `false` was a false negative — confirms the doc's caveat that the property is unreliable and the actual `AudioRecord` open is the real test. Calibration is meaningful on this device (the AGC/NS/AEC-off path exists).
- **Native config locked:** `InputPreset::Unprocessed`, 48000 Hz, mono, float; framework burst 240 frames/buffer.

## Probe B — getSensitivity()

- `builtin_mic_1` → **+37.0 dBFS** (physically impossible — 37 dB above full scale), `builtin_mic_2` → −37.0 dBFS, two peripheral mics unpopulated.
- **Verdict: tier-1 is populated-but-wrong.** Exactly the doc's warning ("transducer spec ≠ AudioRecord digital gain"). **Tier-2 manual single-point calibration against the on-hand reference meter is the path.** Do not build UI around tier-1.

## Consequences for the build

1. Oboe input stream: unprocessed preset, 48k/mono/float — no fallback rung needed on this device.
2. Calibration resolver ships with tier-2 (manual) as the primary source; tier-1 `getSensitivity` is recorded but flagged untrusted.
3. OVER/over-range + A/Z weighting per design-point-1 still apply.

## Carry-forward (per design doc § #11)

Mirror to claude-workbench #11: first contact with the `MicrophoneInfo` / `AudioRecord`-source
APIs happened here. The resolver architecture transfers; the device-specific values do not.
(Not yet transferred — staged here.)

---

# Tier-2 manual calibration result

**Date:** 2026-06-25 · **Device:** Pixel 10 Pro XL · **Weighting/time at cal:** A / Slow
**Reference meter:** BAFX Advanced Sound Level Meter, ±1.5 dB (dBA/dBC), set to dBA.
**Method:** steady 1 kHz tone through a speaker, phone mic + BAFX mic side-by-side, equal
distance. 1 kHz chosen so A=Z=C — one point calibrates both Howler weightings (per design doc).

**Linearity pre-check (two points, before saving):**

| Point | Howler (dBFS) | BAFX (dB SPL) | offset = SPL − dBFS |
|-------|--------------:|--------------:|--------------------:|
| 1     |        −68.2  |        58.2   |              126.4  |
| 2     |        −56.6  |        69.4   |              126.0  |

ΔHowler +11.6 vs ΔBAFX +11.2 → tracks 1:1 within **0.4 dB**; offsets agree within 0.4 dB.
Linear over the tested range → single-point tier-2 is valid here. **Stored offset ≈ 126.2 dB.**

**Post-save verification:** Howler 68.5 vs BAFX 69.0 → **0.5 dB** (inside BAFX's ±1.5).
Survives app restart (persisted in `CalibrationStore`).

**Honest accuracy claim:** gated on the BAFX's ±1.5 dB class → Howler's truthful claim is
**≈±2 dB(A)** (reference error + phone path), never better. This is what the UI states.

**Does not transfer** (per design doc): the 126.2 dB offset is specific to this unit; the
resolver + procedure transfer, the number does not.
