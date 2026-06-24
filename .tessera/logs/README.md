# Tessera Event Logs

Structured event logs from Tessera sessions live here as `<session-id>.jsonl`.

Each line is a single event with timestamp, event type, and context. The
suggestion-gate recorder (`scripts/gate/emit.py`) appends `suggestion_gate`
events here — see the gate-event contract in `../tessera/docs/contracts/gate-event.md`.

This directory is gitignored — logs are session-local.
