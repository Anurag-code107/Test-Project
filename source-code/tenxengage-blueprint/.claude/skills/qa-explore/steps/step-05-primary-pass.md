# Step 5 — primary-pass

### Inputs
- `ROUTE_MANIFEST`, `INTERACTION_INVENTORY`, `NOISE_BASELINE` from Step 3
- `WORLD_IDS` from Step 4
- `BE_URL`, `FE_URL` from Step 2
- `SLUG`, `ROLE` from Step 1

### Actions

### 5a. Build the spec content

Read the Playwright spec template from `templates/primary-spec.ts.md`. Perform the following substitutions on the template's `{{PLACEHOLDER}}` markers:

| Marker | Value source |
|---|---|
| `{{BE_URL}}` | `BE_URL` from step 02 |
| `{{FE_URL}}` | `FE_URL` from step 02 |
| `{{ROLE}}` | `ROLE` from step 01 |
| `{{SLUG}}` | feature slug from step 01 |
| `{{WORLD_IDS_JSON}}` | `JSON.stringify(WORLD_IDS)` from step 04 |
| `{{ROUTE_MANIFEST_JSON}}` | `JSON.stringify(ROUTE_MANIFEST)` from step 03 |
| `{{INTERACTION_INVENTORY_JSON}}` | `JSON.stringify(INTERACTION_INVENTORY)` from step 03 |

The template also contains loop-internal markers (`{{ROUTE}}`, `{{RESOLVED_ROUTE}}`, `{{GENERATED_AC_TESTS}}`, `{{GENERATED_UI_STATE_TESTS}}`, `{{GENERATED_PERMISSION_TESTS}}`, `{{AC_SUMMARY}}`, `{{AC_VISIBLE_ASSERTION}}`, `{{EXPECTED_TEXT}}`, `{{STATE_NAME}}`, `{{EXPECTED_UI_STATE_TEXT}}`, `{{RESTRICTED_ELEMENT_ASSERTIONS}}`, `{{TIMESTAMP}}`) that the orchestrator substitutes from per-route / per-AC / per-state / per-permission loop context. See the `## Substitutions` section of `templates/primary-spec.ts.md` for the full marker list and source.

Write the substituted result to `../tenxengage-frontend/e2e/<slug>/qa-explore-primary.spec.ts` (gitignored — regenerated each run).

#### 5b. Write the spec to the FE repo

Write the generated spec string to:
```
c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend/e2e/<slug>/qa-explore-primary.spec.ts
```

Use the Write tool directly (this file is gitignored — no commit needed).

#### 5c. Dispatch the primary-pass execution subagent

```javascript
Agent({
  description: "QA Explore — primary pass execution",
  subagent_type: "general-purpose",
  prompt: `
    Execute the primary pass Playwright spec for qa-explore.
    Working directory: c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend

    Run:
      TEST_BACKEND_URL=${BE_URL} npx playwright test e2e/${SLUG}/qa-explore-primary.spec.ts \
        --reporter=json \
        --output=.qa-explore-run/primary \
        --screenshot=on \
        --trace=retain-on-failure \
        2>&1

    Parse the test results. For each failing test, extract:
    - Test full name (test.describe title + test title)
    - Error message / assertion failure message
    - Screenshot path (from test output, usually .qa-explore-run/primary/<test-name>-failed.png)
    - Any console errors captured in the test (look for "Console errors:" in failure messages)
    - Any network errors captured (look for "network" in failure messages)
    - The route being tested (from the describe block title)

    Print exactly:
    PRIMARY_STATUS=<passed|failed|partial>
    PRIMARY_TOTAL=<N>
    PRIMARY_PASSED=<N>
    PRIMARY_FAILED=<N>
    PRIMARY_FINDINGS_JSON=<single-line JSON array of finding objects, or []>

    Each finding object:
    {
      "testName": "...",
      "route": "...",
      "failureMessage": "...",
      "screenshotPath": "...",
      "consoleErrors": ["..."],
      "networkErrors": ["..."]
    }
  `
})
```

Parse subagent output:
- `PRIMARY_STATUS`, `PRIMARY_TOTAL`, `PRIMARY_PASSED`, `PRIMARY_FAILED`
- `PRIMARY_FINDINGS_JSON` → parse as JSON array → `PRIMARY_FINDINGS`

Print: `"Primary pass complete: ${PRIMARY_PASSED}/${PRIMARY_TOTAL} passed, ${PRIMARY_FAILED} failed."`

### Output of Step 5
- `PRIMARY_FINDINGS` list (may be empty if all passed)
- Primary pass execution stats

---

## Boundary

Outputs of this step:
- `PRIMARY_FINDINGS` (list of findings from the primary-pass subagent)
- `PRIMARY_PASS_RAN` set to true

Route to step 06: read `steps/step-06-secondary-pass.md`.
