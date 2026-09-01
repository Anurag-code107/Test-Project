# Step 06: load-shape-references

## Goal
For each shape in the manifest, load the corresponding pattern file. Future steps will reference these as needed.

## Inputs (from prior steps)
- Locked shape manifest (from step 05)

## Loads (just-in-time, per manifest)
- For each pattern key in manifest → Read `tenxengage-blueprint/docs/patterns/{key}.md`

## Procedure

1. For each pattern key in the locked shape manifest, read the corresponding pattern file. Focus on the "Spec authoring guidance" section (the consuming downstream is spec authoring); other sections are background.

2. No user interaction.

## Rules (scoped to this step)
- Read EVERY matched pattern. Do not selectively skip.
- Do NOT load patterns whose gates didn't match. They're irrelevant to this feature.
- The "Implementation guidance" section of each pattern is for downstream skills (load-spec, load-story); it's not directly applicable to spec authoring, but reading it doesn't hurt context discipline.

## User interaction
None.

## Output for downstream steps
- Pattern guidance loaded into conversation context, ready to be referenced from steps 08, 09, 10, 13, 14.

## Boundary
All matched pattern files read → route to step 07: read steps/step-07-scope-decomposition.md`.