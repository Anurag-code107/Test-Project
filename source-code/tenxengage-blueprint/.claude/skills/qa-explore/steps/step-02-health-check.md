# Step 2 — health-check

### Skip if
`--reuse-stack` is set. Print: `"Skipping health check (--reuse-stack). Assuming BE and FE are running."` Set `BE_STARTED_BY_US=false` and `FE_STARTED_BY_US=false` (so Step 10 is a no-op even if `--cleanup` is set), then proceed to Step 3.

### Actions

Initialize tracking variables before any health check:
```
BE_STARTED_BY_US=false
FE_STARTED_BY_US=false
FE_PID=""
```

1. **Check BE health.**
   ```bash
   BE_URL="${TEST_BACKEND_URL:-http://localhost:8081}"
   STATUS=$(curl -s --max-time 10 "$BE_URL/actuator/health" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status','DOWN'))" 2>/dev/null || echo "DOWN")
   ```

   If `STATUS != "UP"`:
   - Print:
     ```
     Backend not reachable at $BE_URL (status: $STATUS).
     Start it? Run: docker compose -f ../tenxengage-backend/docker-compose.test.yml up -d --wait --wait-timeout 180
     Reply [y]es to start / [n]o to abort:
     ```
   - On `yes`: run the docker compose command. Poll `$BE_URL/actuator/health` every 5s up to 60s.
     If still not UP after 60s → STOP: `"Backend failed to start. Check docker compose logs."`.
     **On successful start, set `BE_STARTED_BY_US=true`** (Step 10 will use this to decide whether to tear down).
   - On `no` → STOP: `"Cannot explore without a running backend."`.

   If `STATUS == "UP"` (already running): leave `BE_STARTED_BY_US=false` — we didn't start it, so Step 10 must not stop it.

2. **Check FE.**
   ```bash
   FE_URL="http://localhost:3000"
   HTTP_CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" "$FE_URL" 2>/dev/null || echo "000")
   ```

   If `HTTP_CODE == "000"` (connection refused or timeout):
   - Print:
     ```
     Frontend not reachable at $FE_URL.
     Start it? Run: cd ../tenxengage-frontend && npm run dev (background process)
     Reply [y]es to start / [n]o to abort:
     ```
   - On `yes`:
     ```bash
     cd c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend
     npm run dev &
     FE_PID=$!
     ```
     Poll `http://localhost:3000` every 3s up to 60s. If still not reachable → STOP: `"Frontend failed to start."`.
     **On successful start, set `FE_STARTED_BY_US=true`** and persist `FE_PID` for Step 10.
   - On `no` → STOP: `"Cannot explore without a running frontend."`.

   If `HTTP_CODE != "000"` (already reachable): leave `FE_STARTED_BY_US=false` — Step 10 must not kill a dev server the developer started.

3. **Confirm and set URLs.**
   Print: `"Stack healthy — BE: $BE_URL | FE: $FE_URL"`
   If `BE_STARTED_BY_US=true` or `FE_STARTED_BY_US=true`, also print: `"(qa-explore started: BE=$BE_STARTED_BY_US, FE=$FE_STARTED_BY_US)"` so the developer knows what `--cleanup` would shut down.
   Save `BE_URL`, `FE_URL`, `BE_STARTED_BY_US`, `FE_STARTED_BY_US`, and `FE_PID` for downstream steps.

### Output of Step 2
- `BE_URL` and `FE_URL` confirmed reachable
- `BE_STARTED_BY_US`, `FE_STARTED_BY_US`, `FE_PID` recorded for Step 10
- Variables set for Steps 4–8

---

## Boundary

Outputs of this step:
- `BE_URL`, `FE_URL` confirmed reachable
- `BE_STARTED_BY_US`, `FE_STARTED_BY_US`, `FE_PID` recorded for step 10

Route to step 03: read `steps/step-03-build-plan.md`.
