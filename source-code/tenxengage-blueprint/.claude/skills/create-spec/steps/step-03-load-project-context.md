# Step 03: load-project-context

## Goal
Load the canonical project context per the cross-skill consumption rule. Fewer files than the legacy Phase 1 (~600 lines vs ~2,284), all ones we actually use.

## Inputs (from prior steps)
- Locked FRs and NFRs (from step 01)
- Cross-cutting BRD context if applicable (from step 02)

## Loads (always)
- `tenxengage-blueprint/PROJECT-CONTEXT.md` — application-wide standards
- `tenxengage-backend/PROJECT-CONTEXT.md` — backend conventions
- `tenxengage-frontend/PROJECT-CONTEXT.md` — frontend conventions
- `tenxengage-contracts/PROJECT-CONTEXT.md` — contracts conventions
- `tenxengage-contracts/enums-index.md` — enum registry
- `tenxengage-blueprint/docs/patterns/INDEX.md` — pattern registry
- `tenxengage-blueprint/docs/patterns/domains/INDEX.md` — domain registry (load-bearing for slot-filling features; step 04 reads `{domain}.md` on-demand based on user selection)

## Loads (globs — cheap, useful for overlap detection)
- `tenxengage-backend/src/main/java/com/tenxengage/app/entity/*.java` (filenames only)
- `tenxengage-contracts/models/*.md` (filenames)
- `tenxengage-frontend/src/components/ui/*.tsx` (filenames)
- `tenxengage-blueprint/features/*/spec.md` (filenames — for slug collision detection in step 12)
- `tenxengage-backend/src/main/resources/db/migration/V*.sql` (filenames — to find latest migration number)

## Loads (NEVER)
- `{repo}/CLAUDE.md` for any repo (auto-loaded by Claude Code; not skill input)
- `tenxengage-contracts/enums.md` full file (load JIT only when reusing/extending a specific enum)
- Templates `spec-template.md`, `technical-template.md` (load JIT in steps 13, 14)
- Code examples (DTOs, controllers, etc.) (load JIT in steps 13, 14)

## Procedure

1. Read each "always" file.
2. Run each glob; capture filename lists.
3. No user interaction.

## Rules (scoped to this step)
- This step is reading-only. No analysis, no decisions.
- Do NOT load templates or code examples here (that's steps 13, 14).
- Do NOT load conditional patterns here (that's step 06, gate-driven via INDEX.md).

## User interaction
None.

## Output for downstream steps
- Project context in conversation
- File listings (entities, models, ui components, existing features, latest migration number)

## Boundary
All files read, all globs run → route to step 04: read steps/step-04-resolve-open-questions.md`.