package com.tenxengage.app.dto.request.redemption;

import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public record RedemptionHistoryFilters(
        RedemptionStatus status,
        RedemptionCategory category,
        LocalDate dateFrom,
        LocalDate dateTo
) {
    public static RedemptionHistoryFilters empty() {
        return new RedemptionHistoryFilters(null, null, null, null);
    }

    public Instant dateFromInstant() {
        return dateFrom != null ? dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }

    public Instant dateToInstant() {
        return dateTo != null ? dateTo.atTime(23, 59, 59).toInstant(ZoneOffset.UTC) : null;
    }
}
