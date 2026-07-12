#!/bin/bash
# Mnemos Post-Compaction Injection — Layer 3 (fallback) of task restoration.
#
# This is a PreToolUse hook with NO matcher (fires on ALL tool calls).
# It detects when compaction just occurred and re-injects the full checkpoint.
#
# Fast path: ~5ms when no compaction happened (just a file existence check).
# Slow path: ~100ms when injecting checkpoint (only fires once after compaction).
#
# How it works:
#   1. PreCompact hook writes ".mnemos/just-compacted" marker
#   2. SessionStart (unmatched, so it fires on source=compact too) runs
#      mnemos-session-start.sh and prints the checkpoint — Layer 2, primary.
#      It does NOT consume the marker.
#   3. This hook consumes the marker and injects on the first tool call.
#      Because Layer 2 left the marker, this normally fires too — a second,
#      redundant injection. It is the ONLY layer that fires when the
#      post-compaction turn is pure text with no tool call.
#   4. Marker deletion is atomic (rename) to prevent parallel injection
#   5. Either outcome appends to .mnemos/compaction-log.jsonl — the durable
#      record. The marker itself is destroyed, so this log is the only
#      evidence compaction ever fired.
#
# Corrected 2026-07-09: earlier headers named a "mnemos-compact-recovery.sh"
# fired by a SessionStart "compact" matcher as the primary path. No such
# script or matcher has ever existed. Layer 2 is mnemos-session-start.sh.
#
# Install: add to .claude/settings.json under hooks.PreToolUse (no matcher)

# ─── Fast path: no compaction marker = exit immediately ───


# ── Toolchain interpreter: a PATH, never a NAME. (F-001) ──────────────────────────────────
# This hook used bare `python3`. With sys.path/PYTHONPATH pointed at scripts/, ANY interpreter
# imports mnemos/icpg straight from source — so that did NOT fail, it SILENTLY SUCCEEDED on
# whatever Homebrew currently owns the `python3` name. The original F-001 failed silently
# (import error → no-op); this one *worked*, on an interpreter brew can re-point or delete.
# A silent success is strictly harder to detect than a silent failure.
# No toolchain → this hook goes QUIET. tessera-watch P9 makes that visible.
TOOLCHAIN_PY=""
if [ -x ".venv/bin/python" ]; then
    TOOLCHAIN_PY=".venv/bin/python"
elif command -v mnemos >/dev/null 2>&1; then
    TOOLCHAIN_PY="$(sed -n '1s/^#!//p' "$(command -v mnemos)" 2>/dev/null | awk '{print $1}')"
fi
[ -n "$TOOLCHAIN_PY" ] && [ -x "$TOOLCHAIN_PY" ] || exit 0
# ──────────────────────────────────────────────────────────────────────────────────────────
[ -f ".mnemos/just-compacted" ] || exit 0

# ─── Validate marker is fresh and atomically consume it ───

CONSUMED=$("$TOOLCHAIN_PY" -c "
import json, time, os

marker = '.mnemos/just-compacted'
consumed = '.mnemos/just-compacted.consumed'

def record(event):
    # Durable counterpart to the compaction_fired line written by the
    # PreCompact hook. Marker deletion is destructive; this outlives it.
    try:
        with open('.mnemos/compaction-log.jsonl', 'a') as f:
            f.write(json.dumps({'ts': time.time(), 'event': event}) + '\n')
    except Exception:
        pass

try:
    with open(marker) as f:
        data = json.load(f)
    age = time.time() - data.get('timestamp', 0)
    if age > 300:
        # Stale marker (>5 min): compaction fired but no restore ran in time.
        os.unlink(marker)
        record('restore_missed_stale')
        print('stale')
    else:
        # Fresh marker — atomically consume it
        os.rename(marker, consumed)
        try:
            os.unlink(consumed)
        except:
            pass
        record('restore_injected')
        print('consumed')
except FileNotFoundError:
    # Another hook already consumed it (parallel tool calls)
    print('already_consumed')
except Exception:
    print('error')
")

# Only inject if we successfully consumed the marker
if [ "$CONSUMED" != "consumed" ]; then
    exit 0
fi

# ─── Inject checkpoint into Claude's context ───

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

"$TOOLCHAIN_PY" -c "
import sys, json
sys.path.insert(0, '${SCRIPT_DIR%/templates}/scripts')

try:
    from mnemos.checkpoint import format_for_post_compact_injection
    output = format_for_post_compact_injection('.')
    if output:
        print(output)
    else:
        print('=== MNEMOS: Compaction detected but no checkpoint found. ===')
        print('Previous context was lost. Ask the user what they were working on.')
except Exception as e:
    # Fallback: try to read checkpoint JSON directly
    try:
        with open('.mnemos/checkpoint-latest.json') as f:
            data = json.load(f)
        print('=== MNEMOS: CONTEXT RESTORED AFTER COMPACTION ===')
        print()
        print('Compaction just occurred. Resume from this checkpoint:')
        print()
        print(f'## Goal')
        print(data.get('goal', 'No goal recorded'))
        print()
        constraints = data.get('active_constraints', [])
        if constraints:
            print('## Active Constraints (DO NOT VIOLATE)')
            for c in constraints:
                print(f'- {c}')
            print()
        narrative = data.get('task_narrative', '')
        if narrative:
            print(f'## What You Were Working On')
            print(narrative)
            print()
        print('=== Resume work from this checkpoint. ===')
    except:
        print('=== MNEMOS: Compaction detected but checkpoint unreadable. ===')
        print('Ask the user what they were working on.')
"

exit 0
