# Step 7 — classify

### Inputs
- `PRIMARY_FINDINGS` from Step 5
- `SECONDARY_FINDINGS` from Step 6

### Actions

Merge all findings into a single list. Assign `QE-<n>` IDs sequentially (QE-1, QE-2, ...).

### Apply the rubric

Read `references/classification-rubric.md`. For each finding in `PRIMARY_FINDINGS` and `SECONDARY_FINDINGS`:

1. Assign a severity using the Severity table.
2. Assign a root cause (`FE`, `BE`, or `Config`) using the Root-cause attribution rules.

Produce `CLASSIFIED_FINDINGS` as a list where each item has `{ ...originalFinding, severity, rootCause }`.

Save `CLASSIFIED_FINDINGS = [{ id: "QE-N", severity, rootCause, description, route, reproduction, storyId, screenshotPath, consoleErrors, networkErrors, beEvidence? }]`

Print:
```
Classification complete:
  CRITICAL: N
  HIGH: N
  MEDIUM: N
  LOW: N
  FE root cause: N (eligible for auto-fix)
  BE root cause: N (needs-be-fix)
  Config: N
```

### Output of Step 7
- `CLASSIFIED_FINDINGS` with full metadata per finding
- Counts per severity and root cause

---

## Boundary

Outputs of this step:
- `CLASSIFIED_FINDINGS` (each finding tagged with severity + rootCause)

Route to step 08: read `steps/step-08-auto-fix.md`.
