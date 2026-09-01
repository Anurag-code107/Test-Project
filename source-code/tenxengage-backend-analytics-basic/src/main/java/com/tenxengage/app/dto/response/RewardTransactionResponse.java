package com.tenxengage.app.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RewardTransactionResponse(
    UUID id,
    Instant date,
    String type,
    String currencyId,
    String amount,
    UUID incentiveId,
    String incentiveName,
    UUID claimActionId,
    String purchaseOrderNumber
) {
}
