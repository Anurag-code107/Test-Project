### 9.1. Pre-commit implementation work

- Run `git status --porcelain`
- If output is non-empty:
  - `git add -u`
  - `git commit -m "{US-NN} FE: {title} [pre-ready-check]"`
  - If commit fails: abort with the git error
- If output is empty: skip

## Next step

Read `subagent/step-09.5-ready-check-loop.md`.
