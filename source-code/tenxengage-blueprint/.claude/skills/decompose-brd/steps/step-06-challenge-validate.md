# Step 06: challenge-validate

**Goal:** Push back on the BRD where appropriate (strategic-partner mentality, not extractor). Then run pre-write validation: catch inconsistencies and omissions before step 07 writes anything. Fix everything found before routing forward.

**Inputs:** Slicing + FRs + seeds from step 05; classified BRD content from step 04; persona/partner-type prerequisites queued in step 02.

> **Step 06 — Strategic challenge then pre-write validation. Both must pass.**

## Strategic challenge pass

Look for:

- **Hidden assumptions** — places where the BRD assumes something without stating it. Flag for confirmation.
- **Scope creep candidates** — capabilities that look Phase-1 in the BRD but are actually nice-to-have. Propose deferral with reasoning.
- **Riskiest unknown overall** — across all slices, the single thing most likely to hurt the initiative if wrong. Surface it as the start-here recommendation.
- **Missing requirements** — gaps where the BRD doesn't specify something the slicing surfaces (e.g., "BRD lists 10 personas but no per-persona dashboard requirement — confirm intent").
- **Phasing deviations** — every place where the synthesized phase recommendation from step 05 diverges from the BRD's phasing. Each deviation gets an explicit reasoning note.
- **System-catalog prerequisites** — any new role or partner type approved in step 02 must surface as a Strategic Notes bullet of the form `⚠️ PREREQUISITE: {kind} '{name}' — {what change is required, in which file/table}`. These are not deferrable; they must ship before any feature that depends on them.
- **Alternatives** — places where the BRD picks an approach that may not be the right one. Propose alternative if material; otherwise stay quiet.

Capture findings as a "Strategic Notes" section for the roadmap. Keep the discretionary findings (hidden assumptions, scope creep candidates, alternatives, etc.) concise — typically a small handful. Mandatory items — system-catalog prerequisites, deferred gaps from step 04c, sanity nudges for outlier feature/FR/seed counts, explicit BRD-acceptance deferrals — are added on top and not subject to a discretionary cap. Better to be complete than to artificially trim a real prerequisite.

**G9 extension check (when BRD acknowledges existing platform capabilities)**: Does this BRD acknowledge existing platform capabilities? If yes, are the proposed slices framed as *extensions* (adding to what exists) or *rebuilds* (replacing what exists)? Misframing greenfield on top of existing scaffolding leads to bloated scope estimates. Surface this in Strategic Notes where applicable.

## Inventory format integrity

Before coverage validation runs, verify each line of the requirement inventory is well-formed. Every line must contain exactly four `||` separators (i.e., five positional fields per [../references/req-inventory-format.md](../references/req-inventory-format.md)).

For each malformed line, surface in Strategic Notes as:

```
⚠️ INVENTORY FORMAT ERROR: line {N} — {first 60 chars of the line}
```

Malformed lines are pre-write blockers and are skipped by the coverage check (they cannot be reliably set-compared). The user revises the inventory before step 07 writes. If a `||` literal genuinely appeared in a BRD requirement and step 04 missed the substitution, fix it now per the collision policy in the format reference.

## Coverage validation

For every feature in the slicing, verify two coverage relationships against the requirement inventory (combined: BRD-verbatim items from step 04 plus gap-resolution items appended by step 04c). Coverage gaps surface to the user as decisions; ungrounded FRs surface for revision before write.

**Check: every REQ-NNN is covered.** Every requirement inventory item must appear as a `Source:` tag on at least one FR somewhere in the feature set. Build the set of all source-tagged REQ-NNN IDs across all FRs in all features; compare against the full inventory set; surface the difference.

For each uncovered requirement, present:

```
Coverage gap detected:
  REQ-{NNN} — "{verbatim text}"
  Source anchor: {BRD heading text}
  
This requirement is not covered by any FR in any feature. Options:
  A) Link to existing FR — name the feature and FR (skill adds source tag to that FR)
  B) Add new FR to feature {F-NN} — describe the FR
  C) Add a new feature for this requirement — describe the slice
  D) Defer with TODO (recorded in Strategic Notes as ⚠️ EXPLICIT BRD GAP — DEFERRED)
  E) Remove from inventory if not actually a requirement
```

Wait for the user's response. Apply the chosen option to conversation state. Move to the next gap.

**Check: every FR is grounded.** Every FR must have a `Source:` tag listing one or more `REQ-NNN` IDs from the inventory. Surface ungrounded FRs as:

```
Ungrounded FR detected:
  {FR-NN.X} in feature {F-NN}: "{FR text}"
  
This FR has no inventory source. Options:
  A) Inventory missed a real requirement — name the BRD anchor and verbatim text; skill adds a new REQ to inventory and links the FR
  B) FR represents an unresolved gap from step 04c — name the gap and choose one path:
       - Clarify now: provide an interpretation; skill appends the resulting REQ-NNN item(s) to the inventory and links the FR
       - Defer: skill records the gap in Strategic Notes as ⚠️ EXPLICIT BRD GAP — DEFERRED and removes the FR from the feature set
  C) FR is invented and should be removed
```

Wait for the user's response. Move to the next ungrounded FR.

## FR quality validation

For each FR across all features, verify by judgment that it satisfies all four criteria. Failing FRs surface inline with concrete reasoning; the user (or a re-prompt to the LLM) revises before write. These checks are semantic — described as judgment criteria, not pattern matches.

The four criteria each FR must satisfy:

1. **Describes a capability or outcome from the user/business perspective.** "Vendor PSM can push specific training to any participant in one click" — yes. "PUT /api/v1/training/push" — no.
2. **Doesn't reference technical artifacts.** No entity names, API operation names, schema field names, code paths. If the FR can only be read by an engineer, it has drifted.
3. **Is concretely observable.** "System surfaces recommendations within 30 minutes" — yes. "System is performant" or "System is reliable" — no.
4. **Is appropriately scoped to the feature's primary persona and journey.** An FR that belongs to a different feature's persona or journey is mis-placed and should be moved.

For each FR failing one or more criteria, surface:

```
FR quality issue:
  {FR-NN.X} in feature {F-NN}: "{FR text}"
  Failing criteria: [list which of the four it fails]
  Reasoning: [specific issue — e.g., "references CourseEntity which is a technical artifact"]
  
Suggested rewrite: [LLM-generated rewrite that fixes the issue, for the user to accept or revise]
```

Apply accepted rewrites to conversation state before moving on.

## Cross-feature integrity validation

These are skill-internal correctness checks. They are NOT presented to the user as decisions — the user does not have business intent to apply to a broken cross-reference or a duplicate ID. When step 06 finds a structural integrity issue, the LLM evaluates the issue against its own slicing context and edits the affected briefs in conversation state to fix it. After fixing, re-run the integrity checks. If structural issues persist after one in-place fix attempt, the skill aborts step 06 with a clear error message naming the unresolved issues — rather than writing structurally invalid output to disk.

**Checks to run:**

1. **Dependencies resolve.** Every `Dependencies: F-NN` field on every feature points to a feature that exists in the same roadmap.
2. **Cross-feature signal pairs match.** For every cross-feature signal emitted by feature A that names a specific consumer feature B, verify that B has a corresponding receiving FR. Signals are written in business intent (per step 05's "describe the business fact, not the event name"), so this check is semantic — read the consumer's feature brief and confirm coverage. If the signal's consumer is ambiguous or the BRD framed it as cross-quadrant rather than cross-feature, leave the check alone (do not invent a missing receiver).
3. **No duplicate IDs.** No two features share an F-NN. No two FRs within a feature share an FR-NN.X. No two seeds within a feature share an S-NN. No two seeds globally share an F-NN.S-NN.
4. **Dependency graph is acyclic.** Build the dependency graph from all features; verify no cycles.
5. **Phase chain is valid.** No feature in Phase 1 has a dependency on a feature in Phase 2 or Phase 3. No Phase 2 feature has a dependency on a Phase 3 feature. (Foundation must ship before consumers; phase chain enforces temporal ordering.)
6. **BRD anchors exist.** Every BRD anchor label cited in any feature brief, FR, or seed exists as a heading in the source BRD. (Catches misspelled or stale anchor citations.)
7. **No empty required sections.** Every feature brief has Business Outcome, Primary Persona, FR list, and Story Seeds non-empty.

**On failure:**

1. **In-place fix attempt** — LLM evaluates the issue and edits conversation state. Examples:
   - `Dependencies: F-09` where F-09 doesn't exist → update to the actual feature ID the dependency was meant to point to, OR remove the dependency entirely if no real dependency exists
   - Duplicate FR ID → renumber the second occurrence
   - Missing receiving FR on a consumer feature → add the corresponding receiving FR
   - Stale BRD anchor → re-read the BRD and update to the correct heading text, or remove the anchor citation
2. **Re-run validation** — re-run the integrity checks on the updated state.
3. **Abort if unresolvable** — if structural issues persist after one in-place fix attempt, abort step 06 with a clear error: "Step 06 found unresolvable structural integrity issues: [list]. This is a skill bug — please report it to the maintainer of the `/decompose-brd` skill (typically the team that authored this `.claude/skills/decompose-brd/` directory)."

The user is never prompted to make a decision on a structural integrity issue. If the skill cannot self-correct, that is a skill bug to fix, not a user task.

## Pre-write validation

**Consistency checks** — fix any issues found:
- [ ] **Deferrals vs BRD acceptance** — for every P2/P3 deferral, check if a corresponding BRD acceptance-language clause exists. Any deferred capability that appears in BRD acceptance language → add `"⚠️ EXPLICIT DEFERRAL FROM BRD v1 ACCEPTANCE: {capability} (source: {anchor label}) — {reason}"` in Strategic Notes. No silent deferrals.
- [ ] **Digest/roadmap consistency** — phase labels match; digest personas match per-slice personas; digest ADRs match per-slice ADR blockers.
- [ ] **Persona sanity** — every agent/journey slice primary persona = recipient of action, not escalation target or configurator.
- [ ] **Phasing deviations** — every deviation from BRD phasing is in Strategic Notes with explicit reasoning.
- [ ] **FR completeness** — every feature has at least one FR in business language. Sanity nudges fire for features with extreme outlier counts (e.g., 1 FR or 30 FRs) and surface as Strategic Notes "verify slice scope" entries; these are nudges, not validation gates.
- [ ] **FR source completeness** — every FR has a `Source:` tag listing one or more `REQ-NNN` IDs from the inventory. Ungrounded FRs are surfaced via Coverage validation above before reaching this checklist.
- [ ] **Coverage completeness** — every requirement inventory item has at least one `Source:` reference somewhere in the feature set. Coverage gaps are surfaced via Coverage validation above before reaching this checklist; this row confirms the post-resolution state.
- [ ] **Seed completeness** — every feature has planning-level story seeds describing its journey; every FR maps to at least one seed; FRs with no seed coverage flagged. Sanity nudges fire for features with extreme outlier seed counts (e.g., 1 or 20 seeds) and surface as Strategic Notes "verify slice scope" entries; these are nudges, not validation gates.

**Artifact completeness** — verify steps 04 + 05 captured everything:

*Digest (business-truth):*
- [ ] All BRD numeric SLAs → digest §Concrete SLAs (or BRD has none → section omitted)
- [ ] All load-bearing decision tables → digest §Mission-critical tables verbatim (excluding RBAC, error contracts, KPI tables, risk registers — those have dedicated sections)
- [ ] All v1 acceptance criteria → digest §v1 Acceptance criteria verbatim (or none → omitted)
- [ ] BRD non-goals → digest §Non-goals (v1), separate from "Does NOT own" (or none → omitted)
- [ ] All undefined terms-of-art → digest §Undefined terms-of-art as ADRs (or none → omitted)
- [ ] All companion docs → digest §Companion docs with repo-existence flag (or none → omitted)
- [ ] "Already includes" prose → digest §Current-state foundation verbatim (or none → omitted)
- [ ] All BRD KPIs (metric + target + data source) → digest §KPIs / Success metrics verbatim (or none → omitted)
- [ ] All BRD-stated risks → digest §Risks register verbatim (or none → omitted)
- [ ] Every per-slice block has BRD anchor labels (heading text, not §-numbers)

*Digest-annex (technical-truth, advisory):*
- [ ] Named events → annex §Event vocabulary (or none → section omitted within annex)
- [ ] Named entities (any shape — CamelCase, snake_case, ENUM) → annex §Entity inventory (or none → section omitted)
- [ ] API ops → annex §API surface (or none → section omitted)
- [ ] RBAC roles & permissions → annex §RBAC permission matrix (or none → section omitted)
- [ ] Error codes → annex §Error contract (or none → section omitted)
- [ ] `digest-annex.md` always exists — stub mode if none of the above produced content

*Per-feature briefs:*
- [ ] Every feature file has a numbered FR list (business language, testable; outlier counts surface as Strategic Notes nudges, not gates)
- [ ] Every feature file has a story seeds table (planning-level only, no tech detail; outlier counts surface as Strategic Notes nudges, not gates)
- [ ] Every BRD-recommended module name maps to ≥1 feature (via annex) or flagged in Strategic Notes as orphaned

*Backlog CSV:*
- [ ] `backlog-seeds.csv` header row + one row per seed across all features

## Rules in scope for this step

- **Strategic challenge pass is mandatory** — every roadmap has a Strategic Notes section with at least one substantive bullet, or an explicit "no challenges raised" disclaimer.
- **No silent deferrals** — if the roadmap defers something the BRD lists in acceptance language, mark it explicitly in Strategic Notes.

## Routing

All consistency + artifact-completeness checks pass; Strategic Notes drafted (≥1 substantive bullet or explicit disclaimer) → load `steps/step-07-write-plan.md`.