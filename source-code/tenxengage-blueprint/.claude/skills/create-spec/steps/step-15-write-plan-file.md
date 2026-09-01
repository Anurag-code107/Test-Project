# Step 15: write-plan-file

## Goal
Assemble and write the plan file. The plan file contains a dense outline plus the full verbatim spec.md and technical.md content. Wait for user plan-mode approval.

## Inputs (from prior steps)
- spec.md content (step 13)
- technical.md content (step 14)
- All accumulated findings

## Loads (just-in-time)
- `tenxengage-blueprint/.claude/skills/create-spec/references/plan-file-schema.md` (the plan file structure)

## Procedure

1. Read `plan-file-schema.md` for the assembled plan structure.

2. **Determine plan file path** — provided by the plan mode system message ("You should create your plan at ..."). Plan mode is engaged automatically by Claude Code's harness for skills that use plan files.

3. **Assemble the plan file** with these sections in order:
   - Frontmatter (YAML): `slug`, `stepsCompleted` (the 15 step names completed so far, `parse-input` through `write-plan-file`), `filesWritten: []`
   - `# Spec Plan: {feature-slug}`
   - `## Feature` — slug, folder, branch
   - `## Context` — 2-4 paragraphs (why now, scope, deferrals, de-risking findings)
   - `## Phase 0 answers (locked)` — every NFR answer from step 01
   - `## Scope summary` — single line or decomposition table
   - `## Permissions matrix` — full matrix from step 11
   - `## Kafka topics` (if any) — from step 09
   - `## NEEDS_CLARIFICATION` (if any) — list every deferred item from steps 13 and 14. No cap.
   - `## Registry edits` (if any) — list of `{file, change-description}` pairs for `docs/patterns/domains/{domain}.md` and/or new `{domain}/{builder-type}.md` override files. Step 16 applies these edits in the same commit as `features/{slug}/spec.md`.
   - `---`
   - `### File: features/{slug}/spec.md` followed by full spec.md content (verbatim from step 13)
   - `---`
   - `### File: features/{slug}/technical.md` followed by full technical.md content (verbatim from step 14)

4. **Write the plan via the Write tool** to the plan-mode-provided path. Verify it was written.

5. **Wait for user plan approval** (plan-mode gate). Use this exact approval prompt — do not paraphrase:

   > "Plan file written. Review the spec.md and technical.md sections above.
   > **Approve** → I'll proceed to step 16: create `features/{feature-slug}/` branch in **blueprint only**, write spec.md and technical.md verbatim from this plan, then auto-run /review-spec.
   > **Request changes** → describe what to revise and I'll regenerate the affected sections."

   User can request changes; if so, regenerate spec.md / technical.md content as needed and re-write the plan.

## Rules (scoped to this step)
- spec.md and technical.md content in the plan file is VERBATIM from steps 13 and 14. Do NOT re-summarize.
- Plan file is the only place these contents live until step 16 writes them to `features/{slug}/`.
- Frontmatter `stepsCompleted` array MUST include all 15 step names (`parse-input` through `write-plan-file`) — used by SKILL.md for resumption.
- Other artifacts (stories.md, tasks/foundation.md, etc.) are NOT in the plan — those are `/create-stories`'s job.

## User interaction
- Wait for plan approval. If user requests changes, address them and re-write.

## Output for downstream steps
- Approved plan file at the plan-mode-provided path.

## Boundary
User approves plan → route to step 16: read steps/step-16-branch-write-review-finalize.md`.