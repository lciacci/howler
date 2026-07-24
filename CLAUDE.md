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
  suggestion-gate) dogfooding itself — the log is a reviewable friction journal. **A Stop hook
  now backstops this** (`scripts/gate/scan.py`): it counts gate-shaped turns in the transcript,
  diffs them against the log, and makes you adjudicate a gap before finishing — so forgetting to
  log a gate is now a bug, not just a finding. Its detector over-counts on purpose; you are the
  precision filter. Contract: `../tessera/docs/contracts/gate-event.md`.
- **When you are blocked and cannot proceed, raise an escalation — do not just say so and stop.**
  `tessera-escalate raise --category <cat> --summary "<what is stuck>" --tried "<attempt — how it
  failed>" --option "<what to choose between>"` (if `tessera/bin` is not on your PATH, use
  `python3 scripts/tessera-escalate`). This is the suggestion-gate's *asynchronous* form: #12 needs
  a human to dispose, and one is not always there. `--tried` is required — a packet with no
  attempts is a complaint, not an escalation. Resolve with `tessera-escalate resolve <id> --note
  "<the decision>"`. Contract: `../tessera/docs/contracts/escalation.md`.
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

## Pending harness back-fill — the spend guard (OPEN as of 2026-07-24)

**Howler is the last repo in the fleet without Tessera's spend guard.** This is a deliberate
defer, not an oversight, and it can only be closed *from a howler session* — which is why it is
written here rather than left on Tessera's backlog, where it sat unmoved for two sessions
because nothing in a Tessera session can do it.

**Why it was deferred.** The guard adds a `PreToolUse(Bash)` hook that **denies Bash by
default** unless a spend envelope has been granted. The only way to learn whether that blocks a
real `./gradlew` build, an `$ADB install`, or a Play Store upload is to be in howler running
those commands. Doing it blind, mid-ship, risks bricking the build loop.

**What is missing** (9 files + 2 hook wirings, verified 2026-07-24):

```
.claude/scripts/tessera-spend-backstop.sh   scripts/spend/event.py
.claude/scripts/tessera-spend-guard.sh      scripts/spend/guard.py
scripts/spend/authorize.py                  scripts/spend/test_backstop.py
scripts/spend/backstop.py                   scripts/spend/test_guard.py
scripts/spend/conftest.py
+ PreToolUse → tessera-spend-guard.sh    + Stop → tessera-spend-backstop.sh
```

**How to close it.** With `../tessera` checked out:

```sh
tessera-sync-harness ~/Claude/howler            # dry-run first — it only ever ADDS
tessera-sync-harness ~/Claude/howler --apply    # no --exclude spend this time
```

Then, before the first build, confirm the guard does not block the dev loop. Cost-*reducing*
commands are never blocked by design, but a build is not one. `tessera-authorize grant --usd N
--ttl 4h --note "..."` opens an envelope. If it blocks something it should not, that is a
finding for `docs/FINDINGS.md`, not a reason to delete the hook.

**Do not need `../tessera` to know the task exists** — that is the point of this section. The
file list above is the fallback if the framework is not on this machine.

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
