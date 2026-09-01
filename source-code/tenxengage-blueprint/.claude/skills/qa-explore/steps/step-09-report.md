# Step 9 — report

### Inputs
- `CLASSIFIED_FINDINGS` (with `status` updated by Step 8) from Steps 7 + 8
- `ROUTE_MANIFEST` and exploration stats from Steps 5 + 6
- `SLUG`, `SCOPE_MODE`, `STORY_ID`, `PAGE_ROUTE` from Step 1
- `BE_URL`, `FE_URL` from Step 2
- `FIX_BRANCH` from Step 8 (if auto-fix ran)

### Actions

#### 9a. Create report directory and copy screenshots

```bash
REPORT_TIMESTAMP=$(date +%Y-%m-%d-%H-%M)
REPORT_DIR=".qa-explore/${SLUG}"
SCREENSHOT_DATE=$(date +%Y-%m-%d)
SCREENSHOT_DIR=".qa-explore/${SLUG}/screenshots/${SCREENSHOT_DATE}"
mkdir -p "$REPORT_DIR" "$SCREENSHOT_DIR"
```

Copy screenshots from FE repo run artifacts to blueprint report directory:
```bash
find c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend/.qa-explore-run \
  -name "*.png" 2>/dev/null | while IFS= read -r f; do
  cp "$f" "$SCREENSHOT_DIR/"
done
```

Rename screenshots to match their finding ID. For each finding with a screenshot path, extract the
filename and rename to `QE-<n>-<original-name>.png`.

### 9b. Write the report file

Read `templates/report.md.tmpl`. Perform the following substitutions on the template's `{{PLACEHOLDER}}` markers:

| Marker | Value source |
|---|---|
| `{{SLUG}}` | step 01 |
| `{{TIMESTAMP}}` | generated now: `date -u +%Y-%m-%d-%H-%M` |
| `{{ROLE}}` | step 01 |
| `{{ROUTE_MANIFEST_LINES}}` | step 03 (rendered as a markdown bullet list) |
| `{{FINDINGS_TABLE}}` | step 07 `CLASSIFIED_FINDINGS` rendered as a severity-grouped table |
| `{{LEARNINGS_TIER_1}}` | Tier 1 learnings to be promoted to feature CLAUDE.md |
| `{{LEARNINGS_TIER_2}}` | Tier 2 learnings recorded in the report only |
| `{{LEARNINGS_TIER_3}}` | Tier 3 learnings recorded in the report only |

The template also contains summary-statistic markers (`{{BE_URL}}`, `{{FE_URL}}`, `{{N_PAGES}}`, `{{N_INTERACTIONS}}`, `{{N_PASSED}}`, `{{N_TOTAL}}`, `{{N_CRITICAL}}`, `{{N_HIGH}}`, `{{N_MEDIUM}}`, `{{N_LOW}}`, `{{N_FIXED}}`, `{{FIX_BRANCH}}`, `{{N_NEEDS_HUMAN}}`) computed from prior-step outputs. See the `Substitution variables` section of `templates/report.md.tmpl` for the full marker list and source. The shorter forms `{{NC}}`/`{{NH}}`/`{{NM}}`/`{{NL}}`/`{{N}}`/`{{REPORT_TIMESTAMP}}` used in the developer-facing summary print below (sub-section 9e) and the commit message (9d) are step-internal abbreviations, not report-template markers.

Write the substituted result to `.qa-explore/<slug>/{{TIMESTAMP}}-report.md` (committed; the `screenshots/` subdirectory next to it is gitignored).

#### 9c. Knowledge capture — Tier classification

For each CRITICAL/HIGH finding where `rootCause = "FE"` and `status = "auto-fixed"`:

Apply the curation question: _"Would a competent developer following our existing docs still make this mistake?"_

- **Yes → Tier 1 or Tier 2 candidate:**
  - Is the pattern FE-specific (mutation hook behavior, React routing, state management)? → **Tier 1**
  - Is the pattern cross-cutting (applies to BE and FE, or to all projects)? → **Tier 2**
- **No (one-off, file-specific, already documented) → Tier 3:** stays in report only

**Tier 1 promotion:** Append a bullet to the `## Pitfalls` section of `docs/patterns/frontend.md`:
```markdown
- **{{pattern-name}}:** {{one-line rule, e.g., "Mutation hooks that check response.ok will treat 201 as failure — use status >= 200 && status < 300"}}. Found in qa-explore({{SLUG}}) {{YYYY-MM-DD}}.
```

If `docs/patterns/frontend.md` does not exist: create it with the `## Pitfalls` section.

**Tier 2 promotion:** Add a single line to `PROJECT-CONTEXT.md` under the relevant existing section:
```markdown
- {{Generalized rule applicable across the codebase}}
```

**Tier 3:** No permanent doc write. Stays in report only.

#### 9d. Commit report and knowledge capture (single commit to blueprint repo)

```bash
cd c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-blueprint

git add ".qa-explore/${SLUG}/"

# Only add knowledge capture files if promotions occurred
if [ "$TIER1_COUNT" -gt 0 ] || [ "$TIER2_COUNT" -gt 0 ]; then
  git add docs/patterns/frontend.md PROJECT-CONTEXT.md
fi

git commit -m "$(cat <<'EOF'
qa-explore({{SLUG}}): {{N_CRITICAL}}c/{{N_HIGH}}h/{{N_MEDIUM}}m/{{N_LOW}}l — {{N_FIXED}} auto-fixed

Report: .qa-explore/{{SLUG}}/{{REPORT_TIMESTAMP}}-report.md
{{if Tier 1/2 promotions: one line per promoted learning}}
EOF
)"
```

If nothing was found (all tests passed, no findings): commit with:
```
qa-explore({{SLUG}}): all green — {{N}} routes, {{N}} interactions, 0 issues

Report: .qa-explore/{{SLUG}}/{{REPORT_TIMESTAMP}}-report.md
```

#### 9e. Print final developer-facing summary

```
╔══════════════════════════════════════════════════════╗
║  QA Explore Complete — {{SLUG}}                      ║
╠══════════════════════════════════════════════════════╣
║  Pages:        {{N}} routes explored                 ║
║  Interactions: {{N}} (primary) + {{N}} (secondary)   ║
║  AC coverage:  {{N_PASSED}} / {{N_TOTAL}}            ║
║  Issues:       {{NC}}c / {{NH}}h / {{NM}}m / {{NL}}l ║
║  Auto-fixed:   {{N_FIXED}} commits                   ║
║  Needs human:  {{N_NEEDS_HUMAN}}                     ║
╠══════════════════════════════════════════════════════╣
║  Report: .qa-explore/{{SLUG}}/{{TIMESTAMP}}-report   ║
╚══════════════════════════════════════════════════════╝

{{if N_FIXED > 0:}}
Fix branch: {{FIX_BRANCH}} (in tenxengage-frontend)
Review:  git -C ../tenxengage-frontend diff features/{{SLUG}}...{{FIX_BRANCH}}
Merge:   git -C ../tenxengage-frontend merge --squash {{FIX_BRANCH}}

{{if N_NEEDS_HUMAN > 0:}}
Needs human review: see findings marked "needs-human" in report.

{{if BE_FINDINGS > 0:}}
BE issues found: see findings marked "needs-be-fix" for exact HTTP evidence.
```

### Output of Step 9
- `.qa-explore/<slug>/<YYYY-MM-DD-HH-MM>-report.md` written and committed
- Tier 1/2 learnings promoted (if any)
- Developer has clear next steps

---

## Boundary

Outputs of this step:
- Report file committed at `.qa-explore/<slug>/{{TIMESTAMP}}-report.md`
- Tier 1 learnings promoted to `features/<slug>/CLAUDE.md` (if any)

Route to step 10: read `steps/step-10-teardown.md`.
