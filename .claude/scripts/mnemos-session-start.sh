#!/bin/bash

# ── Toolchain resolution: a PATH, never a NAME, and NO bare-python3 fallback. (F-001) ──
# This block used to fall back to `python3 -m mnemos`. That fallback was the bug: with
# PYTHONPATH=scripts, ANY interpreter imports mnemos straight from source — so it did not
# fail, it silently SUCCEEDED on an unmanaged Python that Homebrew can re-point or delete.
# The original F-001 failed silently (import error → no-op); this one *worked*, on the wrong
# interpreter. A silent success is strictly harder to detect than a silent failure.
# If the toolchain is unreachable, this hook now goes QUIET. tessera-watch P9 catches that.
# Mnemos SessionStart Hook — loads checkpoint on session resume.
#
# Checks for .mnemos/checkpoint-latest.json and injects it into context.
# Also bridges iCPG state if available.
#
# Install: add to .claude/settings.json under hooks.SessionStart

# ─── Load checkpoint if exists ───

if [ -f ".mnemos/checkpoint-latest.json" ]; then
    MNEMOS_CMD=""
    if [ -x ".venv/bin/mnemos" ]; then
        MNEMOS_CMD=".venv/bin/mnemos"
    elif command -v mnemos &>/dev/null; then
        MNEMOS_CMD="mnemos"
    fi

    if [ -n "$MNEMOS_CMD" ]; then
        RESUME_OUTPUT=$($MNEMOS_CMD resume 2>/dev/null)
        if [ -n "$RESUME_OUTPUT" ]; then
            echo "=== MNEMOS SESSION RESUME ==="
            echo "$RESUME_OUTPUT"
            echo ""
            echo "You are resuming from a previous session checkpoint."
            echo "Review the goal and constraints above before proceeding."
            echo "============================="
        fi
    fi
fi

# ─── Bridge iCPG if available and Mnemos DB exists ───

if [ -f ".icpg/reason.db" ] && [ -f ".mnemos/mnemo.db" ]; then
    MNEMOS_CMD=""
    if [ -x ".venv/bin/mnemos" ]; then
        MNEMOS_CMD=".venv/bin/mnemos"
    elif command -v mnemos &>/dev/null; then
        MNEMOS_CMD="mnemos"
    fi

    if [ -n "$MNEMOS_CMD" ]; then
        # Bridge in background — don't block session start
        $MNEMOS_CMD bridge-icpg &>/dev/null &
    fi
fi

# ─── Show iCPG status if available ───

if [ -f ".icpg/reason.db" ]; then
    ICPG_CMD=""
    if [ -x ".venv/bin/icpg" ]; then
        ICPG_CMD=".venv/bin/icpg"
    elif command -v icpg &>/dev/null; then
        ICPG_CMD="icpg"
    fi

    if [ -n "$ICPG_CMD" ]; then
        STATUS=$($ICPG_CMD status 2>/dev/null)
        if [ -n "$STATUS" ]; then
            echo ""
            echo "=== iCPG STATUS ==="
            echo "$STATUS"
            echo "==================="
        fi
    fi
fi

exit 0
