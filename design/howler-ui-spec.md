# Howler — meter screen UI spec

Handoff for the Compose UI pass. The meter (audio path, IEC 61672 weighting, calibration,
Max/Min/Leq, OVER) is already feature-complete per `FEATURES.md`; this covers **look, layout,
and the one reactive behavior**. The two SVG comps (`howler_meter_final.svg`) are the visual target.

Platform: native Android, Kotlin + Jetpack Compose. Min SDK per project; runtime blur path
below assumes API 31+ with a fallback noted.

---

## 1. Visual language — CRT amber phosphor on near-black

Color tokens (use these literal values; they are what the comp was built from):

| Role | Hex |
|------|-----|
| Screen background | `#080400` |
| Readout / active text | `#FFC23A` |
| Label text (MAX/MIN/Leq, sublabel) | `#9A6F22` / `#C98A2E` |
| Caption (calibration) | `#8A6320` |
| Toggle — active fill / border | `#3A2600` / `#FFB02E` |
| Toggle — inactive border / text | `#6E4A0F` / `#A9781F` |
| Glow (reactive backlight) | `#FF961C` (rgb 255,150,28) |
| OVER / danger — bg / border / text | `#3A0E06` / `#FF4326` / `#FF5B3A` |
| Scanline | `#000000` @ ~0.42 alpha, 3px vertical pitch |

Type: monospace throughout. **The hero number should be a 7-segment / 14-segment LED face in
production** — the comp uses plain monospace as a stand-in and it reads worse at size. Labels stay
terminal-mono. Two type roles only: hero readout, and mono labels/controls.

Scanlines: a 3px-pitch horizontal-line overlay across the whole screen at low alpha. Subtle, not a gimmick.

---

## 2. The howler head — constant brand element (does NOT react)

- Source: the cleaned `howler1.svg`, recolored to an **amber phosphor duotone**, internal modeling preserved.
- **Brightness is fixed** at ~33% over black. It does not brighten or dim with level. (Knob A below.)
- Recolor recipe (so the asset can be regenerated, or ship the pre-tinted asset):
  luminance → amber ramp, dark `#140800` → bright `#FFB62A`, gamma ≈ 0.85.
- Placement: centered behind the readout, inset from the screen edges (do not let it kiss the edge —
  that's what produced the earlier stray-tuft artifact).
- Compose: a constant-alpha layer (Image from the tinted asset, or a tinted VectorDrawable) drawn
  behind the readout, above the screen background and below the scanline overlay.

---

## 3. Reactive edge glow — the ONE reactive channel

This is the only thing on the screen that moves with sound.

- An amber glow that emanates **from behind the head, around its outer edges only**. The head interior
  never glows — the glow is a perimeter halo that spills outward from the silhouette edge.
- Driven by the same SPL value that feeds the readout. Both **alpha and spread** increase with level.
- Curve: **eased, not linear** (Knob C). Near-zero through normal room levels, swells through the upper
  range, full at/above the NIOSH threshold. Suggested:
  - `t = smoothstep(FLOOR_DB, CEIL_DB, spl)` with `FLOOR_DB ≈ 50`, `CEIL_DB ≈ 88`
  - `glowAlpha = t * 0.4` (0 → ~0.4) — **kept deliberately subtle; a hint, not a flare**
  - `glowSpread`: blur radius from `r0 ≈ 6dp` to `r1 ≈ 12dp` as `t` goes 0 → 1
- Resting floor: **~0** (edges dark) (Knob B). The head + scanlines carry resting brand presence; the
  glow is purely the loudness cue.
- Implementation, two options:
  1. **Runtime blur** (API 31+): a silhouette layer (amber fill of the head shape) behind the head,
     with `RenderEffect.createBlurEffect(r, r, MIRROR)` where `r = glowSpread`, and layer alpha
     `= glowAlpha`. The head on top masks the interior so only the outer halo shows.
  2. **Precomputed bitmap** (fallback / simpler): ship the edge-glow ring PNG (already generated —
     blurred silhouette with interior erased). Animate its alpha with `glowAlpha`; approximate spread
     by scaling it slightly or compositing a second, softer copy at the top end.
- Optional, your call (Knob D): warm the glow hue toward red as `spl` approaches the threshold
  (lerp `#FF961C → #FF4326` over ~75–85 dB) so the *color of the air* foreshadows danger before OVER
  trips. Default: stay amber; OVER carries red alone.

---

## 4. Readout

- Large centered SPL number, **constant brightness** (never level-driven).
- **Always one decimal**, decimal point always present: format `"%.1f"`. (e.g. `45.5`, `92.4`.)
- Sublabel under it: `dB(<weighting>) · <fast|slow>` reflecting the active weighting and time response.
- Keep the readout the dominant element — the screen stays on (`FLAG_KEEP_SCREEN_ON`), this is a
  watch-not-tap surface, so the number can be big and the controls can recede.

---

## 5. Controls — replace the placeholder TextButtons

- **Weighting**: 3-cell segmented control `A | C | Z`, one active. Active cell = fill `#3A2600`,
  border `#FFB02E`, text `#FFC23A`; inactive = border `#6E4A0F`, dim text. (Resolves the
  "active reads as greyed" review finding — active should read *brighter*, not enabled-vs-disabled.)
- **Time response**: 2-cell segmented `FAST | SLOW`, same active/inactive treatment.

---

## 6. OVER / over-range — the lone red

- Binary indicator, lit on clip / over-range. Lit: bg `#3A0E06`, border `#FF4326`, text `#FF5B3A`.
  Off: dim outline + dim text.
- The **clipped-Max "≥" flag** (review finding) uses the same red language — mark the Max value with
  a small `≥` in `#FF5B3A` when it includes clipped blocks.
- Red appears **nowhere else** on the screen (unless Knob D is taken).

---

## 7. Stats + reset + calibration

- Stats row: `MAX` / `MIN` / `Leq` — dim labels (`#9A6F22`), amber values (`#FFC23A`), one decimal.
- Shared `RESET MAX / MIN / Leq` control below the stats.
- Calibration caption, bottom, dim (`#8A6320`): `≈ ±2 dB(A) · tier <n> <source>`.

---

## 8. States still to style (deferred items, FEATURES.md)

- **Mic-busy / retry**: another app holds the mic on resume. Needs a recoverable state in the same
  phosphor language — a centered dim message + a `RETRY` control, not a bare "failed to open".
- **Clipped-Max "≥"**: covered in §6.

---

## Knobs (your decisions — defaults applied above)

- **A — head brightness**: default ~33% constant. Easy to dial on a real screen.
- **B — glow resting floor**: default ~0 (edges fully dark at rest).
- **C — glow curve**: default eased (smoothstep, floor 50 / ceil 88 dB), **max alpha ~0.4 — subtle**. Linear / brighter available.
- **D — glow color**: default amber-only. Optional warm-to-red toward the threshold.

## Asset deliverables

- Phosphor head: tinted PNG or VectorDrawable, constant alpha.
- Edge glow: runtime-blurred silhouette (preferred, API 31+) or the precomputed edge-glow ring bitmap.
- If going vector for the glow silhouette: two clean shapes from `howler1.svg` — the outer body path
  and (only if you ever want the open-jaw read) the mouth path. The current design doesn't require the
  mouth as a separate shape, since the head is rendered with full detail, not as a flat silhouette.
