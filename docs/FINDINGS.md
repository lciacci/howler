# Howler — Tessera dogfood findings

Runtime friction surfaced while working in Howler. Framework-level fixes land in
`../tessera`, not here — these are staged for transfer next time tessera is worked.

---

## F-001 — `install.md` Mnemos step is Apple-Silicon-fragile (dual-Homebrew shadowing)

**Surfaced:** 2026-06-24, restoring Howler session state on the migrated machine.

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
