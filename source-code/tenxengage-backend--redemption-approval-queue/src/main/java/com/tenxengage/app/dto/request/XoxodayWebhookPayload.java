package com.tenxengage.app.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PLACEHOLDER — US-07 BE-1. Field names and types here are guesses based on common webhook
 * conventions. Replace every @JsonProperty value with the actual Xoxoday callback JSON field
 * names once Xoxoday API credentials are available and the order callback shape is confirmed.
 *
 * Do NOT ship this DTO to production until field names are verified against real Xoxoday payloads.
 * See US-07 BE-1 in tenxengage-blueprint/features/redemption-flow/stories/ for the full spec.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record XoxodayWebhookPayload(

        // PLACEHOLDER — confirm actual Xoxoday field name for idempotency key (US-07 BE-1)
        @JsonProperty("idempotency_key") String idempotencyKey,

        // PLACEHOLDER — the UUID we sent to Xoxoday as the order reference; confirm field name (US-07 BE-1)
        @JsonProperty("redemption_reference_id") String redemptionReferenceId,

        // PLACEHOLDER — confirm how Xoxoday signals fulfillment vs failure (field name + values) (US-07 BE-1)
        @JsonProperty("event_type") String eventType,

        // PLACEHOLDER — confirm Xoxoday failure reason field name (US-07 BE-1)
        @JsonProperty("failure_reason") String failureReason
) {}
