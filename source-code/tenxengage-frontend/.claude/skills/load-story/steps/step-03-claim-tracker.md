### 3. Claim the tracker `FE` cell (blueprint repo)

1. Re-verify blueprint branch: `git -C ../tenxengage-blueprint branch --show-current`
   - If on `features/{feature-id}`: continue.
   - If not (edge case — something switched it): `git -C ../tenxengage-blueprint checkout features/{feature-id}` (local only; do NOT fetch or pull — step 2 already resolved the state).
2. Read `features/{feature-id}/tracker.md`
3. Locate the Stories row for `US-{NN}`. **Note the `BE` cell value** — you will need it to decide scaffold-only vs full-E2E at step 8.
4. Validate:
   - `FE` cell = `not-started` OR `in-progress` where the in-progress session matches this one (resume case). If in-progress by a different session → **abort**.
   - `FE` cell is not `N/A`.
   - All foundation tasks in `Depends on` are `done`.
   - All prior stories in `Depends on` have both BE = `done` and FE = `done` (or `N/A`).
5. Capture wall-clock origin and reset pause accounting (see `## Time accounting` above):

   **If fresh start** (FE cell was `not-started`):
   ```bash
   CLAIM_TIME_EPOCH=$(date +%s)
   CLAIM_TIME_ISO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
   HUMAN_PAUSE_TOTAL_SECS=0
   SCAFFOLD_ACTIVE_SECS=0
   ```
   Flip `FE` → `in-progress`. Write `session=$SKILL_START_EPOCH started=$CLAIM_TIME_ISO` into the `Notes` column (use the exact captured values; do not retype the date by hand). `$SKILL_START_EPOCH` was captured at SKILL.md entry — it identifies this session uniquely within the tracker.

   **If resume** (FE cell was `in-progress` and the session id matches an existing one from a prior scaffold-and-wait exit):
   ```bash
   # Recover scaffold_active_secs=N from the Notes column (default 0 if not present)
   SCAFFOLD_ACTIVE_SECS={parsed from Notes, default 0}
   # Re-init the clock for the resume window
   CLAIM_TIME_EPOCH=$(date +%s)
   HUMAN_PAUSE_TOTAL_SECS=0
   ```
   Do **not** rewrite the `started=…` value already in Notes (that remains the wall-clock origin of the original claim).
6. `git add … && git commit -m "tracker: {feature-slug} {US-NN} FE → in-progress"`
7. `git push` (retry-on-reject up to 3 times)
8. **No `cd` was performed.** All blueprint ops in this step used `git -C ../tenxengage-blueprint`. Your CWD is still the frontend repo. Do **not** run `cd -` or `cd ../tenxengage-blueprint` — both are bugs. If you accidentally `cd`ed into blueprint anywhere above, `cd` back to the frontend repo now before continuing.

## Next step

Read `steps/step-04-create-sub-branch.md`.
