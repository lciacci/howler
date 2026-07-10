# Howler — Tessera dogfood findings

Runtime friction surfaced while working in Howler. Framework-level fixes land in
`../tessera`, not here — these are staged for transfer next time tessera is worked.

---

## F-001 — `install.md` Mnemos step is Apple-Silicon-fragile (dual-Homebrew shadowing)

**Status:** transferred:tessera 6640d19
**Surfaced:** 2026-06-24, restoring Howler session state on the migrated machine.
**Resolved:** 2026-06-24 in `../tessera` `6640d19` (one-time authorized cross-repo edit).

**What happened.** This machine has a stale **Intel** Homebrew at `/usr/local` whose `brew`
sits earlier on `PATH` than the native **arm64** Homebrew at `/opt/homebrew`. Following
`tessera/docs/install.md` step 2 ("`/opt/homebrew/bin/pip3.13` ... or whatever Homebrew Python
you have") with the on-PATH Intel `pip3.13` produced a `mnemos` console-script with shebang
`#!/usr/local/opt/python@3.13/bin/python3.13`. That path later vanished (Intel keg
cleaned/absent), so every `mnemos` invocation died with `bad interpreter: ... no such file or
directory` — silently disabling all Mnemos hooks. It worked at install time, then broke.

**Fix applied (local).** Removed the broken `/usr/local/bin/mnemos`, reinstalled via the native
`/opt/homebrew/bin/pip3.13`. New shebang `#!/opt/homebrew/opt/python@3.13/bin/python3.13`
resolves; `mnemos` is one global pkg so this fixed tessera too.

**Framework fix to transfer.** On Apple Silicon, `install.md` step 2 should:
1. Pin `/opt/homebrew/bin/pip3.13` explicitly (not "whatever's on PATH").
2. Pre-check `file "$(command -v python3.13)"` reports `arm64`.
3. Post-install, verify the shebang resolves: `head -1 "$(command -v mnemos)"` → target exists,
   `mnemos --version` runs. A dead shebang is the silent-failure mode the hooks' graceful
   degradation masks.

---

## F-002 — Migration slug caveat fired; clarify it uses realpath on-disk casing

**Status:** transferred:observatory "Reusable migration skill" (tessera, 2026-07-10)
**Surfaced:** 2026-06-24, restoring `claude-project.tgz`.

**What happened.** `howler-migration-export/RESTORE.md` exports the Claude project transcript
dir under the old machine's path-slug `-Users-lciacci-Claude-howler`. The username differed on
the new machine (`lciacci` → `lorenzociacci`), so per the documented CAVEAT the unpacked dir had
to be renamed → `-Users-lorenzociacci-Claude-howler`. The caveat was present and correct — good.

**Refinement to transfer.** The slug derives from the **realpath with on-disk casing**, not the
cwd string. On-disk dir is `Claude` (capital C) and the live `-Users-lorenzociacci-Claude-tessera`
slug confirmed it — so only the username segment changed, not the case. RESTORE.md's example
("`/Users/lori/dev/howler` → `-Users-lori-dev-howler`") should note the casing-from-realpath
detail, since case-insensitive macOS FS makes lowercase paths *look* equivalent but the slug is
literal. Candidate for a reusable `tessera` migration skill rather than per-project RESTORE.md.

**Confirmed working.** Mnemos `.mnemos/` is path-relative and restored cleanly with no slug
issue — validates the export's split design (repo-relative state vs path-slug state).

---

## F-003 — Downstream statusline scripts diverge from Tessera source

**Status:** transferred:ADR-0004
**Surfaced:** 2026-06-27, after tier-advisory patch landed in tessera but not howler.

**What happened.** `mnemos-statusline.sh` is scaffolded into every downstream project at init time. When Tessera patches its own copy (e.g. tier-flag feature), downstream copies don't get the update — they're inert copies with no sync mechanism. Discovered when tier `⚑tier:` flag appeared in Tessera session but not Howler.

**Immediate fix.** Manually copied `tessera/.claude/scripts/mnemos-statusline.sh` → `howler/.claude/scripts/mnemos-statusline.sh`.

**Framework-level gap.** Tessera has no mechanism to propagate script changes to downstream projects. Options:
1. Symlink `mnemos-statusline.sh` back to a Tessera-owned source (requires tessera checkout at a stable path — fragile)
2. A `tessera sync-scripts` command that diffs + patches known downstream files
3. Accept divergence; document "run tessera sync after framework script changes"

**When to fix in Tessera.** When a second downstream project exists (iOS/KMP) or when this manual copy step happens a third time.

---

## F-004 — Package rename left JNI C++ symbols stale → crash on open, no build error

**Status:** transferred:observatory (cross-cutting rename guard, Watching)
**Surfaced:** 2026-06-30, closed tester reported Howler crashes on open (Android 16).

**What happened.** Commit `7b0feaf` renamed the app package `com.example.howler` →
`com.houseofyeti.howler` (Kotlin sources, namespace, applicationId, manifest). The native
layer was missed: `audio_engine.cpp` still exported `Java_com_example_howler_audio_AudioEngine_*`.
JNI resolves natives by mangled fully-qualified class name, so `nativeStart()` — invoked on the
meter's `ON_START` lifecycle — threw `UnsatisfiedLinkError: No implementation found for ...
nativeStart()` and the app crashed on open. **Nothing failed at build time** — the C++ compiles
and links fine; the symbol is just orphaned. Fixed in `c7c6335` (rename all 11 JNI exports).

**Why it slipped.** Two compounding gaps:
1. No JNI smoke test. `connectedAndroidTest` exists but wasn't run post-rename; a one-line
   instrumented `AudioEngine().nativeStart()` would have caught it. The host-side JVM tests
   (`testDebugUnitTest`) can't — the native lib doesn't load off-device.
2. I anchored on a wrong root cause first. The crash was on Android 16, so I jumped to the
   16KB-page-alignment regression (real, but latent — local 4KB Pixel reproduced the crash too,
   which should have falsified the 16KB theory immediately). Pulling the actual `logcat -b crash`
   stack trace *before* hypothesizing would have gone straight to the JNI mismatch. **Get the
   stack trace before theorizing about a crash.**

**Dogfood angle for Tessera.** A package-rename is a cross-cutting refactor Tessera has no guard
for. The Kotlin/manifest layer is greppable and IDE-refactorable; the native JNI layer is coupled
by *string convention* (`Java_<mangled_fqcn>_<method>`) with no compiler link between them.
Candidates:
1. A `tessera` rename-checklist / lint that, for projects with an `externalNativeBuild`, greps
   `src/main/cpp` for `Java_<old_package_mangled>_` after an applicationId/namespace change.
2. Encourage a minimal JNI-load instrumented test in the NDK scaffold so any symbol-name drift
   fails CI, not on a tester's device in closed testing.

**When to fix in Tessera.** When the iOS/KMP work starts (KMP moves the JNI boundary again) or if
any future rename touches native code.
