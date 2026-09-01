package com.tenxengage.app.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-14 — OpenAPI contract conformance for all 3 redemption-catalog controllers.
 *
 * Reads ../tenxengage-contracts/endpoints/redemption-catalog.yaml and verifies
 * that all declared paths, methods, and key schema fields are present.
 * No DB or HTTP required — pure file-based validation following the ApiContractTest pattern.
 */
@Tag("integration")
class RedemptionCatalogContractConformanceTest {

    private static final Path CONTRACT = Paths.get("../tenxengage-contracts/endpoints/redemption-catalog.yaml");

    @Test
    void contractFileExists() {
        assertThat(Files.exists(CONTRACT))
                .as("redemption-catalog.yaml should exist at %s", CONTRACT)
                .isTrue();
    }

    @Test
    void contractIsNotEmpty() throws IOException {
        assertThat(Files.size(CONTRACT)).isGreaterThan(0);
    }

    // ─── Admin controller (RedemptionCatalogAdminController) ──────────────────

    @Test
    void adminController_listEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/admin/redemption-catalog");
        assertThat(yaml).contains("get:");
        assertThat(yaml).contains("action.redemption.catalog.manage");
    }

    @Test
    void adminController_createEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("post:");
        assertThat(yaml).contains("CreateRedemptionCatalogItemRequest");
        assertThat(yaml).contains("\"201\"");
    }

    @Test
    void adminController_activateDeactivateEndpointsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/activate");
        assertThat(yaml).contains("/deactivate");
        assertThat(yaml).contains("patch:");
    }

    @Test
    void adminController_syncEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/sync");
        assertThat(yaml).contains("\"202\"");
        assertThat(yaml).contains("jobId");
    }

    @Test
    void adminController_integrationHealthEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/integration-health");
        assertThat(yaml).contains("syncStatus");
        assertThat(yaml).contains("lastSyncAt");
        assertThat(yaml).contains("failedSyncCount");
    }

    // ─── Config controller (RedemptionConfigController) ───────────────────────

    @Test
    void configController_settingsEndpointsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/settings");
        assertThat(yaml).contains("batchCadence");
        assertThat(yaml).contains("action.redemption.configure");
    }

    @Test
    void configController_catalogConfigEndpointsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/catalog/config");
        assertThat(yaml).contains("UpsertClientCatalogItemConfigRequest");
        assertThat(yaml).contains("processingModeOverride");
    }

    @Test
    void configController_regionalConfigEndpointsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/regions/");
        assertThat(yaml).contains("regionCode");
        assertThat(yaml).contains("delete:");
        assertThat(yaml).contains("\"204\"");
    }

    // ─── Browse controller (RedemptionCatalogController) ──────────────────────

    @Test
    void browseController_catalogBrowseEndpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/catalog");
        assertThat(yaml).contains("module.redemption_store");
        assertThat(yaml).contains("canAfford");
        assertThat(yaml).contains("shortfallAmount");
        assertThat(yaml).contains("effectiveProcessingMode");
        assertThat(yaml).contains("estimatedPayoutTimeline");
    }

    // ─── Key schema fields ─────────────────────────────────────────────────────

    @Test
    void schema_catalogItemDetailResponseFields() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("RedemptionCatalogItemDetailResponse");
        assertThat(yaml).contains("providerItemId");
        assertThat(yaml).contains("geographicScope");
        assertThat(yaml).contains("defaultMinRedemptionAmount");
        assertThat(yaml).contains("xoxodayLastSyncedAt");
    }

    @Test
    void schema_errorResponseShape() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("\"400\"");
        assertThat(yaml).contains("\"401\"");
        assertThat(yaml).contains("\"403\"");
        assertThat(yaml).contains("\"422\"");
    }
}
