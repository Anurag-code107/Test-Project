package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.QuarterMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SaveFiscalYearConfigRequest(
    @NotBlank @Size(max = 20) String label,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull QuarterMethod quarterMethod,
    Integer quarterSize,
    @NotNull LocalDate q1StartDate,
    @NotNull LocalDate q1EndDate,
    @NotNull LocalDate q2StartDate,
    @NotNull LocalDate q2EndDate,
    @NotNull LocalDate q3StartDate,
    @NotNull LocalDate q3EndDate,
    @NotNull LocalDate q4StartDate,
    @NotNull LocalDate q4EndDate
) {
}
