# Howler — session handoff

How to pick this repo up cold. Pair with `CLAUDE.md` (conventions) and
`FEATURES.md` (what's built). Git is the source of truth; everything below is the
stuff git alone won't tell you.

## Status (2026-06-30)

**In closed testing on Google Play.** Alpha released Jun 29; available on
10,637 devices. Opt-in URL visible in Play Console → Testing → Closed testing.

**1.0.1 (versionCode 2) built Jun 30 — crash-on-open fix, pending upload.** A tester on
Android 16 hit an immediate crash; root cause was the package rename (`7b0feaf`) leaving the
C++ JNI symbols as `com_example_howler` → `UnsatisfiedLinkError` on `nativeStart()`. Fixed in
`c7c6335` (also bumped Oboe 1.9.0→1.10.0 + 16KB-page link flag for a latent Android-16 issue).
AAB at `app/build/outputs/bundle/release/app-release.aab` — **upload to close the loop.**

- Package: `com.houseofyeti.howler`
- Version: 1.0.1 (versionCode 2)
- Signed with upload keystore at `~/howler-upload.keystore` (keep safe — not in repo)
- Signing config reads from `keystore.properties` (gitignored) in repo root

**What shipped:**
- Meter: native Oboe input, unprocessed; Z/A/C weighting, Fast/Slow, Max/Min/Leq,
  OVER, tier-2 manual calibration. See `FEATURES.md`.
- UI: CRT amber-phosphor screen — head + reactive backlight glow, DSEG7 LED
  readout, segmented controls, OVER/clipped-Max/mic-busy states, phosphor dialog.
- First-run calibration notice dialog (SharedPreferences flag `first_run_seen`).
- Howler dark-variant adaptive launcher icon.
- All verified on a physical **Pixel 10 Pro XL** (API 36).

**Web (houseofyeti.com/howler/):**
- `index.html` — help page (calibration first, all features covered)
- `privacy.html` — privacy policy (effective 2026-06-27)
- Both cross-link; privacy URL submitted to Play Console.

**Next:** gather feedback from closed testers, then promote to production.

Other work is project-level, not UI: **iOS via KMP** (the original "after the app"
goal), or a parked out-of-v1 extra (history graph, dose, Ln percentiles, octave
bands, export).

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
