# Spec section authoring guidance

Reference loaded by step 12 (`generate-spec-content`). Section-by-section rules for authoring `spec.md`. Read alongside `templates/spec-template.md`.

---

## Functional Requirements

Extract every verifiable capability the feature must provide and enumerate them as FR-N rows. Each row answers "what must the system allow or enforce?" — not how. Aim for 5–15 rows covering every distinct behavior. These become the traceability anchors for QA, review-spec, and acceptance criteria.

**When a feature brief was read in step 01 (BRD identifier mode with per-feature file):** The spec's FR table *inherits the FR list verbatim from the feature brief* — numbering preserved (FR-NN.1, FR-NN.2, …). Spec authoring may:
- **Refine wording** for technical precision (allowed; preserve the FR-NN.X number)
- **Add technical columns** to the FR table (entity, endpoint, error condition, audit requirement) — this is the spec's value-add over the brief
- **Append new FRs** when codebase reading reveals a capability the brief missed — new FR gets the next number in sequence
- **Renumber, drop, or split FRs** — NOT allowed without an explicit "Brief deviation" note explaining why

When a legacy roadmap or free-text input was used: derive FRs from the scope as before.

---

## DTOs

The DTO section is the contract every downstream surface reads. Authoring rules:

- **Enumerate the fields a UI renders.** For every response DTO that a page, drawer, table, or detail surface renders, list each field the FE displays, with its type and a one-line "rendered as" note. Do not stop at a "Key Fields" summary or a "see contracts repo" pointer.
- **Reference-only detail DTOs are incomplete.** A detail/drawer DTO that references another entity (by `refId`/UUID) MUST carry the display fields the surface needs — at minimum a human-readable name, plus any description/label/status the UI shows — never a bare `refId` + type + order. If the only fields are an opaque reference, the detail surface cannot render and the spec is incomplete.
- **Encoded / polymorphic / JSON-blob fields** are pinned per variant — see the section below.

### UI element completeness

The DTO field-enumeration rule above describes *what the contract carries*; this describes *what each composite element renders*. For every content-bearing / composite component in the Frontend Specification's Key Components table — one that renders entity data and has internal structure (drawer, multi-section panel, detail page, card, dashboard, table); plain buttons and single inputs are out of scope — the spec must make three things explicit:

- **Sections & content** — enumerate the element's sections and, per section, the specific fields/content rendered. This generalizes the detail-surface field-enumeration rule to any composite element: "renders the entity" is not sufficient — name the fields.
- **Interactions** — state what is actionable (clicks, row-select, expand/collapse) and the keyboard behavior.
- **Accessibility & responsive** — confirm both are addressed, referencing `../tenxengage-frontend/PROJECT-CONTEXT.md`'s Accessibility Conventions and responsive breakpoints rather than restating them.

Do not re-require per-element states or permissions here: loading/empty/error states are covered by `## Edge Cases` (and `/review-spec` Check 10), and permission gating is covered by the Pages table (and Check 6). Point to them; do not duplicate.

---

## Encoded / polymorphic / JSON-blob fields

Any field whose value is an encoded string, a JSON blob, or a polymorphic shape that varies by a discriminator (`type`, `format`, `variant`) MUST NOT be documented as "encoded per format" or "see FE". For each such field:
- Name the discriminator and enumerate every variant value.
- Pin a concrete schema per variant (field names + types).
- Give one concrete JSON example per variant.

This is the single contract the BE writer (scorer/persistence), the FE editor, and any AI prompt all read. If the per-variant schema is absent, three implementations will each invent a different shape. Example — an answer field on a question entity:

```
MULTIPLE_CHOICE → { "selectedOptionIds": ["uuid", ...] }
FREE_TEXT       → { "text": "string (<= 5000 chars)" }
MATCH_PAIRS     → { "pairs": [{ "leftId": "uuid", "rightId": "uuid" }] }
```

---

## Functional Completeness Audit

This section records the probe from step-01a — it is always present, even when the probe found zero gaps. It exists so downstream skills (`/create-stories`, `/review-spec`) can see what was considered and what was deliberately rejected or deferred, not just what was accepted.

**Authoring rules:**
- Emit one row per applicable dimension the probe considered. Omit dimensions that were judged not applicable.
- Use natural-language dimension names that match what was shown to the user — not the internal taxonomy labels.
- Status values are exactly: `✓ Already covered`, `⊕ Approved`, `⊕ Modified`, `⊕ Rejected`, or `⚠️ FUNCTIONAL GAP — DEFERRED`.
- For `⊕ Approved` and `⊕ Modified` rows, the FR / Notes column must reference the FR number that was added to the Functional Requirements table (e.g., "FR-8 — Lesson is marked complete when…").
- For `⊕ Modified` rows, capture the user's verbatim wording, not Claude's original proposal.
- For `⚠️ FUNCTIONAL GAP — DEFERRED` rows, describe what the gap is so a future reader can evaluate it — not just "deferred".
- Zero-gap case: replace the table entirely with one sentence: "No functional gaps identified — all applicable dimensions were already covered by the brief."

**Deferred-gap marker format** (verbatim, to match `/decompose-brd` convention):
```
⚠️ FUNCTIONAL GAP — DEFERRED: {description of gap}
```

**Section placement:** immediately after `## Functional Requirements`, before `## Non-Functional Requirements`. Never move it.

---

## Planning Seeds Passthrough (feature briefs only)

When a feature brief was read, add a `## Planning seeds (from feature brief)` section to `spec.md`, populated verbatim from the brief's Suggested Story Seeds table. This is the bridge `/create-stories` reads to start story identification from the planning skeleton rather than a blank slate. `/create-spec` does NOT refine or expand seeds — that is `/create-stories`'s job.

Omit this section when no feature brief was used (legacy roadmap or free-text input).

---

## Non-Functional Requirements

Fill with actual numbers if the user provided them in step 01. If not provided, use reasonable defaults for a multi-tenant SaaS with hundreds of concurrent users:
- P95 < 300ms for reads
- P95 < 500ms for writes
- Page size ≤ 50 (max, enforced with `@Max(50)`)

---

## Security Design

Use findings from step 07. Every PII field must appear in the PII Fields table. Every abuse-prone endpoint must have a rate limit. Reference the actual `RateLimitFilter` mechanism — specify the correct bucket configuration (per-IP, per-user, per-tenant). For OWASP risks, focus only on risks that actually apply to this feature — don't list theoretical risks.

**Conditional rules (apply if matched in shape manifest):**
- `html-content` shape → spec HTML sanitization as service-layer Jsoup before persistence OR a custom `@Constraint` validator. Do NOT reference `@SafeHtml` — that annotation was removed in Hibernate Validator 7+ and does not exist in Spring Boot 3.x. Document the chosen mechanism in the Input Validation table.
- `sse-streaming` shape → apply SSE auth rules from `docs/patterns/sse-streaming.md`. Do NOT spec `?token=jwt` for `EventSource`. Spec either `fetch` + `ReadableStream` or short-lived SSE-specific token.
- `rate-limit-sensitive` shape → deep-detail per-bucket configuration referencing the `RateLimitFilter` rules from `docs/patterns/rate-limit-sensitive.md`.

---

## Audit Trail

Every write operation (CREATE, UPDATE, status transitions, DELETE) must appear in the audit table. Be specific about what data gets captured — not just "entity ID" but the old value and new value for status transitions, or the field names changed for updates.

- **New enum values**: Cross-reference `enums-index.md` to check existing `AuditAction` and `AuditResourceType` values. If this feature needs values not already present (e.g., PUBLISHED, ENROLLED for actions; COURSE, QUIZ for resource types), list them. These are Java enums stored as varchar — no Flyway migration needed, just the Java file update.
- **Non-CRUD audit annotations**: Standard CRUD (Created, Edited, Deleted) can be inferred at implementation time. But for non-standard operations (status transitions, publish, approve, claim, etc.), fill the template's `@Audited Annotation Details` table with the `action`, `resourceType`, and `description` values — the implementer cannot derive these from the HTTP method alone.

---

## Observability

Log events must be actionable for on-call engineers and AI-assisted debugging. Specify the `step` field value for each log event (e.g., `kv("step", "course_published")`). Metrics must be concrete counter or histogram names (e.g., `courses.published.total`, `quiz.submission.duration_ms`).

MDC fields at minimum: `requestId`, `tenantId`, `userId`. Do NOT include PII field values in log Key Fields.

---

## Permissions & Feature Flags

Use findings from step 10. Fill the Permission Matrix table with one row per concrete `module.*` and `action.*` key. Add rows for every non-CRUD verb identified (manage, publish, approve, submit, etc.) — the template's CRUD rows are examples only.

The `Y`/`—` columns for each of the 4 default roles (CLIENT_ADMIN, ACTIVITY_APPROVER, PARTNER_ADMIN, PARTNER_SELLER) are the source of truth for the Flyway seed SQL. Every `Y` cell must appear in the corresponding role block's `IN (...)` list in `technical.md`.

Define the feature flag with tier booleans. The actual Flyway migration SQL is written in `technical.md` (not here) using V2/V3 INSERT patterns with `ON CONFLICT DO NOTHING` for idempotency.

---

## Domain Events (skip if no events shape matched)

Use the actual Kafka topic naming convention from the codebase. Include the full message schema (not just a list of fields). Specify consumer group IDs and idempotency approach (deduplicate on event ID? on entity state?). No PII in event payloads — reference entity IDs only.

---

## Data Retention & Compliance

Be explicit about the soft-delete decision per entity. If PII fields exist, list them explicitly and note their GDPR implications. Do not write vague guidance like "handle GDPR" — write: "If a data subject deletion request is received, NULL out `email` and `full_name` in the `enrollments` table; the enrollment record itself is retained for audit purposes for 7 years."

---

## Caching Strategy

If nothing gets cached, say so explicitly ("No server-side caching — data changes frequently and stale reads are not acceptable"). Don't leave it blank. Common scenarios:
- Rarely-changing lookup data (plan tiers, currencies, permission lists) → `@Cacheable` with TTL
- High-read aggregates → Redis with explicit eviction on write
- Everything else → no caching by default

---

## Acceptance Tests

Tests are distributed across two locations — `spec.md` itself only has a brief pointer to each:
- **Per-story tests** (unit, @WebMvcTest, Vitest, E2E Playwright) — generated by `/create-stories` into individual `stories/US-NN-*.md` files.
- **Cross-story integration tests** — generated by `/create-stories` into `test-plan.md`. Populate from step 09 findings: entity relationships, state machine lifecycle, business rules, multi-entity workflows, tenant isolation, audit/events.

Write the spec's Acceptance Tests section as a pointer only (following the template). The actual test scenarios are the responsibility of `/create-stories`.

---

## Spec audience tagging

Every section must be labeled with one of: `[BE]`, `[FE]`, or `[BE + FE]`. This tells downstream skills and implementers which repo the section is relevant to.

---

## NEEDS_CLARIFICATION

Markers are written only for **deferred** ambiguities — items the user explicitly punted on when asked interactively (per step 12's interactive-resolution procedure). There is **no cap** on count: every deferred item becomes a marker; every answered item is folded into the section and produces no marker.

```
> NEEDS_CLARIFICATION: {specific question}
```

Do NOT use this for things you can determine by reading the codebase or that have established conventions — those are resolved silently, not raised.

---

## Section ordering and content boundaries

- Sections MUST follow the spec template's order exactly. Do NOT reshuffle.
- Sections MUST end at `## Verification Steps`.
- User Flows and Implementation Tasks live in story files generated by `/create-stories` — they do NOT belong in `spec.md`.
- **NO Java code blocks** in spec.md. Spec is decisions, not implementation.
- **NO Flyway SQL** in spec.md. That belongs in `technical.md`.
- **NO TypeScript type code** in spec.md. Reference the contracts repo for types.
- **NO generic placeholders** — every field name, type, endpoint path is concrete.