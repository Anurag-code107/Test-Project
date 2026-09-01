# Design — `decompose-brd` step-04c final user-gap check

**Date:** 2026-05-10
**Skill touched:** `.claude/skills/decompose-brd/steps/step-04c-completeness-audit.md`
**Scope:** Single skill-step edit. No code, no other steps.

## Problem

Step-04c surfaces structural gaps in the BRD that the skill auto-detects (orphan reference, umbrella term, producer-consumer gap, data-model entity without functional home, miscellaneous). The user resolves each one via clarify / defer / out-of-scope, then routing moves on to step-05.

The user has no opportunity to raise gaps that the skill missed. Skill detection is heuristic and semantic — blind spots exist. If the user spots a gap the auto-pass didn't catch, there is currently no structured place to surface it before slicing commits to interpretations.

When the BRD is structurally clean by the skill's reading, step-04c "exits silently" and routes straight to step-05 — also denying the user the chance to add a gap.

## Goal

Add a final user-driven gap-collection prompt at the end of step-04c that:

1. Always runs, including when zero gaps were auto-detected.
2. Lets the user raise any number of additional gaps.
3. Routes each user-raised gap through the same clarify / defer / out-of-scope flow auto-detected gaps already use.
4. Records resolutions into the same `gapResolutions` structure, distinguished by a new `type` value.

## Non-goals

- No change to the auto-detection logic or the four typical gap types.
- No change to step-05 or step-06 — they consume `gapResolutions` unchanged.
- No change to the REQ-inventory append rules in step-04c.
- No new file. The change is contained to `step-04c-completeness-audit.md`.

## Design

### Final-check interaction

After the auto-detected-gap loop completes (whether it produced zero, one, or many gaps), step-04c prompts:

```
Auto-detected gaps complete (N resolved, M deferred, K out-of-scope).

Are there any other gaps you'd like to surface — concepts, ambiguities,
or producer/consumer holes I didn't catch?

  A) Yes — I'll describe one
  B) No — proceed to slicing
```

When the user picks **A**, they describe one gap. The skill captures:

- The concept or term they consider undefined / ambiguous / orphaned.
- The BRD anchor labels (heading text) where it appears, if any.
- A short rationale for why they consider it a gap.

The skill then offers the same three resolution options used for auto-detected gaps:

```
Possible interpretations:
  A) Clarify intent now — provide your interpretation; flows into slicing
  B) Defer with TODO — recorded as known unresolved gap; flows to Strategic Notes
  C) Out of scope — concept removed from consideration; recorded in resolutions
```

After resolution, the skill returns to the "Are there any other gaps?" prompt. The loop continues until the user picks **B**.

### Recording

User-raised gaps append to the same `gapResolutions` list. A new `type` value is introduced: `user-raised`. Existing values remain (`orphan-reference`, `umbrella-term`, `producer-consumer-gap`, `data-model-no-home`, `miscellaneous`).

The `anchors[]` field uses heading text the user cites. If the user cites no heading, the literal value `["user-raised"]` is used as the anchor.

Example:

```yaml
gapResolutions:
  - gap: assignment-grading-rubric
    type: user-raised
    anchors: ["Learning Paths and Assignments"]
    status: clarified
    interpretation: "Assignments need a per-rubric grading scheme separate from quiz auto-grading"
  - gap: instructor-bio-fields
    type: user-raised
    anchors: ["user-raised"]
    status: deferred
    reason: "Out of phase 1; revisit when instructor profile UX is scoped"
```

Inventory append behavior is unchanged: `status: clarified` user-raised gaps decompose into REQ-NNN inventory items using the same Field 3 / Field 5 rules already documented in step-04c.

### Routing

Step-04c routes to step-05 only after the final-check loop terminates with **B**. The current "exits silently" behavior on a clean BRD is removed — the final check still runs.

## Doc edits to step-04c-completeness-audit.md

1. **New section** "Final user-gap check" inserted between the existing "Record decisions" section and "Inventory updates from clarified gaps" section. Contains the prompt format, resolution-flow reuse, loop semantics, and the `user-raised` type with `["user-raised"]` anchor convention.

2. **Update** "What this step does NOT do" — remove or revise the bullet "If the BRD is structurally clean, this step exits silently (no gaps surfaced → route directly to step-05)." Replace with explicit statement that the final user-check always runs.

3. **Update** "Rules in scope for this step" — add: "**Final user check** — after auto-detection, always ask the user whether additional gaps remain; loop until user confirms none."

4. **Update** "Routing" — gate routing on completion of the final-check loop, not auto-detection.

5. **Update** the `gapResolutions` example or its surrounding prose to mention the new `user-raised` type alongside the four typical types already enumerated.

## Risks and mitigations

- **Loop fatigue.** The user could be asked many times in a row. Mitigation: the prompt is binary (A/B); declining is one keystroke. Acceptable.
- **Anchor ambiguity for free-form gaps.** Some user-raised gaps won't map to a heading. Mitigation: the literal `["user-raised"]` anchor convention is documented so downstream steps treat it consistently.
- **Inventory drift.** A user-raised clarified gap appends REQ items the same way auto-clarified gaps do; if the user is loose with interpretations, REQ inventory could grow noisy. Mitigation: existing REQ inventory rules already require capability-statement shape — applies equally here.

## Acceptance criteria

- step-04c always reaches a "any other gaps?" prompt before routing to step-05, regardless of auto-detection count.
- Picking **A** elicits a gap description, then the same three-option resolution flow.
- Picking **B** routes to step-05.
- Resolutions are recorded in `gapResolutions` with `type: user-raised`.
- `status: clarified` user-raised gaps produce REQ inventory items per the existing rules.
- The "exits silently when clean" wording is removed from the doc.
