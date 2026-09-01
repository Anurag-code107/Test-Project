# Tenant Isolation Pattern — Backend

How TenXEngage enforces multi-tenant data isolation. Every tenant-scoped entity carries a `client_id`; servlet filters, Hibernate filters, JPA listeners, and manual validation cooperate so tenants can never read each other's data.

## Isolation layers

Six classes form the isolation chain. Read each `.java` file for current implementation; this doc describes the contract and gotchas only.

1. **`TenantFilter`** (`security/TenantFilter.java`) — `OncePerRequestFilter` that reads the `X-Client-Subdomain` header, resolves it to a `clientId` via `ClientService`, populates `TenantContext`, and clears it in a `finally` block. Registered in the Spring Security chain before authentication so tenant context is available during JWT validation.
2. **`TenantContext`** (`security/TenantContext.java`) — ThreadLocal holder for the current `subdomain` and `clientId`. `clear()` wipes both; critical for thread pools and virtual threads.
3. **`HibernateFilterConfig`** (`security/HibernateFilterConfig.java`) — AOP aspect that activates the Hibernate `tenantFilter` before every `*Repository.*(..)` method call, scoping all JPA queries with `WHERE client_id = :clientId`. See pitfalls below for the `@PersistenceContext` and null-`else` requirements.
4. **`TenantAware`** (`security/TenantAware.java`) — marker interface (`getClientId` / `setClientId`) declaring that an entity carries tenant data and participates in isolation.
5. **`TenantEntityListener`** (`security/TenantEntityListener.java`) — JPA `@PrePersist` / `@PreUpdate` listener that auto-stamps `clientId` from `TenantContext`, but only if the entity doesn't already have one (preserves explicit values). Wired on `BaseEntity` via `@EntityListeners`, so every entity inherits it.
6. **`TenantValidator`** (`security/TenantValidator.java`) — manual cross-tenant check for cases the Hibernate filter can't cover (e.g. loading by path-parameter ID). Bypassed for tenx admins. See the 404-vs-403 convention below.

## Hibernate `@Filter` on entities

Every tenant-scoped entity declares the filter. The `@FilterDef` lives on a shared config class.

```java
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
public class Incentive extends BaseEntity implements TenantAware { ... }
```

The aspect enables this filter for every repository call. If `TenantContext.getClientId()` is null (platform-level admin, async context), the filter must be **explicitly disabled** rather than left in its prior state — see Pitfall #2.

## 404, not 403, on cross-tenant access

Cross-tenant access violations must return **HTTP 404**, not 403. Returning 403 confirms to an attacker that the resource exists in another tenant; 404 reveals nothing. In service code, throw `ResourceNotFoundException`:

```java
if (!entityClientId.equals(currentClientId)) {
    throw new ResourceNotFoundException(entityName, "id", entityId);  // 404
}
```

`TenantValidator.validateClientAccess` follows the same intent (throws `AccessDeniedException` for direct denials, but service-layer checks on entities-by-id should use `ResourceNotFoundException`).

## Checklist for new tenant-scoped entities

1. **Database column**: `client_id UUID NOT NULL` with FK to `clients`.
2. **Entity**: implement `TenantAware`, extend `BaseEntity`, annotate `@Filter(name = "tenantFilter", condition = "client_id = :clientId")`.
3. **Index**: `CREATE INDEX idx_<table>_client_id ON <table>(client_id);` in the Flyway migration. Required for query performance.
4. **Composite indexes**: if the entity is frequently queried by `client_id` plus another column (e.g. `(client_id, status)`), add a composite index — single-column `client_id` index alone is insufficient for selective filters.
5. **Flyway migration**: `V{N}__{description}.sql`. Never rely on `ddl-auto`.

(`BaseEntity` already provides `id`, `createdAt`, `updatedAt`, audit listener, and tenant listener — never redeclare these.)

## Async, batch, and virtual-thread contexts

`TenantContext` is populated by `TenantFilter` for HTTP requests only. In Kafka consumers, scheduled tasks, batch jobs, and virtual threads, the context is **empty** — you must populate and clear it manually:

```java
try {
    TenantContext.setClientId(clientId);  // from event payload / job param
    // ... call service methods ...
} finally {
    TenantContext.clear();
}
```

For SSE virtual threads spawned from a request, propagate `SecurityContext` explicitly and re-set `TenantContext` on the new thread (virtual threads do not inherit thread-local state from the parent).

## Pitfalls

- **Always use `@PersistenceContext` to inject `EntityManager` in `@Aspect` classes** —
  constructor injection (via `@RequiredArgsConstructor` or an explicit constructor) gives
  you a singleton `EntityManager` that is not thread-safe. Spring's `@PersistenceContext`
  wraps the instance in a thread-local proxy automatically. This is the documented
  exception to CLAUDE.md Rule 3 ("always use constructor injection") — `@PersistenceContext`
  is field injection, but it is required here for thread safety. Replace:

  ```java
  // WRONG — singleton, not thread-safe in AOP context
  private final EntityManager entityManager;
  public HibernateFilterConfig(EntityManager entityManager) { ... }

  // CORRECT — thread-local proxy
  @PersistenceContext
  private EntityManager entityManager;
  ```

- **Always include an `else` branch to disable the tenant filter when `clientId` is null**
  — omitting the `else` leaves whatever filter state was set by the previous request on
  the same thread active. In async or system contexts where `TenantContext.getClientId()`
  returns null, this risks cross-tenant data leakage. Correct form:

  ```java
  UUID clientId = TenantContext.getClientId();
  if (clientId != null) {
      session.enableFilter("tenantFilter").setParameter("clientId", clientId);
  } else {
      session.disableFilter("tenantFilter");
  }
  ```

- **Service mutation paths must derive `clientId` from `TenantValidator.getCurrentClientId()`, not `TenantContext.getClientId()`** — `TenantContext` is populated from the `X-Client-Subdomain` request header, which any authenticated caller can set to any arbitrary value. `TenantValidator.getCurrentClientId()` reads from the JWT via `SecurityContextHolder`, which cannot be forged. Using `TenantContext.getClientId()` in service write paths opens a cross-tenant write vulnerability.

  ```java
  // WRONG — header-spoofable, enables cross-tenant write
  UUID clientId = TenantContext.getClientId();

  // CORRECT — JWT-derived, tamper-proof
  UUID clientId = tenantValidator.getCurrentClientId();
  ```

  Note: `TenantEntityListener` correctly uses `TenantContext` for auto-stamping on `@PrePersist` — that is its intended purpose. Service-layer security checks and explicit `clientId` derivation for scoped writes must always go through `TenantValidator`.

- **Add explicit `clientId` filter to all custom `@EntityGraph` / `@Query` repository methods as defense-in-depth** — the AOP tenant filter is the primary isolation mechanism, but any custom `@Query` that fetches entities by a set of IDs (e.g. `findByIdsWithGraph`) becomes a cross-tenant leak vector if the AOP filter is not applied (async context, proxy miss, or direct call from a test). Always add `AND c.clientId = :clientId` to such queries and pass `clientId` from the service layer. Example:

  ```java
  // WRONG — relies solely on AOP filter; cross-tenant if filter is inactive
  @Query("SELECT c FROM EnablementCourse c WHERE c.id IN :ids")
  List<EnablementCourse> findByIdsWithGraph(@Param("ids") List<UUID> ids);

  // CORRECT — defense-in-depth; safe regardless of AOP filter state
  @Query("SELECT c FROM EnablementCourse c WHERE c.id IN :ids AND c.clientId = :clientId")
  List<EnablementCourse> findByIdsWithGraph(@Param("ids") List<UUID> ids,
                                            @Param("clientId") UUID clientId);
  ```
