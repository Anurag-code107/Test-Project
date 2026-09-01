# Pattern: audience-rules

## When this applies

A feature's owning entity (course, learning path, future certification) needs to gate access by tenant-defined predicates against PartnerCompany or User facets. Examples: "only ACTIVE partners in EMEA can enroll", "only users with role = Channel Manager can take this course".

If the feature is the incentive participant-eligibility flow, do **not** use this pattern. Incentive has its own `IncentiveAudienceRule` stack documented in [domains/incentive.md](domains/incentive.md) and is not migrated here.

## Spec authoring guidance

For each owning entity, the spec must declare:

1. **Owner table name:** `{owner}_audience_rules` (e.g. `course_audience_rules`, `customer_audience_rules`).
2. **Owner FK + cascade:** the table has `{owner}_id` with `ON DELETE CASCADE`. Deleting the owner deletes its rules.
3. **Subject types in scope:** which `DataObjectEntityType`s the rules can target (PARTNER_COMPANY, USER, future CUSTOMER). The corresponding `SubjectFacetResolver<E>` implementations must exist.
4. **Operator catalogue:** which `RuleOperator`s the eligibility UI exposes for which `DataObjectField.dataType`. The default mapping lives in `AudienceRuleEvaluator`.

## Implementation guidance

### Java entity model

`AudienceRule` is a `@MappedSuperclass` carrying the shared columns:

```java
@MappedSuperclass
public abstract class AudienceRule extends BaseEntity implements TenantAware {
    private UUID clientId;
    @ManyToOne DataObjectField dataObjectField;
    @Enumerated RuleOperator operator;
    @JdbcTypeCode(SqlTypes.JSON) String valuesJson;
    @Version Long version;
}
```

Each owner extends it with its FK:

```java
@Entity @Table(name = "course_audience_rules")
public class CourseAudienceRule extends AudienceRule {
    @ManyToOne Course course;
}
```

### Schema per owner

```sql
CREATE TABLE {owner}_audience_rules (
  id UUID PRIMARY KEY,
  client_id UUID NOT NULL,
  {owner}_id UUID NOT NULL REFERENCES {owner_table}(id) ON DELETE CASCADE,
  data_object_field_id UUID NOT NULL REFERENCES data_object_fields(id) ON DELETE RESTRICT,
  operator VARCHAR(20) NOT NULL,
  values_json JSONB NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_{owner}_audience_rules_owner ON {owner}_audience_rules({owner}_id);
CREATE INDEX idx_{owner}_audience_rules_client ON {owner}_audience_rules(client_id);
```

### Evaluator + resolver

`AudienceRuleEvaluator` is shared across all owners. It dispatches on `field.getDataObject().getMapsToEntity()` to pick a `SubjectFacetResolver<E>`:

- `PartnerCompanyFacetResolver` for `PARTNER_COMPANY`-mapped DataObjects.
- `PartnerUserFacetResolver` for `USER`-mapped DataObjects.
- `CustomerFacetResolver` (future) for `CUSTOMER`-mapped DataObjects.

Each resolver reads `field.mappedColumn` first (direct entity attribute), falling back to `entity.metadata[field.name]` (JSONB key) if `mappedColumn` is null.

### Adding a new owner entity (checklist)

1. Add the Flyway migration for `{owner}_audience_rules`.
2. Add `{Owner}AudienceRule extends AudienceRule` Java entity.
3. Add `{Owner}AudienceRuleRepository`.
4. Add `{Owner}AudienceRuleService` (model after `CourseAudienceRuleService`).
5. Add the controller at `/api/v1/{owners}/{ownerId}/audience-rules`.
6. If the owner introduces a new `DataObjectEntityType`, add a `SubjectFacetResolver<E>` implementation and register it in the Spring context.
7. Expose the rules in the owner's wizard via the unified `AudienceSection` FE component (see [builder-wizard.md](builder-wizard.md)).

## Examples in codebase

- `entity/course/CourseAudienceRule.java`, `repository/course/CourseAudienceRuleRepository.java`, `service/course/CourseAudienceRuleService.java`.
- `service/course/facet/PartnerCompanyFacetResolver.java`, `service/course/facet/PartnerUserFacetResolver.java`.
- Frontend: `components/course-builder/audience/AudienceRuleEditor.tsx`, `components/course-builder/audience/CascadingLocationValueEditor.tsx`.

## Common gotchas

- **Don't reach for the polymorphic shape.** The pre-2026-05-25 design used a single `audience_rules` table with `owner_type` + `owner_id`. It was dropped because FK cascades were impossible. Always create a per-owner table.
- **`values_json` shape varies by `dataType` + `valueSource`.** STATIC ENUM → `{ "values": ["ACTIVE"] }`. LOCATION_HIERARCHY → `{ "locationLevelId": "...", "valueIds": [...] }`. NUMBER/DATE BETWEEN → `{ "min": x, "max": y }`. The FE shape definitions live in `tenxengage-frontend/src/types/course/audience-rule.types.ts`.
- **Cascading without a `parent_field_id`:** the parent for a depth-N field is *any* depth-(N-1) field in the same DataObject. If a tenant has multiple depth-(N-1) fields, the FE warns and treats the child as non-cascading. See [location-hierarchy.md](location-hierarchy.md).
- **Incentive does not use this pattern (yet).** Migrating `IncentiveAudienceRule` to `extends AudienceRule` is tracked as future work — significant rewrite of `ParticipantEligibilityChecker` required.
