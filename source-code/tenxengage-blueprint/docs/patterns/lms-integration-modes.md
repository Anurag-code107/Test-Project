# Pattern: lms-integration-modes

## When this applies

Feature defines or consumes any of the following:

- **Catalog-bearing enablement entities** — Course, LearningPath, Certification, or any future top-level enablement aggregate.
- **Sub-entities** of those catalog-bearing entities — Lesson, ContentAsset, Assessment, Question, AssessmentAttempt, CertificationExam internals, course/lesson assessment bindings.
- **Activity signals** derived from learner engagement with the above — enrollment, completion, in-progress state, score.

If the feature only consumes the *normalized* activity-signal shape (without inspecting source mode), the pattern still applies — but most of its rules will be no-ops.

## Spec authoring guidance

**The two modes.** Every catalog-bearing enablement entity exists in one of two modes:

- `in_house` — fully owned by TenXEngage. Full sub-entity hierarchy populated. Authoring, consumption, and assessment all happen in-product.
- `external` — mirrored from an external LMS. Catalog metadata only. Sub-entities not populated. Consumption happens on the external LMS via deep-link.

**Catalog entity shape.** Each catalog-bearing entity carries the following (concept names below are indicative; final column / field names are confirmed during `/create-spec` interactive authoring and domain governance):

- A **source discriminator** with values `in_house` or `external`.
- An **external identifier** populated when `source = external` — identifies the entity in the source LMS.
- A **deep-link URL** populated when `source = external` — the learner-redirect target.
- A **source-system reference** populated when `source = external` — identifies which external LMS the entity originates from.
- All other catalog metadata (name, description, tags, audience rules, etc.) is populated in **both** modes.

**Shallow-external rule.** When `source = external` on a catalog-bearing entity:

- Sub-entities (Lesson, ContentAsset, Quiz, Question, CertificationExam internals, course/lesson assessment bindings) are NOT created in our database.
- Authoring features (course builder, lesson editor, quiz editor, exam configurator) are not surfaced — the UI either hides authoring affordances for external rows or shows a "managed externally" indicator.
- Validation rules that depend on sub-entity presence (e.g., "a course must have at least one lesson before publish") apply only to `in_house` rows.

**Activity signal normalization.** Learner activity flows into normalized entities whose schema is **identical** regardless of source. Conceptually (final entity names and shapes are confirmed during `/create-spec`):

- An **enrollment** concept — user × catalog entity, with enrollment timestamp and (where applicable) assignment provenance.
- A **completion** concept — user × catalog entity, with completion timestamp, final score, and pass/fail (where applicable).
- A **progress** concept — user × catalog entity, with percent-complete, last-activity timestamp, and time-spent (where applicable).
- A **certification-earned** concept — user × certification, with earn date, expiry date (if applicable), and evidence reference.

These normalized entities do **NOT** carry the source discriminator themselves — the discriminator lives on the parent catalog entity. Downstream features (recommendation agents, readiness models, reporting, discussions) consume the normalized shape and MUST NOT branch on source.

**Learner UX bifurcation rule.** The learner experience MUST branch on `source` for catalog-bearing entities:

- `in_house` — in-product player / quiz UI / exam UI renders the sub-entity hierarchy.
- `external` — card surfaces catalog metadata + a deep-link to the external LMS; in-product player is not invoked. Completion is observed via the activity-signal stream, not produced in-product.

**Sync mechanism — explicitly deferred to Phase 2.** Specs MUST NOT design webhook handlers, polling jobs, xAPI endpoints, or LTI integration in Phase 1. The entity model must *accommodate* external mode (source discriminator, external identifier, deep-link URL, source-system reference, sub-entity gating); the *mechanism* for populating external rows and activity signals is a separate Phase 2 design. Phase 1 ships in-house mode only; `source = in_house` may be the only value any catalog-bearing entity holds in Phase 1, and that is acceptable.

**Domain primitive guidance — no slot prescription.** This pattern does NOT prescribe which catalog-bearing entity fills which canonical 8-slot domain primitive (Core aggregate, Audience-rule entity, Completion/participation entity, etc.). Slot mapping for the enablement domain — including whether the canonical 8-slot model accommodates the catalog-bearing entity set, and which entities map to which slots — is a governance decision to be made interactively during the first `/create-spec` invocation that encounters a missing `docs/patterns/domains/enablement.md`.

When `/create-spec` runs for a feature that introduces only subordinate primitives (e.g., Assessment, Question), the operator may instruct the skill to defer domain-file authoring until a later feature introduces a catalog-bearing entity. When a feature introduces one or more catalog-bearing entities, the operator decides whether to author `enablement.md` then (potentially with placeholders for catalog entities not yet introduced) or defer further. The pattern provides constraints (dual mode, source discriminator, normalization boundary), not slot assignments.

## Implementation guidance

For features that **own** catalog-bearing entities:

- Migrations add the source discriminator, external identifier, deep-link URL, and source-system reference columns to catalog tables with constraints (CHECK ensuring `external_*` columns are populated iff `source = external`).
- Service-layer validation rules that depend on sub-entity presence must short-circuit for `source = external` rows.
- Authoring API endpoints reject mutations targeting `source = external` rows with a clear error code.
- Audience-rule / eligibility evaluation must work identically for both modes (audience rules live on the catalog entity, which exists in both modes).

For the **learner-experience** feature:

- Catalog card rendering reads `source` and switches between "open in player" and "open in external LMS" actions.
- The deep-link action uses the deep-link URL field; the in-house action routes to the in-product player route.
- Progress display is identical for both modes (reads from the normalized progress entity).

For features that consume **only normalized activity signals**:

- No implementation changes from what a mode-unaware design would produce. The normalization boundary makes these features mode-blind by construction.

## Examples in codebase

None yet — this is a new pattern for an initiative that has not begun implementation. The first implementing feature (course-authoring and surrounding catalog-entity features) will become the canonical example. Update this section at that time.

## Common gotchas

- **Don't add the source discriminator to activity-signal entities.** It belongs only on catalog-bearing entities. Activity entities are normalized and mode-blind.
- **Don't design Phase 2 sync mechanism in Phase 1 specs.** Resist the temptation to spec webhook handlers, polling cadences, or xAPI endpoints — entity-model accommodation is sufficient; the mechanism is a separate Phase 2 design.
- **Don't gate mode-agnostic features on mode.** If you find yourself adding `if (course.source === 'external')` in a reporting query, a recommendation engine, or a readiness-model computation, the activity-signal normalization is broken — fix the normalization, not the consumer.
- **Don't pre-assign domain slots.** This pattern intentionally leaves slot mapping to interactive governance. Resist filling `docs/patterns/domains/enablement.md` from this pattern alone.
- **Sub-entity authoring features (e.g., assessment-authoring) are not "mode-aware" — they are "in-house-only".** The cleaner mental model: their outputs only exist when a catalog-bearing parent is `in_house`. The feature itself does not carry a source discriminator; its existence is conditional on the parent.
