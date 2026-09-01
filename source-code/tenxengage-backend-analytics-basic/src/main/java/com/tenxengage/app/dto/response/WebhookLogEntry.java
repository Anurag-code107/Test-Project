package com.tenxengage.app.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WebhookLogEntry(UUID id, String vendor, String eventType, String status, OffsetDateTime receivedAt) {
}
