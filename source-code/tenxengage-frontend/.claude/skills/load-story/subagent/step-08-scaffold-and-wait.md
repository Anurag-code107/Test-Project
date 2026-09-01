### 8. Decide: scaffold-and-wait vs run Playwright against real BE

Re-verify blueprint branch: `git -C ../tenxengage-blueprint branch --show-current` — if not on `features/{feature-id}`, `git -C ../tenxengage-blueprint checkout features/{feature-id}` (local only; do NOT fetch or pull). Read `features/{feature-id}/tracker.md`. Note the current `BE` cell for this story.

**If BE cell != `done`:**
- Stop here in **scaffold-and-wait** mode.
- Leave the sub-branch local and un-merged.
- Freeze the Claude-active time accumulated so far (see `## Time accounting`):
  ```bash
  SCAFFOLD_ACTIVE_SECS=$(( ${SCAFFOLD_ACTIVE_SECS:-0} + $(date +%s) - CLAIM_TIME_EPOCH - HUMAN_PAUSE_TOTAL_SECS ))
  ```
  The `${SCAFFOLD_ACTIVE_SECS:-0}` is non-zero only if this exit happens during a second-or-later resume (rare but possible if BE flipped back from done — preserves prior-session active time).
- Flip tracker: keep `FE` = `in-progress`, add/update Notes: `"BE endpoint pending — scaffolded against contracts+mocks, Vitest green. Resume when BE done. scaffold_active_secs=$SCAFFOLD_ACTIVE_SECS"` (write the literal numeric value, not the variable name — the next resume invocation parses this).
- Commit tracker update, push.
- Report to the developer:

  ```
  {US-NN} FE scaffolded — waiting on BE

  Vitest: green ({N} tests)
  Playwright: deferred — BE cell = {status}, need 'done' to run against real BE
  Sub-branch: work/{feature-slug}-{US-NN}-fe (local, un-merged)

  Re-run `/load-story {feature-id} {US-NN}` once BE completes to finish the merge.
  ```
- **Exit the skill here.** Do NOT enter the approval pause. Do NOT merge. The sub-branch is preserved for resume.

**If BE cell = `done`:** continue to step 9.

## Routing

- If BE cell != `done` → return `{ "status": "scaffold-and-wait" }` immediately. Do not read any further step files.
- If BE cell = `done` → read `subagent/step-09-playwright.md`.
