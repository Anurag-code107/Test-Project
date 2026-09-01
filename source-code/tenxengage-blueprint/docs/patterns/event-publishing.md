# Pattern: event-publishing

## When this applies

Feature publishes Kafka domain events on state changes that other system components need to react to (course-published triggers notification; quiz-submitted triggers reward issuance; user-deactivated triggers downstream cleanup).

## Spec authoring guidance

- **Topic naming:** follow the codebase's existing convention. Audit existing topic names before proposing new ones. Existing producers: `NotificationEventProducer`, `CompletionEventProducer` in `com.tenxengage.app.event`. Actual convention in use: **`{entity}-events`** kebab-case (e.g., `notification-events`, `completion-events`, `approval-events`, `course-approval-events`). Note: new enablement-domain topics may adopt a namespaced form — audit existing event classes in `com.tenxengage.app.event.course/` before choosing a name for a new topic.
- **Event schema:** spec the FULL message payload. Include: event ID, occurred-at timestamp, tenant ID, entity ID, event-specific payload. The spec must show the actual JSON shape.
- **Idempotency contract:** specify how downstream consumers should deduplicate (event-ID-based, or entity-state-based). The publisher ensures event IDs are unique per emission.
- **Emission trigger:** spec exactly what business action causes emission (e.g., "after `Course.status` transitions to PUBLISHED, in the same transaction as the status update").
- **Failure semantics:** spec what happens if publish fails. Default: transactional outbox pattern (write event row in same DB transaction; separate worker drains outbox to Kafka). Document if using direct emit with at-most-once semantics.

## Implementation guidance

- Use the platform's Kafka producer wrapper (audit existing producers in `tenxengage-backend/src/main/java/com/tenxengage/app/event/`).
- Transactional outbox preferred for at-least-once delivery. Direct emit only for advisory events where loss is tolerable.
- Event ID is a UUID v7 (time-ordered) or v4. Persist it in the outbox row.
- Producer test: integration test that triggers the business action and asserts the event appears on the topic with the expected payload.

## Examples in codebase

- Existing producers: `tenxengage-backend/src/main/java/com/tenxengage/app/event/NotificationEventProducer.java`, `CompletionEventProducer.java`
- Topic naming: `grep -rn "topic\|TOPIC" tenxengage-backend/src/main/java/com/tenxengage/app/event/`

## Common gotchas

- **Transactional gap.** Emitting Kafka inside a JPA transaction is often wrong (Kafka emit succeeds, DB rolls back). Use outbox pattern.
- **Schema evolution.** Adding a required field is a breaking change for consumers. Add optional fields only, or version the topic.
- **Event ordering.** Kafka guarantees ordering within a partition, not across. Partition by entity ID for strict ordering.
- **PII in event payloads.** Events outlive the request; PII in payloads goes to Kafka log retention. Minimize PII; reference entity IDs, not full PII data.
- **JSONB outbox field needs `@JdbcTypeCode(SqlTypes.JSON)`.** If the `EventOutbox.payloadJson` field (or any `String` field with `@Column(columnDefinition="jsonb")`) lacks `@JdbcTypeCode(SqlTypes.JSON)`, Hibernate 6 passes a varchar to PostgreSQL and the INSERT fails with: `column "payload_json" is of type jsonb but expression is of type character varying`. Every JSONB-typed String field needs this annotation — `columnDefinition` alone is not sufficient.
- **`afterCommit` is necessary but not sufficient for at-least-once delivery.** `TransactionSynchronizationManager.afterCommit()` prevents the rollback race (event before DB commit) but `kafkaTemplate.send()` returns a `CompletableFuture` the callback does not wait on. A Kafka broker failure or timeout after the DB transaction commits silently drops the event with no retry path. Always attach `.whenComplete((result, ex) -> { if (ex != null) log.error("Kafka send failed ...", ex); })` to every `kafkaTemplate.send()` call, or implement a transactional outbox so the relay handles retries. Root cause of redemption-returns US-01 ADV-01 (Codex adversarial review, 2026-06-13).
- **Consistency: use `afterCommit` in ALL publish sites in the same service.** If one method uses `TransactionSynchronizationManager.afterCommit()` and another publishes directly inside `@Transactional`, a DB rollback in the direct-publish path broadcasts an event for a state transition that was never saved. Audit all Kafka publish calls in a service and normalise them to `afterCommit`. Found during redemption-returns US-03: `approveReturn()` used `afterCommit`; `processVendorConfirmation()` called `publishReturnConfirmed()` directly inside the `@Transactional` boundary (2026-06-13).
- **An empty `targetUserIds` is a tenant-wide role broadcast, not "no recipients".** `NotificationDispatcher` treats an empty/null `targetUserIds` list as a role-broadcast: it notifies every user in the tenant holding the notification type's seeded default roles (audience filtering only kicks in when `metadata.incentiveId` is present). Returning `List.of()` for a company- or group-scoped recipient — expecting "the consumer will figure out the audience from an id" — leaks the notification to the whole tenant when the event carries no audience key. For any event whose audience is narrower than a role, resolve the explicit recipient user IDs at publish time, or add and enforce an audience key (e.g. `partnerCompanyId`) on both the event payload and the dispatcher. Found during reward-balance-expiration: COMPANY-wallet expiry events returned empty recipients and carried only `walletId`, so a single company's expiry would notify every PARTNER_SELLER/PARTNER_ADMIN in the tenant (Codex adversarial review, 2026-06-29).
