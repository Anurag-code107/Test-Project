# Pattern: soft-delete

## When this applies

Feature introduces one or more entities that should be soft-deleted (logical deletion via a `deleted` flag)
rather than physically removed from the database.

## Spec authoring guidance

- Every new soft-deletable entity needs a `deleted BOOLEAN NOT NULL DEFAULT FALSE` column in its Flyway migration.
- Spec must state the cascade policy on deletion: soft-delete child entities together, or block deletion when children exist.
- The column must be declared in the Flyway migration with a NOT NULL DEFAULT FALSE constraint — never NULL.

## Implementation guidance

### Entity

Add `@SQLRestriction("deleted = false")` at the class level (Hibernate 6.3+ annotation, replaces deprecated `@Where`).
Place it alongside `@Filter` when the entity also has tenant isolation:

```java
@Entity
@Table(name = "question_banks")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@SQLRestriction("deleted = false")
public class QuestionBank extends BaseEntity implements TenantAware {

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;
}
```

Import: `org.hibernate.annotations.SQLRestriction`

`@SQLRestriction` adds a SQL-level predicate (`deleted = false`) to every query that loads the entity —
`findById`, derived query methods, and JPQL SELECT queries all inherit it automatically.
No `AndDeletedFalse` suffixes are needed in repository method names.

### Repository

Write repository methods **without** `AndDeletedFalse` — the restriction is automatic:

```java
// Correct
Optional<QuestionBank> findByIdAndClientId(UUID id, UUID clientId);
Page<QuestionBank> findByClientId(UUID clientId, Pageable pageable);
boolean existsByClientIdAndName(UUID clientId, String name);

// Wrong — never do this
Optional<QuestionBank> findByIdAndClientIdAndDeletedFalse(UUID id, UUID clientId);
```

JPQL `SELECT` queries also do not need an explicit `deleted = false` predicate:

```java
// Correct — @SQLRestriction is applied automatically at the SQL level
@Query("SELECT q FROM Question q WHERE q.clientId = :clientId AND q.questionBank.id = :bankId AND LOWER(q.promptText) LIKE :searchTerm")
Page<Question> searchByClientIdAndBank(...);
```

**Critical exception — bulk DML:** `@SQLRestriction` is NOT applied to `@Modifying` UPDATE or DELETE statements.
Always include an explicit guard in bulk DML:

```java
// @SQLRestriction does NOT apply here — explicit guard is mandatory
@Modifying
@Query("UPDATE Question q SET q.deleted = true WHERE q.clientId = :clientId AND q.questionBank.id = :bankId AND q.deleted = false")
void softDeleteByClientIdAndBankId(@Param("clientId") UUID clientId, @Param("bankId") UUID bankId);
```

### Service

Soft-delete via a setter + save (do not issue a DELETE):

```java
entity.setDeleted(true);
repository.save(entity);
```

When deleting a parent that has child entities, cascade the soft-delete to children in the same `@Transactional`
method — call the child's `softDeleteBy*` bulk-update query first, then soft-delete the parent:

```java
@Transactional
public void deleteBank(UUID bankId) {
    UUID clientId = tenantValidator.getCurrentClientId();
    QuestionBank bank = questionBankRepository.findByIdAndClientId(bankId, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("QuestionBank", "id", bankId));
    questionRepository.softDeleteByClientIdAndBankId(clientId, bankId); // children first
    bank.setDeleted(true);
    questionBankRepository.save(bank);
}
```

## Common gotchas

- **Forgetting the bulk DML guard.** `@SQLRestriction` silently skips `@Modifying` queries. An `UPDATE ... SET deleted = true` without `AND deleted = false` will double-write already-deleted rows (harmless but wasteful) — worse, a bulk `DELETE` without the guard would hard-delete already-soft-deleted rows.
- **Adding `AndDeletedFalse` to a method name on an entity with `@SQLRestriction`.** The restriction is already applied; the suffix is dead code and misleads future readers.
- **Forgetting to cascade soft-delete to children.** A deleted parent with live children creates orphaned data that is invisible in normal queries but still occupies storage and may surface in admin or audit views.
- **Using `@Where` instead of `@SQLRestriction`.** `@Where` is deprecated since Hibernate 6.3 (Spring Boot 3.2+). Use `@SQLRestriction` — same semantics, supported API.
- **Querying deleted records.** `@SQLRestriction` is always-on — there is no session-level switch. If you need to read soft-deleted rows (e.g. admin audit view), you must use a native `@Query` with a literal `deleted IN (true, false)` predicate, or a separate Hibernate Filter (`@FilterDef` / `@Filter`) that can be enabled per session.
