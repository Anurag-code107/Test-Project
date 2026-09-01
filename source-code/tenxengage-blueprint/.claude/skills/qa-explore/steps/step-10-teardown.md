# Step 10 — teardown

### Skip if
- `--cleanup` is not set, OR
- Both `BE_STARTED_BY_US` and `FE_STARTED_BY_US` are false (we started nothing — nothing to tear down).

In either case, print: `"Skipping teardown (--cleanup not set or stack was already running)."` and exit cleanly.

### Inputs
- `BE_STARTED_BY_US`, `FE_STARTED_BY_US`, `FE_PID` from Step 2

### Actions

1. **Stop the FE dev server (if we started it).**
   If `FE_STARTED_BY_US=true`:
   - If `FE_PID` is set and the process is still alive:
     ```bash
     kill "$FE_PID" 2>/dev/null
     # Wait up to 5s for graceful exit
     for i in 1 2 3 4 5; do
       if ! kill -0 "$FE_PID" 2>/dev/null; then break; fi
       sleep 1
     done
     # Force kill if still running
     if kill -0 "$FE_PID" 2>/dev/null; then
       kill -9 "$FE_PID" 2>/dev/null
     fi
     ```
   - If `FE_PID` is unset (recovered from a `--from=teardown` re-entry where state was lost), fall back to:
     ```bash
     pkill -f "vite.*--port 3000" 2>/dev/null || pkill -f "npm exec vite" 2>/dev/null || true
     ```
     Print a warning: `"FE_PID unavailable, used pkill fallback — verify no unrelated vite processes were killed."`
   - Print: `"Stopped FE dev server."`

2. **Stop the backend stack (if we started it).**
   If `BE_STARTED_BY_US=true`:
   ```bash
   docker compose -f c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-backend/docker-compose.test.yml down
   ```
   If `docker compose down` exits non-zero, print stderr and continue — do not error out the whole skill at the very end. Print: `"Stopped backend test stack."` (or `"Backend stack stop returned non-zero — check docker logs."`).

3. **Confirm.**
   Print:
   ```
   Teardown complete.
     BE stopped: $BE_STARTED_BY_US
     FE stopped: $FE_STARTED_BY_US
   To resume exploration, re-run /qa-explore (Step 2 will spin the stack again).
   ```

### Output of Step 10
- Stack stopped (only the parts this run started)
- Report from Step 9 remains committed in the blueprint repo
- Auto-fix branch (if any) remains in the FE repo for the developer to merge or discard

### Why this is opt-in
The default (no `--cleanup`) leaves the stack running so the developer can keep poking at the app, re-run `/qa-explore --from=<step>`, or run other tools against the same warmed-up environment. Most exploration sessions are interactive — automatic teardown would force a 60s+ cold start on the next attempt. Use `--cleanup` in CI-like contexts or when you're truly done.

---

## End of skill

This is the last step. Print the developer summary above and exit. No further routing.
