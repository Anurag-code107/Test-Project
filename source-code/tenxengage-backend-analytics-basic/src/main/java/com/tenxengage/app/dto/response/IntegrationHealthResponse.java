package com.tenxengage.app.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record IntegrationHealthResponse(
        String syncStatus,
        OffsetDateTime lastSyncAt,
        int failedSyncCount,
        List<WebhookLogEntry> recentWebhooks) {

    public static IntegrationHealthResponse from(String syncStatus, OffsetDateTime lastSyncAt, int failedSyncCount) {
        return new IntegrationHealthResponse(syncStatus, lastSyncAt, failedSyncCount, List.of());
    }
}
