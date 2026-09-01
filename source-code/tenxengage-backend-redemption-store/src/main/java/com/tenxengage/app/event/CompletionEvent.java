package com.tenxengage.app.event;

import com.tenxengage.app.entity.enums.IncentiveType;
import java.time.Instant;
import java.util.UUID;

public record CompletionEvent(
    UUID clientId,
    UUID userId,
    UUID incentiveId,
    UUID completionId,
    IncentiveType incentiveType,
    Instant completedAt
) {}
