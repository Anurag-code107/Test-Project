### 8.5. Commit ready-check report

While still on the sub-branch, commit any report files Step 7.5 wrote (and any archive snapshots from `change X` re-runs) so they squash-merge into the feature branch alongside the code. The auto-fix commit inside ready-check (Pre-Step 5) only captures modified tracked files via `git add -u`, so the report dir under `.ready-check/` remains untracked until this step.

- Run `git status --porcelain .ready-check/work/{feature-slug}-{US-NN}-be/ 2>/dev/null`
- If output is non-empty:
  - `git add .ready-check/work/{feature-slug}-{US-NN}-be/`
  - `git commit -m "chore: ready-check report for {US-NN}"`
- If output is empty (ready-check was not run, or nothing new since the last commit): skip

## Next step

Read `steps/step-09-squash-merge.md`.
