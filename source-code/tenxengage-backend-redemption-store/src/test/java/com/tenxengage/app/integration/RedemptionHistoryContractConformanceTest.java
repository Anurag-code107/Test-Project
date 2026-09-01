package com.tenxengage.app.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 — OpenAPI contract conformance for the redemption-history feature.
 * Pure YAML file validation — no DB or HTTP required.
 */
@Tag("integration")
class RedemptionHistoryContractConformanceTest {

    private static final Path CONTRACT =
            Paths.get("../tenxengage-contracts/endpoints/redemption-history.yaml");

    @Test
    void contractFileExists() throws IOException {
        assertThat(Files.exists(CONTRACT)).as("redemption-history.yaml must exist").isTrue();
        assertThat(Files.size(CONTRACT)).isGreaterThan(0);
    }

    @Test
    void personalHistory_getEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/requests");
        assertThat(yaml).contains("action.redemption.view_history");
    }

    @Test
    void personalHistoryResponse_requiredFieldsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("catalogItemName");
        assertThat(yaml).contains("completedAt");
    }

    @Test
    void personalHistoryDetail_linkedReturnIdDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("linkedReturnId");
        assertThat(yaml).contains("/api/v1/redemption/requests/{id}");
    }

    @Test
    void companyHistory_getEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/requests/company");
    }

    @Test
    void tenantHistory_allEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/requests/all");
        assertThat(yaml).contains("action.redemption.view_all_history");
    }

    @Test
    void tenantHistoryResponse_userAndCompanyFieldsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("userDisplayName");
        assertThat(yaml).contains("partnerCompanyName");
    }

    @Test
    void exportTrigger_postEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/requests/export");
        assertThat(yaml).contains("action.redemption.export");
    }

    @Test
    void exportJobStatus_getEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/requests/export/{jobId}");
    }

    @Test
    void exportJobDownload_getEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/requests/export/{jobId}/download");
        assertThat(yaml).contains("downloadUrl");
    }

    @Test
    void exportJobResponse_requiredFieldsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("jobId");
        assertThat(yaml).contains("rowCount");
        assertThat(yaml).contains("expiresAt");
    }
}
