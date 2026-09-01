package com.tenxengage.app.dto.request.xtrm;

import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import jakarta.validation.constraints.NotNull;

/**
 * Request to set the current user's payout rail. {@code payoutMethod} is a typed enum, so an unknown
 * value fails JSON binding (400) and a null fails {@code @NotNull} (400); the service further rejects
 * {@code BANK} without a linked bank ({@code BANK_NOT_LINKED}, 422).
 */
public record SetPayoutMethodRequest(

        @NotNull
        RedemptionPayoutMethod payoutMethod
) {
}
