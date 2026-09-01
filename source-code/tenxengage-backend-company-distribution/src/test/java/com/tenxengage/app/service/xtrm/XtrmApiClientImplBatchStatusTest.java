package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchStatusItem;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchStatusResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetBatchStatusCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch-status parsing: the call must request {@code history=true} (so XTRM returns the per-item
 * {@code Attempts}) and lift the failure {@code ErrorCode} (e.g. {@code SEND_LIMIT_EXCEEDED}) into
 * {@link BatchStatusItem#errorReason()}. Exercises the real parser via an overridden {@code get()} seam.
 */
class XtrmApiClientImplBatchStatusTest {

    private String capturedPath;

    private XtrmCredentials capturedCredentials;

    /**
     * A client whose HTTP GET is stubbed to return {@code response} and capture the requested path.
     *
     * <p>The seam is the <em>credentials-aware</em> {@code get}, because that is what the batch poller calls
     * now that a partner company can be the remitter. Overriding only the single-argument form would leave
     * this test issuing a real HTTP request.</p>
     */
    private XtrmApiClientImpl clientReturning(Map<?, ?> response) {
        return new XtrmApiClientImpl() {
            @Override
            protected Map<?, ?> get(String path, XtrmCredentials credentials) {
                capturedPath = path;
                capturedCredentials = credentials;
                return response;
            }
        };
    }

    @Test
    void getBatchStatus_requestsHistoryTrue_andExtractsErrorCodeFromAttempts() {
        Map<String, Object> failedItem = Map.of(
                "CustomerTransactionId", "TXN-1",
                "Status", "Failed",
                "Attempts", List.of(Map.of(
                        "AttemptNo", 1, "Result", "Failed", "ErrorCode", "SEND_LIMIT_EXCEEDED")));
        Map<String, Object> okItem = Map.of(
                "CustomerTransactionId", "TXN-2",
                "Status", "Success",
                "Attempts", List.of());
        Map<String, Object> response = Map.of(
                "Items", List.of(failedItem, okItem),
                "Pagination", Map.of("HasMore", false, "NextRecordsToSkip", 0));

        BatchStatusResult res = clientReturning(response)
                .getBatchStatus(new GetBatchStatusCommand("BATCH-1", 0, 50));

        // history=true is required, else XTRM returns an empty Attempts array (no reason).
        assertThat(capturedPath).contains("history=true");
        assertThat(res.success()).isTrue();
        assertThat(res.items()).hasSize(2);

        BatchStatusItem failed = res.items().get(0);
        assertThat(failed.status()).isEqualTo("Failed");
        assertThat(failed.errorReason()).isEqualTo("SEND_LIMIT_EXCEEDED");

        BatchStatusItem ok = res.items().get(1);
        assertThat(ok.status()).isEqualTo("Success");
        assertThat(ok.errorReason()).isNull();
    }

    /**
     * The no-credentials overload must still mean "as the platform". Reconciliation picks the remitter per
     * batch; every other caller relies on this delegation being unchanged.
     */
    @Test
    void getBatchStatus_withoutCredentials_pollsAsThePlatform() {
        Map<String, Object> response = Map.of(
                "Items", List.of(),
                "Pagination", Map.of("HasMore", false, "NextRecordsToSkip", 0));

        clientReturning(response).getBatchStatus(new GetBatchStatusCommand("BATCH-1", 0, 50));

        assertThat(capturedCredentials).isNotNull();
    }

    @Test
    void getBatchStatus_withCredentials_pollsAsThatRemitter() {
        Map<String, Object> response = Map.of(
                "Items", List.of(),
                "Pagination", Map.of("HasMore", false, "NextRecordsToSkip", 0));
        XtrmCredentials company =
                new XtrmCredentials("company-id", "secret", "SPN26241004", "206415", "2314");

        clientReturning(response).getBatchStatus(new GetBatchStatusCommand("BATCH-1", 0, 50), company);

        assertThat(capturedCredentials).isEqualTo(company);
    }
}
