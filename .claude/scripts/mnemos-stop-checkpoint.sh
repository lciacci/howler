#!/bin/bash

# ── Toolchain resolution: a PATH, never a NAME, and NO bare-python3 fallback. (F-001) ──
# This block used to fall back to `python3 -m mnemos`. That fallback was the bug: with
# PYTHONPATH=scripts, ANY interpreter imports mnemos straight from source — so it did not
# fail, it silently SUCCEEDED on an unmanaged Python that Homebrew can re-point or delete.
# The original F-001 failed silently (import error → no-op); this one *worked*, on the wrong
# interpreter. A silent success is strictly harder to detect than a silent failure.
# If the toolchain is unreachable, this hook now goes QUIET. tessera-watch P9 catches that.
# Mnemos Stop Hook — writes incremental checkpoint when agent stops.
# Captures final session state so the next session can resume cleanly.
# No set -euo pipefail: hook scripts must be defensive, not strict.

INPUT=$(cat 2>/dev/null || true)
CWD=$(echo "$INPUT" | jq -r '.cwd // empty' 2>/dev/null || echo ".")

if [ ! -d "$CWD/.mnemos" ]; then
  exit 0
fi

# Write checkpoint via Python module.
# NOTE: this MUST be the top-level `checkpoint` subcommand, not `_hook
# checkpoint` — the latter is not a registered hook event, so it errored and
# silently fell through to the thin Python fallback below (placeholder goal,
# empty subgoal/narrative/files). The top-level command runs write_checkpoint(),
# which captures active goal/constraint/result nodes + git branch + uncommitted.
cd "$CWD" 2>/dev/null || true

# Resolve a mnemos-capable runner the same way the sibling hooks do. The console
# script pins its own interpreter (mnemos installed for 3.13; bare `python3` may
# be a newer homebrew python with no mnemos) — prefer it, then `python3 -m mnemos`,
# then the inline sqlite fallback below (degraded: writes file only, no db row).
if [ -x ".venv/bin/mnemos" ]; then
  MNEMOS_CMD=".venv/bin/mnemos"
elif command -v mnemos >/dev/null 2>&1; then
  MNEMOS_CMD="mnemos"
fi

if [ -n "$MNEMOS_CMD" ]; then
  $MNEMOS_CMD checkpoint --force >/dev/null 2>&1 && exit 0
fi

python3 -c "
import json, time, os, uuid

db_path = '.mnemos/mnemo.db'
cp_dir = '.mnemos/checkpoints'
latest = '.mnemos/checkpoint-latest.json'

if not os.path.exists(db_path):
    exit(0)

os.makedirs(cp_dir, exist_ok=True)

import sqlite3
conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row

nodes = conn.execute('SELECT type, content FROM mnemo_nodes WHERE status=\"active\"').fetchall()
node_types = {}
for n in nodes:
    t = n['type']
    node_types[t] = node_types.get(t, 0) + 1

# Get git state
import subprocess
branch = subprocess.run(['git', 'branch', '--show-current'], capture_output=True, text=True).stdout.strip()
status = subprocess.run(['git', 'status', '--short'], capture_output=True, text=True).stdout.strip()
uncommitted = [l.split()[-1] for l in status.split('\n') if l.strip()] if status else []

# Read fatigue
fatigue = 0.0
try:
    with open('.mnemos/fatigue.json') as f:
        fatigue = json.load(f).get('score', 0.0)
except: pass

cp_id = str(uuid.uuid4())
checkpoint = {
    'id': cp_id,
    'task_id': 'auto-stop',
    'goal': 'Session ended — auto-checkpoint',
    'active_constraints': [n['content'] for n in nodes if n['type'] == 'constraint'],
    'active_results': [n['content'] for n in nodes if n['type'] == 'result'],
    'current_subgoal': '',
    'working_memory': '',
    'task_narrative': '',
    'recent_files': [],
    'fatigue_at_checkpoint': fatigue,
    'git_state': {'branch': branch, 'uncommitted': uncommitted},
    'node_summary': {
        'total': len(nodes),
        'active': len(nodes),
        'compressed': 0,
        'by_type': node_types
    },
    'created_at': time.strftime('%Y-%m-%dT%H:%M:%S+00:00', time.gmtime())
}

with open(os.path.join(cp_dir, cp_id + '.json'), 'w') as f:
    json.dump(checkpoint, f, indent=2)
with open(latest, 'w') as f:
    json.dump(checkpoint, f, indent=2)

conn.close()
" 2>/dev/null || true

exit 0
