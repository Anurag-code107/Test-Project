# Step 04c: completeness-audit

**Goal:** Surface structural incompleteness in the BRD before slicing. Detect concepts that are referenced but never defined, umbrella terms hiding distinct subtypes, producer-consumer gaps, and data model entities without functional homes. Get the user's clarification of business intent for each gap, or an explicit deferral, before the slicing in step 05 commits to interpretations.

**Inputs:** Classified BRD content from step 04 (business-truth queued for `digest.md`, technical-truth queued for `digest-annex.md`).

> **Step 04c — Detect structural gaps in the BRD; clarify intent or defer; never silently absorb.**

## Why this step exists

`/decompose-brd` previously had no pass that asked: "Is every concept the BRD references actually defined somewhere?" Concepts referenced across multiple sections of a BRD without being formally defined as authorable capabilities (e.g., "certification exam" mentioned in events, in linked-to relationships, and in the data model — but never in any functional capability section as something the admin creates) get silently absorbed into the nearest-sounding feature during slicing in step 05. The user has no opportunity to clarify business intent before that absorption distorts the result.

This step closes that gap. It runs after step 04 (probe-extract) and before step 05 (slice).

## What the step looks for

Read across all classified BRD content from steps 04 and 04b — both `digest.md` queue and `digest-annex.md` queue — and identify structural gaps via semantic understanding. Use judgment; do not restrict to a closed pattern list. The four typical types are:

| Gap type | Semantic intent |
|---|---|
| **Orphan reference** | A concept the BRD references in events, cross-section relationships, the data model, or acceptance criteria — but which no section in the BRD defines as an authorable, manageable, or configurable capability. |
| **Umbrella term** | A broad noun the BRD uses as if it covers a single thing, but which on closer reading covers multiple distinct subtypes with different lifecycles, authoring flows, or consumers — without explicit differentiation. |
| **Producer-consumer gap** | The BRD describes how a thing is consumed, linked-to, or fed-into other systems, but no section describes how it is produced or authored within scope. |
| **Data model entity without a functional home** | An entity in the BRD's data model section that no functional capability section describes creating, configuring, or managing. |

Note: these four types are typical, not exhaustive. If you observe another class of structural incompleteness while reading, surface it under a "miscellaneous gap" category. Do not skip something because it doesn't match one of the four named types.

## How to interact

Present each detected gap one at a time — consistent with step 04's existing one-question-at-a-time rule. For each gap, state:

1. The concept (or term) involved
2. The BRD anchor labels (heading text) where it appears
3. The specific evidence of the gap (which sections reference it; which section is missing the definition)
4. Three options for the user

Format:

```
Gap detected — {gap type}

The BRD references "{concept}" in [BRD anchor labels: list of heading text],
including {specific evidence: e.g., the entity exists in the data model,
events reference it, cross-section relationships link to it}, but no
section in the BRD defines how it is {created / configured / authored / disambiguated}.

Possible interpretations:
  A) Clarify intent now — provide your interpretation; flows into slicing
  B) Defer with TODO — recorded as known unresolved gap; flows to Strategic Notes
  C) Out of scope — concept removed from consideration; recorded in resolutions

Which option, or describe a different interpretation?
```

Wait for the user's response. Move to the next gap.

## Override mechanism

Every gap has the three options above. **Never block on a gap.** The defer-with-TODO option is real — it records the gap in conversation context with status `deferred` and an explicit reason from the user. Step 06 surfaces deferred gaps in Strategic Notes as `⚠️ EXPLICIT BRD GAP — DEFERRED: {gap} — {reason}`.

## Record decisions

Capture all gap resolutions in conversation context as a `gapResolutions` structure. The `gap:` field is a hyphen-separated slug (no spaces) that downstream sections will reference verbatim — e.g., field 5 of the REQ inventory uses `gap:{slug}` to point back to a resolution.

```yaml
gapResolutions:
  - gap: cert-exam-authoring
    type: orphan-reference
    anchors: ["Assessments and Quizzes", "Certification Management", "Event Architecture"]
    status: clarified
    interpretation: "Distinct capability — needs its own authoring flow within the course builder"
  - gap: assessments-umbrella
    type: umbrella-term
    anchors: ["Functional Scope > Assessments and Quizzes"]
    status: clarified
    interpretation: "Three subtypes — inline quiz (knowledge check, ungraded), end-of-course quiz (graded), certification exam (graded, certification-linked)"
  - gap: milestones-in-learning-paths
    type: orphan-reference
    anchors: ["Learning Paths and Assignments"]
    status: deferred
    reason: "Will define in Phase 2 when path orchestration is fully scoped"
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

These resolutions are inputs to:
- **Step 05** — slicing reads gap resolutions; clarified gaps inform what features to create or which features absorb the resolved concept
- **Step 06** — produces `⚠️ EXPLICIT BRD GAP — DEFERRED:` lines in Strategic Notes for every deferred gap

## Final user-gap check

After the auto-detection loop completes — and regardless of how many gaps were surfaced (including zero) — prompt the user once more to raise any gaps the skill missed. Auto-detection is heuristic; the user may see structural incompleteness the skill did not.

Format:

```
Auto-detected gaps complete ({N} resolved, {M} deferred, {K} out-of-scope).

Are there any other gaps you'd like to surface — concepts, ambiguities,
or producer/consumer holes I didn't catch?

  A) Yes — I'll describe one
  B) No — proceed to slicing
```

If the user picks **A**, ask them to state:

1. The concept or term they consider undefined, ambiguous, or orphaned.
2. The BRD anchor labels (heading text) where it appears, if any.
3. A short rationale for why they consider it a gap.

Then offer the same three resolution options used for auto-detected gaps:

```
Possible interpretations:
  A) Clarify intent now — provide your interpretation; flows into slicing
  B) Defer with TODO — recorded as known unresolved gap; flows to Strategic Notes
  C) Out of scope — concept removed from consideration; recorded in resolutions
```

After resolution, return to the "Are there any other gaps?" prompt. Loop until the user picks **B (No — proceed to slicing)** on the outer prompt.

User-raised gaps append to the same `gapResolutions` structure described above, with `type: user-raised`. The `anchors[]` field uses heading text the user cites; if they cite no heading, use the literal value `["user-raised"]` as the anchor.

Inventory append behavior is unchanged: `status: clarified` user-raised gaps decompose into REQ-NNN inventory items per the rules in "Inventory updates from clarified gaps" below. Deferred and out-of-scope user-raised gaps record in `gapResolutions` only — same as auto-detected gaps.

## Inventory updates from clarified gaps

When a gap resolves with `status: clarified`, decompose the user's interpretation into one or more new inventory items and append them to the REQ inventory built in step 04. Use the next sequential `REQ-NNN` IDs.

Each appended item follows the format defined in [../references/req-inventory-format.md](../references/req-inventory-format.md):

- **Field 1 (id)** — next sequential `REQ-NNN`.
- **Field 2 (verbatim text)** — the user's interpretation rendered as a capability statement (same shape as BRD-verbatim items: "Admin can …", "System publishes …").
- **Field 3 (anchor label)** — the most authoritative anchor from the gap's `anchors[]`. For umbrella-term resolutions where the subtypes belong under different headings, use the heading that best fits each subtype.
- **Field 4 (semantic class)** — judgment label, same vocabulary as BRD-verbatim items.
- **Field 5 (origin or gap-ref)** — `gap:{slug}` where `{slug}` matches the `gapResolutions[].gap` slug.

Umbrella-term resolutions typically yield multiple inventory items (one per subtype); orphan-reference and producer-consumer-gap resolutions typically yield one (sometimes more, if the interpretation expands scope).

Deferred and out-of-scope gap statuses do **not** produce inventory items. Deferred gaps continue to surface in step 06 Strategic Notes as `⚠️ EXPLICIT BRD GAP — DEFERRED:` lines (unchanged); out-of-scope gaps are recorded in `gapResolutions` only.

Example — given the umbrella-term resolution from "Record decisions" above (assessments → inline quiz, end-of-course quiz, certification exam), append:

```
REQ-088||Admin can author inline knowledge-check quizzes scoped to a single lesson||Assessments and Quizzes||capability-statement||gap:assessments-umbrella
REQ-089||Admin can author end-of-course graded quizzes scoped to a course||Assessments and Quizzes||capability-statement||gap:assessments-umbrella
REQ-090||Admin can author certification exams as a distinct authoring flow within the course builder||Certification Management||capability-statement||gap:cert-exam-authoring
```

The `gapResolutions` structure is preserved as-is — it holds the full gap context (type, anchors, status, interpretation/reason). The inventory items reference back to it via `gap:{slug}`.

## What this step does NOT do

- Does not edit the BRD itself. Recording user intent is internal to the skill; the BRD owner edits the source separately.
- Does not propose new features for orphan concepts. Slicing in step 05 owns that decision, informed by the resolved intent.
- Does not require resolution. Defer-with-TODO is a real option for the user, not a fallback.
- Does not enforce a specific count of gaps. Auto-detection may surface zero gaps; the final user-gap check still runs in that case.

## Rules in scope for this step

- **Semantic intent over pattern matching** — describe what to look for in plain meaning; never enumerate closed pattern lists for gap detection.
- **One question at a time** — present each gap individually; wait for user response before the next.
- **Anchor labels, not section numbers** — reference BRD locations by heading text only.
- **Override always available** — every gap has three options (clarify / defer / out-of-scope); never block.
- **No silent absorption** — if a gap exists, it is either resolved or recorded as deferred; never carried into slicing implicitly.
- **Final user check** — after auto-detection, always ask the user whether additional gaps remain; loop until the user confirms none.

## Routing

All auto-detected gaps either resolved, deferred, or marked out-of-scope (or none surfaced), AND the final user-gap check loop terminated with the user confirming no further gaps → load `steps/step-05-slice.md`.