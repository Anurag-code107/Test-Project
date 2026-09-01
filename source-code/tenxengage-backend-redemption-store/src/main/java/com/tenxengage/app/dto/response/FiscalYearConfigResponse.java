package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.FiscalYearConfig;
import com.tenxengage.app.entity.enums.QuarterMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FiscalYearConfigResponse(
    UUID id,
    String label,
    LocalDate startDate,
    LocalDate endDate,
    QuarterMethod quarterMethod,
    Integer quarterSize,
    LocalDate q1StartDate,
    LocalDate q1EndDate,
    LocalDate q2StartDate,
    LocalDate q2EndDate,
    LocalDate q3StartDate,
    LocalDate q3EndDate,
    LocalDate q4StartDate,
    LocalDate q4EndDate,
    Instant createdAt,
    Instant updatedAt
) {

    public static FiscalYearConfigResponse from(FiscalYearConfig config) {
        return new FiscalYearConfigResponse(
            config.getId(),
            config.getLabel(),
            config.getStartDate(),
            config.getEndDate(),
            config.getQuarterMethod(),
            config.getQuarterSize(),
            config.getQ1StartDate(),
            config.getQ1EndDate(),
            config.getQ2StartDate(),
            config.getQ2EndDate(),
            config.getQ3StartDate(),
            config.getQ3EndDate(),
            config.getQ4StartDate(),
            config.getQ4EndDate(),
            config.getCreatedAt(),
            config.getUpdatedAt()
        );
    }
}
