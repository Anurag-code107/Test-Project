# Step 05: slice

**Goal:** Cut the BRD into shippable vertical slices, generate per-slice briefs, write per-feature functional requirements (each tagged with its inventory source), and write planning-level story seeds covering each feature's shippable journey. One continuous creative act per feature — slicing, FRs, and seeds flow together.

**Inputs:** Classified BRD content from step 04 (business-truth queued for `digest.md`, technical-truth queued for `digest-annex.md`); existing `features/*/spec.md` frontmatter loaded in step 03 for reuse-check.

> **Step 05 — Slice into shippable features. Then per feature: brief → FRs → story seeds, in one pass.**

## Slicing rubric

Apply these in order:

1. **Vertical slices** — each slice = primary persona + journey + data + UI delivering one shippable user value. Avoid horizontal slices ("the entire backend", "the entire data model").
2. **Foundation first** — entities/scaffolding consumed by multiple slices ship before the consumers. Entity-shape (configurable data object vs hardcoded JPA entity) is decided per feature in `/create-spec` step 12 — not at decompose-brd time. Foundation work for configurable-data-object schema seeding folds into the first `/create-spec` that encounters the entity.
3. **Synthesize the phase plan** — the BRD's phasing is one input, not the answer. Weigh in order: (a) BRD-stated phasing; (b) dependency graph (foundation before consumers); (c) ADR blockers (a feature gated by an unresolved ADR can't ship in Phase 1 if the ADR isn't going to land in time); (d) greenfield-vs-extension cost (G9 — a "small" Phase-1 BRD feature may be a greenfield foundation in our codebase); (e) risk isolation (rule-based before ML; deterministic before probabilistic); (f) reuse against existing `features/*/spec.md` (read YAML frontmatter `name`/`slug`). Produce your own phase recommendation. **Surface every deviation from the BRD's phasing** — step 06 will collect these into Strategic Notes with reasoning. If the BRD has no phasing at all, propose one based on the dependency graph + risk profile.
4. **Risk isolation** — sequence the simple version before the complex (rule-based before ML, deterministic before probabilistic, manual before automated).
5. **ADR-aware** — features depending on unresolved ADRs are flagged with the blocking ADR. Sequence ADR resolution as a prerequisite, not as part of the feature itself.
6. **Persona-of-record** — every slice has exactly one primary persona named. Secondary personas are explicitly listed. Primary = recipient of agent action / journey owner; NOT escalation target / configurator.
7. **Reuse before invent** — check existing `features/*/spec.md` (read YAML frontmatter `name`/`slug`). Flag overlaps for Strategic Notes; don't propose duplicate features.

## Feature count

Apply the slicing rubric above; the count emerges from it — there is no target number. Each feature = one primary persona accomplishing one end-to-end journey. Two candidate slices that share the same persona AND the same journey are one feature. Same persona but different journeys are separate features.

**Sanity bounds (not targets):**
- **>12 features** → likely too thin. Re-check rubric #1 (vertical slice with shippable user value); merge naturally related capabilities (e.g., "Course CRUD" + "Lesson CRUD within Course" = one feature).
- **<5 features from a multi-section, multi-persona BRD** → likely too coarse. Re-check rubric #6 (one primary persona per slice); if a slice serves multiple distinct primary personas, split.

## Per-slice content (capture for each `features/F-NN-{slug}.md` brief)

For each proposed feature, capture the following — this becomes the per-feature brief that step 07 writes verbatim into the plan and step 08 writes to disk:

- **F-NN: {Feature Name}** — proposed kebab-case slug (e.g., `training-catalog`)
- **Roadmap slug** — the BRD slug from step 01 (e.g., `partner-revenue-readiness`). Written verbatim into the `{roadmap-slug}` field of the feature file header so readers can identify the parent roadmap when viewing a feature file in isolation.
- **Business outcome** — 1–2 sentences: what shipping this enables for the business and users. Plain language; no implementation terms.
- **Primary persona** — one persona; secondary personas listed separately. Primary = recipient of agent action / journey owner.
- **User journey sketch** — entry point → core action → exit (1–2 sentences). Business language. No screen names, no API names.
- **Workflow vs tool** — which one, why
- **IA placement** — top-level menu / nested / modal / contextual / TBD-with-FE
- **Dependencies** — other features (by F-NN), ADR blockers (by ADR-NN)
- **Riskiest unknown** — the one thing that, if wrong, would hurt the slice most
- **Recommended phase** — Phase 1 / 2 / 3, with synthesis reasoning
- **BRD anchors** — topic label(s) from BRD headings where this slice is described (use heading text, NOT §-numbers)
- **BRD-stated SLAs** — filtered from `numeric-sla-patterns` hits in step 04; omit if none apply to this slice
- **Candidate domain concepts** (business nouns) — business-language nouns the feature operates on. **NOT entity names; NOT CamelCase.** If the BRD uses CamelCase entity names, they belong in `digest-annex.md` as advisory hints, not here.
- **Cross-feature / cross-quadrant signals** (business intent) — describe the business fact communicated, NOT the event name or API op (e.g., "When a certificate lapses, eligibility and enrollment features are notified" not "`certification_lapsed` event emitted").

**Do NOT include** per-slice: events emitted/consumed, entities owned, RBAC scope, error contract, backend module hint. These are technical-truth and go to `digest-annex.md`, where they apply initiative-wide.

## Per-feature functional requirements

For every F-NN, write the FRs the feature requires — no more, no less. Each FR:

- Is a **business-language capability statement**. Not "POST /courses". Not "CourseEntity.save()".
- Is **testable in plain language**. "System surfaces recommendations within 30 minutes of triggering deal event" is testable. "System is performant" is not.
- Maps to **at least one story seed** (next sub-section).
- Is numbered **FR-NN.1, FR-NN.2, …** where NN is the zero-padded feature number (e.g., F-01 → FR-01.1, FR-01.2; F-12 → FR-12.1).
- **Records its inventory source(s)** — every FR ends with a `Source:` line listing one or more `REQ-NNN` IDs from the requirement inventory. The inventory is the union of BRD-verbatim items (extracted in step 04) and gap-resolution items (appended in step 04c, identifiable by `gap:{slug}` in field 5). FRs cite both kinds the same way — by `REQ-NNN`. There is no `gap:` syntax in FR source tags. Format example:

```
FR-03.4: Vendor PSM can push specific training to any participant in
one click from within the deal collaboration room.
Source: REQ-047, REQ-052
```

The source tag enables step 06's coverage check to be a deterministic set comparison rather than a fuzzy re-read of the BRD. An FR that doesn't trace back to the inventory is either invented (not grounded in the BRD) or exposes an inventory miss in step 04 — step 06 surfaces both for revision.

Source FRs from:
- The BRD's per-section capability statements ("Admin can create...", "System publishes...")
- The BRD's acceptance criteria (rephrased into capability statements, not test statements)
- The BRD's user stories (rephrased into capabilities)
- Inventory items derived from step 04c clarifications (those items are tagged `gap:{slug}` in field 5 of the inventory; FRs cite them by `REQ-NNN` like any other)

If the BRD has explicit FR-numbered lists, use those numbers mapped locally per feature. Otherwise number locally.

**Quantity guidance — semantic, not numeric.** The FR set should collectively cover the feature's scope without padding or omission. Don't pad to hit a count; don't trim to fit one. If a feature has very few FRs (e.g., 1), double-check the slice is not too narrow. If it has very many (e.g., 30), double-check the slice is not too coarse. These are sanity nudges, not gates — step 06 surfaces outliers in Strategic Notes for re-examination.

## Per-feature story seeds

For each feature, generate planning-level story seeds covering the feature's shippable journey. The seed set together describes the journey end-to-end — no more, no less.

Each seed:
- **Title** — 3–7 words, business slice.
- **Business outcome** — one line. What the user / business gains.
- **Type** (optional) — one of: `UI`, `workflow`, `rules`, `integration`, `reporting`, `admin`, `agent`, `data`.
- **Depends on** (optional) — another seed in this feature (e.g., `S-01`), or another feature's seed (e.g., `F-01.S-01`).
- **Seed ID** — `S-01, S-02, ...` (local per feature; globally unique as `F-NN.S-NN` when combined with feature ID).

**Forbidden at this stage:**
- Acceptance criteria (Gherkin or otherwise)
- Engineering subtasks (entity creation, migration files)
- API endpoints, HTTP codes, controller names
- Database schema, migrations, column names
- Permission keys, RBAC config items
- Code paths or file paths
- Estimates, story points, sprint assignments

**Forbidden phrasings — rewrite these:**
- "Create `{Entity}` entity / repository" → name the **business slice** ("Define certification programs")
- "POST /api/v1/{thing}" → name the **business action** ("Earn certifications")
- "Add Flyway migration for X" → not a story seed; drop it
- "Wire RBAC for X.create" → not a story seed; drop it (RBAC is a platform-given)
- "Issue `{event_name}` Kafka event" → "Publish [business fact] to downstream consumers"

**Test before writing a seed:** could a PM paste this into ClickUp and have a meaningful first-pass backlog item? If no, rewrite.

**Quantity guidance — semantic, not numeric.** The seed set should together describe the feature's shippable journey. Don't pad to hit a count; don't trim to fit one. If a feature has very few seeds (e.g., 1), double-check the slice is not too narrow. If it has very many (e.g., 20), double-check the slice is not too coarse. These are sanity nudges, not gates — step 06 surfaces outliers in Strategic Notes for re-examination.

## Rules in scope for this step

- **Vertical slices only** — no "entire backend" or "entire data model" slices. Each slice ships standalone user value.
- **One primary persona per slice** — if you can't pick one, the slice is too broad. Split.
- **ADR-aware** — features depending on unresolved ADRs are flagged. Sequence ADR resolution as a prerequisite, not part of the feature.
- **Reuse-check** — scan existing `features/*/spec.md` (read YAML frontmatter `name`/`slug`) before proposing a slice. Don't duplicate.
- **Synthesize phasing** — weigh dependency graph, ADR blockers, G9 cost, risk isolation, reuse. Surface every BRD-deviation for step 06.
- **Per-feature FR list is mandatory** — every feature has at least one numbered, business-language, testable FR. The FR set covers the feature's scope without padding or omission. Quantity is semantic, not numeric — sanity nudges flag extreme outliers in step 06.
- **FR source tagging is mandatory** — every FR includes a `Source:` line listing one or more `REQ-NNN` IDs from the inventory (BRD-verbatim and gap-resolution items use the same `REQ-NNN` namespace). Ungrounded FRs surface in step 06 for revision.
- **Per-feature story seeds are mandatory** — every feature has planning-level seeds describing its shippable journey. Title + 1-line outcome (+ optional type, depends_on). No acceptance criteria, no engineering tasks, no API/DB detail. Quantity is semantic, not numeric — sanity nudges flag extreme outliers in step 06.

## Routing

Slicing + FRs + seeds complete for all features → load `steps/step-06-challenge-validate.md`.