#!/usr/bin/env bash
# Usage: run-unit.sh <repo_abs_path> <model> <slug> <unit_id> <slash_command>
#
# Wrapper around `claude -p` for the /run-feature shim. Runs in the
# background from the shim's perspective (Bash run_in_background=true).
#
# Writes (under /tmp/run-feature-<slug>-<unit_id>.*):
#   .stream        full stream-json log
#   .transcript    human-readable running transcript (tail -f this)
#   .session_id    inner session id (populated ~early in the run)
#   .result.json   pre-extracted terminal result event (shim parses this)
#   .done          rc=<n> sentinel
#   .stderr        claude -p's stderr
#
# Note on --setting-sources: omitted intentionally. Empirical probe
# confirmed claude -p loads all three sources (user, project, local)
# by default; the flag would be a no-op.
set -u

REPO="$1"; MODEL="$2"; SLUG="$3"; UNIT="$4"; CMD="$5"
BASE="/tmp/run-feature-${SLUG}-${UNIT}"
STREAM="${BASE}.stream"
TRANSCRIPT="${BASE}.transcript"
SESSION_FILE="${BASE}.session_id"
RESULT_FILE="${BASE}.result.json"
DONE_FILE="${BASE}.done"

cd "$REPO" || { echo "rc=2 unit=$UNIT reason=cd-failed" > "$DONE_FILE"; exit 2; }

# Echo paths to stdout so anyone running the script directly can see them.
echo "STREAM=$STREAM"
echo "TRANSCRIPT=$TRANSCRIPT"
echo "SESSION_FILE=$SESSION_FILE"
echo "RESULT_FILE=$RESULT_FILE"

# 90-min upper bound. NOT a per-unit-kind tunable; uniform. Overridable
# from env for tests. A unit hitting 90 min indicates a hang — the developer
# tails the transcript file and investigates, not raises the ceiling.
HARD_TIMEOUT_SECS="${HARD_TIMEOUT_SECS:-5400}"

# Write directly to STREAM — avoids tee >(…) process substitution which is
# unreliable on Windows Git Bash (it breaks claude -p's stdout pipe mid-session;
# the session keeps running via tool calls but can no longer emit output, so
# result.json ends up empty and the orchestrator halts). session_id, transcript,
# and result.json are extracted post-run from the completed STREAM file.
( claude -p --output-format=stream-json --verbose --include-partial-messages \
            --permission-mode=bypassPermissions --model="$MODEL" "$CMD" \
    2> "${BASE}.stderr" \
  > "$STREAM"
  exit $?
) &
PIPELINE_PID=$!

(
  sleep "$HARD_TIMEOUT_SECS"
  if kill -0 "$PIPELINE_PID" 2>/dev/null; then
    kill -TERM "$PIPELINE_PID" 2>/dev/null
    sleep 30
    kill -KILL "$PIPELINE_PID" 2>/dev/null
  fi
) &
WATCHDOG_PID=$!

wait "$PIPELINE_PID" 2>/dev/null
RC=$?

# Pipeline finished — kill the watchdog so it doesn't linger after exit.
kill "$WATCHDOG_PID" 2>/dev/null
wait "$WATCHDOG_PID" 2>/dev/null

# Post-run extraction using Python (jq may not be available on all systems).
# On Windows/Git Bash, /tmp resolves differently for Python vs Bash — use
# cygpath to hand Windows-native paths so Python can open the files.
_py_stream="$STREAM"; _py_session="$SESSION_FILE"
_py_transcript="$TRANSCRIPT"; _py_result="$RESULT_FILE"
if command -v cygpath >/dev/null 2>&1; then
  _py_stream="$(cygpath -w "$STREAM")"
  _py_session="$(cygpath -w "$SESSION_FILE")"
  _py_transcript="$(cygpath -w "$TRANSCRIPT")"
  _py_result="$(cygpath -w "$RESULT_FILE")"
fi
python3 - "$_py_stream" "$_py_session" "$_py_transcript" "$_py_result" 2>/dev/null <<'PYEOF'
import sys, json

stream_path, session_path, transcript_path, result_path = sys.argv[1:]

session_id = ""
transcript_parts = []
result_line = ""

try:
    with open(stream_path, "r", encoding="utf-8", errors="replace") as f:
        for raw in f:
            raw = raw.strip()
            if not raw:
                continue
            try:
                obj = json.loads(raw)
            except Exception:
                continue

            # Session ID — first event that carries it
            if not session_id and obj.get("session_id"):
                session_id = obj["session_id"]

            # Transcript — assistant message text / tool names
            if obj.get("type") == "assistant":
                msg = obj.get("message", {})
                for block in msg.get("content", []):
                    if isinstance(block, dict):
                        if block.get("type") == "text":
                            transcript_parts.append(block.get("text", ""))
                        elif block.get("type") == "tool_use":
                            transcript_parts.append(f"[tool: {block.get('name','')}]")

            # Result — last result event wins
            if obj.get("type") == "result":
                result_line = raw
except Exception:
    pass

with open(session_path, "w") as f:
    f.write(session_id + "\n")
with open(transcript_path, "w") as f:
    f.write("\n".join(transcript_parts))
with open(result_path, "w") as f:
    f.write(result_line)
PYEOF

echo "rc=$RC unit=$UNIT" > "$DONE_FILE"
exit "$RC"
