# Step 01a: functional-completeness probe

## Goal
Before scope is fixed, surface production functional dimensions the brief didn't address. Present all gaps in one batch, get user approval, fold approved gaps into the locked FR list.

## Inputs (from prior steps)
- Locked FR list (from step-01)
- Input mode flag (BRD identifier / direct / file / multi-file)
- Domain summary understood from step-01

## Loads (just-in-time)
- None — the probe operates on the locked FR list already in conversation context

## Procedure

### 1. Probe internally (do not print the taxonomy)

For each dimension below, ask internally: *"Does the locked FR list address this? If not, would a production implementation of this specific feature need it?"* Omit any dimension that is clearly inapplicable to this feature.

**Taxonomy (internal thinking prompts — never printed to user):**
- **Lifecycle states** — draft / publish / archive / soft-delete / undelete; permissions per stage
- **Completion & exit criteria** — when is this entity "done"?
- **Prerequisites & gating** — what must be true before this action/state is allowed?
- **Resumability & recovery** — what happens if the user leaves mid-flow or a request fails?
- **Expiry & time-bound state** — does it expire / auto-archive / re-trigger?
- **Visibility & access scope** — who sees what, when; cross-tenant boundary; role-based gating
- **Validation & bounds** — input limits, formats, uniqueness, max sizes
- **Cascades & side effects** — when X changes/deletes, what happens to dependent Y?
- **Concurrency & conflict** — two users editing the same entity; optimistic locking
- **Audit & traceability** — who did what, when; field-level capture for status transitions
- **Observability** — events/metrics needed to operate this feature in prod
- **Error recovery** — timeouts, retries, idempotency, partial-success handling
- **Domain-specific open reasoning** — anything this feature's domain needs that production can't skip?

For each applicable dimension, classify:
- **✓ Covered** — the locked FR list already addresses this; note the covering FR(s)
- **⊕ Gap** — applicable but not addressed; compose a concrete proposed FR
- **Omit** — dimension doesn't apply to this feature

### 2. Assign FR numbers to proposed gaps

Number proposed FRs sequentially after the last brief FR. If the brief ends at FR-7, gaps become FR-8, FR-9, …

Number the gaps ⊕-1, ⊕-2, … so the user can reference them in the response grammar.

### 3. Present one-shot proposal

If zero gaps are found across all applicable dimensions, skip the user interaction entirely. Proceed directly to the fold-in step with an empty gap list and the zero-gap flag set.

If gaps exist, present this preamble before the proposal:

> I've checked whether the brief covers the functional dimensions a production implementation typically needs. Items the brief already addresses are marked ✓; production gaps are marked ⊕ with a proposed FR. You can approve all, reject all, or respond per-item.

Then list each applicable dimension — covered items first as ✓ lines, then each gap as a ⊕ block:

    ✓ {Dimension — phrased naturally} — covered by FR-{N}: "{brief FR text}"

    ⊕-{N} {Gap title — phrased naturally, not taxonomy label} — brief silent; proposing:
        "FR-{N}: {Actor} {condition / verb} {outcome}"

After the list, present the response grammar:

    Approve [a]ll, [r]eject all, or specify per-item:
      a              → approve all
      r              → reject all
      a1,r2,m3=<your wording>   → approve ⊕-1, reject ⊕-2, modify ⊕-3 with your wording
      s              → skip probe (lock scope as-is)

## Rules (scoped to this step)
- The taxonomy is internal reasoning — never print dimension labels like "Resumability & recovery" in prose; phrase gaps as natural questions or statements: "When a user exits mid-setup, is their progress saved?"
- Propose only gaps that are actually relevant to this feature's domain; do not pad with theoretical concerns
- Keep proposed FR text actionable: actor + condition + outcome; no vague "the system should handle X"
- Do NOT duplicate FRs already in the locked list
- Do NOT raise gaps that are already covered by standard platform patterns (e.g., every entity has optimistic locking via `@Version` — don't flag this unless the spec's state machine makes conflicting transitions unusually likely)
- If zero gaps: say so in one sentence, skip the user prompt, proceed to fold-in

## User interaction

Mark start of human wait:
```bash
date +%s%3N > /tmp/create_spec_wait_started
```

Present the proposal (if gaps exist). Wait for user response. Parse using the `a/r/m/s` grammar:
- `a` or blank or no response → approve all
- `r` → reject all
- `a1,r2,m3=<wording>` → approve ⊕-1, reject ⊕-2, modify ⊕-3 with verbatim user wording
- `s` or `skip` → skip probe; treat all gaps as rejected

On resume, accumulate wait time:
```bash
echo $(( $(cat /tmp/create_spec_wait) + ($(date +%s%3N) - $(cat /tmp/create_spec_wait_started)) )) > /tmp/create_spec_wait
```

## Fold-in rules

After user response (or when zero gaps were found):

**Approved items** → append to the locked FR list; numbering preserved; sequence maintained.

**Modified items** → append using the user's verbatim wording, not Claude's proposal.

**Rejected items** → recorded in the probe record with status `rejected`; not added to FR list.

**Deferred items** (user says "decide later" / "TBD") → recorded in the probe record with a `⚠️ FUNCTIONAL GAP — DEFERRED` marker; not added to FR list.

**Probe record** (held in conversation context; passed to step-13 to emit as `## Functional Completeness Audit`):
- Each applicable dimension: natural-language name, status (approved / modified / rejected / deferred / already-covered)
- Approved and modified items: the final FR text as it appears in the locked FR list
- Modified items: user's verbatim wording
- Zero-gap case: "No functional gaps identified — all applicable dimensions were already covered by the brief."

## Output for downstream steps
- Updated locked FR list (brief FRs + any approved/modified probe FRs)
- Probe record (full proposal + per-item decisions)
- Zero-gap flag (boolean)

## Boundary
Probe complete (user responded, or zero gaps found) → route to step 02: read `steps/step-02-load-brd-context.md`.

**On resume from any user-gate in this step, FIRST run the wait-accumulation command above before doing anything else.**
