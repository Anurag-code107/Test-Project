# Step 8 — auto-fix

### Skip if
No findings in `CLASSIFIED_FINDINGS` have `severity = CRITICAL` or `severity = HIGH` with
`rootCause = "FE"`. Print: `"No CRITICAL/HIGH FE issues found — skipping auto-fix step."` and
proceed to Step 9.

### Actions

#### 8a. Create the fix sub-branch in the FE repo (once, shared across all fixes in this run)

```bash
FIX_DATE=$(date +%Y%m%d)
FIX_BRANCH="work/${SLUG}-qa-explore-${FIX_DATE}"
cd c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend
git checkout -b "$FIX_BRANCH" features/${SLUG}
```

If the branch already exists (re-entry via `--from=auto-fix`): `git checkout "$FIX_BRANCH"` instead.

#### 8b. For each CRITICAL/HIGH FE finding (process in severity order — CRITICAL first):

**Step 8b-i: Identify the component file**

1. Read the FE repo's router config. Start by looking for:
   `src/router.tsx` or `src/App.tsx` or `src/routes/` directory.
   ```bash
   find c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend/src \
     -name "router.tsx" -o -name "routes.tsx" -o -name "App.tsx" | head -5
   ```

2. Search for the route pattern in the router config:
   ```bash
   grep -r "{{ROUTE_PATTERN}}" c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend/src/
   ```
   Extract the component name and file path from the matching route entry.

3. If the finding has a console stack trace with a file path → use that path directly.

**Step 8b-ii: Dispatch the fix subagent**

For each CRITICAL/HIGH FE finding (cycle 1):

```javascript
Agent({
  description: `QA Explore auto-fix cycle 1 — ${finding.id}`,
  subagent_type: "general-purpose",
  prompt: `
    Fix a bug found during qa-explore.
    Working directory: c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend
    You are already on branch: ${FIX_BRANCH}

    Bug ID: ${finding.id}
    Severity: ${finding.severity}
    Route: ${finding.route}

    Description:
    ${finding.description}

    Reproduction steps:
    ${finding.reproduction}

    Evidence:
    - Console errors: ${JSON.stringify(finding.consoleErrors)}
    - Network errors: ${JSON.stringify(finding.networkErrors)}
    - Screenshot: ${finding.screenshotPath}

    Component file (from router): ${componentFilePath}

    Instructions:
    1. Read the component file: ${componentFilePath}
    2. Read the mutation hook or service function it uses (follow imports for the relevant API call)
    3. Diagnose the root cause. Common patterns in this codebase:
       - Mutation hooks that check response.ok before parsing JSON treat 201 responses as failures
         → Fix: check status >= 200 && status < 300 instead of response.ok
       - Route params accessed before null check when navigating directly to detail page
         → Fix: add null guard before fetch; redirect to list if param is undefined
       - Error toast fires because error handler checks for undefined instead of null (or vice versa)
         → Fix: normalize the check to handle both
    4. Make the minimal targeted fix. Do NOT refactor surrounding code.
    5. Run: npx tsc --noEmit 2>&1 | head -20
       Fix any TypeScript errors introduced by your change.
    6. Run: npm run test -- ${componentName} 2>&1 | tail -20
       (componentName = basename without extension of the component file)
       If a unit test fails because it tests the old (wrong) behavior, update the test to match
       the correct behavior.

    Print exactly:
    FIX_STATUS=applied (if you made a fix and tests pass)
    FIX_STATUS=failed (if you could not determine the fix or tests still fail after the fix)
    FIX_DESCRIPTION=<one-line summary of what changed>
    FIX_FILES=<comma-separated list of files modified>
    If failed: FIX_FAILURE_REASON=<reason>
  `
})
```

**Step 8b-iii: Verify the fix**

If fix subagent returned `FIX_STATUS=applied`:

```javascript
Agent({
  description: `QA Explore verify fix — ${finding.id}`,
  subagent_type: "general-purpose",
  prompt: `
    Verify a qa-explore auto-fix passes the Playwright test.
    Working directory: c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend

    Run:
      TEST_BACKEND_URL=${BE_URL} npx playwright test e2e/${SLUG}/qa-explore-primary.spec.ts \
        --grep "${finding.testName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}" \
        --reporter=line 2>&1

    Print:
    VERIFY_STATUS=passed (if test passes)
    VERIFY_STATUS=failed (if test still fails)
    If failed: VERIFY_FAILURE=<failure message>
  `
})
```

**If `VERIFY_STATUS=passed`:**
- Commit the fix:
  ```bash
  cd c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend
  git add ${FIX_FILES}
  git commit -m "qa-explore(${SLUG}): fix ${FIX_DESCRIPTION} [${finding.storyId ?? 'general'}]"
  ```
- Extract the commit SHA: `git rev-parse --short HEAD` → `fixCommitSha`
- Update `finding.status = "auto-fixed"` and `finding.fixCommit = fixCommitSha`

**If `VERIFY_STATUS=failed` (first verify failure):**
- Run **cycle 2**: dispatch the fix subagent again with the full evidence INCLUDING the diff of the first attempt:
  ```bash
  git -C c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend \
    diff HEAD -- ${FIX_FILES}
  ```
  Include this diff in the cycle-2 fix prompt as "First attempt (diff):" context.
- After cycle-2 fix, run the verify subagent again.
- **If `VERIFY_STATUS=passed` after cycle 2:** commit and mark `auto-fixed`.
- **If `VERIFY_STATUS=failed` after cycle 2:** mark `finding.status = "needs-human"`. Commit whatever was attempted with prefix `wip: qa-explore(${SLUG}): attempted fix for ${finding.id} — needs human review`. Move on to the next finding.

**If `FIX_STATUS=failed` (fix subagent could not determine the fix):**
- Mark `finding.status = "needs-human"`. Do not commit anything for this finding. Move on.

#### 8c. After processing all findings, print summary:

```
Auto-fix summary:
  Applied and verified: N fixes → committed to ${FIX_BRANCH}
  Needs human review: N findings
  
Review the fix branch:
  git -C ../tenxengage-frontend diff features/${SLUG}...${FIX_BRANCH}
  
If fixes look good:
  git -C ../tenxengage-frontend checkout features/${SLUG}
  git -C ../tenxengage-frontend merge --squash ${FIX_BRANCH}
  git -C ../tenxengage-frontend commit -m "qa-explore(${SLUG}): apply N auto-fixes from qa-explore run"
```

### Output of Step 8
- FE repo sub-branch `work/${SLUG}-qa-explore-${YYYYMMDD}` with auto-fix commits
- Each CRITICAL/HIGH FE finding has `status = "auto-fixed" | "needs-human"`
- BE and Config findings have `status = "needs-be-fix" | "needs-config-fix"` (unchanged from Step 7)

---

## Boundary

Outputs of this step:
- `FIX_OUTCOMES` (per-finding outcome: fixed / needs-human / skipped)
- FE sub-branch `work/<slug>-qa-explore-<YYYYMMDD>` with any auto-fix commits

Route to step 09: read `steps/step-09-report.md`.
