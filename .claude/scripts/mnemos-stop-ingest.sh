#!/usr/bin/env bash

# ── Toolchain resolution: a PATH, never a NAME, and NO bare-python3 fallback. (F-001) ──
# This block used to fall back to `python3 -m mnemos`. That fallback was the bug: with
# PYTHONPATH=scripts, ANY interpreter imports mnemos straight from source — so it did not
# fail, it silently SUCCEEDED on an unmanaged Python that Homebrew can re-point or delete.
# The original F-001 failed silently (import error → no-op); this one *worked*, on the wrong
# interpreter. A silent success is strictly harder to detect than a silent failure.
# If the toolchain is unreachable, this hook now goes QUIET. tessera-watch P9 catches that.
# Claude Code Stop hook: ingest the just-closed session transcript into
# mnemos and compute per-session haziness.
#
# Stdin: JSON with session_id, transcript_path, cwd (sent by Claude Code).
# Opt-out: touch $CWD/.mnemos/claude-log.disabled
#
# Never blocks the user on failure -- silently bails on any error.
set -u

HOOK_INPUT=$(cat 2>/dev/null || true)
if [ -z "$HOOK_INPUT" ]; then
  exit 0
fi

if ! command -v jq >/dev/null 2>&1; then
  # No jq: mnemos won't see the fields. Cheaper to bail than fail noisily.
  exit 0
fi

# Resolve mnemos the same way the sibling hooks do (console script first,
# then `python3 -m mnemos`). Bail quietly if neither is available.
MNEMOS_CMD=""
if [ -x ".venv/bin/mnemos" ]; then
  MNEMOS_CMD=".venv/bin/mnemos"
elif command -v mnemos >/dev/null 2>&1; then
  MNEMOS_CMD="mnemos"
fi
if [ -z "$MNEMOS_CMD" ]; then
  exit 0
fi

SESSION_ID=$(printf '%s' "$HOOK_INPUT" | jq -r '.session_id // empty' 2>/dev/null)
TRANSCRIPT_PATH=$(printf '%s' "$HOOK_INPUT" | jq -r '.transcript_path // empty' 2>/dev/null)
CWD=$(printf '%s' "$HOOK_INPUT" | jq -r '.cwd // empty' 2>/dev/null)

if [ -z "$TRANSCRIPT_PATH" ] || [ -z "$SESSION_ID" ]; then
  exit 0
fi

# Per-project opt-out.
if [ -n "$CWD" ] && [ -f "$CWD/.mnemos/claude-log.disabled" ]; then
  exit 0
fi

# Let the final assistant turn finish flushing to the JSONL.
sleep 0.3

# Pick the project dir mnemos operates on: prefer hook cwd, fall back to pwd.
PROJECT_DIR="${CWD:-$PWD}"

# Fire and forget: ingest, then score. Any error is swallowed so we never
# block the user's session exit.
(
  $MNEMOS_CMD --project "$PROJECT_DIR" ingest-claude \
    --session "$SESSION_ID" --transcript "$TRANSCRIPT_PATH" \
    >/dev/null 2>&1 || true
  $MNEMOS_CMD --project "$PROJECT_DIR" haze \
    --session "$SESSION_ID" --quiet \
    >/dev/null 2>&1 || true
) &
disown 2>/dev/null || true

exit 0
