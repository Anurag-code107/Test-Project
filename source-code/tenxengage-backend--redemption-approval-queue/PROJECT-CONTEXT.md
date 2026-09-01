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
- Enum-style `@RequestParam` values must be validated explicitly — a ternary fallthrough silently accepts invalid input; throw `IllegalArgumentException` for unrecognised values
- `@Max` / `@Min` on pagination params must match the feature's contract spec in `contracts/endpoints/{feature}.yaml` — the per-endpoint value is authoritative
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

## Service Layer Patterns
- @Service + @RequiredArgsConstructor
- @Transactional(readOnly = true) at class level, @Transactional on write methods
- Never expose JPA entities in responses — always map to response DTOs
- Business logic in services; controllers are thin
- Uniqueness guard: `existsBy*` is a fast-path optimisation; always also catch `DataIntegrityViolationException` — concurrent requests can both pass the pre-check before either insert commits

## Security Standards
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
- `action` values: `AuditAction` enum; `resourceType` values: `AuditResourceType` enum
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

## Pattern References
Before starting work in these areas, read the relevant pattern file:
- Dynamic builder configuration → docs/patterns/builder-config.md
- Location hierarchy (entity model, storage, service layer) → [docs/patterns/location-hierarchy.md](docs/patterns/location-hierarchy.md)
- AI copilot integration → docs/patterns/ai-copilot.md
- Permissions & feature flags → docs/patterns/permissions-and-feature-flags.md
- Tenant isolation → docs/patterns/tenant-isolation.md
- Kafka event producers → docs/patterns/kafka-events.md
- Entity DB patterns (LIKE, GIN indexes, keyset pagination) → docs/patterns/entity-db.md

## Anti-Patterns

- NEVER use `tenantId` or `tenant_id` — the field is always `clientId`, column is always `client_id`
- NEVER redeclare `id`, `createdAt`, or `updatedAt` in entities — inherited from `BaseEntity`
- NEVER use `OffsetDateTime` for timestamps — use `Instant`
- NEVER create Kafka topics ad-hoc — all topics are defined in `KafkaConfig.java`
- NEVER call `AuditLogService.log()` or `logAsync()` directly from a controller — use `@Audited`
- NEVER expose JPA entities in API responses — always map to response DTOs (records)
- NEVER read the `.env` file — always refer to `.env.example` and instruct the user to copy values manually
- ALWAYS check `contracts/` before creating entities, DTOs, or endpoints — the contracts submodule is the single source of truth
- NEVER register a stub/fake implementation of an external API client as a `@Component` without `@Profile({"local", "localtest", "test"})` — an unrestricted stub silently activates in all environments and can cause mass data loss (e.g. returning `List.of()` from a sync client deactivates every catalog item in production)
- NEVER use `Pageable.unpaged()` upstream of in-memory filtering — it loads every matching row into memory before filtering; push filter predicates into the DB query and pass the real `Pageable` to the repository
- ALWAYS use an enum type (not `String`) for `@RequestParam` or `@PathVariable` parameters that accept a bounded set of values — Spring rejects unknown names with 400 automatically; a raw `String` accepts anything and requires manual validation
- ALWAYS add `@Pattern(regexp = "…")` and `@Size` to String `@PathVariable` parameters — unconstrained path variables pass arbitrary characters (including `..`, `/`, excessively long inputs) into service and repository calls
- ALWAYS use typed enum parameters in JPQL `@Query` methods, never string literals — `AND r.status = :status` with `@Param("status") RedemptionStatus status`, never `AND r.status = 'PENDING_APPROVAL'`. String literals silently produce zero results after any enum rename with no compile-time error.
