package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

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

    private static final Logger log = LoggerFactory.getLogger(XoxodayVendorService.class);

    /**
     * TODO(US-06 BE-1): Replace this entire stub with the real Xoxoday order placement API call.
     * STUB — simulates a successful dispatch for local/dev testing only.
     * Sets a fake vendorReferenceId so the redemption flow can reach COMPLETED status.
     * Do NOT ship to production without a real implementation.
     */
    public void dispatch(RedemptionRequest request) {
        String fakeRef = "STUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        request.setVendorReferenceId(fakeRef);
        log.warn("[STUB] XoxodayVendorService.dispatch() — simulated success, vendorRef={}, redemptionId={}. " +
                 "Replace with real implementation in US-06 BE-1.", fakeRef, request.getId());
    }
}
