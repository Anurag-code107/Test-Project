package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.service.xtrm.XtrmApiClient.DeleteBankCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.DeleteBankResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parsing regression tests for {@link XtrmApiClientImpl#deleteBankBeneficiary}. Overriding the protected
 * {@code post(...)} lets us feed a canned XTRM body with no HTTP. Guards the false-503 bug where a genuine
 * 200 success (wrapped under "DeleteBankBeneficiary", not "…Response") was misread as a retryable failure,
 * leaving the bank deleted at XTRM but still present locally.
 */
class XtrmApiClientImplDeleteBankTest {

    private Map<?, ?> cannedResponse;

    private final XtrmApiClientImpl client = new XtrmApiClientImpl() {
        @Override
        protected Map<?, ?> post(String path, Map<String, Object> body) {
            return cannedResponse;
        }
    };

    @Test
    void deleteBank_recognizesSuccessUnderDeleteBankBeneficiaryEnvelope() {
        // Real XTRM shape: wrapped under "DeleteBankBeneficiary" (no "Response" suffix — same quirk as DeleteCard).
        cannedResponse = Map.of("DeleteBankBeneficiary", Map.of(
                "DeleteBankBeneficiaryResult", Map.of(
                        "OperationStatus", Map.of("Success", true))));

        DeleteBankResult result = client.deleteBankBeneficiary(new DeleteBankCommand("PAT26240089", "bene-1"));

        assertThat(result.success()).isTrue();
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void deleteBank_surfacesXtrmRejectionWithErrors() {
        cannedResponse = Map.of("DeleteBankBeneficiary", Map.of(
                "DeleteBankBeneficiaryResult", Map.of(
                        "OperationStatus", Map.of(
                                "Success", false,
                                "Errors", List.of("Beneficiary not found")))));

        DeleteBankResult result = client.deleteBankBeneficiary(new DeleteBankCommand("PAT26240089", "bene-1"));

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).contains("Beneficiary not found");
    }

    @Test
    void deleteBank_trulyUnrecognizedResponseIsRetryable() {
        // No OperationStatus anywhere → genuinely unrecognized → keep the local row (retryable).
        cannedResponse = Map.of("SomethingElse", Map.of("foo", "bar"));

        DeleteBankResult result = client.deleteBankBeneficiary(new DeleteBankCommand("PAT26240089", "bene-1"));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isTrue();
    }
}
