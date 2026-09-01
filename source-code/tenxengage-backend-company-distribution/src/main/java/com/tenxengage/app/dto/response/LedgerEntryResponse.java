package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.LedgerEntry;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
    UUID id,
    String entryType,
    String amount,
    String currencyId,
    String referenceType,
    UUID referenceId,
    String note,
    Instant createdAt
) {
    public static LedgerEntryResponse from(LedgerEntry e) {
        return new LedgerEntryResponse(
            e.getId(),
            e.getEntryType().name(),
            e.getAmount().toPlainString(),
            e.getCurrencyId(),
            e.getReferenceType(),
            e.getReferenceId(),
            e.getNote(),
            e.getCreatedAt()
        );
    }
}
