package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class XtrmVendorService {

    private static final Logger log = LoggerFactory.getLogger(XtrmVendorService.class);

    @Value("${redemption.xtrm.base-url}")
    private String baseUrl;

    @Value("${redemption.xtrm.token-url}")
    private String tokenUrl;

    @Value("${redemption.xtrm.client-id}")
    private String clientId;

    @Value("${redemption.xtrm.client-secret}")
    private String clientSecret;

    @Value("${redemption.xtrm.issuer-account-number}")
    private String issuerAccountNumber;

    @Value("${redemption.xtrm.payment-method-id}")
    private String paymentMethodId;

    @Value("${redemption.xtrm.wallet-id}")
    private String walletId;

    @Value("${redemption.xtrm.program-id}")
    private String programId;

    private final UserRepository userRepository;
    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final RestClient restClient;

    // OAuth2 token cache
    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public XtrmVendorService(UserRepository userRepository,
                             RedemptionCatalogItemRepository catalogItemRepository) {
        this.userRepository = userRepository;
        this.catalogItemRepository = catalogItemRepository;
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

        RedemptionCatalogItem catalogItem = catalogItemRepository.findById(request.getCatalogItemId())
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", request.getCatalogItemId()));

        String sku = catalogItem.getProviderItemId();
        if (sku == null || sku.isBlank()) {
            throw new IllegalStateException(
                    "Catalog item " + request.getCatalogItemId() + " has no providerItemId (SKU) configured for XTRM");
        }

        Map<String, Object> transactionDetail = new LinkedHashMap<>();
        transactionDetail.put("IssuerTransactionId", request.getId().toString());
        transactionDetail.put("PaymentAmount", request.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        transactionDetail.put("RecipientUserID", recipientUserId);
        transactionDetail.put("UserGiftCardEmailID", user.getEmail());
        transactionDetail.put("SKU", sku);

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

        log.info("[step=xtrm-transfer-fund-request] redemptionId={}, recipientUserId={}, sku={}, amount={}, currency={}, issuerAccount={}",
                request.getId(), recipientUserId, sku,
                request.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                mapCurrency(request.getCurrencyId()), issuerAccountNumber);

        Map<?, ?> response = callXtrmApi(body);

        // TODO: remove before QA — temporary to identify XTRMTransactionID field name in sandbox response
        log.info("[step=xtrm-raw-response] redemptionId={}, response={}", request.getId(), response);

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
            Object xtrmTxId = ((Map<?, ?>) details.get(0)).get("PaymentTransactionId");
            if (xtrmTxId != null) {
                request.setVendorReferenceId(xtrmTxId.toString());
            }
        }

        log.info("[step=xtrm-transfer-fund-response] redemptionId={}, xtrmTransactionId={}, success=true",
                request.getId(), request.getVendorReferenceId());

        log.info("[step=xtrm-dispatch-success] redemptionId={}, vendorRef={}",
                request.getId(), request.getVendorReferenceId());
    }

    /**
     * Returns a valid OAuth2 Bearer token, fetching or refreshing as needed.
     * Token is cached and reused until 60 seconds before expiry.
     */
    synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedToken;
        }

        log.info("[step=xtrm-token-fetch] fetching new OAuth2 token from {}", tokenUrl);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<?, ?> tokenResponse = restClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        cachedToken = (String) tokenResponse.get("access_token");
        int expiresIn = tokenResponse.get("expires_in") instanceof Number n
                ? n.intValue()
                : 3600;
        tokenExpiry = Instant.now().plusSeconds(expiresIn);

        log.info("[step=xtrm-token-fetched] expires in {}s", expiresIn);
        return cachedToken;
    }

    protected Map<?, ?> callXtrmApi(Map<String, Object> body) {
        return restClient.post()
                .uri(baseUrl + "/API/v4/Fund/TransferFund")
                .header("Authorization", "Bearer " + getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                // XTRM returns error details inside the JSON body even on 4xx/5xx.
                // Suppress Spring's default exception so we can read OperationStatus.Success.
                .onStatus(status -> status.isError(), (req, res) -> { })
                .body(Map.class);
    }

    private String mapCurrency(String currencyId) {
        return switch (currencyId.toLowerCase()) {
            case "cash" -> "USD";
            default -> currencyId.toUpperCase();
        };
    }
}
