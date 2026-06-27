# CLAUDE.md — Howler

Project-specific guidance for Claude Code working in this repo.

## What this is

Howler is a **decibel meter** app. Android first (Kotlin + [Oboe](https://github.com/google/oboe)
for low-latency audio), cross-compiling to iOS later. It is the first real **dogfood** project
for the Tessera framework (`../tessera`): the goal is to build something real *and* surface
friction in Tessera itself. When something about the framework chafes, that's a finding — capture
it (see Dogfooding below), don't just work around it silently.

- **Stack:** Kotlin / Android / Oboe (NDK + CMake for the audio path). iOS via Kotlin
  Multiplatform later.
- **Tessera profile:** `standard` (see `.tessera/project.yml`).

## Working conventions

How Lorenzo works. The most important section.

- **Push back when you see drift.** Don't perform agreement. If a decision seems wrong or an
  assumption seems loaded, surface it — as honest feedback, not a refusal.
- **"Batching" is a one-word signal.** It means you're bundling decisions into prose instead of
  surfacing them as numbered choices. Stop, list the decisions, ask before committing.
- **Surface decisions before committing them.** Multi-step or irreversible changes warrant a
  brief "here's what I'd do, OK to proceed?" When you surface such a gate, **also record it**:
  `python3 scripts/gate/emit.py --fired --kind <kind> --note "<what you proposed>"` (use
  `--held` if you weighed surfacing one and decided against). This is Tessera principle #12 (the
  suggestion-gate) dogfooding itself — the log is a reviewable friction journal. Forgetting to
  log a gate is itself a finding, not a failure. Contract: `../tessera/docs/contracts/gate-event.md`.
- **Use numbered lists for decision points.** Binary A/B beats a dense paragraph with embedded
  choices.
- **Name biases you notice in your own reasoning** — confirmation, sunk-cost, excitement,
  familiarity, anchoring. Honesty about bias is part of the trail.
- **Brief acknowledgments.** "Done," "Confirmed," "Clean" — not "Excellent! Great choice!"
- **Flag confidence levels.** Be explicit about what you know vs. infer vs. guess.
- **Tone is direct, not performative.** No witty-coworker framing.

## Dogfooding — capture friction

Howler exists partly to find what's wrong/missing in Tessera. When the framework chafes:

- Note it in `docs/SCAFFOLD-NOTES.md` (setup friction) or a `docs/FINDINGS.md` (runtime friction)
  as you hit it — the friction otherwise lives only in the transcript.
- Framework-level fixes happen in `../tessera`, not here (don't touch `../tessera` unless asked).
  Stage the finding here, transfer when next working in tessera.

## Hook lifecycle (Mnemos)

The hooks in `.claude/settings.json` invoke scripts in `.claude/scripts/`:

- **SessionStart** — `mnemos-session-start.sh` loads any prior checkpoint
- **PreCompact** — `mnemos-pre-compact.sh` writes an emergency checkpoint before compaction
- **PreToolUse** — `mnemos-post-compact-inject.sh` checks for post-compaction restore;
  `mnemos-pre-edit.sh` (Edit/Write) checks fatigue + intent
- **PostToolUse** — `mnemos-post-tool.sh` logs tool outcomes
- **Stop** — `mnemos-stop-checkpoint.sh` checkpoints; `mnemos-stop-ingest.sh` ingests the
  transcript + scores haze

When you see `MNEMOS CHECKPOINT` in context, a hook injected it — announce briefly, resume from
it, don't re-derive. If no checkpoint fires on resume but `.mnemos/` exists, run `mnemos resume`.

Requires the `mnemos` CLI on PATH (pip-installed globally). Hooks degrade gracefully without it.

## Don't

- Don't modify `.env` / `.env.*` (also denied in settings.json)
- Don't add dependencies without checking existing ones cover the need
- Don't commit secrets (keystores, signing keys — gitignored, keep it that way)
- Don't edit `../tessera` unless explicitly asked — stage framework findings here instead

## Commands

Toolchain **is** installed, but nothing is on PATH and the system `java` is 8 (too old for
AGP). Export these every session (see `docs/HANDOFF.md` for the full dev/verify loop):

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # JDK 17, not system java 8
export ANDROID_HOME="$HOME/Library/Android/sdk"
ADB="$ANDROID_HOME/platform-tools/adb"   # adb is not on PATH
```

- `./gradlew :app:assembleDebug` — build debug APK
- `./gradlew :app:testDebugUnitTest` — JVM unit tests
- `./gradlew :app:connectedAndroidTest` — instrumented tests (needs a device/emulator)
- `$ADB install -r app/build/outputs/apk/debug/app-debug.apk` — install to the Pixel
- `$ADB exec-out screencap -p > shot.png` — screenshot for on-device verify
