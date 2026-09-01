### 14. Report

**Orchestrator return (emit when `$GATE != every` OR `$PHASE == merge`):**

Read `references/orchestrator-return-format.md` and emit the structured lines before the human-readable report below. Field values:
- `status` = `success`
- `unit_id` = `US-{NN}-FE` (substitute the actual story ID, e.g. `US-01-FE`)
- `branch` = `$BRANCH` (set in step-04, e.g. `work/{slug}-{US-NN}-fe`)
- `ready_check` = `green` (or `advisory-only` if `$RC_BLOCKED` was true but merge proceeded)
- `antipattern_pass` = `$ANTIPATTERN_PASS` (populated in step-04.5 from subagent `result.antipattern_pass`; `clean` or the one-line list of unresolved items)
- `advisory_findings_count` = count of items in `$RC_MANUAL` (write `0` if `$RC_MANUAL` is empty)
- `advisory_findings_path` = absolute path to `.ready-check/work/{slug}-{US-NN}-fe/advisory.json` if the file exists, otherwise `none`
- `diff_stat` = run `git diff --stat features/{feature-id}..HEAD`; if multi-line output, join lines with `;`
- `summary` = `$IMPL_SUMMARY` (populated in step-04.5 from subagent `result.summary`)

---

```
{US-NN} FE complete — {title}
Squash-merge: {short-sha} on features/{feature-id}
Tracker: FE cell = done
Wall time: {run `date +%s`, subtract $SKILL_START_EPOCH, format as Xh Ym; omit `0h ` if under 1 hour}

{If any new component file created in this session has `// Adapted from: none`:}
No production analog:
  src/components/{feature}/{Component1}.tsx  — {one-line description}
  src/pages/{feature}/{Page2}.tsx            — {one-line description}
{Add a Mirror row when the same shape appears repeatedly across stories. Omit this block entirely if no files have `// Adapted from: none`.}

{If the Step 5c Behavioral reuse gate found no existing domain hook/parser for any new data hook, SSE/stream parser, or service call:}
No reusable domain hook (building new — justification recorded in story Notes):
  src/hooks/{useNewHook}.ts  — no analog — building new because {X}
{Omit this block entirely if every new hook/parser reused or extended an existing domain implementation.}
```

If both BE and FE for this story are now done, note it. Check whether **all US-NN rows** in the tracker have BE and FE = `done` or `N/A`. If so, check the **T1 row (Cross-story integration tests)** — if T1 = `not-started`, remind the developer: "All stories done. T1 (cross-story integration tests in `test-plan.md`) must pass before the feature PR is opened. Run that session next."

## Done

This is the terminal step. The skill run is complete.
