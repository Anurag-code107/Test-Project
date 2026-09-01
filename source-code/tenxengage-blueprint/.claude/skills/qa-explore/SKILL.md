---
name: "qa-explore"
description: "Autonomous QA exploration for a feature: reads story files to build an exploration plan, runs a story-guided primary pass + unconstrained secondary pass against the real stack, auto-fixes CRITICAL/HIGH FE issues (max 2 cycles each), and writes a findings report to .qa-explore/{slug}/."
argument-hint: "<feature-slug> [--story=US-NN] [--page=/route] [--role=admin|learner|seller] [--reuse-stack] [--cleanup] [--dry-run] [--from=<step>]"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

The first positional argument is `<feature-slug>` (e.g., `assessment-authoring`).

---

## Purpose

Autonomous QA exploration for a feature. Fills the gap between story-level mocked E2E tests
(which use `page.route()` and cannot catch real API response mismatches) and T1 cross-story tests
(which cover only a handful of broad workflows).

This skill reads story files to extract an exploration plan (routes, AC assertions, UI states,
interaction inventory, test world spec), runs that plan against the real running stack in two passes
(story-guided primary + unconstrained secondary), auto-fixes CRITICAL/HIGH FE issues, and writes a
structured findings report.

**On-demand only.** Does not gate any tracker row. Does not block `/run-feature`. Developer triggers
it after T1 passes or ad-hoc during development.

**Design reference:** `docs/superpowers/specs/2026-05-19-qa-explore-design.md`

---

## Flags

| Flag | Default | Purpose |
|---|---|---|
| `<feature-slug>` | required | Resolve to `features/<slug>/` directory. Always required — the skill needs feature context to set up test data even in `--page` mode. |
| `--story=US-NN` | unset | Scope to a single story. Routes restricted to that story's routes; only that story file read. |
| `--page=/route` | unset | Scope to exactly one route. All story files still read for test data context. Mutually exclusive with `--story`. |
| `--role=admin\|learner\|seller` | `admin` | Override primary exploration auth role. |
| `--reuse-stack` | off | Skip stack health check + spin-up. Use when BE and FE are already running. |
| `--cleanup` | off | After Step 9, run Step 10 (teardown) — stop BE compose stack and FE dev server, but **only if this run started them**. Stacks that were already up when health-check ran are left running. Ignored when `--reuse-stack` is set (we started nothing). |
| `--dry-run` | off | Print the exploration plan (routes, assertions, test world spec) and exit. No browser interaction. |
| `--from=<step>` | unset | Re-enter at a specific step after a halt. Valid values: `parse`, `health-check`, `build-plan`, `setup-world`, `primary-pass`, `secondary-pass`, `classify`, `auto-fix`, `report`, `teardown`. |

### Scope semantics

| Mode | Routes explored | Story files read | Test data scope |
|---|---|---|---|
| Full feature (no `--story`/`--page`) | All routes in route manifest | All `US-NN-*.md` for the feature | Complete test world for the feature |
| `--story=US-NN` | That story's routes only | That story's file only | Entities that story depends on |
| `--page=/route` | Exactly that route | All story files | Complete test world |

---

## Steps

1. **parse** — validate arguments, resolve slug → `features/<slug>/`, read feature context files
2. **health-check** — verify BE + FE reachable; offer to spin each if down
3. **build-plan** — extract route manifest + interaction inventory + test world spec from story files
4. **setup-world** — create test entities via real API calls; save `WORLD_IDS`
5. **primary-pass** — build story-guided Playwright spec; write to FE repo; dispatch execution subagent
6. **secondary-pass** — build unconstrained deviation spec; write to FE repo; dispatch execution subagent
7. **classify** — classify findings by severity; attribute root cause (FE / BE / Config)
8. **auto-fix** — for each CRITICAL/HIGH FE finding: dispatch fix subagent (max 2 cycles), verify, commit to sub-branch
9. **report** — write `.qa-explore/<slug>/YYYY-MM-DD-HH-MM-report.md`; promote Tier 1/2 learnings; commit
10. **teardown** — *only when `--cleanup` is set* — stop BE compose stack and FE dev server, but only if Step 2 started them

Each step is independently re-enterable via `--from=<step-name>`.

---

## Rules

1. **Scope is absolute.** The primary and secondary pass subagents must never follow links or navigate to routes outside `ROUTE_MANIFEST`. If a `click` would navigate outside the manifest, record the target URL in the finding's network log but do NOT follow. Add a navigation guard in the spec: check `page.url()` after each click and navigate back if the resulting URL doesn't match any manifest pattern.

2. **FE-only auto-fix.** If `rootCause = "BE"` or `rootCause = "Config"`, document the finding and move on. Never write FE code that masks a BE root cause (e.g., do not add a `?? []` fallback where the API contract says the field is always present — that would hide the real bug).

3. **Test world is shared.** Create entity data once in Step 4; reuse across Steps 5 and 6. Do not clean up between tests. Do not re-create per route. The test world is deliberately additive — exploration tests run in a world that has a variety of pre-existing entities.

4. **Two fix cycles maximum.** If a fix still fails the Playwright verify after 2 auto-fix attempts, mark `needs-human` and move on immediately. Do not loop beyond 2 cycles.

5. **Generated spec files are gitignored.** `e2e/<slug>/qa-explore-primary.spec.ts` and `e2e/<slug>/qa-explore-secondary.spec.ts` are regenerated each run. They are never committed to the feature branch.

6. **The fix sub-branch lives in the FE repo only.** Auto-fix commits go to `work/<slug>-qa-explore-<YYYYMMDD>` in `../tenxengage-frontend`. The blueprint repo tracks only the `.qa-explore/` report directory.

7. **Report .md files ARE committed.** The `.qa-explore/<slug>/` report markdown files are committed. Only the `screenshots/` subdirectory is gitignored (binary files).

8. **Re-entry via `--from=<step>`.** All steps are independently re-enterable. Steps 5–8 depend on `WORLD_IDS`. When re-entering at Step 5 or later, re-run Step 4 first (or load `WORLD_IDS` from a persisted state if the subagent printed it on the previous run). The safest re-entry is `--from=setup-world`. `--from=teardown` is supported but loses the `BE_STARTED_BY_US`/`FE_STARTED_BY_US`/`FE_PID` state from the original run — in that case Step 10 falls back to `pkill` for the FE and only stops the BE compose stack if `--cleanup` is explicit; prefer not to use `--from=teardown` and instead just run `docker compose -f ../tenxengage-backend/docker-compose.test.yml down` directly.

---

## Gitignore Entries

The following entries should be present in the blueprint repo root `.gitignore`.
Add them if they are not already present:

```
# qa-explore run artifacts
.qa-explore/*/screenshots/
```

The following entries should be present in `../tenxengage-frontend/.gitignore`.
Add them if they are not already present (check first, only add if missing):

```
# qa-explore generated specs (regenerated each run, not committed)
e2e/*/qa-explore-primary.spec.ts
e2e/*/qa-explore-secondary.spec.ts
.qa-explore-run/
```

---

## Begin

If `--from=<step>` was provided in `$ARGUMENTS`, route directly to the matching step file:

| `--from` value | Route to |
|---|---|
| `parse` | `steps/step-01-parse.md` |
| `health-check` | `steps/step-02-health-check.md` |
| `build-plan` | `steps/step-03-build-plan.md` |
| `setup-world` | `steps/step-04-setup-world.md` |
| `primary-pass` | `steps/step-05-primary-pass.md` |
| `secondary-pass` | `steps/step-06-secondary-pass.md` |
| `classify` | `steps/step-07-classify.md` |
| `auto-fix` | `steps/step-08-auto-fix.md` |
| `report` | `steps/step-09-report.md` |
| `teardown` | `steps/step-10-teardown.md` |

Otherwise, read `steps/step-01-parse.md` to begin.
