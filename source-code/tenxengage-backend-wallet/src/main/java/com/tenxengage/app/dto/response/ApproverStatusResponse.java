package com.tenxengage.app.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ApproverStatusResponse(
    UUID id,
    String email,
    String category,
    Integer sortOrder,
    String decision,
    Instant decidedAt,
    String comment
) {}
