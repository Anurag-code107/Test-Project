# Step 09: events-analysis

## Goal
Determine whether this feature publishes / consumes Kafka events. If yes, record the event-driven design.

## Inputs (from prior steps)
- Locked FRs (step 01)
- Locked shape manifest (step 05)
- Loaded `event-publishing.md` and / or `event-consuming.md` if shape manifest matched (step 06)

## Loads (just-in-time)
- If publishing or consuming: glob for existing producers/consumers, then JIT-read 1 example for naming conventions
  - `find tenxengage-backend/src/main/java -name "*Producer*.java" -o -name "*Consumer*.java"`

## Procedure

### If neither `event-publishing` nor `event-consuming` is in the shape manifest

Skip this step entirely. No findings to record. Proceed to step 10.

### If publishing (`event-publishing` shape)

1. Determine WHICH events to publish. For each:
   - Trigger (the business action that causes emission)
   - Topic name (follow the platform's existing naming convention — read 1 existing producer to confirm)
   - Payload schema (full JSON shape, not just field list)
   - Idempotency contract for consumers (event-ID-based or state-based)

2. Apply rules from `event-publishing.md` (loaded in step 06).

### If consuming (`event-consuming` shape)

1. Determine WHICH events to consume. For each:
   - Source topic
   - Consumer group ID
   - Action this feature takes
   - Idempotency strategy (typically processed-event-IDs table)
   - Retry / DLQ policy

2. Apply rules from `event-consuming.md` (loaded in step 06).

## Rules (scoped to this step)
- Use the codebase's existing topic naming convention; do NOT invent a new one.
- For published events, prefer transactional outbox pattern over direct emit (per `event-publishing.md`).
- For consumed events, idempotency is non-negotiable.
- PII in event payloads is forbidden (events outlive request lifecycle, go to Kafka log retention). Reference entity IDs, not full PII.

## User interaction
None.

## Output for downstream steps
- Event publishing decisions (topic names, schemas, triggers, idempotency)
- Event consuming decisions (topics, groups, idempotency, retry)

## Boundary
Event findings recorded (or skipped) → route to step 10: read steps/step-10-test-strategy.md`.