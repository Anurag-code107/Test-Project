# Tracker — {{feature-slug}}

**Last updated:** {{ISO date}} by {{session-id}}
**Feature PR (BE):** _(opened manually when tracker is all-green — paste URL here)_
**Feature PR (FE):** _(opened manually when tracker is all-green — paste URL here)_

> **Step 0 — Contracts (run before any foundation or story work):**
> `cd ../tenxengage-contracts && /generate-contracts {{feature-slug}}`
> FE story sessions can start immediately after contracts are generated.
> Contracts generated: **{{yes / no}}**

---

## Foundation tasks (sequential)

| # | Task | Status | Session | Started | Completed | Duration | Commit | Notes |
|---|---|---|---|---|---|---|---|---|
| F1 | Enums | not-started | — | — | — | — | — | — |
| F2 | Flyway migrations | not-started | — | — | — | — | — | — |
| F3 | Base entities + repositories | not-started | — | — | — | — | — | — |
| F4 | Permissions + feature flags seed | not-started | — | — | — | — | — | — |
| F5 | BE-only plumbing | not-started | — | — | — | — | — | N/A if no events |

_`Commit` column: squash-merge SHA on the feature branch after the sub-branch is merged locally. `Duration`: elapsed time from claim to done for this session (e.g., `1h 23m`)._

---

## Stories

_BE and FE status track independently. FE `done` = wired to real BE + E2E passes (not just mocked). Use `N/A` for the layer that doesn't apply (BE-only or FE-only stories). `BE Tests` / `FE Tests` are written by load-story after the outer-loop full suite passes; used by ready-check to skip redundant test re-runs._

| US | Title | Layers | Depends on | BE | BE Tests | FE | FE Tests | Mockup | Commit (BE) | Commit (FE) | Duration (BE) | Duration (FE) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| US-01 | {{title}} | BE + FE | Foundation | not-started | not-started | not-started | not-started | — | — | — | — | — | — |
| US-02 | {{title}} | BE | Foundation, US-01 | not-started | not-started | N/A | N/A | N/A | — | — | — | N/A | — |
| US-03 | {{title}} | FE | Foundation | N/A | N/A | not-started | not-started | — | — | — | N/A | — | — |

_`Commit (BE)` / `Commit (FE)`: squash-merge SHA on the feature branch after the sub-branch is locally merged. `BE Tests` / `FE Tests`: `not-started` until load-story outer-loop passes, then `green @ <sha>`; `N/A` for layers not in this story. `Mockup`: `N/A` = BE-only story; `—` = FE story, no mockup created (optional, never required); `src/mockups/…/File.tsx` = mockup file path once created. `Duration (BE)` / `Duration (FE)`: elapsed time from tracker claim to done for that layer session (e.g., `1h 23m`); `N/A` for layers not in this story._

---

## Cross-story integration tests

_`test-plan.md` covers full-lifecycle, contract conformance, cascade, state machine, business rules, multi-entity workflows, tenant isolation, and audit/event tests. This section tracks their execution. The feature is **not ready to ship** until this row is `done`._

| # | Scope | Status | Session | Started | Completed | Commit | Notes |
|---|---|---|---|---|---|---|---|
| T1 | All `test-plan.md` scenarios passing | not-started | — | — | — | — | — |

_`Commit`: squash-merge SHA of the integration-test PR into the feature branch._

---

## Blocked / needs attention

_(empty)_

---

## Session protocol

**Branching model:**
- Feature branch (long-lived, per repo): `features/{{feature-slug}}`
- Sub-branch per session (local-only, never pushed): `work/{{feature-slug}}-{unit-id}` e.g. `work/{{NNN}}-F1-enums`, `work/{{NNN}}-US-01-be`, `work/{{NNN}}-US-01-fe`
- **No GitHub PR for sub-branches.** Sub-branch → feature-branch = local `git merge --squash` after developer approval in chat.
- **Only one GitHub PR per feature:** `features/{{feature-slug}}` → `main`, opened manually when the tracker is all-green.

**Starting a session:**
1. Read this tracker
2. Pick an eligible row: `status = not-started`, all deps = `done`
3. Flip status to `in-progress`, add your session ID + start timestamp
4. Commit this one-line tracker change as your **first commit** in the blueprint repo; push with retry-on-reject
5. In the sibling repo (backend or frontend): ensure the feature branch exists; create sub-branch `work/{{feature-slug}}-{unit-id}` off the feature branch

**During a session:**
- Never re-verify work already marked `done` — trust prior sessions
- Work through the story/task file's `## Execution checklist`, checking items `[x]` as you complete them
- Run scoped tests per item (inner loop), full layer suite before approval

**Ending a session (on success):**
1. Run the unit's "Done when" checks — all green
2. **Pause for developer approval** in chat: show one-paragraph summary + `git diff --stat` of sub-branch vs feature branch
3. Wait for 'merge' or 'change X' reply. On 'change X': implement change, rerun tests, re-enter the pause. Never merge without explicit 'merge'.
4. On 'merge': checkout feature branch, `git merge --squash work/{{feature-slug}}-{unit-id}`, commit with message `"{unit-id}: {title}"`, push the feature branch
5. Delete the sub-branch locally
6. Back to blueprint: flip status → `done`, add completed timestamp + the squash-merge commit SHA in the `Commit` column, commit + push

**Ending a session (on failure or interrupt):**
- Flip to `blocked` with a one-line reason in Notes, push tracker. Or back to `not-started` if work was cleanly undone and sub-branch deleted.

**Consistency rule:** If a checklist item shows `[x]` but the referenced file is missing → **STOP** and surface to human. Never silently redo prior session work.

**Contract change ritual:** If a mid-story DTO change is needed:
1. Edit the relevant section of `spec.md`
2. Re-run `/generate-contracts {{feature-slug}}` (idempotent)
3. Note in the affected story row's Notes column: "contract updated — refetch types"
