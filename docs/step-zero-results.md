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
