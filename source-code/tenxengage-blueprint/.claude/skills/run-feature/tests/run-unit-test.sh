#!/usr/bin/env bash
# Test driver for run-unit.sh. Runs cases in sequence, reports pass/fail.
#
# Usage: ./run-unit-test.sh                    (run all cases)
#        ./run-unit-test.sh <case_name>        (run one case)
#
# Cases live as functions named `case_<name>`. To add a case:
#   1. Write a function `case_my_case` that returns 0 on pass, non-zero on fail.
#   2. Add the name to the CASES array.

set -u
SKILL_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TESTS_DIR="$SKILL_DIR/tests"
WRAPPER="$SKILL_DIR/run-unit.sh"
FAKE_CLAUDE="$TESTS_DIR/fake-claude"

CASES=(happy_path watchdog_kills_hang crash no_orch)

# Each case must clean up its own /tmp files via cleanup_unit <slug> <unit>.
cleanup_unit() {
  local slug="$1" unit="$2"
  rm -f "/tmp/run-feature-${slug}-${unit}".{stream,transcript,session_id,result.json,done,stderr}
}

# run_wrapper <slug> <unit> <fake_mode> [extra_env=value ...]
# Calls run-unit.sh with PATH prefixed so `claude` resolves to fake-claude.
run_wrapper() {
  local slug="$1" unit="$2" mode="$3"; shift 3
  local extra_env=("$@")

  (
    # Subshell so the trap is scoped here and can't bleed to the parent
    # shell. Each run_wrapper call gets its own fake_dir + trap.
    fake_dir=$(mktemp -d)
    trap 'rm -rf "$fake_dir"' EXIT INT TERM
    ln -sf "$FAKE_CLAUDE" "$fake_dir/claude"

    if [ ${#extra_env[@]} -gt 0 ]; then
      env PATH="$fake_dir:$PATH" FAKE_CLAUDE_MODE="$mode" \
        ${HARD_TIMEOUT_SECS:+HARD_TIMEOUT_SECS=$HARD_TIMEOUT_SECS} \
        "${extra_env[@]}" \
        bash "$WRAPPER" \
          "$SKILL_DIR" sonnet "$slug" "$unit" "/load-story $slug US-99 --gate=story"
    else
      env PATH="$fake_dir:$PATH" FAKE_CLAUDE_MODE="$mode" \
        ${HARD_TIMEOUT_SECS:+HARD_TIMEOUT_SECS=$HARD_TIMEOUT_SECS} \
        bash "$WRAPPER" \
          "$SKILL_DIR" sonnet "$slug" "$unit" "/load-story $slug US-99 --gate=story"
    fi
  )
  # Subshell's exit code is the wrapper's exit code.
  return $?
}

case_happy_path() {
  local slug="testslug" unit="US-99-BE"
  cleanup_unit "$slug" "$unit"

  run_wrapper "$slug" "$unit" success >/dev/null 2>&1
  local rc=$?

  local base="/tmp/run-feature-${slug}-${unit}"
  [ "$rc" -eq 0 ]                         || { fail happy_path "wrapper exit rc=$rc"; return; }
  [ -f "$base.stream" ]                   || { fail happy_path "$base.stream missing"; return; }
  [ -f "$base.transcript" ]               || { fail happy_path "$base.transcript missing"; return; }
  [ -f "$base.session_id" ]               || { fail happy_path "$base.session_id missing"; return; }
  [ -f "$base.result.json" ]              || { fail happy_path "$base.result.json missing"; return; }
  [ -f "$base.done" ]                     || { fail happy_path "$base.done missing"; return; }

  grep -q "test-session-abc" "$base.session_id"     || { fail happy_path "session_id contents wrong: $(cat $base.session_id)"; return; }
  grep -q "rc=0" "$base.done"                       || { fail happy_path "done file rc wrong: $(cat $base.done)"; return; }
  grep -q "ORCHESTRATOR_RETURN" "$base.result.json" || { fail happy_path "result.json missing ORCHESTRATOR_RETURN"; return; }
  grep -q "working" "$base.transcript"              || { fail happy_path "transcript missing assistant text"; return; }

  cleanup_unit "$slug" "$unit"
  pass happy_path
}

case_watchdog_kills_hang() {
  local slug="testslug" unit="US-98-BE"
  cleanup_unit "$slug" "$unit"

  # Override HARD_TIMEOUT_SECS to 2s for the test.
  HARD_TIMEOUT_SECS=2 run_wrapper "$slug" "$unit" hang >/dev/null 2>&1
  local rc=$?

  local base="/tmp/run-feature-${slug}-${unit}"

  # Expect rc=143 (SIGTERM) or 137 (SIGKILL after grace).
  if [ "$rc" -ne 143 ] && [ "$rc" -ne 137 ]; then
    fail watchdog_kills_hang "expected rc 143 or 137 from watchdog kill, got $rc"
    return
  fi

  [ -f "$base.done" ] || { fail watchdog_kills_hang "$base.done missing after watchdog kill"; return; }
  grep -q "rc=" "$base.done" || { fail watchdog_kills_hang "done file malformed"; return; }

  cleanup_unit "$slug" "$unit"
  pass watchdog_kills_hang
}

case_crash() {
  local slug="testslug" unit="US-97-BE"
  cleanup_unit "$slug" "$unit"

  run_wrapper "$slug" "$unit" crash >/dev/null 2>&1
  local rc=$?

  local base="/tmp/run-feature-${slug}-${unit}"

  # Wrapper should propagate claude's exit code (1).
  [ "$rc" -eq 1 ] || { fail crash "expected wrapper rc=1, got $rc"; return; }
  [ -f "$base.done" ]   || { fail crash "$base.done missing"; return; }
  grep -q "rc=1" "$base.done" || { fail crash "done file should contain rc=1, got: $(cat $base.done)"; return; }
  grep -q "unit=$unit" "$base.done" || { fail crash "done file should contain unit=$unit, got: $(cat $base.done)"; return; }
  [ -f "$base.stream" ] || { fail crash "$base.stream missing"; return; }

  cleanup_unit "$slug" "$unit"
  pass crash
}

case_no_orch() {
  local slug="testslug" unit="US-96-BE"
  cleanup_unit "$slug" "$unit"

  run_wrapper "$slug" "$unit" no-orch >/dev/null 2>&1
  local rc=$?

  local base="/tmp/run-feature-${slug}-${unit}"

  # Wrapper should succeed even when ORCHESTRATOR_RETURN is absent —
  # the shim, not the wrapper, decides what to do with missing markers.
  [ "$rc" -eq 0 ] || { fail no_orch "expected wrapper rc=0, got $rc"; return; }
  [ -f "$base.done" ] || { fail no_orch "$base.done missing"; return; }
  grep -q "rc=0" "$base.done" || { fail no_orch "done file should contain rc=0, got: $(cat $base.done)"; return; }
  [ -f "$base.result.json" ] || { fail no_orch "$base.result.json missing"; return; }

  # The .result text should NOT contain ORCHESTRATOR_RETURN key=value
  # lines (this is the whole point of no-orch mode) — the shim is what
  # would detect this. We use jq to extract the .result field and grep
  # for the key=value pattern to avoid false positives from prose that
  # merely mentions the string (e.g. "forgot to emit ORCHESTRATOR_RETURN").
  if jq -r '.result // ""' "$base.result.json" 2>/dev/null \
      | grep -q "^ORCHESTRATOR_RETURN [A-Za-z_][A-Za-z0-9_]*="; then
    fail no_orch "result.json unexpectedly contains ORCHESTRATOR_RETURN key=value lines"
    return
  fi

  # But it should still be valid JSON with a .result field.
  if ! jq -e '.result' "$base.result.json" >/dev/null 2>&1; then
    fail no_orch "result.json missing .result field or not valid JSON"
    return
  fi

  cleanup_unit "$slug" "$unit"
  pass no_orch
}

# Pretty-printers
pass() { printf "  \033[32mPASS\033[0m %s\n" "$1"; }
fail() { printf "  \033[31mFAIL\033[0m %s: %s\n" "$1" "$2"; FAILED=1; }

FAILED=0

# Run cases
if [ $# -ge 1 ]; then
  TO_RUN=("$1")
else
  if [ ${#CASES[@]} -gt 0 ]; then
    TO_RUN=("${CASES[@]}")
  else
    TO_RUN=()
  fi
fi

if [ ${#TO_RUN[@]} -eq 0 ]; then
  echo "(no test cases registered yet)"
  exit 0
fi

for tc in "${TO_RUN[@]}"; do
  echo "Case: $tc"
  if declare -f "case_$tc" >/dev/null; then
    "case_$tc"
  else
    fail "$tc" "function case_$tc not defined"
  fi
done

if [ "$FAILED" -eq 0 ]; then
  echo "All cases passed."
  exit 0
else
  echo "Some cases failed."
  exit 1
fi
