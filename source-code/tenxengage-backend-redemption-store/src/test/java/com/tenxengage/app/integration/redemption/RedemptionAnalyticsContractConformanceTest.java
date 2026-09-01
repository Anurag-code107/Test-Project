package com.tenxengage.app.integration.redemption;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 — OpenAPI contract conformance for the redemption analytics feature.
 *
 * Reads ../tenxengage-contracts/endpoints/redemption-analytics.yaml and verifies
 * that all declared paths, methods, permissions, schema fields, and error codes
 * exist in the contract. Pure file-based validation — no DB, no HTTP.
 *
 * Covers: T-01 through T-06 from test-plan.md.
 */
@Tag("integration")
class RedemptionAnalyticsContractConformanceTest {

    private static final Path CONTRACT =
            Paths.get("../tenxengage-contracts/endpoints/redemption-analytics.yaml");

    // ─── T-01: contract file and basic structure ──────────────────────────────

    @Test
    void contractFileExists() {
        assertThat(Files.exists(CONTRACT))
                .as("redemption-analytics.yaml should exist at %s", CONTRACT)
                .isTrue();
    }

    @Test
    void contractIsNotEmpty() throws IOException {
        assertThat(Files.size(CONTRACT)).isGreaterThan(0);
    }

    @Test
    void analyticsGet_endpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/analytics");
        assertThat(yaml).contains("get:");
        assertThat(yaml).contains("action.redemption.view_analytics");
    }

    @Test
    void analyticsGet_dateParamsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("dateFrom");
        assertThat(yaml).contains("dateTo");
    }

    @Test
    void analyticsGet_responseSchemaFields() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("RedemptionAnalyticsSummaryResponse");
        assertThat(yaml).contains("redemptionRates");
        assertThat(yaml).contains("unredeemedBalances");
        assertThat(yaml).contains("failedCancelledRates");
        assertThat(yaml).contains("totalRedemptionCount");
        assertThat(yaml).contains("dateWindow");
    }

    // ─── T-02: 400 for malformed date ────────────────────────────────────────

    @Test
    void analyticsGet_400ErrorDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("\"400\"");
        assertThat(yaml).contains("ErrorResponse");
    }

    // ─── T-03: 422 for invalid date range ────────────────────────────────────

    @Test
    void analyticsGet_422ErrorDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("\"422\"");
        assertThat(yaml).contains("dateTo");
    }

    // ─── T-04: export endpoint content-disposition and CSV type ──────────────

    @Test
    void exportGet_endpointDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("/api/v1/redemption/analytics/export");
    }

    @Test
    void exportGet_contentDispositionHeaderDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("Content-Disposition");
        assertThat(yaml).contains("attachment");
        assertThat(yaml).contains("redemption-unredeemed-balances.csv");
    }

    @Test
    void exportGet_csvColumnsDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("userId");
        assertThat(yaml).contains("userName");
        assertThat(yaml).contains("companyId");
        assertThat(yaml).contains("companyName");
        assertThat(yaml).contains("currencyType");
        assertThat(yaml).contains("availableBalance");
        assertThat(yaml).contains("reservedBalance");
    }

    @Test
    void exportGet_csvContentTypeDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("text/csv");
    }

    // ─── T-05: 403 for non-CLIENT_ADMIN on export ────────────────────────────

    @Test
    void exportGet_403ErrorDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        String exportSection = yaml.substring(yaml.indexOf("/api/v1/redemption/analytics/export"));
        assertThat(exportSection).contains("\"403\"");
    }

    // ─── T-06: 429 with Retry-After on rate limit ────────────────────────────

    @Test
    void exportGet_429WithRetryAfterDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("\"429\"");
        assertThat(yaml).contains("Retry-After");
    }

    @Test
    void exportGet_permissionDeclared() throws IOException {
        String yaml = Files.readString(CONTRACT);
        String exportSection = yaml.substring(yaml.indexOf("/api/v1/redemption/analytics/export"));
        assertThat(exportSection).contains("action.redemption.view_analytics");
    }

    // ─── Cross-cutting schema checks ─────────────────────────────────────────

    @Test
    void currencyTypeRateDto_schemaPresent() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("CurrencyTypeRateDto");
        assertThat(yaml).contains("ratePercentage");
        assertThat(yaml).contains("hasActivity");
        assertThat(yaml).contains("numerator");
        assertThat(yaml).contains("denominator");
    }

    @Test
    void currencyTypeBalanceDto_schemaPresent() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("CurrencyTypeBalanceDto");
        assertThat(yaml).contains("totalOutstanding");
    }

    @Test
    void redemptionCountDto_schemaPresent() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("RedemptionCountDto");
        assertThat(yaml).contains("byStatus");
    }

    @Test
    void errorResponse_schemaPresent() throws IOException {
        String yaml = Files.readString(CONTRACT);
        assertThat(yaml).contains("errorCode");
        assertThat(yaml).contains("errorMessage");
        assertThat(yaml).contains("status");
        assertThat(yaml).contains("timestamp");
        assertThat(yaml).contains("path");
    }
}
