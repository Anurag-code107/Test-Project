# Kafka Events Pattern — Backend

This document describes how to produce Kafka events in the TenXEngage backend.
Every event producer follows the same structure: a Spring component that uses
`KafkaTemplate` to publish typed events, handles the asynchronous `CompletableFuture`,
and never swallows serialization failures.

## Event Classes

Event classes are plain `@Builder` classes. Status fields MUST use their corresponding
enum — never `String`.

```java
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CoursePublishedEvent {
    private UUID courseId;
    private UUID clientId;
    private EnablementCourseStatus previousStatus;  // enum, not String
    private EnablementCourseStatus newStatus;        // enum, not String
    private Instant occurredAt;
}
```

## Event Producer

Every event producer is a Spring `@Component` with `KafkaTemplate<String, String>` and
`ObjectMapper` injected via `@RequiredArgsConstructor`. It serializes to JSON and always
handles the `CompletableFuture` returned by `kafkaTemplate.send()`.

```java
@Component
@RequiredArgsConstructor
public class EnablementCourseEventProducer {

    private static final Logger log = LoggerFactory.getLogger(EnablementCourseEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishCoursePublished(CoursePublishedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("tenxengage.enablement.course.published", payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish CoursePublishedEvent courseId={}",
                                  event.getCourseId(), ex);
                    } else {
                        log.info("Published CoursePublishedEvent courseId={} offset={}",
                                  event.getCourseId(),
                                  result.getRecordMetadata().offset());
                    }
                });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize CoursePublishedEvent", e);
        }
    }
}
```

## Topic Registration

All topics must be defined as `NewTopic` beans in `KafkaConfig.java`. Never create
topics ad-hoc or outside this class.

```java
@Bean
public NewTopic coursePublishedTopic() {
    return TopicBuilder.name("tenxengage.enablement.course.published")
            .partitions(3)
            .replicas(1)
            .build();
}
```

## Pitfalls

- **Never discard the `CompletableFuture` from `kafkaTemplate.send()`** — a broker-level
  failure (network outage, topic misconfiguration) will be silently swallowed with no
  log and no exception. Always attach `.whenComplete()`.

- **Never swallow `JsonProcessingException`** — catching it and returning silently leaves
  the caller with no indication that the event was not published. Rethrow as
  `RuntimeException`, or implement an outbox pattern. Always pass the exception object
  to the logger: `log.error("...", e)` — not just `e.getMessage()`.

- **Always type status fields with their enum, never `String`** — using `String` allows
  invalid values (e.g., `"PROCESSING"` when the enum has no such constant) to be
  serialized and published to Kafka. Consumers will fail or silently deserialize garbage.
  Use the domain enum: `EnablementCourseStatus`, `EnablementImportStatus`, etc.

- **Outbox drain: never block the scheduler thread with `.get()` inside a row loop** — `kafkaTemplate.send(...).get(timeout)` is synchronous. Draining 100 rows at 5s timeout each can block the Spring scheduler thread for up to 500s, starving all other `@Scheduled` tasks. Fire all sends non-blocking first, then await as a batch (`CompletableFuture.allOf(...).get(deadline)`), or configure a dedicated `Executor` for the drain worker via `@Scheduled(scheduler = "outboxScheduler")`.

- **Outbox drain: use `SELECT ... FOR UPDATE SKIP LOCKED` to prevent duplicate publishes in multi-node deployments** — a plain `findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()` query lets all nodes read the same rows concurrently. Each node will fire the Kafka send for the same events. The `markPublished(...) WHERE publishedAt IS NULL` guard prevents double-marking but not duplicate sends. Use a native SQL query with `FOR UPDATE SKIP LOCKED` so each row is claimed by exactly one node, or wrap the drain in a distributed scheduling lock (e.g., ShedLock).

- **`@SchedulerLock` `lockAtMostFor` must exceed worst-case drain time** — setting `lockAtMostFor = "PT1M"` while draining up to 100 rows at 5s each (500s worst case) means the lock expires mid-drain. A second node acquires the lock and starts a new drain while the first transaction is still active and holding `FOR UPDATE SKIP LOCKED` row locks. Compute worst-case drain time as `batchSize × singleRowTimeout` and set `lockAtMostFor` above that ceiling (e.g., `PT10M` for a 100-row / 5s-timeout batch). Mismatched values silently allow overlapping drain windows during broker degradation — the scenario where ShedLock is most needed.

- **`@Scheduled` drain workers must catch and log exceptions** — Spring's scheduled task executor silently swallows any unchecked exception thrown by a `@Scheduled` method. Without a `try/catch`, a transient `DataAccessException` (DB restart, pool exhaustion) or a Kafka serialization error terminates the drain silently: the scheduler continues firing the method on every tick, all executions fail immediately, and the outbox fills up — with no log entry in the drain worker to trace the cause. Always wrap the drain call: `try { producer.drain(); } catch (Exception e) { log.error("step=drain_failed", e); }`.

- **`@RetryableTopic(autoCreateTopics = "false")` requires all retry topics to be pre-provisioned in `KafkaConfig`** — with auto-creation disabled (common in production environments with ACL-controlled brokers), Spring Kafka cannot create the retry/DLQ topics on demand. If the retry topic beans are missing, failed consumer invocations have nowhere to route and will either re-throw endlessly or silently drop messages, defeating the backoff/DLQ strategy. Always declare `NewTopic` beans for every retry and DLQ topic generated by `@RetryableTopic`'s naming strategy (suffix-with-index-value → `{topic}-0`, `{topic}-1`, …, `{topic}.dlq`), or set `autoCreateTopics = "true"` only with explicit operational approval and broker auto-topic-creation enabled.
