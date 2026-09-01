package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateBeneficiaryResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parsing {@code CreateBeneficiary}.
 *
 * <p>This is the only call that ever hands us a company's {@code SecretKey}, and it hands it over once.
 * Misparsing it is unrecoverable: the account exists at XTRM, the company name is taken so the call cannot
 * be replayed, and there is no endpoint to read the secret back. The parse is split out from the HTTP call
 * for exactly that reason — it is the part worth testing directly.</p>
 */
class XtrmApiClientImplCreateBeneficiaryTest {

    private final XtrmApiClientImpl client = new XtrmApiClientImpl();

    private Map<String, Object> sandboxResponse() {
        return Map.of("CreateBeneficiaryResponse", Map.of("CreateBeneficiaryResult", Map.of(
                "BeneficiaryID", "SPN26241004",
                "AccountIdentityLevel", "Basic",
                "ClientID", "2696718_API_User",
                "SecretKey", "a-secret",
                "OperationStatus", Map.of("Success", true, "Errors", List.of()))));
    }

    @Test
    void parsesTheSandboxResponse() {
        CreateBeneficiaryResult result = client.parseCreateBeneficiary(sandboxResponse());

        assertThat(result.success()).isTrue();
        assertThat(result.beneficiaryAccountNumber()).isEqualTo("SPN26241004");
        assertThat(result.clientId()).isEqualTo("2696718_API_User");
        assertThat(result.clientSecret()).isEqualTo("a-secret");
        assertThat(result.accountIdentityLevel()).isEqualTo("Basic");
    }

    @Test
    void failsWhenTheSecretIsMissingRatherThanReportingSuccess() {
        Map<String, Object> noSecret = Map.of("CreateBeneficiaryResponse", Map.of("CreateBeneficiaryResult", Map.of(
                "BeneficiaryID", "SPN26241004",
                "ClientID", "2696718_API_User",
                "OperationStatus", Map.of("Success", true, "Errors", List.of()))));

        CreateBeneficiaryResult result = client.parseCreateBeneficiary(noSecret);

        // An account now exists at XTRM that we can never authenticate as. Reporting success would store a
        // row that looks connectable and can never pay, and the only fix would be a support ticket.
        assertThat(result.success()).isFalse();
        // Not retryable: XTRM reported success, so the name is taken and a retry fails on the duplicate.
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void reportsAVendorRejection() {
        Map<String, Object> rejected = Map.of("CreateBeneficiaryResponse", Map.of("CreateBeneficiaryResult", Map.of(
                "OperationStatus", Map.of("Success", false, "Errors", List.of("Company name already exists")))));

        CreateBeneficiaryResult result = client.parseCreateBeneficiary(rejected);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).contains("Company name already exists");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void reportsAnUnrecognizedResponseAsRetryable() {
        CreateBeneficiaryResult result = client.parseCreateBeneficiary(Map.of("Something", "else"));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void reportsANullResponseAsRetryable() {
        CreateBeneficiaryResult result = client.parseCreateBeneficiary(null);

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isTrue();
    }

    /**
     * The result carries a live credential, so it must never print one — the payout path is exactly where
     * objects end up in log lines and exception messages.
     */
    @Test
    void doesNotPrintTheSecret() {
        CreateBeneficiaryResult result = client.parseCreateBeneficiary(sandboxResponse());

        assertThat(result.toString()).doesNotContain("a-secret");
    }
}
