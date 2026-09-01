# Step 13: generate-spec-content

## Goal
Generate the full content of `spec.md` — decisions, tables, business rules, security, edge cases. Decisions only; NO code, NO Flyway SQL, NO TypeScript types.

## Inputs (from prior steps)
- All accumulated findings: FRs, NFRs, scope, security, events, test scenarios, permissions matrix, slug, BRD digest context, shape manifest
- Probe record from step-01a: full list of probe dimensions with per-item status (approved / modified / rejected / deferred / already-covered); zero-gap flag
- Project context (still in conversation from step 03)
- Pattern guidance for matched shapes (still in conversation from step 06)

## Loads (just-in-time)
- `tenxengage-blueprint/.claude/skills/create-spec/templates/spec-template.md` (the spec.md template)
- `tenxengage-blueprint/.claude/skills/create-spec/references/spec-section-guidance.md` (section-by-section authoring rules)
- `tenxengage-blueprint/.claude/skills/create-spec/references/entity-shape-decisions.md` (loaded before Data Model section authoring)
- For API endpoint generation: glob `tenxengage-backend/src/main/java/com/tenxengage/app/controller/*.java` and JIT-read ONE controller from the closest domain to inform endpoint shape.

## Procedure

1. Read the template and the section-by-section guidance.

2. For each section in spec-template.md, generate the actual content:
   - **Overview** — vision, scope, goals, non-goals (inherit from BRD digest if applicable)
   - **Personas** — primary + secondary; use BRD persona names verbatim if applicable
   - **Functional Requirements** — table (FR-N rows). If feature brief was used in step 01, inherit FRs verbatim with allowed refinements (per `spec-section-guidance.md`). The updated locked FR list from step-01a (brief FRs + any approved/modified probe FRs) is the authoritative source.
   - **Functional Completeness Audit** — emit from the probe record produced by step-01a. Each applicable dimension becomes one table row. Approved and modified FRs reference the FR number they were assigned. Zero-gap case: one sentence noting no gaps were found. See `spec-section-guidance.md → ## Functional Completeness Audit` for authoring rules.
   - **Planning Seeds Passthrough** (only if feature brief was used) — verbatim from brief's Suggested Story Seeds table
   - **Data Model** — entities, fields, types, constraints, relationships (NOT Flyway SQL; that's technical.md). **Before authoring this section, follow the `entity-shape-decisions.md` procedure end-to-end** to lock per-entity shape (configurable data object vs hardcoded JPA entity), inherit prior decisions from digest, ask the user about un-decided entities, and update the shape manifest if a configurable entity was chosen. The locked decisions:
     - Surface in `spec.md` as the `### Entity-shape decisions` sub-section under Data Model (when any entities exist in scope).
     - Mark each entity row in the Data Model entity table with its shape (e.g., type column or a Shape column).
     - Are written back to `digest.md`'s `## Entity-shape decisions` section per the procedure.
     - Flow into step 14 via conversation context for Flyway DDL generation.
     Apply `new-entities.md` and `tenant-isolation.md` if shape matched. If `managed-data` was added to the manifest by the procedure, JIT-load `managed-data.md` now and apply its spec-authoring guidance to the Data Model entries that were marked configurable.

   - **Domain slot-filler drift check** (only if `$SLOT_FILLING`):
     For each slot the spec proposes a filler for (entity name, interface name, table column, topic name), compare against the registry filler resolved in step 04. If different:

     Bracket the gate:
     ```bash
     date +%s%3N > /tmp/create_spec_wait_started
     ```

     Prompt:
     > "This spec fills slot `{slot-name}` with `{proposed}`. The {domain} registry currently lists `{registry-filler}` (from {layer: platform | domain | builder-type override}). Choose:
     >   A) Add `{proposed}` as a domain-specific override (writes to `docs/patterns/domains/{domain}.md`)
     >   B) Replace the registry filler with `{proposed}` (writes to `{domain}.md`)
     >   C) Mark as a deviation for this feature only (no registry write; spec carries an inline note)"

     On resume:
     ```bash
     echo $(( $(cat /tmp/create_spec_wait) + ($(date +%s%3N) - $(cat /tmp/create_spec_wait_started)) )) > /tmp/create_spec_wait
     ```

     Record the decision for the plan file (step 15).

   - **Bottom-up primitive promotion check** (only if `$SLOT_FILLING`): If a sub-entity referenced by the spec (e.g., `Lesson`) already appears in another spec's `features/*/spec.md` within the same domain, prompt:
     > "`{sub-entity}` is now referenced by {existing-features} and this feature. Promote to a shared `{domain}` primitive?"
     On YES, plan-file records an addition to `{domain}.md` under "Shared sub-entities".
   - **API Endpoints** — table per endpoint group; method, path, audience, permissions, request/response shape pointers (concrete shape lives in OpenAPI contracts repo)
   - **DTOs** — for every response DTO a UI renders, enumerate the fields the FE displays (name + type + "rendered as"). Reference-only entries (`refId` + type + order) are rejected: include the display name/description/status the surface shows. See `spec-section-guidance.md → ## DTOs`.
   - **Business Rules** — explicit, testable rules
   - **Security Design** — populate from step 08 findings. Apply rules from `html-content.md` if shape matched (sanitization mechanism), `sse-streaming.md` if matched (auth approach), `rate-limit-sensitive.md` if matched (bucket configs).
   - **Audit Trail** — every CREATE/UPDATE/status-transition/DELETE in audit table. Specify field-level capture. Note new AuditAction / AuditResourceType enum values needed (cross-reference `enums-index.md`).
   - **Observability** — log events with `step` field values, metric names (counters, histograms)
   - **Permissions & Feature Flags** — populate matrix from step 11. Define feature flag with tier booleans.
   - **Domain Events** (if events-publishing or events-consuming shape) — full schema, consumer group IDs, idempotency
   - **Data Retention & Compliance** — soft-delete decisions per entity. Explicit PII fields and GDPR treatment.
   - **Caching Strategy** — explicit choice per cacheable resource (or "no caching — data changes frequently")
   - **Acceptance Tests** — pointer only (per existing template). Test scenarios are `/create-stories`'s job.
   - **Verification Steps** — manual smoke checks
   - Mark every section with `[BE]`, `[FE]`, or `[BE + FE]` audience tag.

3. **Resolve ambiguities interactively.** When an ambiguity surfaces during a section's generation, raise it as a focused question to the user immediately. There is NO cap on count. Bracket each user-gate with the wait-accumulation pattern:

   ```bash
   date +%s%3N > /tmp/create_spec_wait_started
   ```

   (then ask the question; on resume:)

   ```bash
   echo $(( $(cat /tmp/create_spec_wait) + ($(date +%s%3N) - $(cat /tmp/create_spec_wait_started)) )) > /tmp/create_spec_wait
   ```

   - If the user answers: fold the answer into the current section and continue. Do NOT write a marker.
   - If the user defers ("TBD", "ask offline", "I don't know"): write a `NEEDS_CLARIFICATION` marker inline at the point of ambiguity:
     ```
     > NEEDS_CLARIFICATION: {specific question}
     ```

   The "only deferred items become markers" rule is what enforces no-cap without inflating the spec.

## Rules (scoped to this step)
- **Frontmatter `domain`:** populate with `$DOMAIN` from step 04. Set to `null` if the feature is not slot-filling.
- **Frontmatter `builder_type`:** populate with `$BUILDER_TYPE` from step 04. Set to `null` if not builder-shaped.
- **Frontmatter `visual_reference`:** populate from step 04's fidelity prompts. For builder-shaped features, `component_path` MUST be a real path or `null` (never a placeholder). `notes` may be null.
- **Frontmatter `applicable_sections`:** populate from step 04's fidelity prompts. For builder-shaped features, `sections` MUST be a non-empty list. `source` is `builder_definition` when driven by `BuilderDefinition` config, otherwise `manual`.
- **NO Java code blocks.** Spec is decisions, not implementation.
- **NO Flyway SQL.** That's technical.md (step 14).
- **NO TypeScript type code.** Reference contracts repo for types.
- **NO generic placeholders.** Every field name, type, endpoint path is concrete.
- **Encoded / polymorphic fields are pinned per variant.** Any JSON-blob, encoded-string, or discriminator-driven field must enumerate its variants with a concrete schema + example each (per `spec-section-guidance.md → ## Encoded / polymorphic / JSON-blob fields`). "Encoded per format" / "shape varies" is NOT an acceptable spec value.
- **Composite / content-bearing components carry the completeness enumeration.** Every Key Components entry that renders entity data and has internal structure (drawer, multi-section panel, detail page, card, dashboard, table — not plain buttons/inputs) must enumerate its sections + per-section content, its interactions, and confirm a11y/responsive (per `spec-section-guidance.md → ## DTOs → ### UI element completeness`). States and permissions are NOT re-stated per element — they live in `## Edge Cases` and the Pages table.
- Every PII field must appear in the PII Fields table.
- Every endpoint must have a permission key (from step 11's matrix).
- Use the spec template's section ORDER and audience tags exactly. Don't reshuffle.
- Sections MUST end at `## Verification Steps`. User Flows and Implementation Tasks live in story files generated later by `/create-stories`.
- **Entity-shape decisions are written back to `digest.md` lazily.** The first `/create-spec` to encounter an entity records it in `digest.md`'s `## Entity-shape decisions` section per `entity-shape-decisions.md`. Subsequent specs inherit silently unless this feature's modeling context surfaces a clear conflict requiring override.

## User interaction
None directly — content is generated for the plan file in step 15.

## Output for downstream steps
- Full spec.md content held in conversation context, ready to be assembled into the plan file in step 15.

## Boundary
spec.md content fully generated → route to step 14: read steps/step-14-generate-technical-content.md`.