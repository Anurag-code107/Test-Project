package com.tenxengage.app.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 — OpenAPI contract conformance for the redemption-returns feature.
 *
 * Reads ../tenxengage-contracts/endpoints/redemption-returns.yaml and verifies
 * that all declared paths, methods, and key schema fields are present.
 * No DB or HTTP required — pure file-based validation.
 */
@Tag("integration")
class RedemptionReturnContractIT {

    private static final Path CONTRACT =
            Paths.get("../tenxengage-contracts/endpoints/redemption-returns.yaml");

    @Test
    void contractFileExists() {
        assertThat(Files.exists(CONTRACT))
                .as("redemption-returns.yaml should exist at %s", CONTRACT)
                .isTrue();
    }

    @Test
    void contractIsNotEmpty() throws IOException {
        assertThat(Files.size(CONTRACT)).isGreaterThan(0);
    }

    // ─── Partner — POST /returns ───────────────────────────────────────────────

    @Test
    void partnerSubmitReturn_postEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/returns");
        assertThat(yaml).contains("post:");
        assertThat(yaml).contains("SubmitReturnRequest");
        assertThat(yaml).contains("\"201\"");
    }

    @Test
    void partnerSubmitReturn_validationErrorShape_declared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("\"400\"");
        assertThat(yaml).contains("ErrorResponse");
    }

    @Test
    void partnerSubmitReturn_eligibilityFailure_declared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("\"422\"");
        assertThat(yaml).contains("Return window");
    }

    @Test
    void partnerSubmitReturn_rateLimitDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("\"429\"");
        assertThat(yaml).contains("Rate limit");
    }

    @Test
    void partnerSubmitReturn_duplicateActiveReturn_409Declared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("\"409\"");
        assertThat(yaml).contains("active return");
    }

    // ─── Partner — GET /returns ────────────────────────────────────────────────

    @Test
    void partnerListReturns_getEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("PaginatedReturnSummaryResponse");
        assertThat(yaml).contains("ReturnSummaryResponse");
    }

    // ─── Partner — GET /returns/{id} ──────────────────────────────────────────

    @Test
    void partnerGetReturn_notFoundShape_declared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/returns/{id}");
        assertThat(yaml).contains("\"404\"");
    }

    // ─── Admin — POST /admin/returns/{id}/approve ─────────────────────────────

    @Test
    void adminApproveReturn_stateViolationShape_declared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/admin/returns/{id}/approve");
        assertThat(yaml).contains("PENDING_APPROVAL");
        assertThat(yaml).contains("optimistic locking");
    }

    // ─── Admin — GET /admin/returns ───────────────────────────────────────────

    @Test
    void adminListReturns_queueResponseShape_declared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/admin/returns");
        assertThat(yaml).contains("PaginatedReturnQueueResponse");
        assertThat(yaml).contains("ReturnQueueItemResponse");
    }

    // ─── Admin — POST /admin/returns/{id}/reject ──────────────────────────────

    @Test
    void adminRejectReturn_rejectionReasonValidation_declared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/admin/returns/{id}/reject");
        assertThat(yaml).contains("RejectReturnRequest");
        assertThat(yaml).contains("rejectionReason");
    }

    @Test
    void adminRejectReturn_rejectionReasonMaxLength_declared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("1000");
    }

    // ─── Webhook — POST /webhooks/redemption-returns/{vendor} ────────────────

    @Test
    void webhook_hmacFailureShape_declared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/webhooks/redemption-returns/{vendor}");
        assertThat(yaml).contains("X-Webhook-Signature");
        assertThat(yaml).contains("\"403\"");
        assertThat(yaml).contains("HMAC");
    }

    // ─── ReturnDetailResponse schema ──────────────────────────────────────────

    @Test
    void returnDetailResponse_requiredFieldsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("ReturnDetailResponse");
        assertThat(yaml).contains("reviewNotes");
        assertThat(yaml).contains("vendorReturnReference");
        assertThat(yaml).contains("approvedAt");
        assertThat(yaml).contains("confirmedAt");
        assertThat(yaml).contains("rejectedAt");
        assertThat(yaml).contains("cancelledAt");
        assertThat(yaml).contains("timedOutAt");
    }

    @Test
    void returnSummaryResponse_requiredFieldsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("ReturnSummaryResponse");
        assertThat(yaml).contains("catalogItemName");
        assertThat(yaml).contains("resolvedAt");
    }

    @Test
    void errorCodes_allPresent() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("\"400\"");
        assertThat(yaml).contains("\"401\"");
        assertThat(yaml).contains("\"403\"");
        assertThat(yaml).contains("\"404\"");
        assertThat(yaml).contains("\"409\"");
        assertThat(yaml).contains("\"422\"");
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    @Test
    void permissions_returnRequestAndReviewDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("action.redemption.return.request");
        assertThat(yaml).contains("action.redemption.return.review");
    }
}
