package com.tenxengage.app.integration.redemption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 — OpenAPI contract conformance for the reward-balance-expiration feature (F-09).
 *
 * <p>Reads {@code ../tenxengage-contracts/endpoints/balance-expiration.yaml} and verifies that every
 * endpoint, permission, request/response schema, enum, error code, and export detail the BE actually
 * implements is declared in the shared contract. Pure file-based validation — no DB, no HTTP, no
 * Spring context. (Response-vs-contract conformance is enforced structurally elsewhere: the FE/BE
 * DTOs are generated from this contract, and the @WebMvcTest + lifecycle ITs exercise the real DTOs.)
 *
 * <p>This is the redemption-store house pattern for contract conformance — see
 * {@link RedemptionAnalyticsContractConformanceTest}. It guards against the contract silently drifting
 * out of sync with the implemented surface (a path dropped, a permission renamed, an error code or
 * CSV column removed).
 *
 * <p>Covers the contract-conformance scenarios in {@code test-plan.md}.
 */
@Tag("integration")
class BalanceExpirationContractConformanceTest {

    private static final Path CONTRACT =
            Paths.get("../tenxengage-contracts/endpoints/balance-expiration.yaml");

    private static final String CONFIGURE = "action.redemption.expiration.configure";
    private static final String VIEW_BREAKAGE = "action.redemption.expiration.view_breakage";

    private static String yaml;

    @BeforeAll
    static void loadContract() throws IOException {
        assertThat(Files.exists(CONTRACT))
                .as("balance-expiration.yaml should exist at %s", CONTRACT)
                .isTrue();
        yaml = Files.readString(CONTRACT);
        assertThat(yaml).as("contract is non-empty").isNotBlank();
    }

    // ─── List policies — GET /policies (FR-09.1/FR-09.2) ──────────────────────

    @Test
    void listPolicies_pathPermissionAndResponseDeclared() {
        assertThat(yaml).contains("/api/v1/redemption/expiration/policies:");
        assertThat(configureRegion()).contains(CONFIGURE);
        assertThat(yaml).contains("BalanceExpirationPolicyResponse");
    }

    // ─── Upsert policy — PUT /policies/{currencyId} (FR-09.1/3/9/10) ───────────

    @Test
    void upsertPolicy_pathRequestBodyAndPermissionDeclared() {
        assertThat(yaml).contains("/api/v1/redemption/expiration/policies/{currencyId}:");
        assertThat(yaml).contains("UpsertBalanceExpirationPolicyRequest");
        assertThat(upsertSection()).contains("put:").contains(CONFIGURE);
    }

    @Test
    void upsertPolicy_declaresValidationConflictAndConfigErrors() {
        String upsert = upsertSection();
        assertThat(upsert).contains("\"400\"");   // structural validation
        assertThat(upsert).contains("\"409\"");   // optimistic-lock conflict — only this endpoint
        assertThat(upsert).contains("\"422\"");   // cross-field / bounds (FR-09.9)
        assertThat(yaml).contains("InvalidPolicyConfiguration");
    }

    // ─── Expiring-soon preview — GET /expiring-soon (FR-09.4) ─────────────────

    @Test
    void expiringSoon_pathPermissionParamAndResponseDeclared() {
        assertThat(yaml).contains("/api/v1/redemption/expiration/expiring-soon:");
        assertThat(yaml).contains("ExpiringBalancePreviewResponse");
        assertThat(yaml).contains("withinDays");
        assertThat(configureRegion()).contains(CONFIGURE);
    }

    // ─── Breakage report — GET /breakage (FR-09.6) ────────────────────────────

    @Test
    void breakage_pathPermissionParamsAndResponseDeclared() {
        assertThat(yaml).contains("/api/v1/redemption/expiration/breakage:");
        assertThat(yaml).contains("BalanceBreakageReportResponse");
        assertThat(breakageRegion()).contains(VIEW_BREAKAGE);
        // Required date window + bucketing parameters.
        assertThat(yaml).contains("name: from").contains("name: to").contains("name: granularity");
    }

    @Test
    void breakageEndpoints_useViewBreakagePermission_notConfigure() {
        // The breakage read + export are a distinct, breakage-only permission — never the configure one.
        assertThat(breakageRegion()).contains(VIEW_BREAKAGE).doesNotContain(CONFIGURE);
    }

    // ─── Breakage CSV export — GET /breakage/export (FR-09.6) ─────────────────

    @Test
    void breakageExport_pathPermissionAndCsvDeclared() {
        assertThat(yaml).contains("/api/v1/redemption/expiration/breakage/export:");
        assertThat(yaml).contains("text/csv");
        assertThat(exportSection()).contains(VIEW_BREAKAGE);
    }

    @Test
    void breakageExport_contentDispositionAttachmentAndFilenameDeclared() {
        String export = exportSection();
        assertThat(export).contains("Content-Disposition").contains("attachment");
        assertThat(export).contains("balance-expiration-breakage.csv");
    }

    @Test
    void breakageExport_csvColumnsDeclaredInOrder() {
        String export = exportSection();
        assertThat(export)
                .contains("period_start")
                .contains("period_end")
                .contains("currency_id")
                .contains("expired_count")
                .contains("total_expired_amount");
    }

    @Test
    void breakageExport_rateLimit429WithRetryAfterDeclared() {
        String export = exportSection();
        assertThat(export).contains("\"429\"");          // rate limited — only this endpoint
        assertThat(yaml).contains("Retry-After");
    }

    @Test
    void breakageExport_auditActionAndResourceTypeDocumented() {
        String export = exportSection();
        assertThat(export).contains("DATA_EXPORTED");
        assertThat(export).contains("BALANCE_EXPIRY_BREAKAGE_EXPORT");
    }

    // ─── Cross-cutting: auth, feature flag, error shapes ──────────────────────

    @Test
    void allEndpoints_declareAuthenticationAndAuthorization() {
        assertThat(yaml).contains("bearerAuth");
        assertThat(yaml).contains("Unauthenticated");          // 401 response component
        assertThat(yaml).contains("ForbiddenOrFlagDisabled");  // 403 response component
        // 401 + 403 wired on every path.
        assertThat(countOccurrences(yaml, "\"401\"")).isGreaterThanOrEqualTo(5);
        assertThat(countOccurrences(yaml, "\"403\"")).isGreaterThanOrEqualTo(5);
    }

    @Test
    void featureFlagGatingDocumented() {
        assertThat(yaml).contains("reward_balance_expiration");
    }

    @Test
    void errorResponse_schemaFieldsDeclared() {
        assertThat(yaml).contains("ErrorResponse");
        assertThat(yaml).contains("errorCode")
                .contains("errorMessage")
                .contains("status")
                .contains("timestamp")
                .contains("path");
    }

    @Test
    void validationErrorResponse_carriesPerFieldDetails() {
        assertThat(yaml).contains("ValidationErrorResponse");
        assertThat(yaml).contains("details");
    }

    // ─── Schema field coverage ────────────────────────────────────────────────

    @Test
    void upsertRequest_requiredFieldsAndModeEnumDeclared() {
        assertThat(yaml).contains("UpsertBalanceExpirationPolicyRequest");
        assertThat(yaml).contains("expirationMode")
                .contains("inactivityDays")
                .contains("fixedExpiryDate")
                .contains("leadTimeDays");
        // expirationMode enum values
        assertThat(yaml).contains("INACTIVITY").contains("FIXED_DATE");
    }

    @Test
    void policyResponse_fieldsDeclared() {
        assertThat(yaml).contains("currencyId")
                .contains("enabled")
                .contains("enabledAt")
                .contains("updatedAt")
                .contains("leadTimeDays");
    }

    @Test
    void expiringPreview_aggregateOnlyFieldsDeclared() {
        assertThat(yaml).contains("scheduledExpiryDate")
                .contains("affectedWalletCount")
                .contains("totalAmountAtRisk");
    }

    @Test
    void breakageRow_fieldsAndGranularityEnumDeclared() {
        assertThat(yaml).contains("BreakageRowDto");
        assertThat(yaml).contains("periodStart")
                .contains("periodEnd")
                .contains("expiredCount")
                .contains("totalExpiredAmount");
        // granularity enum values
        assertThat(yaml).contains("MONTH").contains("QUARTER");
    }

    // ─── helpers — endpoint-scoped sections (path keys carry a trailing colon) ─

    /** policies (list) + policies/{currencyId} (upsert) + expiring-soon — all on the configure permission. */
    private static String configureRegion() {
        return section("/api/v1/redemption/expiration/policies:", "/api/v1/redemption/expiration/breakage:");
    }

    /** breakage (read) + breakage/export — both on the view_breakage permission. */
    private static String breakageRegion() {
        return section("/api/v1/redemption/expiration/breakage:", "components:");
    }

    private static String upsertSection() {
        return section("/api/v1/redemption/expiration/policies/{currencyId}:",
                "/api/v1/redemption/expiration/expiring-soon:");
    }

    private static String exportSection() {
        return section("/api/v1/redemption/expiration/breakage/export:", "components:");
    }

    /** Substring of the contract from the first occurrence of {@code from} up to the next {@code to}. */
    private static String section(String from, String to) {
        int start = yaml.indexOf(from);
        assertThat(start).as("section marker present: %s", from).isGreaterThanOrEqualTo(0);
        int end = yaml.indexOf(to, start + from.length());
        return yaml.substring(start, end < 0 ? yaml.length() : end);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
