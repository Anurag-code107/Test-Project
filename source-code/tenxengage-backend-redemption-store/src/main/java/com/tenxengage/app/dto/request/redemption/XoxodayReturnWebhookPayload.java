package com.tenxengage.app.dto.request.redemption;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Internal parsing record for inbound Xoxoday return webhook payloads.
 * NOT a response DTO — used only to parse the raw JSON body in ReturnWebhookController.
 * Fields are kept at structural minimum: vendorReturnReference identifies the return,
 * confirmed drives the RETURN_CONFIRMED / RETURN_REJECTED branch, failureReason carries
 * the vendor rejection message when confirmed=false.
 *
 * Note: @Valid is NOT triggered on this record at the controller layer (raw-body HMAC approach).
 * Constraints here are advisory and enforced defensively in ReturnService.processVendorConfirmation().
 */
public record XoxodayReturnWebhookPayload(
        @NotBlank @Size(max = 255) String vendorReturnReference,
        boolean confirmed,
        @Size(max = 1000) String failureReason
) {
}
