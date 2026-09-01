### 4.5. Dispatch implementation subagent

Assemble an implementation brief from the data captured in Steps 2–4, then dispatch an implementation subagent via the `Agent` tool (foreground — this session blocks until the subagent returns).

**Brief — fill from Steps 2–4 and parsed flags:**

| Variable | Source |
|---|---|
| `$FEATURE_SLUG` | Step 2 |
| `$STORY_ID` | Step 2 (e.g., `US-05`) |
| `$STORY_FILE_PATH` | absolute path to `stories/US-{NN}-*.md` |
| `$BRANCH` | `work/{feature-slug}-{US-NN}-fe` (Step 4) |
| `$ENTITIES` | `touches_entities` frontmatter (e.g., `["QuestionBank", "Question"]`) |
| `$FE_TASK_LIST` | task IDs from `## FE tasks [FE]` (e.g., `FE-1, FE-2, FE-3, FE-4`) |
| `$SPEC_PATH` | absolute path to `../tenxengage-blueprint/features/{feature-id}/spec.md` |
| `$TECHNICAL_PATH` | absolute path to `../tenxengage-blueprint/features/{feature-id}/technical.md` |
| `$PROJECT_CONTEXT_PATH` | frontend repo root PROJECT-CONTEXT.md (anti-pattern register) |
| `$BLUEPRINT_PROJECT_CONTEXT_PATH` | `../tenxengage-blueprint/PROJECT-CONTEXT.md` (anti-pattern register) |
| `$DOMAIN` | `domain:` from spec.md frontmatter (or `null`) |
| `$MOCKUP_FILE` | path to mockup file from story frontmatter (or `null`) |
| `$USE_TDD` | parsed flag |
| `$SOFT_STAGES` | parsed flag |

> **Before dispatching:** Substitute all `$VARIABLE` references in the prompt below with their literal values. The Agent tool does not perform variable substitution — the string you pass must be fully resolved.

**Dispatch via the `Agent` tool (foreground), passing this prompt with variables substituted:**

> You are the FE implementation subagent for load-story [$STORY_ID on $FEATURE_SLUG].
>
> The main session has already:
> - Claimed tracker FE cell → in-progress
> - Created sub-branch: $BRANCH — check it out with `git checkout $BRANCH` in the frontend repo
>
> Your job: Steps 5–9.5 (precision reads → implement → tests → ready-check loop). Do NOT merge or touch the tracker.
>
> **Brief:** Feature=$FEATURE_SLUG | Story=$STORY_ID | Branch=$BRANCH | Entities=$ENTITIES | Tasks=$FE_TASK_LIST | Mockup=$MOCKUP_FILE | USE_TDD=$USE_TDD | Soft stages=$SOFT_STAGES
> Spec=$SPEC_PATH | Technical=$TECHNICAL_PATH | Domain=$DOMAIN
>
> **Read the story file at $STORY_FILE_PATH in full as your first reading step — it contains the FE task blocks, E2E scenarios, and the `## Spec references` list that drives step-05's reads.**
>
> **Both anti-pattern registers (`$PROJECT_CONTEXT_PATH` and `$BLUEPRINT_PROJECT_CONTEXT_PATH`) are loaded in full in Step 5 FIRST, before the story/spec reads — see step-05's "Anti-pattern register" block for the authoritative reading procedure.**
>
> **Start by reading `.claude/skills/load-story/subagent/step-05-precision-reads.md` (relative to the frontend repo root, which is your CWD). Follow each step's routing until you produce the return JSON.**

**After the Agent call returns:**

## Routing

- If `status=pass`: populate `$RC_FIXES`, `$RC_MANUAL`, `$RC_BLOCKED`, `$ANTIPATTERN_PASS` (from the subagent's `antipattern_pass` — `clean` or the listed unresolved items), `$IMPL_SUMMARY`, `$DIFF_STAT` from result. Surface `$ANTIPATTERN_PASS` in both the Step 10 approval message and the orchestrator/headless return (Step 14) so a non-`clean` value stays visible even when the per-story approval gate is skipped. Read `steps/step-10-approval-pause.md`.
- If `status=scaffold-and-wait`: freeze scaffold time. Compute `SCAFFOLD_ACTIVE_SECS=$(( $(date +%s) - CLAIM_TIME_EPOCH - HUMAN_PAUSE_TOTAL_SECS ))`. Update tracker Notes: `"BE endpoint pending — scaffolded against contracts+mocks, Vitest green. Resume when BE done. scaffold_active_secs=$SCAFFOLD_ACTIVE_SECS"`. Commit + push tracker. Report to developer. Exit skill.
- If `status=blocked`: read `references/failure-handling.md`.
- If `status=failure`: read `references/failure-handling.md`.
