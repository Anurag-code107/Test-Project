### 12. Report

**Orchestrator return (emit when `$GATE != every` OR `$PHASE == merge`):**

Read `references/orchestrator-return-format.md` and emit the structured lines before the human-readable report below. Field values:
- `status` = `success`
- `unit_id` = `US-{NN}-BE` (substitute the actual story ID, e.g. `US-01-BE`)
- `branch` = `$BRANCH` (set in step-04, e.g. `work/{slug}-{US-NN}-be`)
- `ready_check` = `green` (or `advisory-only` if `$RC_BLOCKED` was true but merge proceeded)
- `antipattern_pass` = `$ANTIPATTERN_PASS` (populated in step-04.5 from subagent `result.antipattern_pass`; `clean` or the one-line list of unresolved items)
- `advisory_findings_count` = count of items in `$RC_MANUAL` (write `0` if `$RC_MANUAL` is empty)
- `advisory_findings_path` = absolute path to `.ready-check/work/{slug}-{US-NN}-be/advisory.json` if the file exists, otherwise `none`
- `diff_stat` = run `git diff --stat features/{feature-id}..HEAD`; if multi-line output, join lines with `;`
- `summary` = `$IMPL_SUMMARY` (populated in step-04.5 from subagent `result.summary`)

---

```
{US-NN} BE complete — {title}
Squash-merge: {short-sha} on features/{feature-id}
Tracker: BE cell = done
Wall time: {run `date +%s`, subtract $SKILL_START_EPOCH, format as Xh Ym; omit `0h ` if under 1 hour}
FE: waiting on frontend repo's /load-story {feature-id} {US-NN}

{IF the Step 5 Reuse-discovery gate found no reusable analog for any new class:}
No reusable analog (building new — justification recorded in story Notes):
  {NewClass1}  — no analog — building new because {X}
  {NewClass2}  — no analog — building new because {X}
{Omit this block entirely if every new class reused or extended an existing type.}
```

If the story is `layers: ["BE", "FE"]` and FE status is `not-started`, remind the developer that FE work for this story can now proceed.

Check whether **all US-NN rows** in the tracker have BE = `done` or `N/A`. If so, check the **T1 row (Cross-story integration tests)** — if T1 = `not-started`, note: "All BE stories done. T1 (cross-story integration tests in `test-plan.md`) must pass before the feature PR is opened. Run that session after all FE stories complete too."

## Done

This is the terminal step. The skill run is complete.
