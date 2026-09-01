package com.tenxengage.app.service.xtrm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real HTTP client for XTRM XAPI v4. Active in every profile except local/localtest/test
 * (those use {@link XtrmApiClientStub}) — mirrors the {@code XoxodayApiClientImpl} / {@code Stub} split.
 *
 * <p>Reuses the proven OAuth2 password-grant token cache + {@code RestClient} + {@code onStatus}
 * swallow-then-parse pattern already used by {@code XtrmVendorService.callXtrmApi}. XTRM returns error
 * details inside the JSON body even on 4xx/5xx, so we suppress Spring's default error handling and read
 * {@code OperationStatus.Success} ourselves.</p>
 *
 * <p><b>Field-name caveat (verify against sandbox before prod).</b> The {@code TransferFund} envelope +
 * response path are copied verbatim from the working {@code XtrmVendorService} call. The
 * {@code CreateUser} / {@code BatchTransfer} / {@code LinkBankBeneficiary} envelopes and endpoint paths
 * follow the same XAPI v4 convention but their exact request/response field names were not pinned in
 * code at authoring time — the same situation as {@code XtrmWebhookPayload}. Parsing here is defensive
 * (tolerant of a small set of likely key names) and centralized so it is cheap to correct once the
 * sandbox responses are confirmed. In {@code localtest}/{@code test} the stub is used; {@code local} now runs this real client against the XTRM sandbox (profile flipped for local sandbox testing), so these field-name guesses matter in local too.</p>
 */
@Service
// Active in every profile EXCEPT localtest/test — which INCLUDES `local`: local dev/demo runs against the
// REAL XTRM sandbox on purpose (needs XTRM_CLIENT_ID/SECRET + IPv4, see build.gradle). Only localtest/test
// use the in-memory stub. To point local at the stub instead, add `local` here and to XtrmApiClientStub.
@Profile("!(localtest | test)")
public class XtrmApiClientImpl implements XtrmApiClient {

    private static final Logger log = LoggerFactory.getLogger(XtrmApiClientImpl.class);

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
    @Value("${redemption.xtrm.wallet-id}")
    private String walletId;
    @Value("${redemption.xtrm.program-id}")
    private String programId;

    private final RestClient restClient = RestClient.create();

    // OAuth2 token cache (reused until 60s before expiry)
    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    // ---------------------------------------------------------------------
    // CreateUser
    // ---------------------------------------------------------------------

    @Override
    public CreateUserResult createUser(CreateUserCommand cmd) {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("AddressLine1", cmd.addressLine1());
        putIfPresent(address, "AddressLine2", cmd.addressLine2());
        putIfPresent(address, "City", cmd.city());
        putIfPresent(address, "Region", cmd.region());
        putIfPresent(address, "PostalCode", cmd.postalCode());
        address.put("CountryISO2", cmd.countryIso2());

        // Sandbox-confirmed shape: fields sit directly in "request" (no "User" wrapper); names are
        // LegalFirstName / LegalLastName / EmailAddress; endpoint is /API/v4/Register/CreateUser.
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("IssuerAccountNumber", issuerAccountNumber);
        request.put("LegalFirstName", cmd.firstName());
        request.put("LegalLastName", cmd.lastName());
        request.put("EmailAddress", cmd.email());
        request.put("EmailNotification", "true");
        // XTRM requires a mobile on the user profile before UserWithdrawFund will run (it rejects a withdrawal
        // with "Mobile # is not available in the user profile" otherwise). MobilePhone = dial code + national.
        putIfPresent(request, "MobilePhone", PhoneDialCodes.mobilePhone(cmd.phoneCountryIso2(), cmd.phone()));
        request.put("Address", address);

        Map<String, Object> body = envelope("CreateUser", request);

        log.info("[step=xtrm_enroll] calling CreateUser");
        Map<?, ?> response;
        try {
            response = post("/API/v4/Register/CreateUser", body);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_enroll_failed] transport error calling CreateUser: {}", e.getClass().getSimpleName());
            return CreateUserResult.failed(List.of("Could not reach XTRM"), true);
        }

        Map<?, ?> result = unwrap(response, "CreateUserResponse", "CreateUserResult");
        if (result == null) {
            log.warn("[step=xtrm_enroll_failed] Unrecognized CreateUser response; top-level keys={}",
                    response == null ? "null" : response.keySet());
            return CreateUserResult.failed(List.of("Unrecognized XTRM CreateUser response"), true);
        }
        if (!isSuccess(result)) {
            return CreateUserResult.failed(errors(result), false);
        }
        // Sandbox-confirmed: UserID + AccountIdentityLevel sit directly on CreateUserResult, not nested under a "User" object.
        String recipientUserId = firstNonBlank(result, "UserID", "RecipientUserID", "PAT");
        String identityLevel = firstNonBlank(result, "AccountIdentityLevel", "IdentityLevel");
        if (isBlank(recipientUserId)) {
            return CreateUserResult.failed(List.of("XTRM CreateUser returned no recipient id"), true);
        }
        return CreateUserResult.ok(recipientUserId, identityLevel);
    }

    // ---------------------------------------------------------------------
    // UpdateUser (2-step OTP profile update)
    // ---------------------------------------------------------------------

    @Override
    public UpdateUserResult updateUser(UpdateUserCommand cmd) {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("AddressLine1", cmd.addressLine1());
        putIfPresent(address, "AddressLine2", cmd.addressLine2());
        putIfPresent(address, "City", cmd.city());
        putIfPresent(address, "Region", cmd.region());
        putIfPresent(address, "PostalCode", cmd.postalCode());
        address.put("CountryISO2", cmd.countryIso2());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("IssuerAccountNumber", issuerAccountNumber);
        request.put("UserId", cmd.recipientUserId()); // UpdateUser uses "UserId" (lowercase d), unlike other calls
        request.put("LegalFirstName", cmd.firstName());
        request.put("LegalLastName", cmd.lastName());
        request.put("Address", address);
        request.put("MobileCountryISO2", cmd.mobileCountryIso2());
        request.put("MobileNumber", cmd.mobileNumber());
        putIfPresent(request, "OTP", cmd.otp());

        Map<String, Object> body = envelope("UpdateUser", request);
        log.info("[step=xtrm_profile_update] calling UpdateUser withOtp={}", cmd.otp() != null);
        Map<?, ?> response;
        try {
            response = post("/API/v4/Register/UpdateUser", body);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_profile_update_failed] transport error on UpdateUser: {}", e.getClass().getSimpleName());
            return UpdateUserResult.failed(List.of("Could not reach XTRM"), true);
        }
        Map<?, ?> result = unwrap(response, "UpdateUserResponse", "UpdateUserResult");
        if (result == null) {
            log.warn("[step=xtrm_profile_update_failed] Unrecognized UpdateUser response; top-level keys={}",
                    response == null ? "null" : response.keySet());
            return UpdateUserResult.failed(List.of("Unrecognized XTRM response"), true);
        }
        if (!isSuccess(result)) {
            List<String> errs = errors(result);
            log.warn("[step=xtrm_profile_update_failed] XTRM rejected UpdateUser: {}", errs);
            return UpdateUserResult.failed(errs, isTransientBankError(errs));
        }
        // The 2-step is driven by whether we sent an OTP: no OTP → XTRM just dispatched one; OTP → applied.
        if (isBlank(cmd.otp())) {
            return UpdateUserResult.otpSent();
        }
        return UpdateUserResult.applied(
                firstNonBlank(result, "UserID", "UserId"),
                firstNonBlank(result, "AccountIdentityLevel", "IdentityLevel"));
    }

    // ---------------------------------------------------------------------
    // TransferFund
    // ---------------------------------------------------------------------

    @Override
    public TransferFundResult transferFund(TransferFundCommand cmd) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("IssuerTransactionId", cmd.issuerTransactionId());
        detail.put("PaymentAmount", amount(cmd.amount()));
        detail.put("RecipientUserID", cmd.recipientUserId());
        putIfPresent(detail, "UserLinkedBankID", cmd.partnerLinkedBankId());
        putIfPresent(detail, "CardToken", cmd.cardToken());
        putIfPresent(detail, "SKU", cmd.sku());                          // digital gift-card rail
        putIfPresent(detail, "UserGiftCardEmailID", cmd.giftCardEmail()); // digital gift-card rail

        Map<String, Object> transaction = baseTransaction(cmd.paymentMethodId(), cmd.currency(), cmd.description());
        transaction.put("TransactionDetails", List.of(detail));

        Map<String, Object> body = envelope("TransferFund", Map.of("Transaction", transaction));

        log.info("[step=xtrm_dispatch] TransferFund issuerTxn={}, amount={}, currency={}",
                cmd.issuerTransactionId(), cmd.amount(), cmd.currency());

        Map<?, ?> response;
        try {
            response = post("/API/v4/Fund/TransferFund", body);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_dispatch_failed] transport error on TransferFund issuerTxn={}: {}",
                    cmd.issuerTransactionId(), e.getClass().getSimpleName());
            return TransferFundResult.failed(List.of("Could not reach XTRM"), true);
        }

        Map<?, ?> result = unwrap(response, "TransferFundResponse", "TransferFundResult");
        if (result == null) {
            return TransferFundResult.failed(List.of("Unrecognized XTRM TransferFund response"), true);
        }
        if (!isSuccess(result)) {
            return TransferFundResult.failed(errors(result), false);
        }
        String txId = firstDetailTransactionId(result);
        String beneficiaryTxId = firstDetailField(result, "BeneficiaryTransactionId");
        return TransferFundResult.ok(txId, beneficiaryTxId);
    }

    // ---------------------------------------------------------------------
    // BatchTransfer
    // ---------------------------------------------------------------------

    @Override
    public BatchTransferResult batchTransfer(BatchTransferCommand cmd) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (BatchItem item : cmd.items()) {
            Map<String, Object> destination = new LinkedHashMap<>();
            if (!isBlank(item.bankBeneficiaryId())) {
                destination.put("BeneficiaryBankID", item.bankBeneficiaryId());
                destination.put("BeneficiaryBankPaymentMethod", "ACH");
            } else if (!isBlank(item.walletId())) {
                destination.put("WalletId", item.walletId());
            } else if (!isBlank(item.cardToken())) {
                destination.put("CardToken", item.cardToken());
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("CustomerTransactionId", item.customerTransactionId());
            entry.put("RecipientId", item.recipientUserId());
            entry.put("Amount", amount(item.amount()));
            entry.put("Destination", destination);
            entry.put("SendMethodId", item.sendMethodId());
            putIfPresent(entry, "Description", item.description());
            items.add(entry);
        }

        // Real XTRM BatchTransfer is a FLAT request (no Transaction wrapper); we supply CustomerBatchId +
        // per-item CustomerTransactionId, which XTRM echoes back on the batch status API.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("IssuerAccountNumber", issuerAccountNumber);
        body.put("SourceWalletId", walletId);
        body.put("CustomerBatchId", cmd.customerBatchId());
        body.put("EmailNotification", "true");
        body.put("Items", items);

        log.info("[step=xtrm_dispatch] BatchTransfer batchId={}, items={}", cmd.customerBatchId(), cmd.items().size());
        Map<?, ?> response;
        try {
            response = post("/API/v4/Fund/BatchTransfer", body);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_dispatch_failed] transport error on BatchTransfer batchId={}: {}",
                    cmd.customerBatchId(), e.getClass().getSimpleName());
            return BatchTransferResult.failed(List.of("Could not reach XTRM"), true);
        }

        Map<?, ?> result = asMap(response == null ? null : response.get("BatchTransferResponse"));
        if (result == null) {
            log.warn("[step=xtrm_dispatch_failed] Unrecognized BatchTransfer response; top-level keys={}",
                    response == null ? "null" : response.keySet());
            return BatchTransferResult.failed(List.of("Unrecognized XTRM response"), true);
        }
        // Batch-level Status is "Accepted" (queued); per-item Accepted[]/Rejected[] carry our CustomerTransactionId.
        List<BatchItemResult> outcomes = new ArrayList<>();
        for (Object o : asList(result.get("Accepted"))) {
            Map<?, ?> a = asMap(o);
            if (a != null) {
                outcomes.add(new BatchItemResult(str(a.get("CustomerTransactionId")), true, null));
            }
        }
        for (Object o : asList(result.get("Rejected"))) {
            Map<?, ?> r = asMap(o);
            if (r != null) {
                String err = firstNonBlank(r, "Error", "Reason", "Message", "FailureReason");
                outcomes.add(new BatchItemResult(str(r.get("CustomerTransactionId")), false,
                        err == null ? "Rejected by XTRM" : err));
            }
        }
        return BatchTransferResult.ok(outcomes);
    }

    @Override
    public TransactionStatusResult getTransactionDetails(GetTransactionDetailsCommand cmd) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("IssuerAccountNumber", issuerAccountNumber);
        request.put("TransactionID", cmd.transactionId());
        request.put("UserID", cmd.recipientUserId());
        // NOTE: request key is "GetUserTransactionDetails" / "Request" (capital R), despite the /Wallet/... path.
        Map<String, Object> body = Map.of("GetUserTransactionDetails", Map.of("Request", request));

        log.info("[step=xtrm_txn_status] GetUserWalletTransactionDetails txnId={}", cmd.transactionId());
        Map<?, ?> response;
        try {
            response = post("/API/v4/Wallet/GetUserWalletTransactionDetails", body);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_txn_status_failed] transport error txnId={}: {}",
                    cmd.transactionId(), e.getClass().getSimpleName());
            return TransactionStatusResult.error(true);
        }
        Map<?, ?> result = unwrap(response, "GetUserTransactionDetailsResponse", "GetUserTransactionDetailsResult");
        if (result == null) {
            log.warn("[step=xtrm_txn_status_unrecognized] txnId={} top-level keys={}",
                    cmd.transactionId(), response == null ? "null" : response.keySet());
            return TransactionStatusResult.error(true);
        }
        if (!isSuccess(result)) {
            // Success=false → transaction not found / invalid id (definitive, not a transient outage).
            log.info("[step=xtrm_txn_status_notfound] txnId={} errors={}", cmd.transactionId(), errors(result));
            return TransactionStatusResult.notFound();
        }
        return TransactionStatusResult.of(extractFieldValue(result, "Transaction Status"));
    }

    @Override
    public BatchStatusResult getBatchStatus(GetBatchStatusCommand cmd) {
        // history=true so the per-item Attempts array is returned — that's where XTRM puts the failure
        // ErrorCode (e.g. SEND_LIMIT_EXCEEDED). With history=false a failed item reports "Failed" with no reason.
        String path = "/API/v4/Fund/BatchTransfer/" + cmd.customerBatchId()
                + "?recordsToSkip=" + cmd.recordsToSkip()
                + "&recordsToTake=" + cmd.recordsToTake() + "&history=true";
        log.info("[step=xtrm_batch_status] batchId={} skip={}", cmd.customerBatchId(), cmd.recordsToSkip());
        Map<?, ?> response;
        try {
            response = get(path);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_batch_status_failed] transport error batchId={}: {}",
                    cmd.customerBatchId(), e.getClass().getSimpleName());
            return BatchStatusResult.failed(true);
        }
        if (response == null || !response.containsKey("Items")) {
            log.warn("[step=xtrm_batch_status_failed] Unrecognized batch status response; keys={}",
                    response == null ? "null" : response.keySet());
            return BatchStatusResult.failed(true);
        }
        List<BatchStatusItem> statusItems = new ArrayList<>();
        for (Object o : asList(response.get("Items"))) {
            Map<?, ?> it = asMap(o);
            if (it != null) {
                String txnId = str(it.get("CustomerTransactionId"));
                String status = str(it.get("Status"));
                String reason = latestAttemptError(it.get("Attempts"));
                if (reason != null) {
                    log.warn("[step=xtrm_batch_item_failed] batchId={} customerTxn={} status={} reason={}",
                            cmd.customerBatchId(), txnId, status, reason);
                }
                statusItems.add(new BatchStatusItem(txnId, status, reason));
            }
        }
        Map<?, ?> pagination = asMap(response.get("Pagination"));
        boolean hasMore = pagination != null && Boolean.TRUE.equals(pagination.get("HasMore"));
        int nextSkip = pagination != null && pagination.get("NextRecordsToSkip") instanceof Number n
                ? n.intValue() : 0;
        return BatchStatusResult.ok(statusItems, hasMore, nextSkip);
    }

    /**
     * The most-recent attempt's error code/reason from a batch item's {@code Attempts} array, or null when
     * there is none. With {@code history=true} XTRM populates Attempts with per-try outcomes; the last one
     * carries the failure {@code ErrorCode} (e.g. {@code SEND_LIMIT_EXCEEDED}).
     */
    private static String latestAttemptError(Object attempts) {
        if (attempts instanceof List<?> list) {
            for (int i = list.size() - 1; i >= 0; i--) {
                Map<?, ?> a = asMap(list.get(i));
                if (a != null) {
                    String code = firstNonBlank(a, "ErrorCode", "Error", "Reason", "FailureReason");
                    if (!isBlank(code)) {
                        return code;
                    }
                }
            }
        }
        return null;
    }

    /** Scan the nested {@code Field:[[{Name,Value}]]} array from GetUserWalletTransactionDetails for a field value. */
    private static String extractFieldValue(Map<?, ?> result, String fieldName) {
        for (Object outer : asList(result.get("Field"))) {
            for (Object inner : asList(outer)) {
                Map<?, ?> f = asMap(inner);
                if (f != null && fieldName.equals(str(f.get("Name")))) {
                    return str(f.get("Value"));
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // LinkBankBeneficiary
    // ---------------------------------------------------------------------

    @Override
    public LinkBankResult linkBankBeneficiary(LinkBankCommand cmd) {
        // Sandbox-confirmed shape: UserID (PAT) + IssuerAccountNumber at the request top; beneficiary split into
        // BeneficiaryDetails.BeneficiaryInformation (contact+address) and BankDetails.BeneficiaryBankInformation
        // (bank); endpoint /API/v4/Bank/LinkBankBeneficiary.
        Map<String, Object> beneficiaryInfo = new LinkedHashMap<>();
        beneficiaryInfo.put("ContactName", cmd.contactName());
        beneficiaryInfo.put("PhoneNumber", cmd.contactPhone());
        beneficiaryInfo.put("AddressLine1", cmd.addressLine1());
        putIfPresent(beneficiaryInfo, "AddressLine2", cmd.addressLine2());
        // XTRM mandates City/Region/PostalCode for the beneficiary (rejects with "… is Mandatory" otherwise).
        beneficiaryInfo.put("City", cmd.city());
        beneficiaryInfo.put("Region", cmd.region());
        beneficiaryInfo.put("PostalCode", cmd.postalCode());
        beneficiaryInfo.put("CountryISO2", cmd.countryIso2());

        Map<String, Object> bankInfo = new LinkedHashMap<>();
        bankInfo.put("InstitutionName", cmd.institutionName());
        bankInfo.put("Currency", "USD"); // LinkBankCommand carries no currency; sandbox issuer wallet is USD
        putIfPresent(bankInfo, "SWIFTBIC", cmd.swiftBic());
        bankInfo.put("AccountNumber", cmd.accountNumber());
        bankInfo.put("RoutingNumber", cmd.routingNumber());
        bankInfo.put("CountryISO2", cmd.countryIso2());
        bankInfo.put("WithdrawType", cmd.withdrawType());

        Map<String, Object> beneficiary = new LinkedHashMap<>();
        beneficiary.put("BeneficiaryDetails", Map.of("BeneficiaryInformation", beneficiaryInfo));
        beneficiary.put("BankDetails", Map.of("BeneficiaryBankInformation", bankInfo));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("IssuerAccountNumber", issuerAccountNumber);
        request.put("UserID", cmd.recipientUserId());
        request.put("Beneficiary", beneficiary);

        Map<String, Object> body = envelope("LinkBankBeneficiary", request);

        log.info("[step=xtrm_bank_linked] calling LinkBankBeneficiary");
        Map<?, ?> response;
        try {
            response = post("/API/v4/Bank/LinkBankBeneficiary", body);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_bank_link_failed] transport error on LinkBankBeneficiary: {}",
                    e.getClass().getSimpleName());
            return LinkBankResult.failed("XTRM_BANK_LINK_FAILED", List.of("Could not reach XTRM"), true);
        }

        Map<?, ?> result = unwrap(response, "LinkBankBeneficiaryResponse", "LinkBankBeneficiaryResult");
        if (result == null) {
            log.warn("[step=xtrm_bank_link_failed] Unrecognized LinkBankBeneficiary response; top-level keys={}",
                    response == null ? "null" : response.keySet());
            return LinkBankResult.failed("XTRM_BANK_LINK_FAILED", List.of("Unrecognized XTRM response"), true);
        }
        if (!isSuccess(result)) {
            List<String> errs = errors(result);
            log.warn("[step=xtrm_bank_link_failed] XTRM rejected LinkBankBeneficiary: {}", errs);
            // XTRM transient/internal errors ("temporary error … try again later") are retryable → 503, not a 422 "check your details".
            return LinkBankResult.failed(classifyBankError(errs), errs, isTransientBankError(errs));
        }
        // Sandbox-confirmed: BeneficiaryId sits directly on LinkBankBeneficiaryResult (no "Beneficiary" wrapper).
        String linkedBankId = firstNonBlank(result, "BeneficiaryId", "UserLinkedBankID", "BeneficiaryID");
        if (isBlank(linkedBankId)) {
            return LinkBankResult.failed("XTRM_BANK_LINK_FAILED", List.of("XTRM returned no bank reference"), true);
        }
        return LinkBankResult.ok(linkedBankId);
    }

    // ---------------------------------------------------------------------
    // DeleteBankBeneficiary
    // ---------------------------------------------------------------------

    @Override
    public DeleteBankResult deleteBankBeneficiary(DeleteBankCommand cmd) {
        // Sandbox-confirmed shape: PAT is sent as RecipientAccountNumber (XTRM's delete-specific field name),
        // and BeneficiaryBankID is the BeneficiaryId returned by LinkBankBeneficiary.
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("IssuerAccountNumber", issuerAccountNumber);
        request.put("RecipientAccountNumber", cmd.recipientUserId());
        request.put("BeneficiaryBankID", cmd.beneficiaryBankId());

        Map<String, Object> body = envelope("DeleteBankBeneficiary", request);

        log.info("[step=xtrm_bank_unlinked] calling DeleteBankBeneficiary");
        Map<?, ?> response;
        try {
            response = post("/API/v4/Bank/DeleteBankBeneficiary", body);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_bank_unlink_failed] transport error on DeleteBankBeneficiary: {}",
                    e.getClass().getSimpleName());
            return DeleteBankResult.failed(List.of("Could not reach XTRM"), true);
        }

        // XTRM wraps this result under "DeleteBankBeneficiary" (no "Response" suffix — the same quirk as
        // DeleteCard), so the exact-envelope unwrap used by the other calls misses it and read a genuine
        // 200 success as a retryable failure → a false 503 while the bank was actually deleted at XTRM.
        // Locate OperationStatus recursively instead — the same check the central call logger uses — so
        // success is recognized regardless of the envelope name.
        Map<?, ?> opStatus = findOperationStatus(response);
        if (opStatus == null) {
            log.warn("[step=xtrm_bank_unlink_failed] Unrecognized DeleteBankBeneficiary response; top-level keys={}",
                    response == null ? "null" : response.keySet());
            // Unrecognized shape → retryable so the caller keeps our row rather than losing sync with XTRM.
            return DeleteBankResult.failed(List.of("Unrecognized XTRM response"), true);
        }
        if (!Boolean.TRUE.equals(opStatus.get("Success"))) {
            List<String> errs = errorsFromStatus(opStatus);
            log.warn("[step=xtrm_bank_unlink_failed] XTRM rejected DeleteBankBeneficiary: {}", errs);
            return DeleteBankResult.failed(errs, isTransientBankError(errs));
        }
        return DeleteBankResult.ok();
    }

    // ---------------------------------------------------------------------
    // GetBeneficiaryWallets (view-only)
    // ---------------------------------------------------------------------

    @Override
    public GetWalletsResult getBeneficiaryWallets(GetWalletsCommand cmd) {
        // Sandbox-confirmed shape: PAT is sent as BeneficiaryAccountNumber for this call.
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("IssuerAccountNumber", issuerAccountNumber);
        request.put("BeneficiaryAccountNumber", cmd.recipientUserId());

        Map<String, Object> body = envelope("GetBeneficiaryWallets", request);

        log.info("[step=xtrm_wallets] calling GetBeneficiaryWallets");
        Map<?, ?> response;
        try {
            response = post("/API/v4/Wallet/GetBeneficiaryWallets", body);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_wallets_failed] transport error on GetBeneficiaryWallets: {}",
                    e.getClass().getSimpleName());
            return GetWalletsResult.failed(List.of("Could not reach XTRM"), true);
        }

        Map<?, ?> result = unwrap(response, "GetBeneficiaryWalletsResponse", "GetBeneficiaryWalletsResult");
        if (result == null) {
            log.warn("[step=xtrm_wallets_failed] Unrecognized GetBeneficiaryWallets response; top-level keys={}",
                    response == null ? "null" : response.keySet());
            return GetWalletsResult.failed(List.of("Unrecognized XTRM response"), true);
        }
        if (!isSuccess(result)) {
            List<String> errs = errors(result);
            log.warn("[step=xtrm_wallets_failed] XTRM rejected GetBeneficiaryWallets: {}", errs);
            return GetWalletsResult.failed(errs, isTransientBankError(errs));
        }

        List<WalletInfo> wallets = new ArrayList<>();
        // Sandbox-confirmed: Wallets[] each with ID (number), Name, Currency, Balance (number).
        for (Object o : asList(result.get("Wallets"))) {
            Map<?, ?> w = asMap(o);
            if (w == null) {
                continue;
            }
            wallets.add(new WalletInfo(
                    firstNonBlank(w, "ID", "WalletID", "Id"),
                    firstNonBlank(w, "Name", "WalletName"),
                    firstNonBlank(w, "Currency", "WalletCurrency"),
                    bigDecimal(w.get("Balance"))));
        }
        return GetWalletsResult.ok(wallets);
    }

    // ---------------------------------------------------------------------
    // LinkCard / DeleteCard (card instruments)
    // ---------------------------------------------------------------------

    @Override
    public LinkCardResult linkCard(LinkCardCommand cmd) {
        // ⚠️ PCI: the raw card is forwarded once and NEVER logged (no CardNo/cvv in any log line).
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("FirstName", cmd.firstName());
        card.put("LastName", cmd.lastName());
        card.put("NameOnCard", cmd.nameOnCard());
        card.put("CardType", cmd.cardType());
        card.put("CardNo", cmd.cardNumber());
        card.put("ExpMonth", cmd.expMonth());
        card.put("ExpYear", cmd.expYear());
        card.put("cvv", cmd.cvv());
        card.put("AddressLine1", cmd.addressLine1());
        putIfPresent(card, "AddressLine2", cmd.addressLine2());
        card.put("City", cmd.city());
        card.put("State", cmd.region());
        card.put("PostalCode", cmd.postalCode());
        card.put("CountryCode2", cmd.countryIso2());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("IssuerAccountNumber", issuerAccountNumber);
        request.put("UserID", cmd.recipientUserId());
        request.put("LinkCardType", "transfer");
        request.put("card", card);

        Map<String, Object> body = envelope("LinkCard", request);
        log.info("[step=xtrm_card_linked] calling LinkCard"); // never logs card fields
        Map<?, ?> response;
        try {
            response = post("/API/v4/Card/LinkCard", body);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_card_link_failed] transport error on LinkCard: {}", e.getClass().getSimpleName());
            return LinkCardResult.failed(List.of("Could not reach XTRM"), true);
        }
        Map<?, ?> result = unwrap(response, "LinkCardResponse", "LinkCardResult");
        if (result == null) {
            log.warn("[step=xtrm_card_link_failed] Unrecognized LinkCard response; top-level keys={}",
                    response == null ? "null" : response.keySet());
            return LinkCardResult.failed(List.of("Unrecognized XTRM response"), true);
        }
        if (!isSuccess(result)) {
            List<String> errs = errors(result);
            log.warn("[step=xtrm_card_link_failed] XTRM rejected LinkCard: {}", errs);
            return LinkCardResult.failed(errs, isTransientBankError(errs));
        }
        String cardToken = firstNonBlank(result, "CardToken");
        if (isBlank(cardToken)) {
            return LinkCardResult.failed(List.of("XTRM returned no card token"), true);
        }
        return LinkCardResult.ok(cardToken, firstNonBlank(result, "CardStatus"));
    }

    @Override
    public DeleteCardResult deleteCard(DeleteCardCommand cmd) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("IssuerAccountNumber", issuerAccountNumber);
        request.put("UserID", cmd.recipientUserId());
        request.put("CardToken", cmd.cardToken());

        Map<String, Object> body = envelope("DeleteCard", request);
        log.info("[step=xtrm_card_removed] calling DeleteCard");
        Map<?, ?> response;
        try {
            response = post("/API/v4/Card/DeleteCard", body);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_card_remove_failed] transport error on DeleteCard: {}", e.getClass().getSimpleName());
            return DeleteCardResult.failed(List.of("Could not reach XTRM"), true);
        }
        // ⚠️ DeleteCard envelope is DeleteCard.DeleteCardResult (not …Response.…Result).
        Map<?, ?> result = unwrap(response, "DeleteCard", "DeleteCardResult");
        if (result == null) {
            log.warn("[step=xtrm_card_remove_failed] Unrecognized DeleteCard response; top-level keys={}",
                    response == null ? "null" : response.keySet());
            return DeleteCardResult.failed(List.of("Unrecognized XTRM response"), true);
        }
        if (!isSuccess(result)) {
            List<String> errs = errors(result);
            log.warn("[step=xtrm_card_remove_failed] XTRM rejected DeleteCard: {}", errs);
            return DeleteCardResult.failed(errs, isTransientBankError(errs));
        }
        return DeleteCardResult.ok();
    }

    // ---------------------------------------------------------------------
    // UserWithdrawFund (wallet cash-out, 2-step OTP)
    // ---------------------------------------------------------------------

    @Override
    public UserWithdrawResult userWithdrawFund(UserWithdrawCommand cmd) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("IssuerAccountNumber", issuerAccountNumber);
        request.put("UserID", cmd.recipientUserId());
        request.put("Amount", amount(cmd.amount()));
        request.put("Currency", cmd.currency());
        request.put("PaymentMethodID", cmd.paymentMethodId());
        putIfPresent(request, "UserLinkedBankID", cmd.userLinkedBankId());
        putIfPresent(request, "CardToken", cmd.cardToken());
        putIfPresent(request, "BankPaymentMethod", cmd.bankPaymentMethod());
        putIfPresent(request, "OTP", cmd.otp());
        request.put("EmailNotification", "true");
        // OTP is sent to both channels; the profile now carries a mobile (CreateUser sends MobilePhone), so the
        // user reads the code from email or SMS. XTRM requires a mobile ON THE PROFILE for withdrawal regardless.
        request.put("SendTransferCodeToEmail", "true");
        request.put("SendTransferCodeToMobile", "true");
        request.put("description", isBlank(cmd.description()) ? "Withdraw Funds" : cmd.description());

        Map<String, Object> body = envelope("UserWithdrawFund", request);
        log.info("[step=xtrm_withdraw] calling UserWithdrawFund withOtp={}", cmd.otp() != null);
        Map<?, ?> response;
        try {
            response = post("/API/v4/Fund/UserWithdrawFund", body);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_withdraw_failed] transport error on UserWithdrawFund: {}", e.getClass().getSimpleName());
            return UserWithdrawResult.failed(List.of("Could not reach XTRM"), true);
        }
        Map<?, ?> result = unwrap(response, "UserWithdrawFundResponse", "UserWithdrawFundResult");
        if (result == null) {
            log.warn("[step=xtrm_withdraw_failed] Unrecognized UserWithdrawFund response; top-level keys={}",
                    response == null ? "null" : response.keySet());
            return UserWithdrawResult.failed(List.of("Unrecognized XTRM response"), true);
        }
        if (!isSuccess(result)) {
            List<String> errs = errors(result);
            log.warn("[step=xtrm_withdraw_failed] XTRM rejected UserWithdrawFund: {}", errs);
            return UserWithdrawResult.failed(errs, isTransientBankError(errs));
        }
        String txnId = firstNonBlank(result, "PaymentTransactionId");
        if (isBlank(txnId)) {
            // Initiate step: Success=true + "One Time Password sent" + null PaymentTransactionId.
            return UserWithdrawResult.otpSent();
        }
        return UserWithdrawResult.completed(
                txnId,
                firstNonBlank(result, "PaymentStatus"),
                bigDecimal(result.get("Amount")),
                bigDecimal(result.get("Fee")),
                bigDecimal(result.get("TotalAmount")),
                firstNonBlank(result, "Currency"));
    }

    @Override
    public GetDigitalGiftCardsResult getDigitalGiftCards(GetDigitalGiftCardsCommand cmd) {
        String currency = isBlank(cmd.currency()) ? "USD" : cmd.currency();
        Map<String, Object> req = Map.of(
                "GetGiftCards", Map.of(
                        "request", Map.of(
                                "IssuerAccountNumber", issuerAccountNumber,
                                "Currency", currency)));
        Map<?, ?> body;
        try {
            body = post("/API/v4/GiftCard/GetDigitalGiftCards", req);
        } catch (RuntimeException e) {
            log.warn("[step=xtrm_giftcards_failed] {}", e.getClass().getSimpleName());
            return GetDigitalGiftCardsResult.failed(java.util.List.of("XTRM_UNAVAILABLE"), true);
        }
        Map<?, ?> response = asMap(body == null ? null : body.get("GetGiftCardResponse"));
        Map<?, ?> result = asMap(response == null ? null : response.get("GetGiftCardResult"));
        Map<?, ?> op = asMap(result == null ? null : result.get("OperationStatus"));
        boolean ok = op == null || !(op.get("Success") instanceof Boolean b) || b;
        if (!ok) {
            log.warn("[step=xtrm_giftcards_error] errors={}", op.get("Errors"));
            return GetDigitalGiftCardsResult.failed(java.util.List.of("XTRM_ERROR"), false);
        }
        java.util.List<GiftCardCatalogItem> items = new java.util.ArrayList<>();
        Object giftCards = result == null ? null : result.get("GiftCard");
        if (giftCards instanceof java.util.List<?> brands) {
            for (Object brandObj : brands) {
                Map<?, ?> brand = asMap(brandObj);
                if (brand == null) {
                    continue;
                }
                String brandName = str(brand.get("brandName"));
                String brandImage = firstImageUrl(brand.get("imageUrls"));
                if (brand.get("items") instanceof java.util.List<?> its) {
                    for (Object itemObj : its) {
                        Map<?, ?> it = asMap(itemObj);
                        if (it == null) {
                            continue;
                        }
                        items.add(new GiftCardCatalogItem(
                                str(it.get("sku")), str(it.get("rewardName")), brandName, brandImage,
                                str(it.get("currencyCode")), str(it.get("valueType")),
                                str(it.get("rewardType")), str(it.get("status")),
                                bigDecimal(it.get("faceValue")), bigDecimal(it.get("minValue")),
                                bigDecimal(it.get("maxValue"))));
                    }
                }
            }
        }
        log.info("[step=xtrm_giftcards_loaded] items={}", items.size());
        return GetDigitalGiftCardsResult.ok(items);
    }

    /** First usable image URL from an XTRM {@code imageUrls} list (prefers a mid-size render). */
    private String firstImageUrl(Object imageUrls) {
        if (imageUrls instanceof java.util.List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
            for (String key : new String[]{"Img200W", "Img130w", "Img300W", "Img80W"}) {
                String v = str(m.get(key));
                if (!isBlank(v)) {
                    return v;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // HTTP + token
    // ---------------------------------------------------------------------

    protected Map<?, ?> post(String path, Map<String, Object> body) {
        log.info("[step=xtrm_call] POST {}", path);
        ResponseEntity<Map> resp = restClient.post()
                .uri(baseUrl + path)
                .header("Authorization", "Bearer " + getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                // XTRM returns error details inside the JSON body even on 4xx/5xx — read OperationStatus ourselves.
                .onStatus(status -> status.isError(), (req, res) -> { })
                .toEntity(Map.class);
        logXtrmResult("POST", path, resp);
        return resp.getBody();
    }

    protected Map<?, ?> get(String path) {
        log.info("[step=xtrm_call] GET {}", path);
        ResponseEntity<Map> resp = restClient.get()
                .uri(baseUrl + path)
                .header("Authorization", "Bearer " + getAccessToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> { })
                .toEntity(Map.class);
        logXtrmResult("GET", path, resp);
        return resp.getBody();
    }

    /**
     * Central XTRM call-result log: HTTP status plus, when the body carries an XTRM {@code OperationStatus},
     * whether it succeeded and any error text. PCI-safe — logs only the path, status and error descriptions,
     * never the request/response body (which may contain card data).
     */
    private void logXtrmResult(String method, String path, ResponseEntity<Map> resp) {
        int status = resp.getStatusCode().value();
        Map<?, ?> opStatus = findOperationStatus(resp.getBody());
        boolean bodyOk = opStatus == null || !(opStatus.get("Success") instanceof Boolean b) || b;
        if (resp.getStatusCode().is2xxSuccessful() && bodyOk) {
            log.info("[step=xtrm_result] {} {} status={} ok=true", method, path, status);
        } else {
            // Prefer XTRM's own error text. If OperationStatus could not be located (an unexpected
            // envelope — the class of bug that produced the false delete-bank 503), fall back to the
            // body's top-level keys (PCI-safe: key names only, never values) so the shape is still
            // diagnosable instead of logging an empty error.
            String errors = opStatus != null
                    ? String.valueOf(opStatus.get("Errors"))
                    : "(OperationStatus not found; top-level keys=" + keysOf(resp.getBody()) + ")";
            if (errors.length() > 500) {
                errors = errors.substring(0, 500);
            }
            log.warn("[step=xtrm_result] {} {} status={} ok=false errors={}", method, path, status, errors);
        }
    }

    /** PCI-safe body summary: top-level key names only (never values, which may carry card data). */
    private static String keysOf(Object body) {
        return body instanceof Map<?, ?> m ? m.keySet().toString() : "null";
    }

    /** Recursively locate the first XTRM {@code OperationStatus} map in a (possibly nested) response body. */
    private static Map<?, ?> findOperationStatus(Object node) {
        if (node instanceof Map<?, ?> m) {
            if (m.get("OperationStatus") instanceof Map<?, ?> osm) {
                return osm;
            }
            for (Object v : m.values()) {
                Map<?, ?> found = findOperationStatus(v);
                if (found != null) {
                    return found;
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object v : list) {
                Map<?, ?> found = findOperationStatus(v);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedToken;
        }
        log.info("[step=xtrm_token_fetch] fetching new OAuth2 token");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        // Read the body ourselves even on 4xx/5xx (onStatus no-op) so an OAuth failure such as
        // invalid_credentials surfaces XTRM's own {error, error_description} in the logs instead of
        // an opaque RestClientResponseException. Previously the token call threw un-logged, which is
        // why auth failures were hard to diagnose.
        ResponseEntity<Map> resp;
        try {
            resp = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> { })
                    .toEntity(Map.class);
        } catch (RuntimeException e) {
            log.error("[step=xtrm_token_failed] transport error fetching OAuth2 token: {}", e.getClass().getSimpleName());
            throw e;
        }

        Map<?, ?> tokenResponse = resp.getBody();
        String token = str(tokenResponse == null ? null : tokenResponse.get("access_token"));
        if (!resp.getStatusCode().is2xxSuccessful() || isBlank(token)) {
            // PCI/secret-safe: log ONLY the OAuth error fields + status — never the token, client_secret,
            // or full body.
            String error = str(tokenResponse == null ? null : tokenResponse.get("error"));
            String description = str(tokenResponse == null ? null : tokenResponse.get("error_description"));
            log.error("[step=xtrm_token_failed] OAuth2 token fetch failed status={} error={} error_description={}",
                    resp.getStatusCode().value(), error, description);
            throw new RuntimeException("XTRM OAuth token fetch failed (status=" + resp.getStatusCode().value()
                    + ", error=" + error + ")");
        }

        cachedToken = token;
        int expiresIn = (tokenResponse.get("expires_in") instanceof Number n) ? n.intValue() : 3600;
        tokenExpiry = Instant.now().plusSeconds(expiresIn);
        return cachedToken;
    }

    // ---------------------------------------------------------------------
    // Payload + parse helpers
    // ---------------------------------------------------------------------

    private Map<String, Object> envelope(String operation, Map<String, Object> request) {
        return Map.of(operation, Map.of("request", request));
    }

    private Map<String, Object> baseTransaction(String paymentMethodId, String currency, String description) {
        Map<String, Object> transaction = new LinkedHashMap<>();
        transaction.put("IssuerAccountNumber", issuerAccountNumber);
        transaction.put("PaymentType", "Personal");
        transaction.put("PaymentMethodID", paymentMethodId);
        transaction.put("WalletID", walletId);
        transaction.put("PaymentDescription", description == null ? "Reward redemption" : description);
        transaction.put("PaymentCurrency", currency);
        transaction.put("EmailNotification", "true");
        transaction.put("ProgramID", programId);
        return transaction;
    }

    private String amount(java.math.BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (!isBlank(value)) {
            map.put(key, value);
        }
    }

    /** Navigate {@code response -> outerKey -> innerKey}; null if any hop is missing/mistyped. */
    private static Map<?, ?> unwrap(Map<?, ?> response, String outerKey, String innerKey) {
        Map<?, ?> outer = asMap(response == null ? null : response.get(outerKey));
        return outer == null ? null : asMap(outer.get(innerKey));
    }

    private static boolean isSuccess(Map<?, ?> result) {
        Map<?, ?> status = asMap(result.get("OperationStatus"));
        return status != null && Boolean.TRUE.equals(status.get("Success"));
    }

    private static List<String> errors(Map<?, ?> result) {
        return errorsFromStatus(asMap(result.get("OperationStatus")));
    }

    /** Extract error strings from an already-located OperationStatus map (used by the recursive path). */
    private static List<String> errorsFromStatus(Map<?, ?> status) {
        List<String> out = new ArrayList<>();
        if (status != null) {
            for (Object e : asList(status.get("Errors"))) {
                if (e != null) {
                    out.add(e.toString());
                }
            }
        }
        return out.isEmpty() ? List.of("Unknown XTRM error") : out;
    }

    private static String firstDetailTransactionId(Map<?, ?> result) {
        return firstDetailField(result, "PaymentTransactionId", "TransactionId");
    }

    /** First non-blank value of any of {@code fields} across the {@code TransactionDetail} entries. */
    private static String firstDetailField(Map<?, ?> result, String... fields) {
        for (Object o : asList(result.get("TransactionDetail"))) {
            Map<?, ?> d = asMap(o);
            String v = d == null ? null : firstNonBlank(d, fields);
            if (!isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private static boolean itemAccepted(Map<?, ?> item, String txId) {
        Map<?, ?> status = asMap(item.get("OperationStatus"));
        if (status != null && status.get("Success") instanceof Boolean b) {
            return b;
        }
        // No per-item OperationStatus — treat presence of a transaction id as acceptance.
        return !isBlank(txId);
    }

    private static String firstItemError(Map<?, ?> item) {
        Map<?, ?> status = asMap(item.get("OperationStatus"));
        if (status != null) {
            List<Object> errs = asList(status.get("Errors"));
            if (!errs.isEmpty() && errs.get(0) != null) {
                return errs.get(0).toString();
            }
        }
        return "Rejected by XTRM";
    }

    /** Map an XTRM bank-link rejection to a normalized code the FE can key friendly copy on. */
    private static String classifyBankError(List<String> errs) {
        String joined = String.join(" ", errs).toLowerCase();
        if (joined.contains("duplicate") || joined.contains("already")) {
            return "XTRM_BANK_DUPLICATE";
        }
        return "XTRM_BANK_LINK_FAILED";
    }

    /** XTRM transient/internal errors are retryable → surface as 503 (try again), not a 422 "check your details". */
    private static boolean isTransientBankError(List<String> errs) {
        String joined = String.join(" ", errs).toLowerCase();
        return joined.contains("temporary") || joined.contains("try again later");
    }

    private static Map<?, ?> asMap(Object o) {
        return o instanceof Map<?, ?> m ? m : null;
    }

    private static List<Object> asList(Object o) {
        if (o instanceof List<?> l) {
            return new ArrayList<>(l);
        }
        return List.of();
    }

    private static String firstNonBlank(Map<?, ?> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            String v = str(map.get(key));
            if (!isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** Parse a JSON number/string balance to BigDecimal; null on absent/unparseable. */
    private static BigDecimal bigDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
