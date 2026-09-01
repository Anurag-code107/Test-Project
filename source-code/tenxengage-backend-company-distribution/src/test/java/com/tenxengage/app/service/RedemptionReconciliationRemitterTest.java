package com.tenxengage.app.service;

import com.tenxengage.app.service.xtrm.XtrmApiClient;
import com.tenxengage.app.service.xtrm.XtrmCredentials;
import com.tenxengage.app.service.xtrm.XtrmRemitterResolver;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconciliation must ask the same question dispatch asks.
 *
 * <p>{@code CompanyDistributionDispatcher} deliberately parks an <em>ambiguous</em> payout in
 * {@code PROCESSING} and relies on reconciliation to settle it — "never release on an unknown outcome" is
 * the right call. But a reconciliation that polls as the platform for a transaction the company remitted
 * finds nothing on every run. The item never settles and the recipient's share stays reserved
 * indefinitely, which is worse than either releasing or failing because nothing reports it.</p>
 *
 * <p>These are structural assertions on purpose. The behaviour they protect only manifests against a live
 * XTRM, and what actually goes wrong is someone reintroducing a second copy of the remitter logic — which a
 * mock-based test proving two mocks agree would not catch. {@code RedemptionReconciliationServiceTest}
 * covers the polling behaviour itself.</p>
 */
class RedemptionReconciliationRemitterTest {

    @Test
    void reconciliationDependsOnTheSharedRemitterResolver() {
        boolean usesResolver = Arrays.stream(RedemptionReconciliationService.class.getDeclaredFields())
                .anyMatch(f -> f.getType().equals(XtrmRemitterResolver.class));

        assertThat(usesResolver)
                .as("RedemptionReconciliationService must resolve the remitter through XtrmRemitterResolver — "
                        + "the same method XtrmVendorService uses, not its own copy of the logic")
                .isTrue();
    }

    @Test
    void dispatchDependsOnTheSameResolver() {
        boolean usesResolver = Arrays.stream(XtrmVendorService.class.getDeclaredFields())
                .anyMatch(f -> f.getType().equals(XtrmRemitterResolver.class));

        assertThat(usesResolver)
                .as("XtrmVendorService must resolve the remitter through the shared XtrmRemitterResolver")
                .isTrue();
    }

    @Test
    void theTransactionStatusApiAcceptsCredentials() throws NoSuchMethodException {
        Method m = XtrmApiClient.class.getMethod("getTransactionDetails",
                XtrmApiClient.GetTransactionDetailsCommand.class, XtrmCredentials.class);

        assertThat(m).isNotNull();
    }

    @Test
    void theBatchStatusApiAcceptsCredentials() throws NoSuchMethodException {
        Method m = XtrmApiClient.class.getMethod("getBatchStatus",
                XtrmApiClient.GetBatchStatusCommand.class, XtrmCredentials.class);

        assertThat(m).isNotNull();
    }
}
