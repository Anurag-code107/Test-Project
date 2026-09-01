package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XtrmVendorServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RedemptionCatalogItemRepository catalogItemRepository;

    private XtrmVendorService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String EXTERNAL_USER_ID = "xtrm-user-abc123";
    private static final String VENDOR_REF_ID = "XTRM-TX-98765";

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new XtrmVendorService(userRepository, catalogItemRepository));
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.xtrm.test");
        ReflectionTestUtils.setField(service, "tokenUrl", "https://auth.xtrm.test/oAuth/token");
        ReflectionTestUtils.setField(service, "clientId", "test-client-id");
        ReflectionTestUtils.setField(service, "clientSecret", "test-client-secret");
        ReflectionTestUtils.setField(service, "issuerAccountNumber", "ISS-001");
        ReflectionTestUtils.setField(service, "paymentMethodId", "PM-001");
        ReflectionTestUtils.setField(service, "walletId", "WL-001");
        ReflectionTestUtils.setField(service, "programId", "PROG-001");
        Mockito.lenient().doReturn("test-token").when(service).getAccessToken();
    }

    @Test
    void dispatch_userNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dispatch(buildRequest("cash")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void dispatch_nullExternalUserId_throwsIllegalStateException() {
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().build()));

        assertThatThrownBy(() -> service.dispatch(buildRequest("cash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("XTRM externalUserId");
    }

    @Test
    void dispatch_blankExternalUserId_throwsIllegalStateException() {
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().externalUserId("   ").build()));

        assertThatThrownBy(() -> service.dispatch(buildRequest("cash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("XTRM externalUserId");
    }

    @Test
    void dispatch_success_setsVendorReferenceId() {
        stubUser();
        doReturn(successResponse(VENDOR_REF_ID)).when(service).callXtrmApi(any());

        RedemptionRequest request = buildRequest("cash");
        service.dispatch(request);

        assertThat(request.getVendorReferenceId()).isEqualTo(VENDOR_REF_ID);
    }

    @Test
    void dispatch_xtrmFailureWithErrors_throwsRuntimeException() {
        stubUser();
        doReturn(failureResponse(List.of("Insufficient vendor funds"))).when(service).callXtrmApi(any());

        assertThatThrownBy(() -> service.dispatch(buildRequest("cash")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient vendor funds");
    }

    @Test
    void dispatch_xtrmFailureNoErrors_throwsRuntimeExceptionWithUnknownMessage() {
        stubUser();
        doReturn(failureResponse(null)).when(service).callXtrmApi(any());

        assertThatThrownBy(() -> service.dispatch(buildRequest("cash")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unknown XTRM error");
    }

    @Test
    void dispatch_noTransactionDetail_vendorReferenceIdRemainsNull() {
        stubUser();
        doReturn(successResponseNoDetail()).when(service).callXtrmApi(any());

        RedemptionRequest request = buildRequest("cash");
        service.dispatch(request);

        assertThat(request.getVendorReferenceId()).isNull();
    }

    @Test
    void dispatch_cashCurrency_mapsToUSD() {
        stubUser();
        doReturn(successResponse(VENDOR_REF_ID)).when(service).callXtrmApi(any());

        RedemptionRequest request = buildRequest("cash");
        service.dispatch(request);

        assertThat(request.getVendorReferenceId()).isEqualTo(VENDOR_REF_ID);
    }

    @Test
    void dispatch_nonCashCurrency_mapsToUpperCase() {
        stubUser();
        doReturn(successResponse(VENDOR_REF_ID)).when(service).callXtrmApi(any());

        RedemptionRequest request = buildRequest("eur");
        service.dispatch(request);

        assertThat(request.getVendorReferenceId()).isEqualTo(VENDOR_REF_ID);
    }

    // ---- helpers ----

    private void stubUser() {
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder()
                        .externalUserId(EXTERNAL_USER_ID)
                        .email("user@example.com")
                        .build()));
        when(catalogItemRepository.findById(any(UUID.class)))
                .thenReturn(Optional.of(RedemptionCatalogItem.builder()
                        .providerItemId("SKU-001")
                        .build()));
    }

    private RedemptionRequest buildRequest(String currencyId) {
        RedemptionRequest r = RedemptionRequest.builder()
                .userId(USER_ID)
                .walletId(UUID.randomUUID())
                .catalogItemId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currencyId(currencyId)
                .walletType(WalletType.INDIVIDUAL)
                .status(RedemptionStatus.PROCESSING)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(RedemptionCategory.CASH)
                .submittedAt(Instant.now())
                .deleted(false)
                .build();
        r.setId(REQUEST_ID);
        return r;
    }

    private Map<?, ?> successResponse(String txId) {
        return Map.of("TransferFundResponse", Map.of(
                "TransferFundResult", Map.of(
                        "OperationStatus", Map.of("Success", Boolean.TRUE),
                        "TransactionDetail", List.of(Map.of("XTRMTransactionID", txId)))));
    }

    private Map<?, ?> successResponseNoDetail() {
        return Map.of("TransferFundResponse", Map.of(
                "TransferFundResult", Map.of(
                        "OperationStatus", Map.of("Success", Boolean.TRUE))));
    }

    private Map<?, ?> failureResponse(List<?> errors) {
        LinkedHashMap<String, Object> opStatus = new LinkedHashMap<>();
        opStatus.put("Success", Boolean.FALSE);
        if (errors != null) {
            opStatus.put("Errors", errors);
        }
        return Map.of("TransferFundResponse", Map.of(
                "TransferFundResult", Map.of("OperationStatus", opStatus)));
    }
}
