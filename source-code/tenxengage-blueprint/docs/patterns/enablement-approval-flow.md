# enablement-approval-flow

Pattern for multi-approver approval workflows on enablement aggregates (Course, LearningPath, CertificationProgram). Mirrors the incentive approval flow but with lifecycle-date composition.

## When this applies

Any enablement aggregate whose publish action can be gated behind a configurable set of approvers.

## Schema

```sql
-- Columns on the parent entity
ALTER TABLE {entity}s
  ADD COLUMN requires_approval  BOOLEAN  NOT NULL DEFAULT FALSE,
  ADD COLUMN required_approvals INTEGER  NOT NULL DEFAULT 0,
  ADD COLUMN approval_round     INTEGER  NOT NULL DEFAULT 1,
  ADD COLUMN status_changed_at  TIMESTAMPTZ;

-- Approvers (per entity, per round)
CREATE TABLE {entity}_approvers (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  client_id  UUID NOT NULL REFERENCES clients(id),
  {entity}_id UUID NOT NULL REFERENCES {entity}s(id),
  email      VARCHAR(255) NOT NULL,
  category   VARCHAR(100) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted    BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_{entity}_approvers_{entity}_email
  ON {entity}_approvers({entity}_id, lower(email)) WHERE deleted = FALSE;

-- Decisions (immutable history; never cascade-deleted)
CREATE TABLE {entity}_approval_decisions (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  client_id      UUID NOT NULL REFERENCES clients(id),
  {entity}_id    UUID NOT NULL REFERENCES {entity}s(id),
  approver_email VARCHAR(255) NOT NULL,
  decision       VARCHAR(20)  NOT NULL,  -- APPROVED | REJECTED
  decided_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  token_id       UUID NOT NULL UNIQUE,
  comment        TEXT,
  approval_round INT NOT NULL DEFAULT 1,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_{entity}_approval_decisions_{entity}_email_round
  ON {entity}_approval_decisions({entity}_id, lower(approver_email), approval_round);
```

## Submit / Resubmit flow

1. Author calls `POST /{entity}s/{id}/submit-for-approval` with `approvers`, `requiredApprovals`, `version`.
2. Service validates `status == DRAFT` + publish-readiness gates (lessons, AV, AI artifacts, `effectiveAt` set + future).
3. Old approver rows soft-deleted; new ones inserted. Status → `PENDING_APPROVAL`. `statusChangedAt = now`.
4. `{Entity}ApprovalRequestEvent` published to `{entity}-approval-events` Kafka topic.
5. Consumer generates one JWT approval token per approver (`ownerType=ENTITY`, `resourceId`, `approverEmail`, `approvalRound`) and sends email.
6. Resubmit (from `DENIED`): bumps `approvalRound`; old round tokens become invalid (round mismatch check in handler).

## Decide flow

`POST /api/v1/approvals/decide?token=…&action=APPROVED|REJECTED` dispatches to `CourseApprovalHandler` (via `ownerType` claim in the JWT).

### Threshold logic

| Condition | Outcome |
|---|---|
| `approvedCount >= requiredApprovals` | `effectiveAt <= now` → PUBLISHED; `effectiveAt > now` → SCHEDULED |
| `currentApprovals + remainingApprovers < requiredApprovals` | DENIED |
| Otherwise | Stays PENDING_APPROVAL |

See [lifecycle-dates.md](lifecycle-dates.md) for SCHEDULED → PUBLISHED via scheduler.

## JWT token (`ApprovalOwnerType` discriminator)

```java
.claim("ownerType", ownerType.name())  // INCENTIVE | COURSE | …
.claim("incentiveId", resourceId)       // field name kept for back-compat
```

**Back-compat:** tokens without `ownerType` claim default to `INCENTIVE`. This preserves in-flight incentive tokens across the deploy window. Drop the default once the TTL (7 days) has elapsed.

## ApprovalHandler strategy

```java
interface ApprovalHandler {
    ApprovalOwnerType ownerType();
    ApprovalReviewResult getForReview(UUID resourceId, ApprovalClaims claims);
    ApprovalResult processDecision(UUID resourceId, ApprovalClaims claims, String action, String comment);
}
```

`ApprovalService` holds a `Map<ApprovalOwnerType, ApprovalHandler>` injected by Spring. Adding a new entity type requires:
1. New `ApprovalOwnerType` enum value.
2. New `@Component ApprovalHandler` implementation.
3. New `GET /api/v1/approvals/{entity}?token=…` endpoint.

## Approver categories (7 canonical)

Budget / T&C / Compliance / Legal / Overall / Finance / Marketing — same as incentive.

## Approval round invalidation

When resubmitting, `approvalRound` is incremented. The handler checks `claims.approvalRound() == entity.approvalRound()` before recording any decision. Old round tokens return an error without persisting anything.

## Notification kinds

- `{ENTITY}_APPROVAL_REQUESTED`
- `{ENTITY}_APPROVAL_DECISION`
- `{ENTITY}_APPROVED`
- `{ENTITY}_DENIED`

## Examples in codebase

- `entity/course/CourseApprover.java`, `CourseApprovalDecision.java`
- `service/course/CourseApprovalService.java`, `CourseApprovalHandler.java`
- `event/course/CourseApprovalRequestEvent.java`, `CourseApprovalEventConsumer.java`
- `controller/course/CourseController.java` — `/submit-for-approval`, `/resubmit-for-approval`, `/approval-status`
- `controller/ApprovalController.java` — `/approvals/course`
- Migration: `V41__course_lifecycle_rewards_approval.sql` sections 1c + 1d.

## Common gotchas

- Decisions table uses `deleted = false` NOT on decisions (history is immutable). Approvers table uses soft-delete (replaced on resubmit).
- `requiresApproval = true` blocks direct `/publish`. Both the FE publish CTA and the BE gate must be toggled together (coordinate FE step 5 with BE step 6).
- Don't null the `incentiveId` JWT claim when renaming — the field name is intentionally kept for backward compatibility.
