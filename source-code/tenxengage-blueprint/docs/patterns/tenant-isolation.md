# Pattern: tenant-isolation

## When this applies

Use this pattern when a feature **introduces new DB entities** that belong to a specific tenant. Almost every feature in TenXEngage adds at least one entity, so this pattern applies very broadly. The exception is platform-level configuration entities (e.g., global enums, platform-wide feature flags) that intentionally have no `client_id`.

## Spec authoring guidance

- For each new entity, explicitly state whether it is tenant-scoped or platform-scoped. Default to tenant-scoped unless there is a clear reason otherwise.
- For tenant-scoped entities, include in the technical.md Flyway migration: a `client_id UUID NOT NULL` column, a foreign key constraint to the `clients` table, and a `CREATE INDEX` on `client_id`.
- Mention that the entity implements `TenantAware` and carries `@Filter(name="tenantFilter")` — this is the signal to implementers to wire the Hibernate filter.
- Note that cross-tenant access violations return **404, not 403**. This is a deliberate security decision and must not be changed.
- When specifying repository query signatures, always include `clientId` as a parameter — even for simple lookups by ID.

## Implementation guidance

### TenantContext

`TenantContext` is a `ThreadLocal` holder storing the current request's `subdomain` and resolved `client_id`. It is set at the start of every request and cleared in a `finally` block. Any code that needs the current tenant reads from `TenantContext`. Because it uses `ThreadLocal`, each request thread (or virtual thread) has its own isolated copy.

### TenantFilter

`TenantFilter` is a `OncePerRequestFilter` that runs early in the Spring Security filter chain:
1. Reads the `X-Client-Subdomain` header
2. Resolves the subdomain to a `client_id` via the `clients` table
3. Stores both values in `TenantContext`

If the header is missing or the subdomain doesn't resolve, the request is rejected. This ensures every authenticated request has a known tenant context before any business logic runs.

### Hibernate @Filter

`HibernateFilterConfig` (AOP aspect) intercepts all repository method calls. Before the method executes, it enables the Hibernate filter named `"tenantFilter"` on the current session, setting `client_id` to the value from `TenantContext`. This appends `WHERE client_id = :clientId` automatically to every query on filtered entities — the primary defense against cross-tenant data leakage.

### TenantAware Interface

`TenantAware` is a marker interface all tenant-scoped entities must implement:

```java
public interface TenantAware {
    UUID getClientId();
    void setClientId(UUID clientId);
}
```

Implementing this interface signals participation in tenant isolation machinery (Hibernate filtering, automatic `client_id` population, cross-tenant validation).

### TenantEntityListener

`TenantEntityListener` is a JPA entity listener that automatically populates `client_id` on `@PrePersist` and `@PreUpdate` events by reading from `TenantContext`. Service code **never** needs to manually set `client_id`.

### BaseEntity

All entities extend `BaseEntity`, annotated with:
```java
@EntityListeners({AuditingEntityListener.class, TenantEntityListener.class})
```

This automatically provides:
- Audit fields: `id`, `createdAt`, `updatedAt` (via `AuditingEntityListener`)
- Tenant field: `client_id` auto-populated (via `TenantEntityListener`)

Extending `BaseEntity` is sufficient to opt into both audit tracking and tenant isolation.

### TenantValidator

`TenantValidator` provides explicit cross-tenant checks for cases where the Hibernate filter alone is insufficient (e.g., after fetching by a non-primary key, or for related entity references):

| Method | Behavior |
|---|---|
| `validateClientAccess(clientId)` | Confirms entity's `client_id` matches current tenant; throws if not |
| `validatePartnerCompanyAccess(partnerCompanyId)` | Similar check for partner company relationships |

**Cross-tenant violations return 404, not 403.** This is deliberate: 403 would confirm to an attacker that the resource exists but belongs to another tenant. 404 reveals nothing.

### Rules for New Entities

When creating a new tenant-scoped entity, follow this checklist:

1. Add `client_id UUID NOT NULL` with a foreign key constraint to the `clients` table.
2. Implement `TenantAware` (`getClientId` / `setClientId`).
3. Add `@Filter(name = "tenantFilter", condition = "client_id = :clientId")` on the entity class.
4. Create an index on `client_id` in the Flyway migration — critical for query performance since almost every query filters by `client_id`.
5. Write the Flyway migration with: column, constraint, and index.
6. Extend `BaseEntity` to automatically get audit fields and `TenantEntityListener`.

If the entity is **platform-scoped** (e.g., platform-level config), do **not** implement `TenantAware` and do not add `client_id`. Such entities are rare.

## Examples in codebase

- `../tenxengage-backend/src/main/java/com/tenxengage/app/entity/Incentive.java` — canonical tenant-scoped entity (TenantAware, @Filter, extends BaseEntity)
- `../tenxengage-backend/src/main/java/com/tenxengage/app/config/HibernateFilterConfig.java` — AOP aspect that enables the tenant filter per request
- `../tenxengage-backend/src/main/java/com/tenxengage/app/filter/TenantFilter.java` — OncePerRequestFilter that populates TenantContext
- `../tenxengage-backend/src/main/java/com/tenxengage/app/util/TenantValidator.java` — explicit cross-tenant validation with 404 behavior
- `../tenxengage-backend/src/main/resources/db/migration/V1__create_incentives_table.sql` — reference for client_id column, FK, and index pattern

## Common gotchas

- **Never return 403 for cross-tenant access violations.** Always 404. This is a security invariant — 403 leaks resource existence.
- **Repository queries must always include `clientId`.** Even `findById(UUID id)` is unsafe without a tenant check — the Hibernate filter handles this at query time, but explicit `clientId` parameters in custom queries are a good defense-in-depth.
- **`@Filter` on the entity class is not optional.** If an entity implements `TenantAware` but lacks `@Filter`, the Hibernate filter won't apply and every query will return cross-tenant data silently.
- **`client_id` index is mandatory, not optional.** Without it, every paginated list query does a full table scan filtered post-query. This is a correctness issue at scale.
- **Do not set `client_id` in service code.** `TenantEntityListener` handles it automatically. Manually setting it bypasses the listener and creates an inconsistency if `TenantContext` differs.
- **`TenantContext` must be cleared in a `finally` block.** Leaking `ThreadLocal` state between requests (in thread pool scenarios) causes one tenant's requests to run under another tenant's context.
- **Platform-scoped entities must be explicitly justified in the spec.** The default is tenant-scoped. Any entity without `client_id` needs a clear comment in the spec explaining why it is platform-scoped.

## Pitfalls for new implementations

These pitfalls require implementation-level knowledge — verify each against the actual backend code before shipping a new entity or service.

- **Always use `@PersistenceContext` to inject `EntityManager` in `@Aspect` classes** — constructor injection (via `@RequiredArgsConstructor` or an explicit constructor) gives you a singleton `EntityManager` that is not thread-safe. Spring's `@PersistenceContext` wraps the instance in a thread-local proxy automatically. This is the documented exception to the "always use constructor injection" rule — `@PersistenceContext` is field injection, but it is required here for thread safety:
  ```java
  // WRONG — singleton, not thread-safe in AOP context
  private final EntityManager entityManager;
  // CORRECT — thread-local proxy
  @PersistenceContext
  private EntityManager entityManager;
  ```

- **Always include an `else` branch to disable the tenant filter when `clientId` is null** — omitting the `else` leaves whatever filter state was set by the previous request on the same thread active. In async or system contexts where `TenantContext.getClientId()` returns null, this risks cross-tenant data leakage:
  ```java
  UUID clientId = TenantContext.getClientId();
  if (clientId != null) {
      session.enableFilter("tenantFilter").setParameter("clientId", clientId);
  } else {
      session.disableFilter("tenantFilter");  // REQUIRED — never omit
  }
  ```

- **Add explicit `clientId` filter to all custom `@EntityGraph` / `@Query` repository methods** — the AOP tenant filter is the primary isolation mechanism, but any custom `@Query` that fetches entities by a set of IDs (e.g. `findByIdsWithGraph`) becomes a cross-tenant leak vector if the AOP filter is not applied (async context, proxy miss, or direct call from a test). Always add `AND c.clientId = :clientId` to such queries and pass `clientId` from the service layer.

- **Never use `TenantContext.getClientId()` in service methods to scope tenant data** — `TenantContext` is populated from the `X-Client-Subdomain` request header, which a caller can forge. The authenticated pattern is `TenantValidator.getCurrentClientId()`, which reads from the JWT principal. The only legitimate use of `TenantContext.getClientId()` is inside `TenantEntityListener` for auto-stamping `client_id` on `@PrePersist`.

- **Guard TENX_ADMIN before calling `tenantValidator.getCurrentClientId()`** — `getCurrentClientId()` returns `null` for TENX_ADMIN users (no `clientId` in their JWT). Passing `null` to a repository query silently returns empty results. Check `tenantValidator.isTenxAdmin()` first and throw `AccessDeniedException`.

- **Cross-tenant sweep queries must not live alongside tenant-scoped repository methods** — the Hibernate `@Filter` is active for all calls on a shared repository interface from a tenant request context. A sweep method that intentionally omits `clientId` (e.g., `findByAutoSubmitAtBeforeAndStatus`) placed on the same interface can silently under-select rows when called from a tenant context. Move such methods to a dedicated `SchedulerXxxRepository` interface or guard them with an aspect that rejects calls when `TenantContext.getClientId() != null`.
