package com.tenxengage.app.dto.response;

import java.util.UUID;

/**
 * A candidate recipient for a distribution, with per-rail readiness.
 *
 * <p>Ineligible sellers are returned rather than filtered out, carrying {@code ineligibleReason} so the UI can
 * grey the row and explain it. Hiding them would leave the admin wondering who is missing and why.</p>
 */
public record DistributionRecipientResponse(
        UUID userId,
        String fullName,
        String email,
        /** Whether the currently-selected rail can actually reach this person. */
        boolean eligible,
        /** Null when eligible; otherwise a reason phrased for the admin, e.g. "No bank account linked". */
        String ineligibleReason,
        /** Where the money would land — masked bank label, gift-card email, or {@code Cash wallet}. */
        String destination
) {
}
