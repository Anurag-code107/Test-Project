# Step 04: resolve-open-questions-and-additions

## Goal
Resolve open questions (Mode 1 only — those live in a feature brief), detect slot-filling and run domain awareness (all modes), gather any net-new additions from the user (all modes), and — if the feature is builder-shaped — present a draft builder structure for review (all modes).

## Inputs (from prior steps)
- Locked FR/NFR set (step 01)
- Input mode flag
- For Mode 1: BRD slug and feature ID; the feature brief content already in conversation

## Procedure

### Phase 1 — derive draft (no user interaction)

Re-read the input (feature brief for Mode 1; user prompt / file content for Modes 2/3/4 — already in context from step 01) and extract:

1. **Open questions (Mode 1 only)** — if input mode is Mode 1, list every bullet under the brief's `## Edge cases / open questions` section, verbatim; if the section is missing or empty, record "none". For Modes 2, 3, 4 → there is no structured open-questions section to extract; record "n/a (non-Mode-1 input)" and skip to item 2.

2. **Slot-filling detection.** The feature is *slot-filling* if the input introduces or modifies any of: a builder (multi-section wizard producing a configurable entity), an audience model, an eligibility decision, a budget concept, an approval workflow, or a completion semantic. Set `$SLOT_FILLING = true | false`.

3. **Builder-shape detection.** The feature is *builder-shaped* if Phase-1 detected a builder. Set `$BUILDER_SHAPED = true | false`.

4. **Builder draft (only if `$BUILDER_SHAPED`).** From the input's FRs, business rules, and user journey (or equivalent prose for Modes 2/3/4), derive a first-pass draft:
   - Section list with lock flags (`locked` | `customizable`).
   - Per-section sub-entities and fields, where derivable from the input.
   - Mark items the input does not specify as **"needs your input"** — they become focused questions in Phase 2.

5. **Domain awareness (only if `$SLOT_FILLING`).** See the domain awareness procedure below.

### Phase 2 — present draft and gather input

This phase has THREE parts, presented in one consolidated message to the user.

**Bracket the gate with wait-accumulation:**
```bash
date +%s%3N > /tmp/create_spec_wait_started
```
(present Phase 2; on resume:)
```bash
echo $(( $(cat /tmp/create_spec_wait) + ($(date +%s%3N) - $(cat /tmp/create_spec_wait_started)) )) > /tmp/create_spec_wait
```

**Part A — open questions from the brief (Mode 1 only).**
If input mode is not Mode 1, SKIP Part A entirely and proceed to Part B. Modes 2/3/4 do not carry a structured open-questions section to surface.

For Mode 1, for each open question:
- If the brief gives context (e.g., "ADR-07 must be resolved"), include it.
- Ask the user. The user answers, or defers with "TBD" / "ask offline".
- Resolved answers → carried to step 13 for inlining into the appropriate spec section.
- Deferred items → list of `(question, target_section)` for `NEEDS_CLARIFICATION` placement in step 13.

**Part B — draft review (builder sub-flow, only if `$BUILDER_SHAPED`).**
Before presenting the builder structure draft, resolve two required fidelity fields:

1. **Visual reference:** Ask — "Which existing builder does this feature most resemble visually? (e.g., CourseBuilderLayout.tsx — enter the path relative to the frontend repo, or `null` if there is no close visual sibling)."
2. **Applicable sections:** Ask — "Which sections from the `BuilderDefinition` for this entity type will this feature render? (e.g., `basics, dates, audience, lessons, tags, rewards, publish` — list them or enter `all`)."

Hold both answers in context for use in step 13 (spec frontmatter fields `visual_reference` and `applicable_sections`).

Open with a short preamble explaining what a "builder" is, then present the draft plainly. Render approximately as:

> A "builder" is the multi-section wizard a user walks through to configure this feature — its sections, the sub-entities each section produces, and the fields on those. Below is my first-pass structure based on your input.

```
## Proposed {Feature} Builder structure

Sections:
  1. {section-key}    {locked|customizable}
       {SubEntity}:
         - {field}
         - {field}                                ← need your input

Items that need your input:
  • {focused question 1}
  • {focused question 2}

Anything in the draft that's missing or wrong?
```

The user confirms, edits, or pushes back. "Needs your input" items become focused sub-prompts; the "missing or wrong?" prompt gives them the floor.

**Part C — anything else?**
Final prompt, phrased per input mode:
- Mode 1 → "Anything else to add or change beyond what's captured in the feature brief?"
- Mode 2 → "Anything else to add or change beyond what's in your prompt?"
- Mode 3 → "Anything else to add or change beyond what's in the file?"
- Mode 4 → "Anything else to add or change beyond what's in the files?"

Each net-new item is **classified by the skill into a target spec section** (FRs, NFRs, Business Rules, Domain Concepts, Domain Events, Permissions, Data Retention, Caching Strategy, Observability, Edge Cases, Out of Scope). Present the classification back to the user and confirm.

### Domain awareness procedure (only if `$SLOT_FILLING`)

`docs/patterns/domains/INDEX.md` is already in conversation context (loaded by step 03's always-load list).

1. Prompt the user:
   ```
   This feature is filling builder-shaped slots. Which domain does it belong to?
     • incentive  (existing legacy bespoke domain — uses IncentiveAudienceRule etc.)
     • enablement (new domain — anchored on platform primitives)
     • A new domain not listed above
   ```

2. Load the domain file based on selection:
   - `incentive` → read `docs/patterns/domains/incentive.md`.
   - `enablement` → if `docs/patterns/domains/enablement.md` exists, read it; otherwise enter bootstrap flow (interactively author `enablement.md` with the user, anchored on `platform-primitives.md`; the new file is committed alongside the spec in step 16).
   - new domain → bootstrap flow: interactively author `{new-domain}.md` anchored on `platform-primitives.md`.

3. Resolve slot fillers in this order: per-`builder_type` override file > domain file > `platform-primitives.md`. Hold the resolved slot fillers in conversation context for drift detection in step 13.

4. Set `$DOMAIN` and (if applicable) `$BUILDER_TYPE` for spec frontmatter (step 13 / step 15).

5. **Slot addition guard.** If during Phase 1 derivation you identified that the canonical 8-slot list is insufficient for this feature, DO NOT propose a slot addition mid-feature. Emit `NEEDS_GOVERNANCE_DECISION: {description}` to the user and abort the run. The user resolves out-of-band before re-running.

## Output for downstream steps

- Resolved-question decisions → `{question, answer, target_section}` list
- Deferred questions → `{question, target_section}` list
- Builder structure (if applicable) → `{sections: [{key, lock, sub_entities, fields}]}`
- Net-new "anything else?" additions → `{section_name → [items]}`
- `$DOMAIN`, `$BUILDER_TYPE` (when set)
- Resolved slot fillers (when slot-filling)

## Reliability

On resume from any user-gate in this step, FIRST run the wait-accumulation command above before doing anything else.

## Boundary
All inputs gathered → route to step 05: read `steps/step-05-detect-feature-shape.md`.
