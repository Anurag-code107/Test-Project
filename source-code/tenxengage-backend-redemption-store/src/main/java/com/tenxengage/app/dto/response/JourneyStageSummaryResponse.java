package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;

public record JourneyStageSummaryResponse(
    int sortOrder,
    IncentiveType incentiveType,
    String incentiveName,
    String incentiveDescription,
    IncentiveStatus incentiveStatus,
    Boolean userCompleted
) {
}
