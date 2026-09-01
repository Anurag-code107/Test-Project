package com.tenxengage.app.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 — OpenAPI contract conformance for the redemption approval queue.
 *
 * Reads ../tenxengage-contracts/endpoints/redemption-approval-queue.yaml and verifies
 * that all declared paths, methods, and key schema fields are present.
 * No DB or HTTP required — pure file-based validation.
 */
@Tag("integration")
class RedemptionApprovalQueueContractConformanceTest {

    private static final Path CONTRACT =
            Paths.get("../tenxengage-contracts/endpoints/redemption-approval-queue.yaml");

    @Test
    void contractFileExists() {
        assertThat(Files.exists(CONTRACT))
                .as("redemption-approval-queue.yaml should exist at %s", CONTRACT)
                .isTrue();
    }

    @Test
    void contractIsNotEmpty() throws IOException {
        assertThat(Files.size(CONTRACT)).isGreaterThan(0);
    }

    // ─── Approval Queue GET ───────────────────────────────────────────────────

    @Test
    void approvalQueue_getEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/requests/approval-queue");
        assertThat(yaml).contains("get:");
        assertThat(yaml).contains("action.redemption.approve");
    }

    @Test
    void approvalQueue_requestTypeFilterDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("requestType");
        assertThat(yaml).contains("REDEMPTION");
        assertThat(yaml).contains("RETURN");
    }

    // ─── Approve / Reject POSTs ───────────────────────────────────────────────

    @Test
    void approveEndpoint_declared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/requests/{id}/approve");
        assertThat(yaml).contains("PENDING_APPROVAL");
        assertThat(yaml).contains("\"409\"");
    }

    @Test
    void rejectEndpoint_declared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/requests/{id}/reject");
        assertThat(yaml).contains("RejectRedemptionRequest");
        assertThat(yaml).contains("rejectionReason");
    }

    // ─── Error codes ──────────────────────────────────────────────────────────

    @Test
    void errorCodesComplete() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("\"400\"");
        assertThat(yaml).contains("\"401\"");
        assertThat(yaml).contains("\"403\"");
        assertThat(yaml).contains("\"404\"");
        assertThat(yaml).contains("\"409\"");
    }

    // ─── Schema fields ────────────────────────────────────────────────────────

    @Test
    void approvalQueueItemResponse_requiredFieldsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("requesterDisplayName");
        assertThat(yaml).contains("catalogItemName");
        assertThat(yaml).contains("walletType");
        assertThat(yaml).contains("submittedAt");
    }

    @Test
    void redemptionRequestDetailResponse_approvalFieldsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("reviewedBy");
        assertThat(yaml).contains("reviewedAt");
        assertThat(yaml).contains("rejectionReason");
    }

    @Test
    void rejectRequest_rejectionReasonLengthConstraintDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("rejectionReason");
        assertThat(yaml).contains("1000");
    }
}
