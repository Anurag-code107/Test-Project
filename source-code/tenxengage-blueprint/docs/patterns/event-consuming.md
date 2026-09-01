# Pattern: event-consuming

## When this applies

Feature consumes Kafka domain events from other parts of the system (e.g., on `user.deactivated`, cancel that user's enrollments).

## Spec authoring guidance

- **Topics consumed:** spec each topic with the expected event schema and the action this feature takes.
- **Consumer group ID:** every consumer belongs to a consumer group. Use the naming convention `tenxengage-{purpose}` (e.g., `tenxengage-journey`, `tenxengage-approval`, `tenxengage-training`). Audit existing consumers in `tenxengage-backend/src/main/java/com/tenxengage/app/event/` for the convention — groups may be shared when multiple features share the same concern (e.g., `tenxengage-journey` is shared by all journey-completion consumers).
- **Idempotency:** consumers MUST be idempotent. Spec the deduplication strategy. Two patterns in use:
  - **State-based (implicit):** re-applying the event produces no further change because the outcome is already in the desired state. Simpler — no extra table needed. Suitable when the handler is a pure upsert or status-gated operation.
  - **Event-ID-based (explicit):** track processed event IDs before committing side effects. Use this when the handler has non-idempotent side effects (e.g., sending emails, charging credits). Requires a deduplication table or Redis set.
  - The platform does **not** currently have a shared `processed_events` table — each consumer that needs event-ID tracking creates its own deduplication store.
- **Failure semantics:** spec retry behavior (DLQ after N attempts? Block the partition? Exponential backoff?).
- **Transactional boundary:** spec the transaction scope. Typical pattern: process event, write outcome state, commit DB transaction, then commit Kafka offset.

## Implementation guidance

- Use the platform's Kafka consumer wrapper (audit existing consumers in `tenxengage-backend/src/main/java/com/tenxengage/app/event/`).
- Idempotency is the consumer's responsibility. If the handler is not naturally idempotent, add an explicit deduplication store scoped to that consumer.
- DLQ topic naming: `{original-topic}.dlq`.
- Integration test: feed a synthetic event into the consumer (test container Kafka), assert the side effect.

## Examples in codebase

- Existing consumers: `tenxengage-backend/src/main/java/com/tenxengage/app/event/NotificationEventConsumer.java`, `CompletionEventConsumer.java`, `ApprovalEventConsumer.java`
- Consumer group configs: `grep -rn "groupId\|@KafkaListener" tenxengage-backend/src/main/java/`

## Common gotchas

- **Reprocessing on restart.** Without idempotency, a consumer restart double-applies effects. Idempotency is non-negotiable.
- **Slow consumer = partition lag.** If processing is expensive, consider per-event async work with separate offset commit.
- **Schema drift.** Producer adds a field; consumer crashes on unexpected JSON. Use lenient deserialization (ignore unknown fields).
- **Time travel.** Replaying a topic from earliest re-fires every effect. Idempotency saves you.
