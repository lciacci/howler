# Howler — Google Play listing pack

Everything you paste into Play Console, plus the answers to the questionnaires.
Fill the `[bracketed]` placeholders. Screenshots are intentionally left for last.

---

## 1. Store listing copy

**App name** (≤30 chars)
```
Howler — SPL Meter
```
_Alternatives: "Howler: Decibel Meter", "Howler — Decibel Meter"._

**Short description** (≤80 chars)
```
An honest decibel meter — real-time sound levels in dB, with A/C/Z weighting.
```

**Full description** (≤4000 chars)
```
Howler is a clean, honest sound level meter for Android. Point your phone at the
world and read real sound pressure levels in decibels — no clutter, no fake
precision, no nonsense.

Built on a proper measurement signal chain — unprocessed microphone input and
IEC 61672-style frequency weighting — Howler aims to tell you the truth about how
loud your environment really is.

FEATURES
• Real-time SPL readout in decibels
• A, C, and Z (flat) frequency weighting — switch with one tap
• Fast (125 ms) and Slow (1 s) time response
• Max hold, Min hold, and Leq (equivalent continuous level)
• Over-range (OVER) indicator when the signal clips
• One-tap reset for Max / Min / Leq
• Single-point calibration to tighten accuracy on your device
• Screen stays awake while you watch the meter

HONEST BY DESIGN
Howler reads the microphone with the usual audio "enhancements" — automatic gain,
noise suppression — turned off, because those features lie to a sound meter.
Accuracy is roughly ±2 dB(A) on a calibrated device: good enough to make real
decisions, and clearly labeled so you always know what you're getting. Howler is
not a certified Type 1 or Type 2 sound level meter and should not be used for
legal, regulatory, or occupational-compliance measurements.

PRIVATE BY DESIGN
Howler does its work entirely on your device. The microphone is used only to
measure sound level in real time — audio is never recorded, saved, or sent
anywhere. No accounts, no ads, no tracking.

WHO IT'S FOR
Musicians and live-sound folks checking stage and room levels, home-studio
owners, and anyone curious how loud a concert, workshop, gym, or city street
really is.

Loud is a howler monkey's whole thing. Now it's yours to measure.
```

**What's new** (release notes, v1.0)
```
First release. Real-time SPL metering with A/C/Z weighting, Fast/Slow response,
Max/Min/Leq, over-range indicator, and single-point calibration.
```

**Category:** Tools (primary). _Music & Audio is a defensible alternative given the
target users; Tools is the safer fit for a measurement utility._

**Tags / ASO terms** (weave naturally; Play has no keyword field): decibel meter,
sound level meter, SPL meter, dB meter, noise meter, sound meter.

---

## 2. Data Safety form answers (Play Console → App content → Data safety)

- **Does your app collect or share any of the required user data types?**
  → **No.**

That single answer collapses most of the form. For completeness / if prompted:

- Data collected: **None.**
- Data shared: **None.**
- Audio (voice or sound recordings): **Not collected.** The microphone is
  processed on-device in real time to compute a decibel level; no audio is
  recorded, stored, or transmitted off the device. (Play defines "collection" as
  transmission off the device — Howler does none.)
- Is all collected data encrypted in transit? → **N/A** (no data leaves the device).
- Do you provide a way for users to request data deletion? → **N/A** (no data collected).

> NOTE: This is only true while Howler has no analytics/crash-reporting/ads SDKs.
> If you ever add Crashlytics, Firebase Analytics, an ad network, etc., you must
> update this form **and** the privacy policy before that build ships.

---

## 3. Microphone permission justification

Some review/permission prompts ask why you use `RECORD_AUDIO`. Use:

```
Howler is a sound level meter. The RECORD_AUDIO permission is used solely to
measure ambient sound pressure level in real time. Audio is processed on-device
and is never recorded, stored, or transmitted. Microphone access is essential to
the app's core and only function.
```

---

## 4. Other App content sections to complete

- **Privacy policy URL:** host `howler-privacy-policy.md` at a public URL
  (e.g. https://houseofyeti.com/howler/privacy) and paste it here.
- **Content rating:** complete the IARC questionnaire. With no objectionable
  content, expect **Everyone / PEGI 3**.
- **Target audience & content:** not directed at children; select an adult/teen
  target audience to stay out of the Families program.
- **Ads:** declare **No ads**.
- **Government apps / financial / health:** No to all.

---

## 5. Assets checklist (what goes where)

| Asset | Spec | File |
|-------|------|------|
| App icon (Play listing) | 512×512 PNG, no alpha | `howler_icon_512.png` |
| Adaptive launcher — foreground | head, transparent | `howler_icon_adaptive_foreground.png` |
| Adaptive launcher — background | solid amber | `howler_icon_adaptive_background.png` |
| Adaptive launcher — monochrome | themed-icon shape | `howler_icon_monochrome.png` |
| Feature graphic | 1024×500 PNG | `howler_feature_graphic.png` |
| Phone screenshots | 2–8, real device/emulator | **last — pending final UI** |

_Dark-background icon variant also included (`howler_icon_512_dark_variant.png`) if
you prefer brand cohesion over launcher pop._
