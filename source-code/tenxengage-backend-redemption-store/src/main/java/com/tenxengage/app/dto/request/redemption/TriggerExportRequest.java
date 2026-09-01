package com.tenxengage.app.dto.request.redemption;

import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.redemption.ExportFormat;
import com.tenxengage.app.entity.enums.redemption.ExportScope;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TriggerExportRequest(
        @NotNull ExportFormat format,
        LocalDate dateFrom,
        LocalDate dateTo,
        RedemptionStatus status,
        RedemptionCategory category,
        // Which tab the export was triggered from (Personal / Company / All-tenant). Honored
        // only up to the caller's permissions; null means "widest scope the caller is permitted".
        ExportScope scope,
        // ALL_TENANT-only name filters (mirror the All-Redemptions list search); ignored for
        // PERSONAL/COMPANY scope. Null/blank means "no filter".
        String userName,
        String companyName
) {
    // Backward-compatible constructor for callers (and tests) that predate the scope + name fields.
    public TriggerExportRequest(ExportFormat format, LocalDate dateFrom, LocalDate dateTo,
                                RedemptionStatus status, RedemptionCategory category) {
        this(format, dateFrom, dateTo, status, category, null, null, null);
    }

    // Backward-compatible constructor for callers that predate the name fields.
    public TriggerExportRequest(ExportFormat format, LocalDate dateFrom, LocalDate dateTo,
                                RedemptionStatus status, RedemptionCategory category, ExportScope scope) {
        this(format, dateFrom, dateTo, status, category, scope, null, null);
    }
}
