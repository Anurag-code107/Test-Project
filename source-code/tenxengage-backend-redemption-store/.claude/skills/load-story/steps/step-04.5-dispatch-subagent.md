### 4.5. Dispatch implementation subagent

Assemble an implementation brief from the data captured in Steps 2–4, then dispatch an implementation subagent via the `Agent` tool (foreground — this session blocks until the subagent returns).

**Brief — fill from Steps 2–4 and parsed flags:**

| Variable | Source |
|---|---|
| `$FEATURE_SLUG` | Step 2 |
| `$STORY_ID` | Step 2 (e.g., `US-05`) |
| `$STORY_FILE_PATH` | absolute path to `stories/US-{NN}-*.md` |
| `$BRANCH` | `work/{feature-slug}-{US-NN}-be` (Step 4) |
| `$ENTITIES` | `touches_entities` frontmatter (e.g., `["QuestionBank", "Question"]`) |
| `$BE_TASK_LIST` | task IDs from `## BE tasks [BE]` (e.g., `BE-1, BE-2, BE-3, BE-4`) |
| `$SPEC_PATH` | absolute path to `../tenxengage-blueprint/features/{feature-id}/spec.md` |
| `$TECHNICAL_PATH` | absolute path to `../tenxengage-blueprint/features/{feature-id}/technical.md` |
| `$PROJECT_CONTEXT_PATH` | backend repo root PROJECT-CONTEXT.md (anti-pattern register) |
| `$CONTRACTS_PATH` | contracts dirs (`contracts/endpoints/`, `contracts/models/`) for `$ENTITIES` |
| `$DOMAIN` | `domain:` from spec.md frontmatter (or `null`) |
| `$USE_TDD` | parsed flag |
| `$SOFT_STAGES` | parsed flag |

> **Before dispatching:** Substitute all `$VARIABLE` references in the prompt below with their literal values. The Agent tool does not perform variable substitution — the string you pass must be fully resolved.

**Dispatch via the `Agent` tool (foreground), passing this prompt with variables substituted:**

> You are the BE implementation subagent for load-story [$STORY_ID on $FEATURE_SLUG].
>
> The main session has already:
> - Claimed tracker BE cell → in-progress
> - Created sub-branch: $BRANCH — check it out with `git checkout $BRANCH` in the backend repo
>
> Your job: Steps 5–7.5 (precision reads → implement → tests → ready-check loop). Do NOT merge or touch the tracker.
>
> **Brief:** Feature=$FEATURE_SLUG | Story=$STORY_ID | Branch=$BRANCH | Entities=$ENTITIES | Tasks=$BE_TASK_LIST | USE_TDD=$USE_TDD | Soft stages=$SOFT_STAGES
> Spec=$SPEC_PATH | Technical=$TECHNICAL_PATH | Domain=$DOMAIN
>
> **Read `$PROJECT_CONTEXT_PATH` (the anti-pattern register) in full as your very first action — it primes every implementation decision. Then read the story file at $STORY_FILE_PATH in full — it contains the BE task blocks, non-functional notes, and the `## Spec references` list that drives step-05's reads. This ordering is mandated by step-05 step 1.**
>
> **The contracts (`contracts/endpoints/`, `contracts/models/`) for `$ENTITIES` are also loaded in full in Step 5 — see step-05's "Contracts" block for the authoritative reading procedure.**
>
> **Start by reading `.claude/skills/load-story/subagent/step-05-precision-reads.md` (relative to the backend repo root, which is your CWD). Follow each step's routing until you produce the return JSON.**

**After the Agent call returns:**

## Routing

- If `status=pass`: populate `$RC_FIXES`, `$RC_MANUAL`, `$RC_BLOCKED`, `$ANTIPATTERN_PASS` (from the subagent's `antipattern_pass` — `clean` or the listed unresolved items), `$IMPL_SUMMARY`, `$DIFF_STAT` from result. Surface `$ANTIPATTERN_PASS` in both the Step 8 approval message and the orchestrator/headless return (Step 12) so a non-`clean` value stays visible even when the per-story approval gate is skipped. Read `steps/step-08-approval-pause.md`.
- If `status=blocked`: read the ready-check review JSON for full blocker detail. Flip tracker BE cell to `blocked` with `Notes: "subagent blocked: {rc_blocked_detail}"`, commit + push tracker, surface findings to developer, stop. Read `references/failure-handling.md` for the flip procedure.
- If `status=failure`: read `references/failure-handling.md`.
