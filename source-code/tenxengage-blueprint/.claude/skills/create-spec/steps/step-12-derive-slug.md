# Step 12: derive-slug

## Goal
Derive the canonical kebab-case slug for the feature. Confirm no collision with existing features.

## Inputs (from prior steps)
- Locked FRs (step 01) — gives the feature name
- Existing features list from glob (step 03)

## Loads (just-in-time)
- None

## Procedure

1. **Derive the slug.** Lowercase kebab-case of the feature name. Examples:
   - "Bulk CSV Import" → `bulk-csv-import`
   - "Quiz Engine" → `quiz-engine`
   - "Partner Revenue Readiness" → `partner-revenue-readiness`

2. **Check for collision** against the existing features glob loaded in step 03. If `features/{slug}/` already exists:
   > "A feature with slug `{slug}` already exists at `features/{slug}/`. Please pick a different slug for the new feature."

   **Do NOT auto-suffix.** The human picks the disambiguation.

3. **No NNN prefix.** The slug is the canonical ID for the feature.

## Rules (scoped to this step)
- Slug must be all lowercase, kebab-case (hyphens between words), no underscores, no dots.
- Collision = STOP and ask the user. Do not invent a suffix.
- Branch name will be `features/{slug}` in step 16.

## User interaction
- Collision: ask user for replacement slug, wait for input.
- No collision: no gate.

## Output for downstream steps
- Locked slug

## Boundary
Slug locked → route to step 13: read steps/step-13-generate-spec-content.md`.