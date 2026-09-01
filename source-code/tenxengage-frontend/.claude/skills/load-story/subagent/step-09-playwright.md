### 9. Run Playwright against a real BE

1. Determine how to get a real BE running locally:
   - Preferred: `cd ../tenxengage-backend && git checkout features/{feature-id} && git pull && ./gradlew bootRun &` (background). Wait for the app to be ready (poll `http://localhost:8080/actuator/health` until `UP`, max 60s).
   - If the developer already has BE running, ask them. **Pause-wrap this prompt** (see `## Time accounting`):
     ```bash
     PAUSE_START=$(date +%s)
     ```
     Ask. The instant the developer replies — before doing anything else:
     ```bash
     HUMAN_PAUSE_TOTAL_SECS=$(( HUMAN_PAUSE_TOTAL_SECS + $(date +%s) - PAUSE_START ))
     ```
     If yes, skip the bootRun step.
2. Read the `## E2E test [FE]` section of the story file and collect **all scenario names** (each `**Scenario N:**` block). Run each one in turn:
   ```
   npx playwright test e2e/{feature}.spec.ts -g '{scenario test name}'
   ```
   All declared scenarios must pass. If any fail, if `$USE_TDD = true`, invoke `superpowers:systematic-debugging` before proposing fixes; otherwise diagnose directly. Then flip to `blocked` if unresolved.
2.5. **FE↔BE wire reconciliation (mandatory).** With the real BE running, capture an actual response/stream for each endpoint/SSE this story consumes (network log or a `curl`/`EventSource` probe against `localhost:8080`). For each, confirm the exact field names and discriminator values the FE code reads (e.g. the keys passed to `parsed.*`/`data.*`, drawer field names, answer-JSON keys) appear verbatim in the live payload. Any mismatch (BE emits `type`/`text`, FE reads `action`/`delta`; DTO field renamed; answer shape differs) is a wire-drift defect. Branch on the source of the drift:
   - **FE-drift (FE is wrong):** the contract and live payload agree, but the FE reads the wrong key/discriminator. Fix the FE to match the contract+live payload here, then re-run.
   - **Contract/BE-drift (the contract or BE is wrong):** the live payload disagrees with the contract, so fixing the FE is not the right call. Do NOT silently run a large autonomous contracts push. STOP and surface it as a human decision: add it to `$RC_MANUAL` (cite the exact field/discriminator and where it diverges), and if the mismatch blocks the story's scenarios from being correct, set `status=blocked` so it flows into `$RC_BLOCKED`.
   Do NOT proceed to merge with an unreconciled mismatch even if all named scenarios pass.
3. If Playwright passes → proceed to step 10
4. If Playwright fails:
   - Flip tracker `FE` → `blocked` with Notes `"Playwright failed: {one-line summary}"`
   - Push tracker
   - Surface the failure to the developer. Stop.
5. Shut down any BE instance you started (`kill %1` or similar).

## Next step

Read `subagent/step-09.1-pre-commit.md`.
