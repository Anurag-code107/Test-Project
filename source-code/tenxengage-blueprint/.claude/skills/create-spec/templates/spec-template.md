---
slug: {{slug}}
name: {{FEATURE_NAME}}
status: draft
format: story-sliced
roadmap: {{roadmap_or_null}}
domain: {{domain_or_null}}              # incentive | enablement | {new-domain} | null (non-slot-filling)
builder_type: {{builder_type_or_null}}  # e.g., COURSE | LEARNING_PATH | SALES | null (non-builder-shaped)
created: {{DATE}}
contract: null
visual_reference:
  component_path: {{path_relative_to_frontend_repo_or_null}}  # e.g., src/components/courses/builder/CourseBuilderLayout.tsx
  notes: {{which_sections_or_aspects_apply_or_null}}
applicable_sections:
  source: {{builder_definition_or_manual_or_null}}
  sections: []  # e.g., ["basics", "dates", "audience", "lessons", "tags", "rewards", "publish"]
---

# Feature: {{FEATURE_NAME}}

> **Format:** story-sliced
> **Stories, tasks, and per-story tests live in sibling files:**
> - [`stories.md`](stories.md) — story index + dependency graph
> - [`stories/`](stories/) — one `US-NN-*.md` per story (self-contained execution unit)
> - [`tasks/foundation.md`](tasks/foundation.md) — horizontal bedrock tasks
> - [`tracker.md`](tracker.md) — session status tracker
> - [`test-plan.md`](test-plan.md) — cross-story integration tests
>
> **This file is the design reference.** Implementers read it alongside their story file.
>
> **Technical artifacts** (Flyway SQL, file paths, hook specs): see [`technical.md`](technical.md).

---

## Overview

_One paragraph describing what this feature does, why it's being built, and how it fits into the TenXEngage product._

---

## Functional Requirements

_What must the system do? Each row is one verifiable capability, traceable to acceptance criteria and QA. Aim for 5–15 rows — cover every distinct thing the feature enables or enforces._

| ID | Requirement |
|---|---|
| FR-1 | _{{Actor}} can {{do what}} — e.g., "CLIENT_ADMIN can create a course with a name, description, and creation mode"_ |
| FR-2 | _{{Actor}} can {{do what}}_ |

---

## Functional Completeness Audit

_Records the functional-completeness probe run during `/create-spec` — what was proposed, what was approved, what was rejected or deferred. Downstream skills and `/review-spec` use this section to understand what was considered, not just what was accepted._

| # | Dimension | Status | FR / Notes |
|---|---|---|---|
| 1 | {{Lifecycle states — phrased naturally}} | ✓ Already covered | FR-{{N}} — {{covering FR text}} |
| 2 | {{Completion criteria}} | ⊕ Approved | FR-{{N}} — {{approved FR text}} |
| 3 | {{Cascades}} | ⊕ Modified | FR-{{N}} — {{user's verbatim wording}} |
| 4 | {{Resumability}} | ⊕ Rejected | {{reason if given, or "—"}} |
| 5 | {{Expiry}} | ⚠️ FUNCTIONAL GAP — DEFERRED | {{description of deferred gap}} |

_Omit rows for dimensions not applicable to this feature. If no gaps were identified, write: "No functional gaps identified — all applicable dimensions were already covered by the brief."_

---

## Non-Functional Requirements

_These shape implementation decisions across every layer. Fill from user input or use the defaults below._

| Dimension | Requirement | Notes |
|---|---|---|
| **Response time (reads)** | P95 < 300ms | List endpoints with filters |
| **Response time (writes)** | P95 < 500ms | Create/update operations |
| **Peak concurrent users** | {{N}} users | Informs pagination defaults, connection pool sizing |
| **Max page size** | 50 items | Hard cap on `size` query param |
| **Availability** | 99.9% | Core user-facing flow / internal tool |
| **Data sensitivity** | {{PUBLIC \| INTERNAL \| CONFIDENTIAL \| PII}} | Drives encryption, masking, audit requirements |
| **Compliance** | {{GDPR \| None}} | Drives Data Retention section |
| **Audit retention** | {{7 years \| 1 year \| N/A}} | How long audit records must be kept |

---

## Prerequisites

- [ ] Spec reviewed via `/review-spec` (status must be `reviewed`)
- [ ] Contracts generated via `/generate-contracts` in the backend repo
- [ ] Next Flyway migration number confirmed (current latest: V{{N}})
- [ ] _Add any feature-specific prerequisites_

---

## New Enums [BE]

| Enum Class | Values | Notes |
|---|---|---|
| `{{EnumName}}.java` | `VALUE_1, VALUE_2, VALUE_3` | _Purpose_ |

_Path: `src/main/java/com/tenxengage/app/entity/enums/`_

---

## Data Model / Entities [BE]

### Entity-shape decisions

> **OPTIONAL — emit only when at least one entity in this spec's scope was resolved by the `entity-shape-decisions.md` procedure.** Omit the entire sub-section if the feature operates on no entities.

| Entity | Shape | Source |
|---|---|---|
| `{{Entity 1}}` | Configurable data object | This spec / Inherited from digest / Override of digest (was: hardcoded) |
| `{{Entity 2}}` | Hardcoded JPA entity | This spec |

### {{EntityName}} (table: `{{table_name}}`)

_Path: `src/main/java/com/tenxengage/app/entity/`_
_Extends `BaseEntity`, implements `TenantAware`_
_Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`_

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Inherited from BaseEntity |
| `client_id` | `UUID` | NOT NULL, FK → clients | Tenant isolation — NEVER expose in API responses |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |
| `deleted` | `BOOLEAN` | NOT NULL, DEFAULT `false` | Soft delete flag |
| `version` | `BIGINT` | NOT NULL, DEFAULT `0` | Optimistic locking (`@Version`) — prevents lost updates |
| `{{field}}` | `{{type}}` | `{{constraints}}` | _{{notes}}_ |

**PII Fields** (if any — drives Data Retention section):
- `{{field}}` — contains `{{PII type: name / email / phone / address}}`

**Relationships:**
- `@ManyToOne` → {{ParentEntity}} (FK: `{{parent_id}}`)
- `@OneToMany` → {{ChildEntity}} (mappedBy: `{{field}}`)

**Indexes:**
- `idx_{{table}}_client_id` on `client_id`
- `idx_{{table}}_client_status` on `(client_id, status)` — supports filtered list queries
- `idx_{{table}}_{{field}}` on `{{commonly_queried_field}}`

_Repeat for each entity._

---

## Permissions & Feature Flags [BE + FE]

_Every feature needs permissions — every endpoint has `@RequiresPermission`, every page needs `ProtectedRoute`, every sidebar item needs `permissionKey`. This section is the single source of truth for what permissions exist, who gets them, and the Flyway SQL to seed them._

### Permission Matrix

_One row per permission key — this is the single source of truth for what exists, who gets it, and the Flyway seed SQL. Add rows for every non-CRUD verb (manage, publish, approve, submit, enroll, clone, export, import). Remove role columns not relevant to this feature. Scope: `ALL` = both client and partner roles; `INTERNAL` = client-level only; `EXTERNAL` = partner-level only._

_Role semantics: CLIENT_ADMIN manages and configures. ACTIVITY_APPROVER reviews/approves — view + approve only. PARTNER_ADMIN oversees their company's team — view + participate. PARTNER_SELLER is an individual — self-service only (view, submit, claim). Getting this wrong means security gaps or broken access._

| Permission Key | Display Name | Type | Scope | Category | CLIENT_ADMIN | ACTIVITY_APPROVER | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|---|---|---|---|
| `module.{{feature}}` | {{Feature}} Access | MODULE | `INTERNAL` | MODULE_ACCESS | Y | — | — | — |
| `action.{{entity}}.view` | View {{Entities}} | ACTION | `ALL` | {{DOMAIN}}_ACTIONS | Y | — | {{Y/—}} | {{Y/—}} |
| `action.{{entity}}.create` | Create {{Entities}} | ACTION | `INTERNAL` | {{DOMAIN}}_ACTIONS | Y | — | — | — |
| `action.{{entity}}.edit` | Edit {{Entities}} | ACTION | `INTERNAL` | {{DOMAIN}}_ACTIONS | Y | — | — | — |
| `action.{{entity}}.delete` | Delete {{Entities}} | ACTION | `INTERNAL` | {{DOMAIN}}_ACTIONS | Y | — | — | — |
| `action.{{entity}}.{{verb}}` | {{Verb}} {{Entities}} | ACTION | `{{scope}}` | {{DOMAIN}}_ACTIONS | {{Y/—}} | {{Y/—}} | {{Y/—}} | {{Y/—}} |

_Every key in this table must also appear in the Flyway Seed Migration's `IN (...)` list for the appropriate role block._

### Feature Flag

| Feature Key | Description | starterEnabled | professionalEnabled | enterpriseEnabled | Category |
|---|---|---|---|---|---|
| `{{feature_key}}` | {{Description of what this feature enables}} | `false` | `{{true/false}}` | `true` | {{category}} |

_Feature flags control whether the entire feature is available for a tenant's subscription tier. Permissions control who within a tenant can use the feature. Both are required._

_Flyway seed SQL for this permission matrix lives in `technical.md → ## Flyway Migrations [BE]`._

---

## DTOs [BE]

### Request DTOs

_Path: `src/main/java/com/tenxengage/app/dto/request/`_

| Record | Key Fields | Validation |
|---|---|---|
| `Create{{Entity}}Request` | `{{field1}}, {{field2}}` | Structural only: `@NotBlank`/`@NotNull` for mandatory fields, `@Size(max=255)`, `@Email`. Do NOT add `@Pattern`/format constraints for fields whose domain rule lives in the service with a spec-mandated `errorCode` — Jakarta validation fires first and masks the spec error shape (see Validation rules below). |
| `Update{{Entity}}Request` | `{{field1}}, {{field2}}` | Same as Create |

**Validation rules:**
- All string fields: `@Size(max=N)` — prevents oversized payloads
- Free-text fields (`description`, `notes`): `@Size(max=5000)`; if rendered as HTML, sanitize in the service layer (see Security Design — `@SafeHtml` was removed in Hibernate Validator 7+)
- UUID references: `@NotNull` — never allow null references to cascade as silent failures
- Enums: validate via `@ValidEnum(enumClass=...)` — reject unknown values with 400, not 500
- Format / domain-constrained fields (URL scheme, slug shape, cross-field rules): do NOT add `@Pattern`/`@NotBlank` at the DTO layer when the same rule is enforced in the service with a spec-mandated `errorCode`. Jakarta bean validation fires first and returns a generic `VALIDATION_ERROR` instead of the spec's `errorCode` shape. Keep only structural constraints (`@Size`, `@NotNull`, `@Email`) on the DTO and let the service throw the domain-specific exception so `GlobalExceptionHandler` maps the correct status + code.

**Nested records:** `{{NestedRecord}}({{field1}}, {{field2}})`

### Response DTOs

_Path: `src/main/java/com/tenxengage/app/dto/response/`_

| Record | Static Factory | Rendered Fields |
|---|---|---|
| `{{Entity}}Response` | `from({{Entity}})` | List view — enumerate every field the list row/table renders (no nested collections) |
| `{{Entity}}DetailResponse` | `from({{Entity}}, List<...>)` | Detail view — enumerate every field the detail surface renders (see "UI-backing response DTOs" below); each referenced child entry carries `{id, displayName, description, type, order}`, NOT just `refId`. A field-list shorthand is not sufficient — list each rendered field. |

**Never include in responses:** `client_id`, `deleted`, `version`, passwords, tokens, or any field not needed by the caller.

**UI-backing response DTOs — enumerate rendered fields.** For every response DTO that a page, drawer, table, or detail surface renders, list each field the FE displays, with its type and a one-line "rendered as" note. A detail/drawer DTO that references another entity (by `refId`/UUID) MUST carry the display fields the surface needs — at minimum a human-readable name, plus any description/label/status the UI shows — never a bare `refId` + type + order. If the only fields are an opaque reference, the detail surface cannot render and the spec is incomplete.

_Full DTO shapes: see `../tenxengage-contracts/`. Do not hand-write Java records in the spec._

---

## API Endpoints [BE + FE]

_Base path: `/api/v1/{{resource-path}}`_
_Tag: `{{Tag Name}}`_

| Method | Path | Request Body | Response | Status | Permission | Audit |
|---|---|---|---|---|---|---|
| `GET` | `/` | — | `Page<{{Entity}}Response>` | 200 | `{{PERMISSION}}` | — |
| `POST` | `/` | `Create{{Entity}}Request` | `{{Entity}}DetailResponse` | 201 | `{{PERMISSION}}` | `@Audited` |
| `GET` | `/{id}` | — | `{{Entity}}DetailResponse` | 200 | `{{PERMISSION}}` | — |
| `PUT` | `/{id}` | `Update{{Entity}}Request` | `{{Entity}}DetailResponse` | 200 | `{{PERMISSION}}` | `@Audited` |
| `DELETE` | `/{id}` | — | — | 204 | `{{PERMISSION}}` | `@Audited` |
| `PATCH` | `/{id}/status` | `Update{{Entity}}StatusRequest` | `{{Entity}}Response` | 200 | `{{PERMISSION}}` | `@Audited` |

_The `Permission` column must use the exact `permission_key` values from the Permissions & Feature Flags section above. These map directly to `@RequiresPermission("{{key}}")` annotations in the controller._

**Query parameters for list endpoint:**
- `status` (optional filter)
- `search` (optional, searches title/description — sanitized via parameterized JPQL `LOWER(e.title) LIKE :q`)
- `page`, `size` (max 50), `sort` (allowlist of sortable columns — reject unknown sort fields with 400)

**Error responses:**
- `400` — Validation failure / invalid state transition / unknown sort field
- `401` — Not authenticated (missing or expired JWT)
- `403` — Insufficient permissions
- `404` — Entity not found or belongs to a different tenant (always 404, never 403, to prevent tenant enumeration)
- `409` — Conflict (duplicate, optimistic lock failure — retry with current `version`)
- `429` — Rate limit exceeded

---

## Service Layer [BE]

_Path: `src/main/java/com/tenxengage/app/service/`_

### {{Entity}}Service

| Method | Return Type | Notes |
|---|---|---|
| `create{{Entity}}(request)` | `{{Entity}}DetailResponse` | `@Transactional` |
| `get{{Entities}}(status, search, pageable)` | `Page<{{Entity}}Response>` | `@Transactional(readOnly=true)` |
| `get{{Entity}}ById(id)` | `{{Entity}}DetailResponse` | `@Transactional(readOnly=true)` |
| `update{{Entity}}(id, request)` | `{{Entity}}DetailResponse` | `@Transactional` — passes `version` from request for optimistic lock check |
| `delete{{Entity}}(id)` | `void` | `@Transactional` — soft delete (`deleted = true`), never hard delete |
| `updateStatus(id, request)` | `{{Entity}}Response` | `@Transactional` — validates state machine transition before persisting |

**Business rules:**
- _List specific business rules and validation logic_
- _e.g., "Cannot PUBLISH a course with 0 lessons — throw `BusinessRuleException` with message 'A course must have at least one lesson before publishing'"_
- _e.g., "Soft delete sets `deleted = true` and fires a domain event; the record is never physically removed"_

**Tenant isolation contract:** Every service method resolves `clientId` from `TenantContext.getCurrentClientId()` — it never accepts `clientId` as a parameter from the API layer.

_File paths and method signatures: see `technical.md → ## Package Layout [BE]`._

---

## Workflow / Status Transitions [BE + FE]

_**Omit this entire section** if the feature has no entity status field or state machine — do not write "None" or "N/A"._

```
{{STATE_1}} → {{STATE_2}} (action: {{action_name}}, trigger: {{who/what}})
{{STATE_2}} → {{STATE_3}} (action: {{action_name}}, trigger: {{who/what}})
{{STATE_2}} → {{STATE_1}} (action: {{reverse_action}})
```

**Invalid transitions** (must return 400 with descriptive message):
- `{{STATE_3}} → {{STATE_1}}` — _reason (e.g., "Cannot revert a published course to draft once users have enrolled")_

**Who can trigger:**
- `{{STATE_1}} → {{STATE_2}}` — `CLIENT_ADMIN` only
- `{{STATE_2}} → {{STATE_3}}` — `CLIENT_ADMIN` or `PARTNER_ADMIN`

**Concurrent transition handling:** Use optimistic locking (`@Version` on entity). If two users attempt a status transition simultaneously, the second will receive a `409 Conflict` — the FE must show "This record was updated by someone else. Please refresh and try again."

---

## Security Design [BE]

_This section is mandatory. Every production feature in a multi-tenant system has security considerations._

### Data Classification

| Field / Dataset | Classification | Handling |
|---|---|---|
| `{{pii_field}}` | PII | Not returned in list responses; masked in logs; subject to GDPR deletion |
| `{{financial_field}}` | Confidential | Encrypted at rest via column-level encryption or stored in dedicated secrets store |
| `{{internal_field}}` | Internal | Not exposed to PARTNER_USER role |
| `{{public_field}}` | Public | No restrictions |

### Rate Limiting

_Reference the `RateLimitFilter` mechanism observed in Phase 1._

| Endpoint / Operation | Limit | Scope | Reason |
|---|---|---|---|
| `POST /` (create) | {{N}} req/min | Per tenant | Prevents bulk data injection |
| `GET /?search=` | {{N}} req/min | Per user | Search queries are expensive (full-text scan) |
| `{{expensive endpoint}}` | {{N}} req/min | Per user | _{{reason}}_ |
| `PATCH /{id}/status` | {{N}} req/min | Per user | Prevents rapid status cycling / state machine abuse |

### OWASP Risks & Mitigations

_List only risks that actually apply to this feature._

| Risk | Where | Mitigation |
|---|---|---|
| **Injection (A03)** | `search` query param, free-text fields | Parameterized JPQL (`LOWER(e.title) LIKE :q`); `@Size` limits; reject queries > 200 chars |
| **Broken Access Control (A01)** | All `/{id}` endpoints | ID resolved via `findByIdAndClientId` — wrong-tenant ID returns 404, never 403 |
| **IDOR (A01)** | `{{field}}` in request body that references another entity | Validate referenced entity also belongs to `TenantContext.getCurrentClientId()` |
| **Insecure Design (A04)** | File upload (if applicable) | Validate MIME type server-side (not client-supplied Content-Type); enforce max size; store in object storage, never on disk |
| **Mass Assignment** | PUT/POST request bodies | DTOs are explicit records — only declared fields are bound; no dynamic property binding |

### Input Validation Summary

| Field | Constraints | Rejection |
|---|---|---|
| `{{text_field}}` | `@NotBlank`, `@Size(max=500)` | 400 with field-level error |
| `{{free_text_field}}` | `@Size(max=5000)`, Jsoup service-layer sanitization if HTML-rendered | 400, sanitized before persistence |
| `{{enum_field}}` | `@ValidEnum(enumClass=CourseStatus.class)` | 400 — unknown enum value |
| `sort` query param | Allowlist: `["title", "createdAt", "status"]` | 400 — unknown sort column |
| `size` query param | `@Max(50)` | 400 — capped to prevent oversized responses |

---

## Audit Trail [BE]

_Audit records provide tamper-evident history of who did what, when, and from where. These are mandatory for any write operation on business entities._

_Path: `src/main/java/com/tenxengage/app/audit/` (use existing `@Audited` infrastructure)_

| Operation | Entity | Data Captured | Who Can View |
|---|---|---|---|
| CREATE `{{Entity}}` | `{{Entity}}` | Full created state snapshot, `createdBy`, `createdAt`, source IP | `CLIENT_ADMIN` |
| UPDATE `{{Entity}}` | `{{Entity}}` | Changed fields only (`oldValue` → `newValue`), `updatedBy`, `updatedAt` | `CLIENT_ADMIN` |
| STATUS CHANGE | `{{Entity}}` | `oldStatus` → `newStatus`, `changedBy`, `changedAt`, reason (if applicable) | `CLIENT_ADMIN` |
| DELETE `{{Entity}}` | `{{Entity}}` | `deletedBy`, `deletedAt`, entity ID | `CLIENT_ADMIN` |
| `{{sensitive_read}}` | `{{Entity}}` | Accessor `userId`, accessed entity ID, `accessedAt` | `PARTNER_ADMIN` only |

### New Audit Enum Values

_Check existing values in `entity/enums/AuditAction.java` and `entity/enums/AuditResourceType.java`. List only values that don't already exist._

| Enum | New Value | Reason |
|---|---|---|
| `AuditAction` | `{{PUBLISHED}}` | _e.g., Course published by admin_ |
| `AuditResourceType` | `{{COURSE}}` | _e.g., New entity type for audit tracking_ |

_These are Java enums stored as `varchar(50)` in the DB — no Flyway migration needed, just add to the Java enum file. If no new values are needed, write "None — existing enum values cover all operations."_

### `@Audited` Annotation Details (Non-CRUD Only)

_Standard CRUD annotations (POST → `action="Created"`, PUT → `action="Edited"`, DELETE → `action="Deleted"`) are inferred at implementation time — no need to list them here. Only specify non-standard operations where the action, resourceType, or description cannot be derived from the HTTP method._

| Endpoint | `action` | `resourceType` | `description` |
|---|---|---|---|
| `PATCH /{id}/status` (publish) | `Published` | `{{ENTITY}}` | `Published {{entity}}` |
| `PATCH /{id}/status` (archive) | `Deactivated` | `{{ENTITY}}` | `Archived {{entity}}` |
| `POST /{id}/duplicate` | `Created` | `{{ENTITY}}` | `Duplicated from {{entity}}` |

_The `resourceName` and `resourceId` SpEL expressions follow the standard pattern (`#result.body.title`, `#result.body.id.toString()`) and are filled in at implementation time._

**Audit record retention:** {{7 years / 1 year — per NFR section}}. Audit records are **never** soft-deleted. They are append-only.

**What NOT to audit:** Read operations on non-sensitive data (list views, detail views of non-PII entities) — log volume would be prohibitive and provides no security value.

---

## Observability [BE]

_Structured logs, metrics, and MDC fields that make this feature debuggable in production._

### MDC Fields (propagated to every log line within a request)

| MDC Key | Value | Set By |
|---|---|---|
| `requestId` | UUID from `X-Request-ID` header (or generated) | `RequestContextFilter` (existing) |
| `tenantId` | `clientId` from JWT | `TenantFilter` (existing) |
| `userId` | User ID from JWT | `JwtAuthenticationFilter` (existing) |
| `featureArea` | `"{{feature-kebab-name}}"` | Set in `{{Entity}}Service` constructor |

### Key Log Events

| Event | Level | `step` value | Key Fields | Purpose |
|---|---|---|---|---|
| `{{Entity}}` created | INFO | `{{entity}}_created` | `entityId`, `createdBy` | Business event tracking |
| Status changed | INFO | `{{entity}}_status_changed` | `entityId`, `oldStatus`, `newStatus` | Workflow audit trail |
| Validation rejected | WARN | `{{entity}}_validation_failed` | `field`, `reason` | Detect abuse / bad clients |
| Tenant isolation violation | ERROR | `tenant_isolation_violation` | `requestedId`, `callerTenantId` | Security alert — should never happen |
| Rate limit exceeded | WARN | `rate_limit_exceeded` | `endpoint`, `userId` or `tenantId` | Detect abuse patterns |
| Optimistic lock conflict | WARN | `optimistic_lock_conflict` | `entityId`, `expectedVersion`, `actualVersion` | Detect concurrent-edit issues |

**Sensitive data in logs:** Never log PII fields, JWT tokens, passwords, or full request bodies containing confidential data. Log entity IDs and `userId` (UUID — not PII) only.

### Metrics

| Metric Name | Type | Labels | Purpose |
|---|---|---|---|
| `{{entity}}.created.total` | Counter | `tenantId`, `status` | Volume tracking |
| `{{entity}}.status_transition.total` | Counter | `fromStatus`, `toStatus` | Workflow funnel analysis |
| `{{entity}}.list.duration_ms` | Histogram | — | Latency monitoring (alert if P95 > 500ms) |
| `{{entity}}.delete.total` | Counter | — | Deletion rate (spike = potential abuse) |

---

## Domain Events [BE]

_**Omit this entire section** if this feature produces no events other system components need to react to — do not write "None" or "N/A"._

_Topic naming convention: `tenxengage.{{domain}}.{{entity}}.{{past-tense-event}}` (e.g., `tenxengage.enablement.course.published`)_

### Events Produced

| Topic | Trigger | Message Schema | At-Least-Once Guarantee |
|---|---|---|---|
| `tenxengage.{{domain}}.{{entity}}.{{event}}` | When `{{trigger condition}}` | See schema below | Deduplicate on `eventId` at consumer |

**Message schema:**
```json
{
  "eventId": "uuid",
  "eventType": "{{ENTITY_EVENT}}",
  "occurredAt": "2026-04-14T10:00:00Z",
  "tenantId": "uuid",
  "entityId": "uuid",
  "triggeredBy": "uuid (userId)",
  "payload": {
    "{{field}}": "{{value}}"
  }
}
```

### Events Consumed

| Topic | Consumer Group | Handler | Idempotency |
|---|---|---|---|
| `tenxengage.{{source-domain}}.{{entity}}.{{event}}` | `{{feature-name}}-consumer` | `{{HandlerClass}}` | Check if `eventId` already processed in `processed_events` table |

---

## Frontend Specification [FE]

_TypeScript types live in `../tenxengage-contracts/` — copy from there, do not hand-write. Full FE file paths and hook specs: see `technical.md`._

### Pages

| Page | Route | Layout | Permission | Sidebar Entry |
|---|---|---|---|---|
| `{{Page}}Page` | `/{{route}}` | `ClientAdminLayout` | `CLIENT_ADMIN` | Yes — under "{{Section}}" |
| `{{Detail}}Page` | `/{{route}}/:id` | `ClientAdminLayout` | `CLIENT_ADMIN` | No |

### Key Components

| Component | Props | Data Source | Notes |
|---|---|---|---|
| `{{Component}}` | `{{props}}` | `use{{Entity}}()` hook | _Purpose_ |
| `{{Entity}}DetailDrawer` | `{{entityId}}` | `use{{Entity}}(id)` hook | Composite — see completeness rule. Sections: **Header** (name, status badge), **Details** (description, owner, dates), **Related** ({{child}} list: name + status per row) |

**Composite / content-bearing completeness.** Any component that renders entity data and has internal structure — drawers, multi-section panels, detail pages, cards, dashboards, tables — must additionally enumerate, in its Notes: (a) its **sections**, and per section the specific fields/content rendered (the DTO field-enumeration rule from detail surfaces applies to every such element, not just pages); (b) its **interactions** — what is actionable (clicks, row-select, expand/collapse) and keyboard behavior; (c) confirmation that **accessibility & responsive** are handled per `../tenxengage-frontend/PROJECT-CONTEXT.md` (reference, do not restate). Plain controls (buttons, single inputs) are exempt. States (loading/empty/error) are covered by `## Edge Cases`; per-element permission gating is covered by the Pages table — do not duplicate either here.

### Forms

| Form | Fields | Validation | Submit Action |
|---|---|---|---|
| `{{Entity}}Form` | `{{field1}}, {{field2}}` | `create{{Entity}}Schema` (zod) | `POST /api/v1/{{path}}` |

### Data Flow (TanStack Query)

| Hook | Query Key | Endpoint | StaleTime | Invalidation |
|---|---|---|---|---|
| `use{{Entities}}()` | `['{{entities}}', clientId]` | `GET /api/v1/{{path}}` | 5 min | On create/update/delete |
| `use{{Entity}}(id)` | `['{{entities}}', id]` | `GET /api/v1/{{path}}/{id}` | 5 min | On update |

_Service file path and hook query keys: see `technical.md → ## Package Layout [FE]` and `## Hook Specs [FE]`._

---

## Caching Strategy [BE]

_**Omit this entire section** if there is no server-side caching for this feature — do not write "None". Only include this section when `@Cacheable` is actually being introduced. (TanStack Query client-side caching is assumed for all FE features and does not need to be documented here.)_

| What | Cache Location | TTL | Cache Key | Invalidation Trigger |
|---|---|---|---|---|
| `{{Entity}}` detail | `@Cacheable("{{entities}}")` | 5 min | `{tenantId}:{entityId}` | `@CacheEvict` on update/delete |
| `{{Enum / config}}` | `@Cacheable("{{configs}}")` | 30 min | `{tenantId}` | `@CacheEvict` on config change |

_If no server-side caching: "No server-side caching applied. Data changes frequently and incorrect stale reads across tenants are not acceptable. TanStack Query handles client-side caching with 5-minute stale time."_

---

## Data Retention & Compliance [BE]

### Soft Delete vs Hard Delete

**Decision: Soft delete** (`deleted = BOOLEAN` flag on entity).
- **Why**: Preserves referential integrity for audit logs; allows recovery; required for GDPR "right to erasure" via anonymization rather than deletion.
- **Hard delete** applies only to: `{{specify any child entities that are hard-deleted on cascade}}`.

### PII Handling

_List every PII field in this feature and what happens to it under GDPR._

| Field | Entity | PII Type | GDPR Treatment |
|---|---|---|---|
| `{{field}}` | `{{Entity}}` | Name / Email / Phone | On data-subject deletion request: NULL out field value; preserve record shell for audit trail |
| `{{field}}` | `{{Entity}}` | Pseudonymous ID | Retain — UUID is not PII by itself |

### Data Retention Periods

| Data Type | Retention Period | Justification |
|---|---|---|
| Business entity records (soft-deleted) | {{7 years}} | Audit / legal hold requirement |
| Audit log entries | {{7 years}} | Compliance requirement |
| PII fields (after erasure request) | Immediate anonymization | GDPR Article 17 |
| Transient / session data | Per session / 24h | No long-term business value |

### Data Export (GDPR Article 20)
_If this feature stores PII: which tables and fields must be included in a data subject export request?_
- `{{table}}.{{field}}` — include in export
- `{{audit_table}}` — include (access records linked to the user)

---

## Configurable Dimensions [BE]

_**Omit this entire section** if the feature has no per-client configurable dimensions — do not write "None" or "N/A"._

| Dimension | Storage | Default | Notes |
|---|---|---|---|
| `{{dimension}}` | JSONB in `PartnerCompany.metadata` | `{{default}}` | Per-client configurable |
| `{{dimension2}}` | Hardcoded | `{{value}}` | Not configurable |

---

## Edge Cases [BE + FE]

1. **Empty state** — When there are 0 items in a list, FE shows `<EmptyState message="No {{entities}} yet" />` component; BE returns `Page` with `content: []` and `totalElements: 0`.
2. **Max limits** — `size` query param capped at 50 at the controller level (`@Max(50)`); requests with `size > 50` return 400 with message "Page size must not exceed 50".
3. **Concurrent update** — Two admins update the same entity simultaneously: second writer receives `409 Conflict`; FE shows "This record was changed by someone else. Refresh to see the latest version."
4. **Cross-tenant access** — Any request where the resolved entity's `client_id ≠ TenantContext.getCurrentClientId()` returns `404 Not Found` (never 403 — 403 reveals the entity exists).
5. **Permission boundary** — User with `VIEW_{{ENTITY}}` only attempts `POST /` → `403 Forbidden`. User with no role token → `401 Unauthorized`.
6. **Soft-deleted entity access** — `GET /{id}` for a soft-deleted entity returns `404`; it is excluded from all queries via `WHERE deleted = false`.
7. **Invalid state transition** — Attempting `ARCHIVED → DRAFT` (if invalid) returns `400` with message "Cannot transition from ARCHIVED to DRAFT".
8. **Special characters** — Unicode, emoji, and HTML in text fields: stored as-is in the DB (PostgreSQL handles Unicode natively); HTML-rendered fields are sanitized via Jsoup in the service layer before persistence and output-encoded on the FE.
9. **Pagination edge cases** — Request for `page=999` when only 2 pages exist returns `200` with `content: []` (Spring Page behavior — no error).
10. **Optimistic lock version mismatch** — Client sends stale `version` value on `PUT /{id}` → `409 Conflict` with message "Entity was modified concurrently. Re-fetch and retry."
11. **Rate limit hit** — FE receives `429 Too Many Requests`; show toast "You're doing that too fast. Please wait a moment and try again." with retry-after guidance.
12. **Tenant context missing** — If `TenantContext` is null (misconfigured filter chain): return `500` and log at ERROR with `step=tenant_isolation_violation`.

---

## Acceptance Tests

_Tests are split across two locations:_
- **Per-story tests** (unit, @WebMvcTest, Vitest, E2E Playwright) — live inside each `stories/US-NN-*.md` file alongside the code they verify
- **Cross-story integration tests** (Testcontainers full-lifecycle, multi-entity workflows, tenant isolation, audit/events) — in [test-plan.md](test-plan.md)

---

## Modified Existing Endpoints [BE + FE]

_**Omit this entire section** if no existing endpoints are modified — do not write "None" or "N/A"._

| Endpoint | Change | Reason | Breaking? |
|---|---|---|---|
| `GET /api/v1/{{existing-path}}` | Added `{{newField}}` to response | _Why this change is needed_ | No — additive change |

_Breaking changes (field removal, type change, required→optional) require a new API version (`/api/v2/...`) or a migration window with deprecation notice._

---

## Out of Scope

_Explicitly list what is NOT included to prevent scope creep._

- {{Future feature 1}}
- {{Future feature 2}}
- {{Related but deferred capability}}

---

## Verification Steps

### Backend Verification
1. `./gradlew bootRun` — app starts; Flyway V{{N}} migration applies without errors
2. `./gradlew test` — all new and existing tests pass
3. Security spot-checks: cross-tenant `GET /{id}` → `404`; write endpoint without permission → `403`; no JWT → `401`
4. Observability: tail logs on a `POST` request; verify `step={{entity}}_created`, `tenantId`, `userId` appear

### Frontend Verification
1. `npm run build` — no TypeScript errors
2. `npm run test` — Vitest passes; `npx playwright test` — E2E passes
3. UI: list renders / empty state shows; form validates inline; concurrent-edit conflict warning appears
