# Step 05: detect-feature-shape

## Goal
Build an explicit shape manifest of which feature-shape patterns apply to this feature, so step 06 can load only the relevant pattern files.

## Inputs (from prior steps)
- Locked FRs and NFRs (step 01)
- Project context including `docs/patterns/INDEX.md` (step 03)

## Loads (just-in-time)
- Already loaded: `tenxengage-blueprint/docs/patterns/INDEX.md` (from step 03)

## Procedure

1. **For each row in `INDEX.md`**, evaluate the gate signal against:
   - Locked FRs and NFRs (from step 01)
   - BRD digest content (if applicable, from step 02)
   - Project context (from step 03)

2. **Build the shape manifest** — a list of pattern keys whose gate signals match. Patterns gated as "ALWAYS" are always included (e.g., `permissions-and-feature-flags`, `package-structure`).

3. **Surface the manifest to the user:**
   > "Detected feature shapes: [comma-separated list]. Anything missing or wrong?"

4. **Wait for user confirmation or correction.** If the user adds or removes a shape, update the manifest accordingly. Common corrections:
   - User adds a shape we missed (gate signal was ambiguous)
   - User removes a shape we incorrectly inferred (false positive)

## Rules (scoped to this step)
- Use natural-language judgment to evaluate gates. INDEX.md gates are descriptive, not mechanical.
- Always include "ALWAYS" patterns. They're not gated — they apply universally.
- Do NOT load pattern files in this step. That's step 06.
- Surface the manifest to the user before locking. Hidden assumptions about shape lead to wrong guidance loaded downstream.

## User interaction
Present manifest. Wait for confirmation or correction.

## Output for downstream steps
- Locked shape manifest (set of pattern keys)

## Boundary
User confirms manifest → route to step 06: read steps/step-06-load-shape-references.md`.