package com.tenxengage.app.dto.response.redemption;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.WalletType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ApprovalQueueItemResponse(
        UUID id,
        String requesterDisplayName,
        String catalogItemName,
        String currencyId,
        BigDecimal amount,
        WalletType walletType,
        Instant submittedAt
) {
    public static ApprovalQueueItemResponse from(RedemptionRequest r) {
        String displayName = r.getUser() == null ? "(unknown)"
                : trim(r.getUser().getFirstName()) + " " + trim(r.getUser().getLastName());
        return new ApprovalQueueItemResponse(
                r.getId(),
                displayName,
                r.getCatalogItem() == null ? "(unknown)" : r.getCatalogItem().getName(),
                r.getCurrencyId(),
                r.getAmount(),
                r.getWalletType(),
                r.getSubmittedAt()
        );
    }

    private static String trim(String s) {
        return Objects.toString(s, "").trim();
    }
}
