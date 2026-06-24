# Scaffold notes — Howler (Tessera dogfood #1)

Captured live while hand-scaffolding Howler on 2026-06-23. The point of the first dogfood is to
surface Tessera friction; the scaffold itself is the first source. Each item below is a candidate
to fix in `../tessera` — most distill into the missing `templates/tessera/` scaffold.

## Resolution status (2026-06-24)

Most of this is now fixed back in tessera — `bin/tessera-new-project` + `templates/tessera/`
(commit `239d668`). Per-item below:

- **Headline (no scaffold), items 1–3** — RESOLVED. CLAUDE.md.template, harness assembler, and a
  stack-agnostic settings/gitignore base now exist. Scaffold copies hooks from tessera's live
  source (no third stale copy).
- **Item 4 (gate recorder vendored vs installed)** — STILL OPEN. Scaffold copies `emit.py` from
  live source (mitigates drift) but it's still vendored per-project, not an installed CLI. Decision
  deferred.
- **Item 5 (app-stack coexistence)** — DOCUMENTED in the scaffold README/script: generate the app
  with the platform's own tool, layer the harness on top.
- **CORRECTION (2026-06-24) — "maggy cruft" was wrong.** An earlier draft of these notes flagged
  the inherited `templates/` top-level and `commands/initialize-project.md` as "maggy cruft to
  clean." That is **false** and is NOT a drift point — do not delete them. Checked against
  `../tessera/docs/design-principles.md` ("Skills — keep" / "What's Out"): the design doc
  **intentionally keeps** mnemos, icpg, polyphony, codex-review, etc., and the cut-list skills are
  **already removed** (commit `e4ae042`). The `templates/` files are the **install payload** for
  kept subsystems, and `initialize-project.md` is the inherited maggy **installer** (a candidate
  distribution mechanism), not dead weight.
- **Real open item — downstream packaging.** Two mechanism candidates compete: (a) the inherited
  maggy installer (`initialize-project.md` + `templates/` + `~/.claude/.bootstrap-dir`), and
  (b) `bin/tessera-new-project` (built here, harness-only, assumes a sibling tessera checkout).
  tess-dashboard (downstream #0) and Howler (#1) were each hand-rolled differently — divergent
  shapes, no shared mechanism. The scaffold is the convergence point; reconciling it with the
  inherited installer is the actual decision. Tracked, not a cleanup.

## The headline: there is no scaffold

Tessera has **no native way to stand up a downstream project.** Every step here was manual. What
exists in tessera today: `templates/tessera/*.template` (3 `.tessera/` config files only). What's
missing: a `CLAUDE.md.template`, a `.claude/` harness bundle, and a command to assemble them.
`commands/initialize-project.md` is the inherited maggy **installer** (reads
`~/.claude/.bootstrap-dir`, no Tessera-specific wiring) — not usable *as-is* for a sibling-checkout
scaffold, but it's a candidate distribution mechanism, **not cruft** (see Correction above).

## Friction items (→ fix in tessera)

1. **No `CLAUDE.md.template`.** Hand-wrote Howler's `CLAUDE.md` by distilling tessera's own
   framework-dev `CLAUDE.md` — which is the *wrong* base (it's framework-internal: ADRs,
   observatory, design-principles). The downstream template should be derived from **Howler's**
   CLAUDE.md, not tessera's. → Promote `howler/CLAUDE.md` to `templates/tessera/CLAUDE.md.template`
   with the project-specifics (`## What this is`, stack, commands) as fill-in placeholders.

2. **Harness pieces are scattered, no bundle to copy.** A working Tessera project needs:
   `.claude/settings.json` + `.claude/scripts/mnemos-*.sh` (8) + `scripts/gate/emit.py` +
   `.tessera/project.yml` + `.tessera/logs/README.md` + `.gitignore` patterns + `.mnemos/.gitignore`.
   These live in different places in tessera. → A `templates/tessera/harness/` tree (or an `init`
   command) should lay all of it down in one shot.

3. **`settings.json` permissions are stack-specific.** Tessera's allow-list carries
   JS/Python/icpg/polyphony entries irrelevant to an Android project; had to hand-curate for
   Kotlin/Gradle (`./gradlew`, `adb`) and drop the rest. The safety `deny` block and the mnemos
   hook wiring are stack-agnostic and copy verbatim. → Template should split a stack-agnostic base
   (hooks + denies + statusline + mnemos/gate allows) from a stack-specific allow overlay.

4. **`scripts/gate/emit.py` is vendored per-project.** Copied the recorder into Howler. This is
   inconsistent with mnemos, which is an installed CLI on PATH. Open question: should the gate
   recorder also be an installed `tessera`/`mnemos`-adjacent CLI rather than a copied file? Vendored
   = drifts when the contract changes; installed = one source of truth. → Decide; likely fold the
   recorder into the mnemos package or a small `tessera` CLI.

5. **No app-stack awareness.** Tessera has no notion of "this is an Android project, scaffold a
   Gradle/Kotlin/Oboe skeleton." That's arguably out of scope (let Android Studio do it) — but the
   *coexistence* (Tessera harness layered onto a wizard-generated Android project) needs an
   ordering story. → Document: generate app via the platform's own tool first, layer Tessera on top.

## Blocked: no Android toolchain on this machine

No JDK, no Android SDK, no Gradle, no Android Studio, no kotlin CLI (checked 2026-06-23). Cannot
build, verify, or even generate a Gradle wrapper jar.

- **App scaffold deferred** to Android Studio's New Project wizard (Empty Activity, Kotlin). The
  wizard gets AGP/wrapper/compileSdk versions right; hand-rolled Gradle would be unbuildable and
  version-fragile. Oboe (NDK + CMake) added when the first audio code lands — YAGNI until then.
- **Next physical step:** install Android Studio (bundles JDK + SDK + Gradle) → wizard generates
  the app into this repo → re-verify the Tessera harness still fires → add Oboe.

## What worked (no friction — worth keeping)

- **mnemos hook scripts are fully portable.** All 8 `.claude/scripts/mnemos-*.sh` are path-relative
  (`.claude/scripts/`, `.mnemos/`) with zero hardcoded tessera paths — copied as-is, no edits.
- **The `.claude/settings.json` hook block is verbatim-reusable** across projects (only permissions
  differ).
- **Gate recorder ran first try** in this session (model-emitted, logged to tessera's own session).

## Gate-granularity observation (for the gate dogfood)

Logged ONE `suggestion_gate` event for the whole "scaffold Howler" decision episode, then made
several sub-decisions (harness-first, toolchain pivot, app deferral) without re-emitting — to avoid
gate spam. Open question for the recorder convention: what's the right granularity — one event per
decision *episode*, or per *sub-decision*? Too fine = noise; too coarse = the journal misses the
real forks. Watch this across the dogfood.
