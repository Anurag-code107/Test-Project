package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CurrencyResponse(
    UUID id,
    String code,
    String name,
    String type,
    BigDecimal conversionRate,
    String unit,
    boolean isCurrencyFormatted,
    boolean isDefault,
    Instant createdAt,
    Instant updatedAt
) {

    public static CurrencyResponse from(Currency c) {
        return new CurrencyResponse(
            c.getId(),
            c.getCode(),
            c.getName(),
            c.getType().name(),
            c.getConversionRate(),
            c.getUnit(),
            c.isCurrencyFormatted(),
            c.isDefault(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }
}
