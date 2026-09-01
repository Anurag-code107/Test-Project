# lifecycle-dates

Pattern for enablement entities that gate their active window with `effective_at` / `expiry_at` timestamps and a `SCHEDULED` intermediate status.

## When this applies

Any enablement aggregate (Course, LearningPath, CertificationProgram) that needs:
- a mandatory publish date (`effective_at`) that may be in the future, and
- an optional auto-archive date (`expiry_at`).

## Data shape

```sql
ALTER TABLE {entity}
  ADD COLUMN effective_at   TIMESTAMPTZ,          -- required before publish
  ADD COLUMN expiry_at      TIMESTAMPTZ,          -- optional auto-archive
  ADD COLUMN status_changed_at TIMESTAMPTZ;

ALTER TABLE {entity}
  ADD CONSTRAINT chk_{entity}_expiry_after_effective
  CHECK (expiry_at IS NULL OR effective_at IS NULL OR expiry_at > effective_at);

-- Partial indexes for scheduler queries
CREATE INDEX idx_{entity}_status_effective_at ON {entity}(status, effective_at)
  WHERE status IN ('SCHEDULED', 'PUBLISHED');
CREATE INDEX idx_{entity}_status_expiry_at ON {entity}(status, expiry_at)
  WHERE status = 'PUBLISHED' AND expiry_at IS NOT NULL;
```

The `status` column stays VARCHAR (no Postgres enum).

## State machine

```
DRAFT → PENDING_APPROVAL → SCHEDULED → PUBLISHED → ARCHIVED
  ↘ (direct, no approval)   ↗ (scheduler tick)      ↑ (scheduler tick)
                 DENIED ──→ PENDING_APPROVAL (resubmit)
```

Transitions:
- `DRAFT → SCHEDULED`: direct publish with `effectiveAt > now`. No `publishedAt` set yet.
- `DRAFT → PUBLISHED`: direct publish with `effectiveAt <= now`. Sets `publishedAt = now`.
- `PENDING_APPROVAL → SCHEDULED/PUBLISHED`: approval threshold met. Scheduler or handler checks `effectiveAt`.
- `SCHEDULED → PUBLISHED`: scheduler tick, sets `publishedAt = effectiveAt`.
- `PUBLISHED → ARCHIVED`: scheduler tick at `expiryAt`, sets `archivedAt = expiryAt`.
- `SCHEDULED → DRAFT`: unpublish (author cancels).
- `PUBLISHED → DRAFT`: unpublish.

## Validation rules

- `effectiveAt` **required** at publish (direct or approval-threshold-met). Throws `{ENTITY}_REQUIRES_EFFECTIVE_AT` (422).
- `effectiveAt` must be in the future when submitting for approval. Throws `{ENTITY}_EFFECTIVE_AT_IN_PAST` (422).
- `expiryAt`, when present, must be after `effectiveAt`. Enforced by CHECK constraint + `@ExpiryAfterEffective` cross-field validator on the request DTO.

## Scheduler contract

```java
@Scheduled(cron = "${app.{entity}.lifecycle-scheduler.cron:0 */5 * * * *}")
@SchedulerLock(name = "{entity}LifecycleTick", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
public void tick() { /* per-tenant loop */ }
```

- ShedLock prevents concurrent runs across nodes.
- Per-tenant loop: set `TenantContext`, then call `promoteScheduled` and `archiveExpired` in separate `@Transactional` methods.
- Idempotent: status check inside row lock prevents double-flip even if the same row is loaded twice.

## Outbox events emitted

| Transition | Event type | Payload key |
|---|---|---|
| SCHEDULED → PUBLISHED | `{entity}_published` | `publishedAt = effectiveAt` |
| PUBLISHED → ARCHIVED | `{entity}_archived` | `archivedAt = expiryAt` |

## Feature flag

Scheduler is guarded by `app.{entity}.lifecycle-scheduler.enabled` (default `true` in `application.yml`; set `false` in environments that should not auto-flip statuses).

## Examples in codebase

- `config/CourseLifecycleScheduler.java` — first implementation.
- `repository/course/CourseRepository.java` — `findScheduledReadyToPublish` / `findPublishedReadyToArchive`.
- Migration: `V41__course_lifecycle_rewards_approval.sql` sections 1a + partial indexes.

## Common gotchas

- Always check status inside the pessimistic row lock before flipping — a concurrent tick may have already promoted the row.
- `publishedAt` is set to `effectiveAt` (not `now()`) on scheduler promotion, so historical published time is accurate.
- The `effectiveAt` gate in `submitForApproval` checks `effectiveAt >= now` — approvers need future dates so tokens don't expire before review.
- **Order publish pre-conditions cheapest-first.** In `publish()`, check field-level guards (no DB) before DB-query guards: `status`, `requiresApproval`, `effectiveAt` → then `findByLearningPathId` for steps → then milestones. This prevents unnecessary DB round-trips when the request is obviously invalid (e.g. requiresApproval=true should short-circuit before a steps query).
- **Re-validate the triggering business condition under the row lock — not just status/idempotency — before an irreversible action.** A scheduled work-item (a `NOTIFIED` notice, a `SCHEDULED` row) carries a precomputed decision that can go stale between when it was created and when the sweep executes it. Re-checking only the status flag and an idempotency guard is not enough if the *condition that justified the action* can change in the meantime. Recompute that condition from live data inside the lock and cancel/reschedule the item if it no longer holds. Found during reward-balance-expiration: the expire phase debited a wallet to zero using a notice computed in an earlier warn phase, without re-checking last-activity — a wallet that became active after the warning was still expired, destroying valid balance (Codex adversarial review, 2026-06-29).
- **Compute every date boundary with a fixed zone (`LocalDate.now(ZoneOffset.UTC)`), never the JVM default.** TIMESTAMPTZ columns store instants, but `LocalDate.now()` (no zone) reads the server's default zone, so a window edge computed that way is off by a day relative to UTC-stored data on a non-UTC host. Mixing `LocalDate.now()` and `LocalDate.now(ZoneOffset.UTC)` within one feature is the trap — pick the explicit-UTC form everywhere. Found during reward-balance-expiration: the expiring-soon preview used `LocalDate.now()` while the rest of the feature used `ZoneOffset.UTC` (ready-check code review, 2026-06-29).
