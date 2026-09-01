package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.CurrencyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SaveCurrencyRequest(
    @NotBlank @Size(max = 50) @Pattern(regexp = "^[a-z][a-z0-9_-]*$",
        message = "Code must be lowercase, start with a letter, and contain only letters, digits, hyphens, or underscores")
    String code,

    @NotBlank @Size(max = 100)
    String name,

    @NotNull
    CurrencyType type,

    BigDecimal conversionRate,

    @Size(max = 20)
    String unit,

    Boolean isCurrencyFormatted
) {
}
