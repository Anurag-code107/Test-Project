### 3. Claim the tracker `BE` cell (blueprint repo)

1. Re-verify blueprint branch: `git -C ../tenxengage-blueprint branch --show-current`
   - If on `features/{feature-id}`: continue.
   - If not (edge case — something switched it): `git -C ../tenxengage-blueprint checkout features/{feature-id}` (local only; do NOT fetch or pull — step 2 already resolved the state).
2. Read `features/{feature-id}/tracker.md`
3. Locate the Stories row for `US-{NN}`
4. Validate:
   - `BE` cell = `not-started`. If `in-progress` by another session → **abort** with "US-{NN} BE is already in-progress by session {id}."
   - `BE` cell is not `N/A` (sanity check — would mean layers didn't include BE; the step-2 validation should have caught this).
   - **All foundation tasks** listed in `Depends on` are `done`. Abort with specific missing deps if not.
   - **All prior stories** in `Depends on` have BE = `done` (or `N/A`). FE status of dependency stories is irrelevant when claiming a BE session. Abort with specific missing deps if not.
5. Capture wall-clock origin and reset pause accounting (see `## Time accounting` above):
   ```bash
   CLAIM_TIME_EPOCH=$(date +%s)
   CLAIM_TIME_ISO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
   HUMAN_PAUSE_TOTAL_SECS=0
   ```
   Edit the row: set `BE` → `in-progress`. Write `session=$SKILL_START_EPOCH started=$CLAIM_TIME_ISO` into the `Notes` column (use the exact captured values; do not retype the date by hand). `$SKILL_START_EPOCH` was captured at SKILL.md entry — it identifies this session uniquely within the tracker.
6. `git add features/{feature-id}/tracker.md && git commit -m "tracker: {feature-slug} {US-NN} BE → in-progress"`
7. `git push` — on reject: `git pull --rebase && git push`. Retry up to 3 times. If the cell was flipped by another session, abort.
8. **No `cd` was performed.** All blueprint ops in this step used `git -C ../tenxengage-blueprint`. Your CWD is still the backend repo. Do **not** run `cd -` or `cd ../tenxengage-blueprint` — both are bugs. If you accidentally `cd`ed into blueprint anywhere above, `cd` back to the backend repo now before continuing.

## Next step

Read `steps/step-04-create-sub-branch.md`.
