# Step 6 — secondary-pass

### Inputs
- `ROUTE_MANIFEST` and `INTERACTION_INVENTORY` from Step 3 (for selectors and route scoping)
- `WORLD_IDS` from Step 4
- `PRIMARY_FINDINGS` from Step 5 (to avoid re-testing already-known failures — skip routes where
  the baseline test already failed with a blank-page, as there is nothing useful to explore there)
- `BE_URL`, `FE_URL` from Step 2
- `SLUG` from Step 1

### Actions

### 6a. Build the spec content

Read the Playwright spec template from `templates/secondary-spec.ts.md` and the deviation reference from `references/deviation-playbook.md`. For each of the 10 deviation groups in the playbook, emit one or more concrete test cases drawn from the interaction inventory (step 03). Serialize the resulting list as `DEVIATIONS_JSON`. Then perform the following substitutions on the template's `{{PLACEHOLDER}}` markers:

| Marker | Value source |
|---|---|
| `{{BE_URL}}` | `BE_URL` from step 02 |
| `{{FE_URL}}` | `FE_URL` from step 02 |
| `{{ROLE}}` | `ROLE` from step 01 |
| `{{SLUG}}` | feature slug from step 01 |
| `{{WORLD_IDS_JSON}}` | `JSON.stringify(WORLD_IDS)` from step 04 |
| `{{ROUTE_MANIFEST_JSON}}` | `JSON.stringify(ROUTE_MANIFEST)` from step 03 |
| `{{DEVIATIONS_JSON}}` | the serialized list built above |

The template also contains loop-internal markers (`{{ROUTE}}`, `{{RESOLVED_ROUTE}}`, `{{GENERATED_DEVIATION_TESTS}}`, `{{TIMESTAMP}}`) that the orchestrator substitutes from per-route / per-deviation loop context. See the `## Substitutions` section of `templates/secondary-spec.ts.md` for the full marker list and source.

Write the substituted result to `../tenxengage-frontend/e2e/<slug>/qa-explore-secondary.spec.ts` (gitignored — regenerated each run).

#### 6b. Write the secondary-pass spec to the FE repo

Write the generated spec string to:
```
c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend/e2e/<slug>/qa-explore-secondary.spec.ts
```

This file is gitignored — no commit needed.

#### 6c. Dispatch the secondary-pass execution subagent

```javascript
Agent({
  description: "QA Explore — secondary pass execution",
  subagent_type: "general-purpose",
  prompt: `
    Execute the secondary (unconstrained deviation) pass Playwright spec.
    Working directory: c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend

    Run:
      TEST_BACKEND_URL=${BE_URL} npx playwright test e2e/${SLUG}/qa-explore-secondary.spec.ts \
        --reporter=json \
        --output=.qa-explore-run/secondary \
        --screenshot=on \
        --trace=retain-on-failure \
        2>&1

    For each failing test, extract the same fields as the primary pass.
    The secondary pass looks for ANOMALIES (crashes, blank pages, unhandled errors),
    not correctness-against-spec. Mark each finding with source="secondary".

    Print exactly:
    SECONDARY_STATUS=<passed|failed|partial>
    SECONDARY_TOTAL=<N>
    SECONDARY_PASSED=<N>
    SECONDARY_FAILED=<N>
    SECONDARY_FINDINGS_JSON=<single-line JSON array, or []>
  `
})
```

Parse output → `SECONDARY_FINDINGS` list.

Print: `"Secondary pass complete: ${SECONDARY_PASSED}/${SECONDARY_TOTAL} passed, ${SECONDARY_FAILED} anomalies found."`

### Output of Step 6
- `SECONDARY_FINDINGS` list (anomalies from deviation testing)

---

## Boundary

Outputs of this step:
- `SECONDARY_FINDINGS` (list of findings from the secondary-pass subagent)
- `SECONDARY_PASS_RAN` set to true

Route to step 07: read `steps/step-07-classify.md`.
