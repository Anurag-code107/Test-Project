package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionRequest;
import org.springframework.stereotype.Service;

/**
 * STUB — US-06 BE-1. Remove this entire class and replace with a full implementation
 * when Xoxoday API credentials are provided.
 *
 * The real implementation must:
 *   - Assemble the Xoxoday order placement payload using the catalog item's Xoxoday
 *     product code, amount, and recipient details — no credentials stored on platform
 *   - Call the Xoxoday order placement API via HTTP client (RestClient / WebClient)
 *   - Use Spring Retry @Retryable with exponential backoff
 *   - On @Recover (permanent failure): write RELEASE ledger entry, set status=FAILED,
 *     publish REDEMPTION_FAILED Kafka event
 *   - Store vendorReferenceId from the Xoxoday response on the RedemptionRequest on success
 *
 * See US-06 BE-1 in tenxengage-blueprint/features/redemption-flow/stories/ for full spec.
 */
@Service
public class XoxodayVendorService {

    /**
     * STUB — replace with real Xoxoday order placement API call when US-06 BE-1 is unblocked.
     * Remove this method body and implement retry + recover logic at that point.
     */
    public void dispatch(RedemptionRequest request) {
        throw new UnsupportedOperationException(
                "XoxodayVendorService.dispatch() not yet implemented — blocked on Xoxoday credentials (US-06 BE-1)");
    }
}
