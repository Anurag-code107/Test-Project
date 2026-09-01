---
layer: platform-infra
status: in-design
authored: 2026-05-14
---

# Pattern: tagging

Cross-module sidecar primitive for attaching content-intrinsic tags to entities so downstream features (gap detection, deal-context recommendations, readiness reporting) can match content by attribute.

Tagging is a true **platform sidecar**: any module can opt any of its entities into tagging without coupling the tagging code to the module. The tagging package knows about `(entity_type, entity_id)` pairs but has no knowledge of what an Assessment or Course or DiscussionGroup actually is. Each consuming module ships a `TaggableEntityResolver` that teaches tagging how to check entity existence and access permissions for its own entities.

## When this applies

Use this pattern when a feature introduces or operates on an entity that benefits from **content-intrinsic matching** — i.e., a downstream agent or report needs to find entities by attributes describing what the content *is about* (product line, topic, difficulty, technical architecture, etc.).

Typical signals:
- The feature's user journey mentions filtering, finding, or recommending content by attribute.
- A downstream feature (gap detection, deal-context recommendation, readiness reporting) needs to query "give me all entities with these attributes."
- An AI authoring service generates content and should annotate it with attribute metadata for matching.

## What this pattern is NOT

This is the most important rule of the pattern. Tags describe **what content is about** (content-intrinsic attributes), not **who content is for** (audience-side targeting). The following are explicitly NOT tagging concerns:

| ❌ Don't use tagging for | ✅ Use this instead |
|---|---|
| Audience targeting ("who can see / take / be assigned this content") | `audience-rule` primitive + eligibility engine |
| Population filters by region ("APAC sellers") | `location-hierarchy.md` |
| Role-based access ("Account Executives only") | The role system / `ClientRole` entities + permissions |
| Visibility / access control ("restricted-to: gold-tier partners") | `audience-rule` primitive |
| Authoritative business-data lookups (Partner Type values, etc.) | `managed-data.md` |
| Multi-step entity authoring (wizards, builders) | `builder-wizard.md` / platform builder primitives |

Examples of valid content-intrinsic tags:
- `product: salesforce-sales-cloud` — content is about Sales Cloud
- `topic: objection-handling` — content covers this skill
- `difficulty: intermediate` — content is at this authoring level
- `architecture: security` — content covers the security architecture domain
- `compliance-framework: gdpr` — content covers GDPR

Examples of tags that should NOT be created (use other primitives instead):
- `region: apac` — audience-side; use location-hierarchy
- `role: ae` — audience-side; use role system
- `audience: partner-sellers` — audience-side; use audience-rule
- `partner-tier-can-view: gold` — visibility; use audience-rule

Tenants have full authoring authority over their own namespaces and values, so the platform cannot mechanically prevent misuse. This rule is documented so engineers and admins make the right choice at design time.

## Spec authoring guidance

For each entity the feature wants to participate in tagging, the spec must capture:

- **TaggableEntityType registration.** Name the new enum value being added to `com.tenxengage.app.platform.tagging.TaggableEntityType` (e.g., `ASSESSMENT`, `COURSE`, `LEARNING_PATH`, `CERTIFICATION`, `DISCUSSION_GROUP`).
- **Resolver implementation.** State that the feature ships a `TaggableEntityResolver` for its entity type, with `exists`, `canRead`, `canEdit` implementations referencing the entity's repository and permission keys.
- **Authoring surface integration.** Name the authoring component(s) that embed `<TagsSectionWidget>` — typically the feature's builder or edit page. State whether tagging participates in completion tracking (most builders make it an optional step).
- **AI suggestion participation (optional).** If the feature has an AI authoring service that generates entity content, state whether AI also suggests tags. When yes, name the trigger paths (bundled with content generation, standalone "Suggest tags" button, both).
- **Matching consumer contract.** Name which downstream consumers (F-07, F-08, F-10 or others) will read tags for this entity and what query shape they need (single-entity tag read, multi-tag faceted match, or both).
- **Pre-seeded namespaces or tags (rare).** Usually none. The platform seeds three system namespaces (`product`, `topic`, `difficulty`); features should not seed additional values on tenants' behalf except in well-justified cases. Document the justification when proposing additions.

Specs do NOT need to capture:
- The tagging tables themselves (owned by the platform tagging package, not the feature)
- The Tag Library admin UI (shipped once by F-01, reused thereafter)
- The tagging service / controller / hook code (shipped once by F-01, reused thereafter)
- Tenant isolation invariants (enforced by the platform tagging service)

## Implementation guidance

### Backend

#### Entities (owned by `platform.tagging.*`)

- **`TagNamespace`** (`com.tenxengage.app.platform.tagging.TagNamespace`) — the keys. Tenant-scoped (`client_id NOT NULL`), unique `(client_id, name)` where `status = ACTIVE`. `is_system = true` for the three platform-seeded namespaces; tenant-created namespaces have `is_system = false`. Status enum `ACTIVE | ARCHIVED` (archiving cascades to child tags; the row itself is preserved for historical EntityTag references).
- **`Tag`** (`com.tenxengage.app.platform.tagging.Tag`) — the values within a namespace. Tenant-scoped, unique `(client_id, namespace_id, value)` where `status = ACTIVE`. Same status enum and archive cascade semantics.
- **`EntityTag`** (`com.tenxengage.app.platform.tagging.EntityTag`) — the polymorphic join. Carries `entity_type` (enum), `entity_id` (UUID, no FK due to polymorphism), `tag_id` (FK to Tag), `source` (`HUMAN | AI_SUGGESTED`), `suggested_at`, `confirmed_by_user_id`, `confirmed_at`, `ai_model_version` (for FR-01.16-style lineage on AI-generated rows). Unique composite `(client_id, entity_type, entity_id, tag_id)`. **Hard delete on detach.**

All three entities extend `BaseEntity implements TenantAware` and carry the standard `@Filter(name = "tenantFilter", condition = "client_id = :clientId")` Hibernate filter.

Required indexes per `new-entities.md`:
- `tag_namespaces`: `(client_id)`, `(client_id, is_system)`, `(client_id, status)`
- `tags`: `(client_id)`, `(client_id, namespace_id)`, `(client_id, status)`
- `entity_tags`: `(client_id, entity_type, entity_id)` — primary read, `(client_id, tag_id)` — reverse read, `(client_id, entity_type, tag_id)` — matching reads

#### Service layer

- **`TaggingService`** — namespace + tag CRUD, archival/restore, attach/detach, AI suggestion staging, confirmation. Enforces cross-entity tenant invariants (`Tag.namespace.clientId == Tag.clientId`, etc.) at the service layer, since Postgres FKs alone don't express same-tenant constraints.
- **`TagSuggestionService`** — AI suggestion endpoint. Pulls tenant's active vocabulary, sends to Anthropic SDK, validates the returned tag IDs against the active vocabulary, drops unknown/cross-tenant/archived suggestions silently (with telemetry counter), stages valid ones via `EntityTag` with `source = AI_SUGGESTED`.
- **`TaggableEntityResolver`** interface — implemented per consuming module. Spring auto-discovers all `@Component`-registered implementations and the tagging service routes calls by `entityType()`.

```java
public interface TaggableEntityResolver {
    TaggableEntityType entityType();
    boolean exists(UUID entityId, UUID clientId);
    boolean canRead(Principal principal, UUID entityId);
    boolean canEdit(Principal principal, UUID entityId);
}
```

#### Controllers

- `TagLibraryController` (`/api/v1/platform/tagging/namespaces` and `/tags`) — admin CRUD, gated by `action.tagging.manage`.
- `EntityTaggingController` (`/api/v1/platform/tagging/entities/{type}/{id}/tags`) — read/write tags on entities; delegates permission to the entity's resolver.
- `TagSuggestionController` (`/api/v1/platform/tagging/ai/suggest`) — standalone AI suggestion endpoint. Rate-limited per `rate-limit-sensitive.md`.
- `TagMatchController` (`/api/v1/platform/tagging/entities` with filter params) — read-only matching reads consumed by F-07/F-08/F-10.

#### Repositories

Standard Spring Data JPA. Every query method must include `clientId` per `new-entities.md`. Belt-and-suspenders with the `@Filter`.

#### New permission and enums

- New permission: `action.tagging.manage` (gates Tag Library admin).
- New `AuditAction` values: `TAG_NAMESPACE_CREATE / UPDATE / ARCHIVE / RESTORE`, `TAG_CREATE / UPDATE / ARCHIVE / RESTORE`, `ENTITY_TAG_ATTACH / DETACH / CONFIRM_AI_SUGGESTION`.
- New `AuditResourceType` values: `TAG_NAMESPACE`, `TAG`, `ENTITY_TAG`.
- New `TaggableEntityType` enum: starts at `ASSESSMENT`, `QUESTION_BANK`; each consuming module adds its enum value.

#### Feature flag

- `tagging_enabled` — per-tenant boolean. Defaults `true` for all tenants. When false: admin UI hidden, `<EntityTagPicker>` not rendered, AI suggestion endpoint returns 204 no-op, matching reads return empty lists. Per `permissions-and-feature-flags.md` conventions.

### Frontend

#### Tag Library admin (`src/platform/tagging/admin/`)

- Mounted at `/settings/platform → Tag Library` tab, next to Managed Data and Builder Config.
- Two-pane layout: namespace list (left), tags within selected namespace (right).
- System namespaces show locked indicators (`is_system = true` rows). `display_name` and `description` editable; `name` immutable post-create.
- "Show archived" toggle adds `?status=ALL` to namespace list query.
- Archival dialog includes a warning explaining that existing tagged content keeps the tag for historical accuracy.

#### `<TagsSectionWidget>` component (`src/platform/tagging/components/TagsSectionWidget.tsx`)

The canonical drop-in for any builder or edit page that needs a tag section. Callers provide:

- `entityType` — required, identifies which tag resolver to use
- `value` + `onChange` — controlled tag ID list; parent owns persistence
- `entityId?` — when the entity already exists in DB; widget sends this to the unified AI endpoint so the backend can resolve full content via its `TaggableEntityResolver`
- `aiContext?` — when entity is a draft (no ID yet); widget sends `{ title, description, samples }` inline
- `description?` — helper text rendered above the picker
- `autoTriggerCount?` — increment to programmatically fire AI suggestion (used by copilot `OPEN_TAG_SUGGESTIONS` action)

If neither `entityId` nor `aiContext` is provided, the AI "Suggest tags" button is hidden. If `aiContext` is provided but `aiContext.title` is empty, a "Add a title above to enable AI suggestions" hint replaces the button.

Each entity type flattens its domain content into `aiContext.samples` as human-readable strings (e.g. `"Lesson: Competitive Analysis"`, `"Criteria: Close 3 enterprise deals"`). The widget has zero knowledge of what lessons or criteria are.

#### `<EntityTagPicker>` component (`src/platform/tagging/components/EntityTagPicker.tsx`)

Pure controlled UI — chip list, search combobox, create-tag option, AI suggestion ribbon. Always receives `value` + `onChange` from the parent. No API calls inside the component. Consumers reach for `<TagsSectionWidget>` instead of `<EntityTagPicker>` directly.

#### `useEntityTagManager` hook (`src/platform/tagging/hooks/useEntityTagManager.ts`)

Used by edit-page parents that need immediate persistence on tag change. Wraps `useEntityTags` (read) and `useReplaceEntityTags` (write), providing optimistic local state so chips appear and disappear immediately without waiting for the server round-trip. Returns a standard `{ value, onChange, isLoading, isError, refetch, tagNameLookup }` interface for `<TagsSectionWidget>`.

#### Typical wiring — create wizard (draft entity)

```tsx
const [tagIds, setTagIds] = useState<string[]>([]);

<TagsSectionWidget
  entityType="ASSESSMENT"
  value={tagIds}
  onChange={setTagIds}
  aiContext={{ title: watchedTitle, samples: [...relevantSamples] }}
/>
// On entity save: replaceEntityTags(entityType, newId, { tagIds })
```

#### Typical wiring — edit page (saved entity)

```tsx
const tagManager = useEntityTagManager("COURSE", courseId);

<TagsSectionWidget
  entityType="COURSE"
  entityId={courseId}
  value={tagManager.value}
  onChange={tagManager.onChange}
/>
```

#### Hooks

- `useTagApi.ts` — TanStack Query wrappers around all tagging endpoints. Naming follows `useDataObjectApi.ts`.
- `useEntityTagManager.ts` — immediate-persistence wrapper for edit pages (see above).

### AI suggestion — unified endpoint

All AI tag suggestions, whether for saved or draft entities, flow through a single endpoint:

    POST /api/v1/platform/tagging/ai/suggest

Request body sends either `entityId` (BE fetches full content from DB via the entity's `TaggableEntityResolver`) or `context { title, description, samples }` (inline content from the client). Never both — the contract enforces mutual exclusion.

The `samples` field is a `List<String>` of free-form text fragments. Each entity type serialises its domain-specific content into strings — lesson titles, question prompts, incentive criteria, etc. The LLM receives these verbatim as "Content samples: [...]". There are no entity-specific fields in the `ContextDto`; the endpoint is fully domain-agnostic.

AI suggestions returned from the endpoint are **ephemeral in the UI session**. They are not staged as `AI_SUGGESTED` entity-tag rows in the database. When a user accepts a suggestion, it is written as a `HUMAN`-source `EntityTag` via the standard `PUT /tags` bulk-replace. This keeps the data model simple and avoids pending-review state for single-author flows. If multi-user collaborative tag review (user A suggests, user B approves) becomes a product requirement, the stage/confirm backend infrastructure remains in place and can be re-enabled as a specialised mode.

Two-layer grounding is still enforced (mirrors FR-01.17's reference-grounding discipline):

1. **Prompt-level:** the model receives the tenant's serialised active vocabulary in-context and is instructed to return only tag IDs from that list. Rationale (one-sentence "why this tag") returned in-line with each suggestion.
2. **Server-side validation:** every returned `tagId` is re-validated against `Tag` rows where `clientId = currentTenant AND status = ACTIVE`. Unknown, cross-tenant, or archived IDs are silently dropped (logged + counter incremented for model-drift alarm). The user is never told a suggestion was rejected — exposing prompt details is a security leak.

### Seeding and migration

Four Flyway migrations:
1. `V{N}__create_tag_namespaces.sql`
2. `V{N+1}__create_tags.sql`
3. `V{N+2}__create_entity_tags.sql`
4. `V{N+3}__seed_system_tag_namespaces.sql` — per-tenant insert of three system namespaces (`product`, `topic`, `difficulty`). Idempotent via `WHERE NOT EXISTS` guard. **No tag values are seeded** — tenants populate during onboarding.

New-tenant provisioning: `ClientService.createClient(...)` (or platform equivalent) calls `TagNamespaceSeedService.seedSystemNamespaces(clientId)` to seed the three system namespaces for the newly-created tenant. Parallel obligation to `managed-data.md`'s new-tenant provisioning gap.

### Observability

Required metrics:

| Metric | Purpose |
|---|---|
| `tagging.suggestion.served{tenant_id}` counter | AI suggestion volume per tenant |
| `tagging.suggestion.accepted_rate{tenant_id}` gauge | Acceptance rate over rolling window — drift indicator |
| `tagging.suggestion.rejected_drop{tenant_id}` counter | Server-side validation drops — **model-drift alarm if non-zero in steady state** |
| `tagging.vocabulary.size{tenant_id}` gauge | Active tag count per tenant |
| `tagging.entity_tag.attach{entity_type}` counter | Attach volume by entity type |
| `tagging.match.query_duration{endpoint}` histogram | Match endpoint latency |

## Examples in codebase

To be filled in after F-01 lands the platform tagging infrastructure. Expected paths once implemented:

- Backend service: `../tenxengage-backend/src/main/java/com/tenxengage/app/platform/tagging/TaggingService.java`
- Backend entities: `../tenxengage-backend/src/main/java/com/tenxengage/app/platform/tagging/{TagNamespace,Tag,EntityTag}.java`
- Resolver interface: `../tenxengage-backend/src/main/java/com/tenxengage/app/platform/tagging/TaggableEntityResolver.java`
- First resolver implementations: `../tenxengage-backend/src/main/java/com/tenxengage/app/enablement/assessment/AssessmentTaggableResolver.java` and `QuestionBankTaggableResolver.java`
- Frontend admin: `../tenxengage-frontend/src/platform/tagging/admin/TagLibrary.tsx`
- Frontend picker: `../tenxengage-frontend/src/platform/tagging/components/EntityTagPicker.tsx`
- Frontend hook: `../tenxengage-frontend/src/platform/tagging/hooks/useTagApi.ts`

## Common gotchas

- **Don't add `tag_id` array columns on entity tables.** All tagging goes through `EntityTag`. Adding tag columns on entity tables breaks the sidecar contract — the tagging package owns the join, no other package adds tag-shaped state.
- **Don't seed tag values, only namespaces.** Seeded values become noise across tenants with different vocabularies. The three system namespaces are seeded empty; tenants populate during onboarding.
- **Archive, don't hard-delete namespaces or tags.** Hard-deleting orphans historical `EntityTag` rows and breaks F-07/F-08 matching against past content. Archival preserves history while hiding from new tagging UIs. The single exception is `EntityTag` itself, which hard-deletes on detach (audit log captures the event).
- **Service principals bypass `TaggableEntityResolver` permission checks, but still respect tenant isolation.** F-07/F-08/F-10 run as service principals; they don't have a user identity to check `canRead` against. Tenant scoping via `client_id` is preserved.
- **AI must never invent tag values; server validates and silently drops.** The grounding rule is enforced twice (prompt + server). If `tagging.suggestion.rejected_drop` is non-zero in steady state, the model has drifted — investigate.
- **New-tenant provisioning seed must wire up `ClientService`.** A Flyway migration only seeds existing tenants at deploy time. New tenants created later must get the three system namespaces via the tenant-provisioning code path. Parallel obligation to `managed-data.md`'s same gotcha.
- **Never use tagging for audience targeting.** See `## What this pattern is NOT`. Region, role, audience, visibility are not tagging concerns. If a tenant creates audience-shaped namespaces (e.g., `for-aes-only`), that's tenant misuse; the platform documents the principle but does not mechanically prevent it.
- **Tag CRUD must enforce namespace status.** `createTag` and `restoreTag` must check that the parent namespace is `ACTIVE` before mutating tag state. Skipping this check allows ACTIVE tags to exist under ARCHIVED namespaces, breaking the lifecycle invariant and producing inconsistent vocabulary reads.
- **Cascade-archive and user-archive must be distinguished for safe restore.** A namespace restore that bulk-updates ALL ARCHIVED tags to ACTIVE will silently restore tags the operator deliberately archived before the namespace was archived. Track which tags were cascade-archived (e.g., a `cascade_archived` boolean column set during namespace archive, cleared on restore) and restrict `restoreArchivedTagsByNamespace` to only those rows.
- **Partial unique index + restore = potential collision.** The active-tag uniqueness index is partial (`WHERE status = 'ACTIVE'`). If a tag value is archived, a second tag with the same value is created (allowed by the partial index), and then the namespace is archived (cascade-archiving both), a subsequent namespace restore will produce a unique constraint violation when it tries to make both ACTIVE simultaneously. The cascade-archive flag (above) naturally prevents this by limiting restore to only one of the two rows.
- **`TaggableEntityType` enum lives in tagging; resolvers live in consuming modules.** Avoid the temptation to put resolver implementations in the tagging package — that re-introduces the inverted dependency the resolver pattern is designed to eliminate.
- **Cross-tenant invariants are service-layer, not DB-layer.** Postgres FKs alone don't express "same client_id on both sides." Service-layer checks must validate `Tag.namespace.clientId == Tag.clientId`, `EntityTag.tag.clientId == EntityTag.clientId`, and `EntityTag.entityId` resolves in target table for the same tenant. Skipping these checks is a tenant-isolation breach.
- **Gate the tagging UI behind the same lifecycle check as other mutating sections.** When an entity page hides edit controls for archived/locked records (e.g., `assessment.status !== 'ARCHIVED'`), the `<TagsSectionWidget>` must be wrapped in the same guard. It exposes save (via `onChange` → `useEntityTagManager`) and AI-suggest mutations — omitting the guard lets archived entities be mutated through tagging while appearing read-only everywhere else.
- **No tag-change Kafka events in v1.** Consumers poll on their own schedule. If event-driven consumption becomes necessary (e.g., reactive recommendation cache invalidation), that's a v1.1 extension via `event-publishing.md`.
- **`<TagsSectionWidget>` is the entry point; do not reach for `<EntityTagPicker>` directly.** Reaching for the lower-level component bypasses AI suggestion state management, status messaging, and the `entityId`/`context` routing logic. `<EntityTagPicker>` is an internal implementation detail of the tagging platform; only `<TagsSectionWidget>` is part of the public component API.
- **`aiContext.samples` is free-form text; keep strings human-readable.** The LLM interprets them literally. Prefer `"Lesson: Pricing Strategy"` over a raw UUID or a camelCase field name. Poor sample serialisation degrades suggestion quality. Each entity type is responsible for flattening its own domain content — `<TagsSectionWidget>` has no knowledge of lessons, questions, or criteria.
- **Do not send both `entityId` and `context` to the AI suggest endpoint.** The contract is mutually exclusive. Send one or the other — `entityId` for saved entities (BE resolves content from DB), `context` for drafts (FE provides inline summary). The backend validates this. Sending both is undefined behaviour and may change in a future release.
