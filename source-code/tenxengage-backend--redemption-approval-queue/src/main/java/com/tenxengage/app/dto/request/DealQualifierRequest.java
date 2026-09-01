package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DealQualifierRequest(
        @NotNull BigDecimal dealValue,
        @NotEmpty List<String> productSkus,
        @NotBlank String customerSegment,
        String region,
        @NotNull Instant closeDate
) {}
