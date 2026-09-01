# Entity-shape decisions

Reference loaded by step 12 (`generate-spec-content`) at the start of the Data Model section authoring. Defines how `/create-spec` resolves entity shape (configurable data object vs hardcoded JPA entity) per feature, how cross-feature consistency is preserved via `digest.md`, and how the answer flows into `spec.md` and (downstream) `technical.md`.

This reference replaces `/decompose-brd` Step 04b. Decompose-brd no longer makes entity-shape decisions; `/create-spec` owns them per feature.

---

## When this runs

Always, at the start of step 12's Data Model section authoring. Even when no digest applies (Mode 2 / free-text input), the procedure still asks for new entities introduced by this feature.

---

## Procedure

### 1. Identify candidate entities

Build a candidate list of entities this feature operates on, sourced in this order:

1. Entities surfaced by step 06 (scope decomposition) — the canonical "what does this feature operate on" list.
2. BRD-named entities listed in `digest-annex.md` that are likely relevant to this feature (when a digest applies — these come through advisory-reconciliation in step 02).
3. Any new entities surfaced during modeling in step 12 itself (entities the FRs imply but step 06 didn't name).

Deduplicate by name.

If the candidate list is **empty**, skip the rest of this procedure. The feature operates on no entities — no entity-shape decision is needed.

### 2. Inherit prior decisions from `digest.md`

When a digest applies (Mode 1 or any digest signal per `brd-digest-handling.md`):

- Read `roadmaps/{slug}/digest.md`'s `## Entity-shape decisions` section if present.
- For each candidate entity that already appears in that section, **inherit the prior decision silently** — do not re-ask.
- Treat the inherited decision as the working answer for this feature unless step 4 surfaces an explicit reason to override.

If `digest.md` has no `## Entity-shape decisions` section (the feature is the first to encounter any entity in this roadmap, OR `/decompose-brd` predates this procedure), no inheritance applies — proceed to step 3 with the full candidate list.

### 3. Ask the user about un-decided entities

For candidate entities with no prior decision in `digest.md`, ask one batched question:

> The following entities are candidates for this feature:
>
> - `{Entity 1}`
> - `{Entity 2}`
> - `{Entity 3}`
>
> Which should be **configurable data objects** (tenant-editable fields under Platform Settings → Managed Data) rather than hardcoded JPA entities?
>
> **Default = hardcoded entity.** Pick all that should be configurable.
>
> *(Field details — default fields, field types, value sources, validation — are deepened later in this same `/create-spec` run via `docs/patterns/managed-data.md`. This question only locks the shape.)*

Wait for the user's response. Accept any of: a list of names, "all", "none", or specific overrides like "Course and Lesson, the rest hardcoded".

### 4. Surface override opportunities (only when prior decisions exist)

For candidate entities that **were inherited** from `digest.md` in step 2, surface them in a separate question — but only if the modeling context in this feature suggests the prior decision may be wrong. Use judgment: if the FRs and scope of this feature suggest the entity is actually hardcoded when a prior spec said configurable (or vice versa), ask:

> The digest records `{Entity}` as a {configurable data object | hardcoded entity} from a prior spec. For this feature, the FRs suggest `{Entity}` is actually a {hardcoded entity | configurable data object}. Override the prior decision?
>
> Y / N — default N (keep prior decision)

If no signal of conflict, do not ask — silently inherit. Only ask if you have high confidence the modeling context truly conflicts with the prior decision. If borderline, inherit silently per Rule 2 ("Inherit silently") and let the user catch any drift on spec review.

### 5. Record decisions in two places

**Place 1 — `digest.md`'s `## Entity-shape decisions` section:**

When a digest applies, append (or create the section if absent) using this format:

```markdown
## Entity-shape decisions

The following entities are modeled as configurable data objects (tenant-editable fields under Platform Settings → Managed Data):

- `{Entity}` — first introduced by `/create-spec` for feature `{F-NN-slug}`

All other entities default to hardcoded JPA entities. Field-level configuration is deepened per-feature in `/create-spec` via `docs/patterns/managed-data.md`.
```

If the section already exists, append the new entity rows. If a prior entity was overridden in step 4, update its row to reflect the new decision and add a note: `previously: configurable (overridden by F-NN-slug)` or vice versa.

When no digest applies (Mode 2 / free-text), skip this place — no shared digest exists.

**Place 2 — `spec.md`'s `### Entity-shape decisions` sub-section under Data Model:**

Always emit (even when no digest applies). The sub-section format is:

```markdown
### Entity-shape decisions

| Entity | Shape | Source |
|---|---|---|
| `{Entity 1}` | Configurable data object | This spec |
| `{Entity 2}` | Hardcoded JPA entity | This spec |
| `{Entity 3}` | Configurable data object | Inherited from digest |
| `{Entity 4}` | Hardcoded JPA entity | Override of digest (was: configurable) |
```

Omit the sub-section entirely if the candidate list was empty in step 1 (no entities to decide on).

### 6. Update the shape manifest

If any entity was decided as configurable (newly or via inheritance), ensure `managed-data` is in the shape manifest from step 04. If it's not in the manifest:

1. Add `managed-data` to the shape manifest in conversation context.
2. JIT-load `tenxengage-blueprint/docs/patterns/managed-data.md` — focus on the "Spec authoring guidance" section.

This guarantees the data-model and technical-content sub-steps apply the pattern's rules even if step 04's gate didn't catch it.

---

## What flows downstream

- **Step 12** (this step) writes the `### Entity-shape decisions` sub-section into `spec.md`'s Data Model section. The Data Model entity table notes which entities are configurable in their type column.
- **Step 13** reads the locked decisions from conversation context. For each configurable entity, it emits Flyway DDL for the `data_objects` + `data_object_fields` seed rows per `managed-data.md`'s implementation guidance.
- **`/create-stories`** reads `spec.md` + `technical.md` and rolls Managed-Data Flyway seed (`data_objects` + `data_object_fields` rows for configurable entities) into foundation task F2, hardcoded JPA entity classes into F3, and consuming stories reference Platform Settings → Managed Data UI per the spec's design. Configurable entities do NOT generate JPA entity classes — they live entirely as rows in `data_objects` + `data_object_fields`.

---

## Rules

- **Default = hardcoded.** Silence is not "configurable". An entity the user does not explicitly mark as configurable is hardcoded.
- **Inherit silently.** Prior digest decisions are inherited without asking unless the current feature's modeling context surfaces a clear conflict.
- **Override is opt-in, not implicit.** Step 4's surface check requires judgment — when in doubt, do not ask; inherit and let the user catch it on spec review.
- **Empty list short-circuits.** If the candidate list is empty, the procedure does nothing. No empty `### Entity-shape decisions` sub-section.
- **One ask per `/create-spec` run.** Step 3 batches all un-decided entities into a single question. Don't fragment per entity.