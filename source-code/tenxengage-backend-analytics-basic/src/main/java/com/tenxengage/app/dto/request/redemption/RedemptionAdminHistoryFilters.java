package com.tenxengage.app.dto.request.redemption;

import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionStatus;

import java.time.LocalDate;
import java.util.UUID;

public record RedemptionAdminHistoryFilters(
        RedemptionStatus status,
        RedemptionCategory category,
        LocalDate dateFrom,
        LocalDate dateTo,
        UUID userId,
        UUID companyId
) {
    public static RedemptionAdminHistoryFilters empty() {
        return new RedemptionAdminHistoryFilters(null, null, null, null, null, null);
    }

    public RedemptionHistoryFilters toBaseFilters() {
        return new RedemptionHistoryFilters(status, category, dateFrom, dateTo);
    }
}
