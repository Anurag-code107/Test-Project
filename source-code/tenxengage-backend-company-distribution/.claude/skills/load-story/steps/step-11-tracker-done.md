### 11. Flip tracker BE → done (blueprint repo)

1. Re-verify blueprint branch: `git -C ../tenxengage-blueprint branch --show-current`
   - If on `features/{feature-id}`: continue.
   - If not (edge case — something switched it): `git -C ../tenxengage-blueprint checkout features/{feature-id}` (local only; do NOT fetch or pull).
1a. **Recover `CLAIM_TIME_EPOCH` if running under `--phase=merge`.** On a `--phase=merge` invocation the skill bypassed step-03, so `CLAIM_TIME_EPOCH` is unset. Recover it from the tracker `Notes` column (which contains `started=$CLAIM_TIME_ISO` from the original `implement` phase, per G6):

   ```bash
   if [ -z "${CLAIM_TIME_EPOCH:-}" ] || [ "${CLAIM_TIME_EPOCH:-0}" = "0" ]; then
     # Find the US-NN row in tracker.md, extract started=...ISO from its Notes column
     STARTED_ISO=$(grep "US-{NN}" "../tenxengage-blueprint/features/{feature-slug}/tracker.md" \
       | grep -oE "started=[^ |]+" | tail -1 | sed 's/started=//')
     if [ -n "$STARTED_ISO" ]; then
       # Parse ISO 8601 to epoch — try BSD date (macOS) then GNU date (Linux)
       CLAIM_TIME_EPOCH=$(date -u -j -f "%Y-%m-%dT%H:%M:%SZ" "$STARTED_ISO" +%s 2>/dev/null \
         || date -u -d "$STARTED_ISO" +%s 2>/dev/null \
         || echo "")
       HUMAN_PAUSE_TOTAL_SECS=${HUMAN_PAUSE_TOTAL_SECS:-0}
     fi
     if [ -z "${CLAIM_TIME_EPOCH:-}" ]; then
       # No started= field in Notes — proceed with sentinel; duration formatting below handles this.
       DURATION_OVERRIDE="<orchestrator-driven>"
     fi
   fi
   ```

2. Compute the completed timestamp and Claude-only active duration (see `## Time accounting` above):
   ```bash
   COMPLETED_ISO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
   NOW_EPOCH=$(date +%s)
   ACTIVE_SECS=$(( NOW_EPOCH - CLAIM_TIME_EPOCH - HUMAN_PAUSE_TOTAL_SECS ))
   ```
   Format `ACTIVE_SECS` as `Xh Ym` (e.g., `2h 05m`); omit `0h ` prefix if under 1 hour (e.g., `47m`). Sanity check: if `ACTIVE_SECS < 0` (clock skew or instrumentation bug), do not write a negative duration — write the formatted wall-clock elapsed (`NOW_EPOCH - CLAIM_TIME_EPOCH`) and append `(pause-tracking error)` to Notes.
3. Edit `features/{feature-id}/tracker.md`:
   - Stories row `US-{NN}`: `BE` → `done`
   - `BE Tests` → `green @ {first 12 chars of SHA from step 9}`
   - `Commit (BE)` → first 12 chars of the SHA from step 9
   - `Duration (BE)` → if `$DURATION_OVERRIDE` is set (recovery in 1a failed to find a `started=` value), write `$DURATION_OVERRIDE` literally; otherwise the formatted `ACTIVE_SECS` from above. This is **Claude execution time only**; it excludes the developer approval pause and any other interactive waits.
   - `Notes`: append `BE completed $COMPLETED_ISO` (write the exact captured value; do **not** retype the date by hand — that is the bug class this skill guards against). If `$RC_MANUAL` is non-empty, also append one line per item: `[{severity}] {file}:{line} — {rule}: {manualAction}`.
4. `git add … && git commit -m "tracker: {feature-slug} {US-NN} BE → done ({short-sha})"`
5. `git push` (retry-on-reject)

## Next step

Read `steps/step-12-report.md`.
