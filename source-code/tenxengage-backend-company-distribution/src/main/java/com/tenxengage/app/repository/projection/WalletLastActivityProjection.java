package com.tenxengage.app.repository.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection for the bulk last-activity query (AC-9): the most recent activity-type ledger
 * timestamp per wallet, computed in a single {@code GROUP BY} over a page of candidate wallets
 * instead of an N+1 per-wallet query.
 */
public interface WalletLastActivityProjection {
    UUID getWalletId();

    Instant getLastActivityAt();
}
