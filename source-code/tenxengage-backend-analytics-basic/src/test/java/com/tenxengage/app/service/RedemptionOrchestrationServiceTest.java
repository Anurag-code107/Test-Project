package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.event.RedemptionEventPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedemptionOrchestrationServiceTest {

    @Mock private NotificationService notificationService;
    // STUB mocks — remove when US-05 BE-1 (XtrmVendorService) and US-06 BE-1 (XoxodayVendorService)
    // replace the vendor service stubs with real implementations.
    @Mock private XtrmVendorService xtrmVendorService;
    @Mock private XoxodayVendorService xoxodayVendorService;

    @InjectMocks private RedemptionOrchestrationService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("50.00");

    private RedemptionEventPayload event(String eventType) {
        return new RedemptionEventPayload(
                UUID.randomUUID(), eventType, Instant.now(),
                UUID.randomUUID(), REQUEST_ID, USER_ID,
                AMOUNT, "CASH", "INSTANT", "RESERVED", null);
    }

    private RedemptionEventPayload failedEvent(String failureReason) {
        return new RedemptionEventPayload(
                UUID.randomUUID(), "REDEMPTION_FAILED", Instant.now(),
                UUID.randomUUID(), REQUEST_ID, USER_ID,
                AMOUNT, "CASH", "INSTANT", "FAILED", failureReason);
    }

    private RedemptionRequest redemptionRequest(RedemptionCategory category) {
        RedemptionRequest r = RedemptionRequest.builder()
                .clientId(UUID.randomUUID())
                .userId(USER_ID)
                .walletId(UUID.randomUUID())
                .catalogItemId(UUID.randomUUID())
                .amount(AMOUNT)
                .currencyId("cash")
                .walletType(WalletType.INDIVIDUAL)
                .status(RedemptionStatus.PROCESSING)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(category)
                .submittedAt(Instant.now())
                .deleted(false)
                .build();
        r.setId(UUID.randomUUID());
        return r;
    }

    // --- notification dispatch tests (US-04) ---

    @Test
    void handleEvent_redemptionRequested_dispatchesSubmissionNotification() {
        service.dispatchNotification(event("REDEMPTION_REQUESTED"));

        verify(notificationService).sendRedemptionSubmitted(USER_ID, REQUEST_ID);
    }

    @Test
    void handleEvent_redemptionCompleted_dispatchesCompletionNotification() {
        service.dispatchNotification(event("REDEMPTION_COMPLETED"));

        verify(notificationService).sendRedemptionCompleted(USER_ID, REQUEST_ID, AMOUNT);
    }

    @Test
    void handleEvent_redemptionFailed_dispatchesFailureNotification() {
        RedemptionEventPayload payload = failedEvent("Insufficient funds at vendor");

        service.dispatchNotification(payload);

        verify(notificationService).sendRedemptionFailed(USER_ID, REQUEST_ID, "Insufficient funds at vendor");
    }

    @Test
    void handleEvent_unknownEventType_logsWarnAndSkips() {
        service.dispatchNotification(event("REDEMPTION_PROCESSING"));

        verify(notificationService, never()).sendRedemptionSubmitted(USER_ID, REQUEST_ID);
        verify(notificationService, never()).sendRedemptionCompleted(USER_ID, REQUEST_ID, AMOUNT);
        verify(notificationService, never()).sendRedemptionFailed(USER_ID, REQUEST_ID, null);
    }

    @Test
    void handleEvent_notificationServiceThrows_doesNotRethrow() {
        doThrow(new RuntimeException("Notification system down"))
                .when(notificationService).sendRedemptionSubmitted(USER_ID, REQUEST_ID);

        assertThatCode(() -> service.dispatchNotification(event("REDEMPTION_REQUESTED")))
                .doesNotThrowAnyException();
    }

    // --- routing tests (US-05 BE-2 + US-06 BE-2) ---
    // These verify routing logic only. The vendor services are stubs; replace mocks with
    // real assertions once US-05 BE-1 and US-06 BE-1 are implemented.

    @Test
    void dispatch_cashCategory_routesToXtrm() {
        RedemptionRequest cashRequest = redemptionRequest(RedemptionCategory.CASH);
        doNothing().when(xtrmVendorService).dispatch(cashRequest);

        service.dispatch(cashRequest);

        verify(xtrmVendorService).dispatch(cashRequest);
        verify(xoxodayVendorService, never()).dispatch(cashRequest);
    }

    @Test
    void dispatch_nonCashCategory_routesToXoxoday() {
        RedemptionRequest nonCashRequest = redemptionRequest(RedemptionCategory.NON_CASH);
        doNothing().when(xoxodayVendorService).dispatch(nonCashRequest);

        service.dispatch(nonCashRequest);

        verify(xoxodayVendorService).dispatch(nonCashRequest);
        verify(xtrmVendorService, never()).dispatch(nonCashRequest);
    }

    @Test
    void dispatch_cashCategory_doesNotRouteToXoxoday() {
        RedemptionRequest cashRequest = redemptionRequest(RedemptionCategory.CASH);
        doNothing().when(xtrmVendorService).dispatch(cashRequest);

        service.dispatch(cashRequest);

        verify(xoxodayVendorService, never()).dispatch(cashRequest);
    }

    @Test
    void dispatch_nonCashCategory_doesNotRouteToXtrm() {
        RedemptionRequest nonCashRequest = redemptionRequest(RedemptionCategory.NON_CASH);
        doNothing().when(xoxodayVendorService).dispatch(nonCashRequest);

        service.dispatch(nonCashRequest);

        verify(xtrmVendorService, never()).dispatch(nonCashRequest);
    }
}
