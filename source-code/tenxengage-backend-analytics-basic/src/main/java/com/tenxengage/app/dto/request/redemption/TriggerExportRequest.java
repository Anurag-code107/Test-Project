package com.tenxengage.app.dto.request.redemption;

import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.redemption.ExportFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TriggerExportRequest(
        @NotNull ExportFormat format,
        LocalDate dateFrom,
        LocalDate dateTo,
        RedemptionStatus status,
        RedemptionCategory category
) {
}
