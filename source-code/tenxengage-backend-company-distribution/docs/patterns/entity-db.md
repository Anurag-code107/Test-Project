# Entity & Database Patterns

Covers JPA entity conventions, Lombok gotchas, indexing requirements, and search patterns. Read before adding new entities, indexes, or search queries.

---

## JPA Entity Conventions

### Lombok `@Data` pitfalls on JPA entities

`@Data` is allowed on JPA entities (per project convention) but has two non-obvious traps:

**1. Mutable `equals`/`hashCode` breaks collection membership.**
`@Data` generates `equals`/`hashCode` from all non-excluded, non-static fields. After any field mutation before a flush, the entity's hash changes — breaking `HashSet<Entity>` membership and Hibernate dirty-checking in collections. Always restrict to the identity field:

```java
// WRONG — hash changes on field mutation
@Data
@EqualsAndHashCode(callSuper = true)
public class Assessment extends BaseEntity { ... }

// CORRECT — identity only; stable through mutation
@Data
@EqualsAndHashCode(callSuper = true, of = {})   // uses only BaseEntity.id
public class Assessment extends BaseEntity { ... }
```

Or use `exclude = {"collectionField1", ...}` to suppress lazy-loaded collections from the hash. At minimum, always exclude all `@OneToMany` / `@ManyToMany` collections.

**2. `@Data` exposes a public `setVersion()` on `@Version` fields.**
Calling `entity.setVersion(0L)` before a `save()` silently resets the optimistic lock counter, allowing stale writes to succeed. JPA manages the version column exclusively — no external setter should exist.

```java
// WRONG — public setter defeats optimistic locking
@Version
@Column(nullable = false)
@Builder.Default
private Long version = 0L;

// CORRECT — prevent Lombok from generating the setter
@Setter(AccessLevel.NONE)
@Version
@Column(nullable = false)
@Builder.Default
private Long version = 0L;
```

Apply `@Setter(AccessLevel.NONE)` to the `version` field on every versioned entity.

---

## When to add `@Version`

Add `@Version` to any entity where two concurrent writes to the same row must not silently clobber each other. This includes:

- **Obviously mutable entities**: assessments, questions, tags, namespaces — already obvious.
- **"Mostly immutable" entities with post-creation mutations**: even a single flag that can flip after creation (e.g., `groundingValidated`, `confirmedAt`, `isBestScore`) is a lost-update risk on multi-pod deployments.  
  - If a mutation truly happens exactly once (publish flow), a partial unique index (`WHERE published_at IS NULL`) is a lower-friction alternative to `@Version`.
- **Fact/event entities with confirmation fields**: `EntityTag.confirmedByUserId`, `EntityTag.confirmedAt` can race under concurrent AI tagging and human confirmation; add `@Version`.

Do NOT add `@Version` to insert-only entities (e.g., `event_outbox`, `audit_log`) — a second attempt to update them is already a bug.

---

## CascadeType on high-cardinality child collections

`CascadeType.ALL` on a `@OneToMany` collection of audit-critical or high-volume children (attempts, answers, event rows) risks accidental cascade-delete when the parent aggregate is deleted. In the assessment domain:

```java
// RISKY — assessmentRepo.delete(assessment) cascade-deletes all attempt records
@OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL)
private List<AssessmentAttempt> attempts;

// SAFER — manage attempt lifecycle independently
@OneToMany(mappedBy = "assessment", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
private List<AssessmentAttempt> attempts;
```

Rule of thumb:
- Use `CascadeType.ALL` + `orphanRemoval = true` only for children whose lifecycle is fully owned by the parent (e.g., `Question` owned by `Assessment`).
- Use `{PERSIST, MERGE}` for children that have independent audit/reporting significance (attempts, results, answers, outbox rows).

---

## GIN Trigram Indexes for LIKE Search

`LOWER(col) LIKE :q` without a GIN trigram index causes a full-table sequential scan. Add a trigram index for every text column used in a LIKE/iLIKE search:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_<table>_<col>_trgm ON <table> USING gin (lower(<col>) gin_trgm_ops);
```

Columns requiring GIN trigram indexes when used in LIKE queries:
- `questions.prompt_text` — ✅ added in V7
- `assessments.title` — ⚠ add in next migration
- `question_banks.name` — ⚠ add in next migration

Escape LIKE special characters (`%`, `_`) in the search string before binding:

```java
String q = "%" + searchTerm.toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
```

Document in Javadoc on any repository search method that the caller must pass a pre-escaped, `%`-wrapped string:

```java
/**
 * @param q LIKE pattern, must be pre-escaped and wrapped: e.g. {@code "%foo%"}.
 */
Page<Assessment> searchByClientId(..., String q, Pageable pageable);
```

---

## Pitfalls

- **Free-text `@RequestParam` (search, query) must have `@Size(max = 200)`** — unbounded strings feed directly into LIKE queries and cause excessive memory and sequential scans.
- **Escape LIKE special characters before passing to `CONCAT`** — `@Size` limits length but not wildcard content, enabling full-table scan amplification.
- **`LIKE` with a leading wildcard (`%word`) cannot use a B-tree index** — requires a GIN trigram index for any substring or leading-wildcard search.
- **Two-query keyset pagination: use `idPage.getTotalElements()` for the empty-page short-circuit** — never hardcode `0`.
- **JPQL enum null-binding in Postgres — use `CAST(:param AS STRING) IS NULL` not bare `:param IS NULL`** — when a Java `@Param` of enum type is `null`, Hibernate/Postgres cannot infer the type from a plain `:param IS NULL` expression and throws a runtime error (`operator does not exist: bytea = null`). The established workaround is `CAST(:param AS STRING) IS NULL`, which coerces to a type Postgres can evaluate. This applies to every nullable enum parameter in a `@Query`.

  ```java
  // WRONG — fails at runtime when type or status is null
  "AND (:type IS NULL OR a.type = :type) " +
  "AND (:status IS NULL OR a.status = :status) "

  // CORRECT — matches the pattern used in IncentiveRepository and AssessmentRepository
  "AND (CAST(:type AS STRING) IS NULL OR a.type = :type) " +
  "AND (CAST(:status AS STRING) IS NULL OR a.status = :status) "
  ```

  The idiomatic alternative (avoids the CAST entirely) is `Specification<T>` or separate query methods per filter combination — prefer CAST only when adding a Specification layer would require refactoring a stable query.

- **Never store denormalized FK columns alongside a JPA association** — storing `assessmentId` and `userId` as plain `@Column` UUIDs next to a `@OneToOne attempt` relationship creates two sources of truth that can drift. Either derive them from the association at query time, or add a `@PrePersist` / `@PreUpdate` validation enforcing they always match `attempt.assessment.id` / `attempt.userId`.
- **`SELECT COUNT(e) > 0` is invalid JPQL** — the SELECT clause cannot contain a boolean comparison expression; Hibernate 6.x throws at startup or first invocation. Use a Spring Data derived method (`existsBy...`) or return `long` from `SELECT COUNT(e) FROM ...` and compare at the call site.
- **`@Data` on entities with bidirectional `@ManyToOne` causes `toString()` StackOverflow** — `@Data`-generated `toString()` includes all fields; if the child's `toString()` traverses back to the parent and the parent's `toString()` traverses the child collection, the cycle causes `StackOverflowError`. Use `@Getter @Setter @NoArgsConstructor` with explicit `@ToString(callSuper = true, exclude = {"parentField"})` on any entity that is the "many" side of a bidirectional relationship.
