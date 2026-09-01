package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class XtrmVendorService {

    private static final Logger log = LoggerFactory.getLogger(XtrmVendorService.class);

    @Value("${redemption.xtrm.base-url}")
    private String baseUrl;

    @Value("${redemption.xtrm.auth-token}")
    private String authToken;

    @Value("${redemption.xtrm.issuer-account-number}")
    private String issuerAccountNumber;

    @Value("${redemption.xtrm.payment-method-id}")
    private String paymentMethodId;

    @Value("${redemption.xtrm.wallet-id}")
    private String walletId;

    @Value("${redemption.xtrm.program-id}")
    private String programId;

    private final UserRepository userRepository;
    private final RestClient restClient;

    public XtrmVendorService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.restClient = RestClient.create();
    }

    public void dispatch(RedemptionRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        String recipientUserId = user.getExternalUserId();
        if (recipientUserId == null || recipientUserId.isBlank()) {
            throw new IllegalStateException(
                    "User " + request.getUserId() + " has no XTRM externalUserId configured");
        }

        Map<String, Object> transactionDetail = new LinkedHashMap<>();
        transactionDetail.put("IssuerTransactionId", request.getId().toString());
        transactionDetail.put("PaymentAmount", request.getAmount().toPlainString());
        transactionDetail.put("RecipientUserID", recipientUserId);

        Map<String, Object> transaction = new LinkedHashMap<>();
        transaction.put("IssuerAccountNumber", issuerAccountNumber);
        transaction.put("PaymentType", "Personal");
        transaction.put("PaymentMethodID", paymentMethodId);
        transaction.put("WalletID", walletId);
        transaction.put("PaymentDescription", "Reward redemption");
        transaction.put("PaymentCurrency", mapCurrency(request.getCurrencyId()));
        transaction.put("EmailNotification", "true");
        transaction.put("TransactionDetails", List.of(transactionDetail));
        transaction.put("ProgramID", programId);

        Map<String, Object> body = Map.of(
                "TransferFund", Map.of(
                        "request", Map.of(
                                "Transaction", transaction)));

        log.info("[step=xtrm-dispatch] redemptionId={}, amount={}, currency={}",
                request.getId(), request.getAmount(), request.getCurrencyId());

        Map<?, ?> response = callXtrmApi(body);

        Map<?, ?> result = (Map<?, ?>) ((Map<?, ?>) response.get("TransferFundResponse"))
                .get("TransferFundResult");
        Map<?, ?> status = (Map<?, ?>) result.get("OperationStatus");
        Boolean success = (Boolean) status.get("Success");

        if (!Boolean.TRUE.equals(success)) {
            List<?> errors = (List<?>) status.get("Errors");
            String errorMsg = (errors != null && !errors.isEmpty())
                    ? errors.get(0).toString()
                    : "Unknown XTRM error";
            log.error("[step=xtrm-dispatch-failed] redemptionId={}, error={}", request.getId(), errorMsg);
            throw new RuntimeException("XTRM TransferFund failed: " + errorMsg);
        }

        // Store vendor reference ID from XTRM response if present
        List<?> details = (List<?>) result.get("TransactionDetail");
        if (details != null && !details.isEmpty()) {
            Object xtrmTxId = ((Map<?, ?>) details.get(0)).get("XTRMTransactionID");
            if (xtrmTxId != null) {
                request.setVendorReferenceId(xtrmTxId.toString());
            }
        }

        log.info("[step=xtrm-dispatch-success] redemptionId={}, vendorRef={}",
                request.getId(), request.getVendorReferenceId());
    }

    protected Map<?, ?> callXtrmApi(Map<String, Object> body) {
        return restClient.post()
                .uri(baseUrl + "/API/v4/Fund/TransferFund")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    private String mapCurrency(String currencyId) {
        return switch (currencyId.toLowerCase()) {
            case "cash" -> "USD";
            default -> currencyId.toUpperCase();
        };
    }
}
