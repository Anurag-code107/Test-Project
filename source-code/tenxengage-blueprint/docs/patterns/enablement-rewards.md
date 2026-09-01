# enablement-rewards

Pattern for normalized per-entity reward rows on enablement aggregates (Course, LearningPath, CertificationProgram).

## When this applies

Any enablement aggregate that awards currency-denominated rewards on completion.

**Explicit non-pattern:** No budget cap concept. Rewards here model what the learner earns, not an organizational spending limit. Contrast with `IncentiveBudget` (incentive domain) which tracks allocated funds.

## Data shape

```sql
CREATE TABLE {entity}_rewards (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  client_id    UUID NOT NULL REFERENCES clients(id),
  {entity}_id  UUID NOT NULL REFERENCES {entity}(id),
  currency_id  UUID NOT NULL REFERENCES currencies(id),
  amount       NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
  message      TEXT,
  sort_order   INT NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted      BOOLEAN NOT NULL DEFAULT FALSE,
  version      BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_{entity}_rewards_client ON {entity}_rewards(client_id);
CREATE INDEX idx_{entity}_rewards_{entity} ON {entity}_rewards({entity}_id) WHERE deleted = FALSE;
CREATE UNIQUE INDEX uq_{entity}_rewards_{entity}_currency
  ON {entity}_rewards({entity}_id, currency_id) WHERE deleted = FALSE;
```

## Uniqueness rule

Each `(entity_id, currency_id)` pair must be unique among non-deleted rows. Enforced by the partial unique index. The service validates for duplicates before writing.

## Currency catalog binding

`currency_id` references `currencies(id)`. The service validates each submitted currency exists in the tenant's catalog before persisting. Cross-tenant currencies are rejected as 404.

## Replace semantics

Rewards use **replace-not-patch** semantics. `PUT /courses/{id}` with `rewards: [...]` soft-deletes all existing reward rows and inserts fresh ones. `rewards: null` in the request = no change (pass-through). `rewards: []` = clear all rewards.

## Fields

| Field | Type | Notes |
|---|---|---|
| currencyId | UUID | FK → currencies; validated in tenant catalog |
| amount | NUMERIC(15,2) | ≥ 0 |
| message | TEXT | Optional localized message shown to learner on award |
| sortOrder | INT | Display ordering; defaults to insertion index |

## Repo methods

- `findByEntityIdAndClientIdOrderBySortOrderAsc` — load for display
- `softDeleteByEntityIdAndClientId` — mark-deleted before replace

## JPA entity

```java
@Entity
@Table(name = "{entity}_rewards")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@SQLRestriction("deleted = false")
public class {Entity}Reward extends BaseEntity implements TenantAware { ... }
```

Parent entity side:
```java
@OneToMany(mappedBy = "{entity}", cascade = ALL, orphanRemoval = true, fetch = LAZY)
@BatchSize(size = 20)
private List<{Entity}Reward> rewards = new ArrayList<>();
```

## Examples in codebase

- `entity/course/CourseReward.java`
- `repository/course/CourseRewardRepository.java`
- `service/course/CourseRewardService.java`
- Migration: `V41__course_lifecycle_rewards_approval.sql` section 1b.

## Pitfalls

**FE: Single shared message field — not per-currency.** `EnablementRewardsState` uses `message: string` (one message shared across all currency rows). The server schema allows per-currency messages (`message?: string | null` on each row), but the FE builder UI deliberately has one message textarea. Hydration (`serverRewardsToRewardsState`) takes the first non-empty message; the save mapper applies it to every row. Do not redesign this as per-currency without a spec change.

**FE: Namespace API mock format in E2E tests.** `listNamespaces` does `response.data.data` — the response body is `{ data: TagNamespaceResponse[], message, success }`. Use `apiResponse([])` (not `apiResponse({ data: [], page: 0, ... })`) to mock it. The double-nested form produces an object where an array is expected, causing the tagging section to silently malfunction.

**FE: Always include `rewards[]` in PUT body on both save paths (dual-save-path pitfall).** Because replace semantics interpret `rewards: []` as "clear all rewards", omitting the field on the create-baseline save path would leave rewards unset. AC-2 is explicit: always send `rewards[]` — empty on create baseline, populated on edit.
