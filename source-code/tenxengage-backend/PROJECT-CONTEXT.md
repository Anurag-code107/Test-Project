# Project Context — TenXEngage Backend

## Stack

- **Language:** Java 21
- **Framework:** Spring Boot 3.2.5
- **Database:** PostgreSQL 16
- **Cache:** Redis 7
- **Messaging:** Apache Kafka (KRaft mode)
- **Security:** Spring Security 6 (JWT / OAuth2 / SAML2)
- **Migrations:** Flyway
- **Build:** Gradle 8.5
- **API Docs:** SpringDoc OpenAPI (Swagger UI)

## Architecture
- Layered: controller → service → repository → entity
- Multi-tenant via client_id + Hibernate @Filter
- Virtual threads enabled, async via @EnableAsync

## Project Structure

```
src/main/java/com/tenxengage/app/
  TenxengageApplication.java
  config/                  # SecurityConfig, CorsConfig, RedisConfig, KafkaConfig, OpenApiConfig, JacksonConfig, AsyncConfig
  controller/              # REST controllers
  service/                 # Business logic
  repository/              # Spring Data JPA
  entity/                  # JPA entities
    enums/                 # Java enums (UserStatus, RoleName, etc.)
  dto/
    request/               # Inbound DTOs (validated)
    response/              # Outbound DTOs (records)
  exception/               # Custom exceptions + GlobalExceptionHandler
  security/                # JWT, filters, tenant context
  batch/                   # Spring Batch job configs

src/main/resources/
  application.yml / application-{local,dev,prod}.yml
  logback-spring.xml       # Console local, JSON non-local
  db/migration/            # Flyway SQL migrations

src/test/java/com/tenxengage/app/
  AbstractIntegrationTest.java   # Testcontainers base class
  controller/              # @WebMvcTest tests
  service/                 # Mockito unit tests

contracts/                 # API contracts (git submodule — source of truth)
```

## Coding Conventions
- 4 spaces, 120 char max line, no wildcard imports
- PascalCase classes, camelCase methods, UPPER_SNAKE_CASE constants
- lowercase packages, kebab-case REST paths, snake_case DB columns
- Lombok: @Data/@Builder/@NoArgsConstructor/@AllArgsConstructor on entities
- Java records for response DTOs
- @RequiredArgsConstructor for constructor injection (never field @Autowired)
- Never declare `static final ObjectMapper MAPPER = new ObjectMapper()` — inject the Spring-managed `ObjectMapper` bean via constructor instead. Spring Boot auto-configures it with `JavaTimeModule` and consistent serialization settings; a hand-constructed instance diverges from the rest of the app and wastes heap.
- Never concatenate nullable name fields — use `String.join(" ", Objects.toString(firstName, ""), Objects.toString(lastName, "")).strip()` to prevent `"null null"` output
- SLF4J logging: `private static final Logger log = LoggerFactory.getLogger(...)`
- When catching exceptions for a fallback, always pass the exception as final arg: `log.warn("msg {}", detail, e)` — omitting `e` loses the stack trace in log aggregators
- Service validation methods that throw `BusinessRuleException` must emit `log.warn()` with the failing field/value (no PII) **before** throwing — operators cannot distinguish validation failure causes in production logs without this context

## API Patterns
- Base: /api/v1/, plural resources, kebab-case paths
- All controllers return `ResponseEntity<T>`
- Request DTOs: classes with Bean Validation (@NotBlank, @Size, @Email)
- Response DTOs: Java records with static `from()` factory method
- HTTP status codes: 200 (GET/PUT), 201 (POST), 204 (DELETE)
- Error codes: 400, 401, 403, 404, 409, 422, 500
- SpringDoc OpenAPI annotations (@Tag, @Operation) on controllers
- Paginated list endpoints must return `PaginatedResponse<T>` (not raw `Page<T>`) via `PaginatedResponse.from(servicePage)` — raw `Page<>` serialises to a different JSON shape that does not match the contract envelope
- Free-text `@RequestParam` (e.g. `search`) must have `@Size(max = 200)` — unbounded params feed directly into LIKE queries
- POST endpoints must return `201 Created` with an **absolute** `Location` header via `ServletUriComponentsBuilder` — never `URI.create("/api/v1/...")` (RFC 9110 requires absolute URI)
- When `ServletUriComponentsBuilder.fromCurrentRequest()` is used to build Location headers, ensure the deployment has Spring's `ForwardedHeaderFilter` configured in strict/trusted mode — without it, a caller who controls `Host` or `X-Forwarded-Host` can inject an arbitrary URL into the 201 Location response header (host-header injection). Alternative: build Location from a configured base-URL property. Root cause of redemption-returns US-01 SEC-07 advisory finding.
- Enum-style `@RequestParam` values must be validated explicitly — a ternary fallthrough silently accepts invalid input; throw `IllegalArgumentException` for unrecognised values
- `@Max` / `@Min` on pagination params must match the feature's contract spec in `contracts/endpoints/{feature}.yaml` — the per-endpoint value is authoritative
- `@Min`, `@Max`, `@Size`, and other Bean Validation constraints on `@RequestParam` parameters require `@Validated` on the controller class — without it, annotation constraints are silently ignored and never enforced. All controllers using parameter-level constraints must be annotated with `@Validated`. Root cause of redemption-returns US-01 missing @Max(50) enforcement on `size` parameter.
- Response DTO fields that hold **pre-serialized JSON text** (entity column is `String` storing JSON, produced by `objectMapper.writeValueAsString(...)`) must be declared `String` with `@JsonRawValue` — never `Object`.
- When an OpenAPI contract specifies `type: string` for a monetary/BigDecimal field (not `type: number`), annotate the DTO field with `@JsonSerialize(using = ToStringSerializer.class)` — Jackson serialises `BigDecimal` as a JSON number by default, violating the contract. Pattern: `@JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) BigDecimal totalAmountAtRisk`. Discovered: balance-expiration US-01 contract review. With `Object`, Jackson re-encodes the string as a JSON string literal, so clients receive `"[{\"id\":...}]"` instead of `[{"id":...}]` and crash on `.map(...)`. Examples: `Question.optionsJson`, `Question.correctAnswerJson`.
- Always run `git submodule update --remote` before declaring a feature complete

## Entity & Database Patterns
- All IDs: UUID (uuid_generate_v4), never auto-increment
- Audit fields on every entity: id, created_at, updated_at (from BaseEntity — never redeclare)
- Flyway migrations only (ddl-auto: validate); naming: `V{N}__{description}.sql`
- snake_case columns, indexes on client_id and frequently queried columns
- LIKE / search / pagination details → docs/patterns/entity-db.md (GIN trigram indexes, LIKE escaping, keyset pagination)

## Mandatory Entity Pattern (TenantAware entities)
- Entities scoped to a client must implement `TenantAware` and extend `BaseEntity`
- Tenant field: `clientId` (UUID), column: `client_id` — NEVER use `tenantId` / `tenant_id`
- Must have `@Filter(name = "tenantFilter", condition = "client_id = :clientId")`
- Must have `@EqualsAndHashCode(callSuper = true)`
- Timestamps: `Instant`, not `OffsetDateTime`
- `TenantEntityListener` auto-stamps `clientId` from `TenantContext` on `@PrePersist` — do not manually set `clientId` from `TenantContext` in service code; derive it from `tenantValidator.getCurrentClientId()` (JWT-bound) for all security-scoped operations

## Soft-Delete Pattern
- Soft-deletable entities carry a `Boolean deleted` field (column `deleted BOOLEAN NOT NULL DEFAULT FALSE`)
- Add `@SQLRestriction("deleted = false")` (Hibernate 6.3+, `org.hibernate.annotations.SQLRestriction`) at the entity class level — this automatically filters all SELECT queries, derived methods, and JPQL loads
- **Repository methods must NOT include `AndDeletedFalse` in their names** — `@SQLRestriction` makes them redundant. Write `findByIdAndClientId`, never `findByIdAndClientIdAndDeletedFalse`
- JPQL `SELECT` queries also need no explicit `deleted = false` predicate — omit it
- **Exception — bulk DML:** `@SQLRestriction` is NOT applied to `@Modifying` UPDATE/DELETE statements. Always include an explicit `AND deleted = false` guard in bulk DML queries
- **`@Modifying` must include `clearAutomatically = true, flushAutomatically = true`:** bulk JPQL UPDATE/DELETE bypasses the Hibernate first-level cache. Without `clearAutomatically`, entities already loaded in the same persistence context will see stale state after the bulk operation. Without `flushAutomatically`, pending dirty entities are not flushed before the DML executes, causing the DML to run on stale DB rows. Always write `@Modifying(clearAutomatically = true, flushAutomatically = true)`.
- Cascade policy: soft-delete child entities before the parent within the same `@Transactional` method
- Full pattern reference: `../tenxengage-blueprint/docs/patterns/soft-delete.md`

## Service Layer Patterns
- @Service + @RequiredArgsConstructor
- @Transactional(readOnly = true) at class level, @Transactional on write methods
- Never expose JPA entities in responses — always map to response DTOs
- Business logic in services; controllers are thin
- Uniqueness guard: `existsBy*` is a fast-path optimisation; always also catch `DataIntegrityViolationException` — concurrent requests can both pass the pre-check before either insert commits
- `GlobalExceptionHandler` must handle `ObjectOptimisticLockingFailureException` and `DataIntegrityViolationException` → both map to `409 CONCURRENT_MODIFICATION`. Without an explicit handler, these fall through to the generic 500 handler and the contract's 409 response is never reached. Root cause of balance-expiration US-01 adversarial review finding.

## Security Standards
- Path variable is always the canonical resource ID — when a request body also contains an entity ID field (e.g. `courseId`), the controller MUST override the body field with the path value before passing to the service, preventing IDOR where a caller supplies a different ID in the body. Root cause of US-14 security finding.
- Never echo raw exception cause messages into API response bodies — log server-side, return a fixed generic string
- Sanitize attacker-influenced strings before logging — strip CR/LF to prevent log injection: `causeMessage.replaceAll("[\\r\\n]", " ")`
- Service mutation paths must derive `clientId` from `tenantValidator.getCurrentClientId()` (reads from JWT via `SecurityContextHolder`), not `TenantContext.getClientId()` (reads from `X-Client-Subdomain` header — spoofable by any authenticated caller)
- External sync jobs that deactivate or delete records must fail-closed on empty or suspiciously small provider responses — abort if the response would deactivate more than 80% of currently active records; do not deactivate anything on an obviously truncated response
- HMAC webhook signatures must be compared with `MessageDigest.isEqual(expected.getBytes(), received.getBytes())` — never `equals()` or `equalsIgnoreCase()`. String equality is not constant-time and leaks the expected value byte-by-byte via response timing.
- Webhook controllers must validate the vendor path variable against an explicit allowlist (`Set.of(...)`) before any HMAC or auth logic. Return 404 for unknown vendors — not 401/403 — to prevent information leakage about the auth flow. The expected HMAC value must never be logged, even at DEBUG level.
- Webhook signing secrets must be validated non-blank at startup — inject via `@Value` and add a `@PostConstruct` (or `@Validated` config-properties) that throws if any secret is blank in non-test profiles. A blank secret causes HMAC validation to always return false, silently dropping all webhook callbacks with no error in logs until someone notices requests failing.
- JWT auth (1hr access, 7day refresh, HS512)
- @RequiresPermission for authorization (custom annotation, AOP-enforced)
- @PreAuthorize only for simple isAuthenticated() checks
- CORS configured per environment

## User Action Tracking

### Audit Log (admin + system actions)
- Annotate controller methods with `@Audited(action="...", resourceType="...", resourceName="...", resourceId="...")`
- SpEL in `@Audited` referencing `#result.body.*` must use safe-navigation: `#result.body?.email` (NPE if body is null)
- SpEL parameter references in `@Audited` must use the parameter **name** (e.g. `#request.assessmentId`, `#lessonId`), NOT `#args[N]` index notation — `AuditAspect` registers params by name via `Method.getParameters()`, so `#args` is never populated
- `action` values: `AuditAction` enum; `resourceType` values: `AuditResourceType` enum
- **`@Audited(action=...)` MUST use `UPPER_SNAKE_CASE` matching an existing `AuditAction` constant** — `AuditAspect` resolves via `AuditAction.valueOf(action.toUpperCase().replace(" ", "_"))`. CamelCase multi-word strings (e.g. `"SubmittedForApproval"`) produce `SUBMITTEDFORAPPROVAL` which has no match and silently swallows the audit log. Use `"SUBMITTED_FOR_APPROVAL"` (exact constant name).
- **Adding a new `@Audited` action requires three steps in order:** (1) add the constant to `AuditAction.java`, (2) use it in `@Audited`, (3) document it in `contracts/enums.md` `§AuditAction` and update the count in `contracts/enums-index.md`. Same rule applies to new `AuditResourceType` values.
- Logs written async; actor, client, requestId captured automatically from SecurityContext + TenantContext + MDC
- API: `GET /api/v1/audit-logs` (permission: `action.activity_log.view`)
- Key files: `audit/Audited.java`, `audit/AuditAspect.java`, `service/AuditLogService.java`, `entity/AuditLog.java`
- NEVER call `AuditLogService.log()` or `logAsync()` directly from a controller — use `@Audited`

### Recommendation Interactions & Activity Progress
- Recommendation interactions: `POST /api/v1/recommendations/{type}/{targetId}/interactions` via `RecommendationService.recordInteraction()` (idempotent)
- Activity progress: managed by `ActivityCompletionService` / `ActivityDocumentService` — never write to these entities directly

### Request Tracing
- `RequestLoggingFilter` populates MDC with `requestId`, `userId`, `userEmail`, `clientId`, `subdomain` on every request
- MDC is the source of truth for correlation — do not pass these as method args

## Testing Standards
- JUnit 5 + Mockito for unit tests; Testcontainers (PostgreSQL 16) for integration tests
- @WebMvcTest + MockMvc for controller tests; `AbstractIntegrationTest` base class with @DynamicPropertySource
- JaCoCo: 60% line / 50% branch coverage minimum
- Controller tests must assert response body content (not just HTTP status) — mock service to return known data and assert `$.data.totalElements` etc. via `jsonPath`

## Observability
- SLF4J + MDC (requestId, tenantId, userId)
- JSON structured logging for non-local (logstash-logback-encoder)
- Actuator endpoints: health, info, metrics

## External Integrations
- Anthropic Claude API (AI features)
- Multi-cloud storage (S3, GCP, MinIO, local)
- Kafka for async messaging
- Redis for caching
- Email via Resend SMTP
- Snowflake for data warehouse

## Enablement Builders (Course, Learning Path, future modules)
The enablement family (builder-shaped catalog entities) follows shared conventions — see the blueprint domain registry `../tenxengage-blueprint/docs/patterns/domains/enablement.md` (authoritative) before adding a module.
- **Core aggregate extends `Enablement`** — every enablement entity extends `entity/enablement/Enablement.java` (e.g. `Course extends Enablement`, `LearningPath extends Enablement`). Never fork an independent entity re-declaring status/lifecycle/audience fields.
- **Terminal save with a unified `Save{Entity}Request`** — one request DTO carries the full builder state (basics, dates, composition/steps, milestones, rewards, audience rules) and is bound to BOTH `POST /{entities}` (create) and `PUT /{entities}/{id}` (update). References: `dto/request/learningpath/SaveLearningPathRequest.java`, `dto/course/SaveCourseRequest.java`. Do NOT add per-section PATCH endpoints for a new module.
- On create, `version` is `null` and `status`/`authorUserId` are derived from the JWT (never read from the body — mass-assignment guard); on update `version` is the optimistic-lock counter. Document each DTO's null-section semantics (LP: `null` section = no change; `[]` collection = clear, `null` = pass-through).
- When renaming a request field to match the contract, add `@JsonAlias("oldName")` — `JacksonConfig` disables `FAIL_ON_UNKNOWN_PROPERTIES`, so a stale name is silently read as `null` (data-loss on replace-semantics saves). Root cause of paths-and-assignments US-03.
- **Builder sections seed `is_locked` + `info_message`** for every system-managed section (`basics`, `tags`, `publish`, and module system sections like LEARNING_PATH `composition`) and `display_name = 'Availability'` for `dates` — these are NOT optional in the seed migration. See `enablement.md` § "Universal sections" + the V57/V61 root-cause note.

## Pattern References
Before starting work in these areas, read the relevant pattern file:
- Dynamic builder configuration → docs/patterns/builder-config.md
- Location hierarchy (entity model, storage, service layer) → [docs/patterns/location-hierarchy.md](docs/patterns/location-hierarchy.md)
- AI copilot integration → docs/patterns/ai-copilot.md
- Permissions & feature flags → docs/patterns/permissions-and-feature-flags.md
- Tenant isolation → ../tenxengage-blueprint/docs/patterns/tenant-isolation.md
  (Backend copy was deleted; blueprint canonical now includes the promoted Pitfalls section)
- Kafka event producers → docs/patterns/kafka-events.md
- Entity DB patterns (Lombok pitfalls, @Version rules, CascadeType, GIN indexes, LIKE escaping, keyset pagination) → docs/patterns/entity-db.md

## Anti-Patterns

- NEVER use `tenantId` or `tenant_id` — the field is always `clientId`, column is always `client_id`
- NEVER redeclare `id`, `createdAt`, or `updatedAt` in entities — inherited from `BaseEntity`
- NEVER use `OffsetDateTime` for timestamps — use `Instant`
- NEVER add `AndDeletedFalse` to a repository method name on an entity that has `@SQLRestriction("deleted = false")` — the restriction is automatic and the suffix is dead code
- NEVER use `@Where` for soft-delete filtering — use `@SQLRestriction` (Hibernate 6.3+, same semantics, supported API)
- NEVER create Kafka topics ad-hoc — all topics are defined in `KafkaConfig.java`
- NEVER call `AuditLogService.log()` or `logAsync()` directly from a controller — use `@Audited`
- NEVER use a CamelCase multi-word string in `@Audited(action=...)` — `AuditAspect` calls `AuditAction.valueOf(action.toUpperCase().replace(" ", "_"))`, so `"SubmittedForApproval"` resolves to `SUBMITTEDFORAPPROVAL` (no match) and the audit log is silently dropped. Always use the exact `UPPER_SNAKE_CASE` constant name (e.g. `"SUBMITTED_FOR_APPROVAL"`). Root cause of course-authoring audit-silent-drop finding.
- NEVER add a new `@Audited(action=...)` value without first declaring it in `AuditAction.java` **and** documenting it in `contracts/enums.md §AuditAction` + updating the count in `contracts/enums-index.md`. Same applies to new `AuditResourceType` values. Root cause of course-authoring audit-silent-drop finding.
- NEVER expose JPA entities in API responses — always map to response DTOs (records)
- NEVER read the `.env` file — always refer to `.env.example` and instruct the user to copy values manually
- NEVER call `@CacheEvict` methods via `this.` — Spring's cache proxy is bypassed; self-calls silently skip eviction. Place `@CacheEvict` directly on the method that mutates state, or inject the bean via `@Autowired @Lazy` self-reference.
- NEVER annotate a service method `@Transactional` when it delegates to an external AI or HTTP call — the DB connection is held open for the full duration of the external call, risking connection pool exhaustion under latency spikes. Move AI/HTTP invocations outside the transactional boundary; let individual repository writes manage their own `@Transactional` scope. Root cause of US-14 adversarial finding.
- NEVER use check-then-act (`existsBy...` + `save`) inside `@Transactional` seed or upsert methods — concurrent calls both pass the check before either commits, causing a unique-constraint violation or silent duplicate insert. Use `INSERT ... ON CONFLICT DO NOTHING` or catch `DataIntegrityViolationException` as an idempotency guard.
- NEVER use `TenantContext.getClientId()` in service methods to scope queries — it reads from the `X-Client-Subdomain` header (spoofable); use `tenantValidator.getCurrentClientId()` (JWT-derived). The only valid use of `TenantContext` in services is `TenantEntityListener` stamping `client_id` on `@PrePersist`.
- NEVER pass a nullable Java enum parameter in JPQL with bare `:param IS NULL` — Postgres/Hibernate cannot infer the type and throws at runtime. Use `CAST(:param AS STRING) IS NULL OR col = :param`.
- NEVER evict Spring cache entries inside an open `@Transactional` method — a concurrent reader can repopulate the cache with stale DB state between the eviction and the commit. Register evictions via `TransactionSynchronizationManager` or `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- ALWAYS check `contracts/` before creating entities, DTOs, or endpoints — the contracts submodule is the single source of truth
- NEVER register a stub/fake implementation of an external API client as a `@Component` without `@Profile({"local", "localtest", "test"})` — an unrestricted stub silently activates in all environments and can cause mass data loss (e.g. returning `List.of()` from a sync client deactivates every catalog item in production)
- NEVER use `Pageable.unpaged()` upstream of in-memory filtering — it loads every matching row into memory before filtering; push filter predicates into the DB query and pass the real `Pageable` to the repository
- ALWAYS use an enum type (not `String`) for `@RequestParam` or `@PathVariable` parameters that accept a bounded set of values — Spring rejects unknown names with 400 automatically; a raw `String` accepts anything and requires manual validation
- ALWAYS add `@Pattern(regexp = "…")` and `@Size` to String `@PathVariable` parameters — unconstrained path variables pass arbitrary characters (including `..`, `/`, excessively long inputs) into service and repository calls
- ALWAYS use typed enum parameters in JPQL `@Query` methods, never string literals — `AND r.status = :status` with `@Param("status") RedemptionStatus status`, never `AND r.status = 'PENDING_APPROVAL'`. String literals silently produce zero results after any enum rename with no compile-time error.
- NEVER invoke an `@Async` method via `this.method()` inside the same bean — Spring's AOP proxy is bypassed and the method runs synchronously on the caller thread. Extract the async method into a separate `@Component` bean or inject a self-reference via `@Lazy @Autowired private MyService self` to preserve proxy interception.
- NEVER use `PageRequest.of(0, Integer.MAX_VALUE, ...)` to load unbounded result sets — materialising millions of rows in a single `Page` will cause OOM. Use chunked processing (`for (int page = 0; hasMore; page++)`) or a streaming query (`@QueryHints(@QueryHint(name = HINT_FETCH_SIZE, value = "1000"))`) for export and large-data paths.
- NEVER offset-paginate a repository query (iterate `PageRequest` page numbers) without a deterministic `ORDER BY` in the `@Query` (or a `Sort` on the `Pageable`) — offset pagination over an unordered result is non-deterministic, so rows are silently skipped or processed twice across pages. A code comment claiming "ordered for stable paging" is not the same as an `ORDER BY` in the JPQL; verify the clause is actually present. Root cause of reward-balance-expiration code-review finding (`findExpiryCandidateWallets` paged candidates with no `ORDER BY`).
- In-memory rate limiters (`ConcurrentHashMap` + `synchronized`) are NOT production-safe in multi-instance deployments — they reset on restart and do not share state across pods. Use a Redis-backed counter (e.g. Bucket4j + Spring Data Redis) or a distributed rate-limiter for any limit that must be enforced cluster-wide.
- NEVER compute an entity count by loading the full collection and calling `.size()` — use a repository-level `countBy...` derived query (or `@Query("SELECT COUNT(e) ...")`) instead. Loading entities to count them pulls large fields (e.g. `criteriaJson`, `composition_json`) into heap unnecessarily. Root cause of US-12 adversarial finding (milestone count via list materialization).
- ALWAYS update `contracts/enums.md` when adding new constants to a persisted Java enum (`@Enumerated(EnumType.STRING)`) — the documentation lags silently and breaks external consumer expectations if not updated. Update the enum's section in `enums.md` with descriptions for each new constant.
- `@Size(max=N)` does NOT reject empty strings — it only enforces a maximum length. When blank values are also invalid, use `@Size(min=1, max=N)` or pair with a separate `@NotBlank`. Missing `min=1` means `""` silently passes `@Valid` and reaches the service. Root cause of redemption-returns US-01 SEC-08 advisory finding.
- NEVER add `@Pattern` or `@NotBlank` to a DTO field whose domain-validation also lives in the service layer with a spec-mandated `errorCode` — Jakarta bean validation fires first and returns a generic `VALIDATION_ERROR` (`$.details.{field}`) instead of the spec-mandated error shape (`$.errorCode`). Keep only structural constraints (`@Size`, `@NotNull` for mandatory fields, `@Email`) at the DTO layer; let the service throw the domain-specific exception (e.g. `ContentAssetValidationException(ERR_URL_SCHEME, ...)`) so `GlobalExceptionHandler` maps it to the correct status and code. Root cause of US-05 LOW / US-06 high error-code drift.
- NEVER create a one-to-one `@MapsId` join table (bind table) without a `@Version Long version` field — bind/rebind operations on these tables have no natural optimistic-lock guard otherwise, making concurrent bind races invisible to the caller. Always pair the entity field (`@Version @Column(name="version") Long version`) with `version BIGINT NOT NULL DEFAULT 0` in the Flyway migration. Root cause of F3 @Version-on-quiz-bindings finding.
- Upload endpoints (`POST /uploads`) MUST return `storageUrl` in the response — clients cannot reconstruct storage URLs because the server embeds a random UUID in the object key. Without `storageUrl`, the FE cannot call the subsequent asset-create endpoint with a valid `url`. This was the root cause of the US-05 HIGH FE/BE wire gap.
- NEVER type response DTO fields as `Object` when a concrete type exists — Jackson serializes `Object` containing a nested record correctly, but the Jackson contract is invisible to callers and the field accepts unintended types silently. Always use the most specific type (e.g. `AIDraftAssessmentResponse` not `Object` for quiz drafts). Root cause of US-18 medium api-design finding.
- NEVER use `stream().anyMatch()` for an existence check in a service — it loads all rows into memory. Use a derived `existsBy*` repository method instead; Spring Data JPA generates a single `SELECT 1` query. Root cause of US-19 medium performance finding.
- NEVER use a bare `default → true` (or `default → false`) in a `switch` that validates an externally-supplied enum string — unknown values silently pass (or fail) instead of throwing a domain exception. Always add an explicit allowlist guard before the `switch` and throw `BusinessRuleException` for unrecognised values. Root cause of US-10 advisory finding.
- Response DTO fields for types that accept **multiple wire formats** (e.g. MULTI_SELECT sending either `String` or `List<String>`) must validate against all accepted types — adding a new frontend wire format without updating the backend type-check silently rejects valid payloads with a misleading type-mismatch error. Root cause of US-20 important finding.
- NEVER add `@JsonInclude(Include.NON_NULL)` to a response DTO whose nullable fields represent intentional optional data (e.g., quiz drafts absent in structure-only mode) — the annotation silently drops those keys from the JSON payload entirely, changing "field present with null" to "field absent"; this is a wire-contract regression for typed clients and SSE consumers expecting stable field presence. Root cause of course-authoring adversarial-review finding.
- ALWAYS make a defensive copy of `SecurityContext` before passing it into an SSE callback or async virtual-thread task: `new SecurityContextImpl(ctx.getAuthentication())` — the live context returned by `SecurityContextHolder.getContext()` is cleared when the request thread ends, causing null authentication inside the callback and a security failure. Root cause of course-authoring code-review finding.
- WHENEVER a new value is added to a URL-path-bound enum (e.g. `BuilderType`, `IncentiveType` on a `@PathVariable`), verify that the endpoint's `@RequiresPermission` gate still enforces the right boundary for the new value — a coarse single-permission guard written for one value silently grants access to all future values the enum accepts. Add a per-value permission branch or an explicit allowlist check before shipping the enum expansion. Root cause of paths-and-assignments F1 adversarial finding.
- When implementing per-enum runtime permission resolution (switch on `@PathVariable` enum to pick the permission key), keep `@RequiresPermission(value = {all-applicable-keys}, logic = ANY)` on the method so `EndpointSecurityValidator` (startup check, throws in prod) still sees a secured endpoint. The annotation provides coarse gateway enforcement; the switch body provides fine-grained per-value enforcement. Removing the annotation entirely causes a prod startup failure. Root cause of paths-and-assignments F2 security-review finding.
- ALWAYS add `AND deleted = false` to partial-index predicates on soft-deletable tables that also encode logical state (e.g. `WHERE state = 'ACTIVE' AND deleted = false`) — a predicate on only the state column reserves the dedupe key for soft-deleted rows, blocking valid re-enrollment/re-creation after partial failure or cleanup flows. Root cause of paths-and-assignments F2 adversarial finding.
- When a nullable FK column is written by the same transaction that creates the referenced row (e.g. `learning_paths.current_version_id → learning_path_versions.id`), enforce referential integrity with `REFERENCES ... DEFERRABLE INITIALLY DEFERRED` so the FK check runs at commit-time rather than statement-time, allowing the write order path→version to succeed in a single transaction without a nullable workaround. Root cause of paths-and-assignments F2 adversarial finding.
- NEVER place a cross-tenant query method (one that intentionally omits `clientId`) on a standard tenant-scoped `*Repository` interface — `HibernateFilterConfig` applies the `tenantFilter` AOP advice to all `com.tenxengage.app.repository..*Repository.*` methods whenever `TenantContext.getClientId()` is non-null, silently scoping the "cross-tenant" query to a single tenant with no error signal. Cross-tenant scheduler methods must live in a dedicated `Scheduler*Repository` interface (e.g. `SchedulerAssignmentRuleRepository`) and must only be injected into scheduler/event-consumer components. Root cause of paths-and-assignments F3 adversarial finding.
- NEVER use `SELECT COUNT(e) > 0 FROM ... WHERE ...` in a JPQL `@Query` — JPQL does not permit boolean comparison expressions in the SELECT clause; Hibernate throws a `QueryException` at startup or first invocation. Use a Spring Data derived query (`existsBy...`) or return `long` from `SELECT COUNT(e) FROM ...` and check `> 0` at the call site. Root cause of paths-and-assignments F3 code-review finding.
- NEVER use string literals for enum comparisons in JPQL `@Query` (e.g. `WHERE r.status = 'ACTIVE'`) — if the enum constant is renamed the literal silently mismatches and the query always returns empty. Use a typed `@Param` with the Java enum value instead. For methods that always query a fixed enum value, add a no-arg `default` wrapper that passes the constant. Root cause of paths-and-assignments F3 code-review finding.
- ALWAYS include an error code string when throwing `BusinessRuleException` for a domain validation failure — `new BusinessRuleException("STABLE_CODE", message)` not `new BusinessRuleException(message)`. The error code is the machine-readable handle API consumers use to distinguish failure causes; omitting it makes the 422 response shape unparseable by typed clients. Root cause of paths-and-assignments US-13 TARGET_NOT_PUBLISHED finding.
- WHENEVER renaming a request DTO field to align with the contract, add `@JsonAlias("oldName")` on the new field name — `JacksonConfig` globally disables `FAIL_ON_UNKNOWN_PROPERTIES`, so payloads using the old name are silently accepted as `null` (not rejected), which causes silent data-loss on replace-semantics endpoints that rebuild from the request body. Root cause of paths-and-assignments US-03 adversarial-review finding.
- NEVER use `@Data` on a JPA entity that has a bidirectional `@ManyToOne` back-reference — `@Data` generates `toString()` that includes all fields, and if the owning side also lacks an explicit `@ToString(exclude = {...})`, recursive calls between parent and child `toString()` produce a `StackOverflowError`. Use explicit `@Getter @Setter @NoArgsConstructor` with `@ToString(callSuper = true, exclude = {"parentField"})` on entities that participate in bidirectional associations. Root cause of paths-and-assignments F3 code-review finding.
- NEVER register a stub `@KafkaListener` without `autoStartup = "false"` (or a feature-flag gate) — Spring Kafka commits the offset on any successful return, so a no-op handler that logs and returns will consume and permanently discard real messages from the consumer group before the implementation lands. The missed offsets cannot be recovered without a manual offset reset. Always gate stub consumers: `@KafkaListener(..., autoStartup = "${kafka.{consumer}.auto-start:false}")`. Root cause of paths-and-assignments F5 adversarial finding.
- ALWAYS apply the same Jsoup input sanitization to user-supplied override fields in copy/clone flows as in the original create flow — it is easy to forget that a clone endpoint with an optional `name` override accepts arbitrary user input, not just data copied from the already-sanitized source. Pass the override through `sanitizeName()` (simpleText) before setting it on the new entity; fall back to the default title if post-sanitize result is blank. Root cause of paths-and-assignments US-11 code-review finding.
- ALWAYS acquire a pessimistic write lock on the source entity before reading its composition children in a clone/copy flow — use `lockForLifecycleByIdAndClientId` (or equivalent `@Lock(PESSIMISTIC_WRITE)` query) so a concurrent composition-update cannot produce an inconsistent snapshot in the clone. Without the lock, two concurrent requests can interleave: one clones from a half-updated step list while the other is mid-replace. Root cause of paths-and-assignments US-11 adversarial-review finding.
- ALWAYS persist the idempotency/dedupe marker to the database BEFORE dispatching the external notification — if the save fails the exception propagates and prevents the notification, and the operation retries cleanly on the next tick. The reverse order (notify then save) permits duplicate notifications when the save fails after the notification has already been sent. Root cause of paths-and-assignments US-10 notification-before-persist finding.
- NEVER publish a Kafka event from inside a `@Transactional` service method — the producer hands the message to Kafka asynchronously before the DB transaction commits, so a later rollback leaves consumers with an event for a state that never existed; conversely, a broker failure silently orphans a committed entity with no downstream event. Publish via `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() { @Override public void afterCommit() { producer.publish(event); } })`, or use a transactional outbox table written in the same DB transaction and polled by a dedicated relay. Root cause of redemption-returns US-01 CR-02/CR-03 and ADV-01 findings (confirmed by two independent review passes).
- ALWAYS handle the `CompletableFuture` returned by `kafkaTemplate.send()` — `afterCommit` prevents the rollback race but does NOT protect against Kafka broker failures; if the send future is silently discarded, a committed entity can permanently lose its lifecycle event with no retry or alert. Add `.whenComplete((result, ex) -> { if (ex != null) log.error("Kafka send failed: ...", ex); })` to every `kafkaTemplate.send()` call, or use a transactional outbox where the relay is responsible for retries. Root cause of redemption-returns US-01 ADV-01 (Codex adversarial review finding).
- NEVER implement a batch-load helper using a per-item loop over a single-entity repository method (e.g. `ids.stream().map(id -> repo.findById(id)).filter(...).toList()`) — this produces N queries for N items and degrades linearly as page size grows. Always add a batch repository method (`findAllByIdIn(ids)` or `findAllByIdInAndClientId(ids, clientId)`) and call it once. The list-to-map transform (`Collectors.toMap(Entity::getId, Entity::getName)`) is safe in Java; only the per-item DB round-trips need eliminating. Root cause of redemption-returns US-01 CR-01 finding.
- WHENEVER a response DTO record has a field that is nullable per contract but the global `JacksonConfig` sets `JsonInclude.Include.NON_NULL`, annotate that specific component with `@JsonInclude(JsonInclude.Include.ALWAYS)` — without the override the `null` value is omitted from the JSON entirely (field absent ≠ field null), breaking typed clients that expect stable field presence. Root cause of redemption-analytics-advanced US-05 security-review finding.
- ALWAYS use `EmptySqlParameterSource.INSTANCE` instead of `new MapSqlParameterSource()` when a `NamedParameterJdbcTemplate` query takes no bind parameters — `MapSqlParameterSource()` allocates a mutable map and signals "parameters are coming" to readers, while `INSTANCE` is a no-allocation singleton that clearly communicates "no parameters." Root cause of redemption-analytics-advanced US-05 code-review finding.
- WHENEVER querying an append-only refresh-log table to derive a staleness signal, include `COUNT(*)` alongside `MIN(last_refreshed_at)` and compare against the expected row count — a `MIN()` over existing rows silently ignores absent rows (datasets that never refreshed), reporting fresh analytics while one or more MVs are empty. Gate `isStale=false` on `rowCount == EXPECTED_COUNT AND minLastRefreshedAt IS NOT NULL`. Root cause of redemption-analytics-advanced US-05 adversarial-review finding.
- WHENEVER a response schema in OpenAPI YAML contains a field typed `string format: date-time` that the Java service can legitimately return `null` for (e.g. `lastRefreshedAt` on first deploy before any MV refresh has run), declare `nullable: true` on that schema property — omitting it causes generated clients to reject a valid null response as a schema violation. Apply consistently across ALL response schemas in the same file that share the same nullable field. Root cause of redemption-analytics-advanced US-04 adversarial-review finding.
