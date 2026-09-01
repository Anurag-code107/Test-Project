package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.FiscalYearConfig;

import java.time.LocalDate;

public record FiscalYearLabelResponse(
    String label,
    LocalDate startDate,
    LocalDate endDate
) {

    public static FiscalYearLabelResponse from(FiscalYearConfig config) {
        return new FiscalYearLabelResponse(
            config.getLabel(),
            config.getStartDate(),
            config.getEndDate()
        );
    }
}
