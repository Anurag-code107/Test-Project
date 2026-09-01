package com.tenxengage.app.dto.response;

import java.util.UUID;

public record IneligibleIncentiveResponse(
    UUID incentiveId,
    String incentiveName,
    String reason
) {}
