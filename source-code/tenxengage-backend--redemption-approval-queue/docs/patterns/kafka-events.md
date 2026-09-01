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
