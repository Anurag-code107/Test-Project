package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionOrigin;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient;
import com.tenxengage.app.service.xtrm.XtrmCredentials;
import com.tenxengage.app.service.xtrm.XtrmRemitterResolver;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchItem;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchTransferCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchTransferResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.TransferFundCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.TransferFundResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.WalletInfo;
import com.tenxengage.app.service.xtrm.XtrmEnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatches cash redemptions to XTRM as an AnyPay/Bank <b>TransferFund</b> (F-03 payout enhancement).
 *
 * <p>Thin adapter over {@link XtrmApiClient}: it resolves the recipient's stored XTRM PAT (enrolling on
 * demand), picks the rail from the user's {@link PartnerRedemption} payout method, delegates the HTTP call +
 * envelope/parse to the client, and maps the outcome to this service's <b>throw-on-failure</b> contract:
 * on success it stamps {@code vendorReferenceId} on the passed request; on failure it throws so the caller
 * reconciles per its model (INSTANT rolls back and releases the reservation; APPROVAL/BATCH leave the
 * request RESERVED/PROCESSING for reconciliation rather than risk a double-payment).</p>
 *
 * <p>Money lands in the recipient's XTRM AnyPay wallet (auto-created); the issuer source wallet / account /
 * program are XTRM client config, never a recipient value. This replaces the previous digital-gift-card SKU
 * dispatch on the wrong payment method — no {@code SKU} / {@code UserGiftCardEmailID} is sent.</p>
 */
@Service
public class XtrmVendorService {

    private static final Logger log = LoggerFactory.getLogger(XtrmVendorService.class);

    /** AnyPay Individual rail (XTR94502) — the default cash payout method. */
    @Value("${redemption.xtrm.payment-method-id}")
    private String anypayPaymentMethodId;

    /** Bank / ACH rail (XTR94500) — used when the user's payout method is BANK. */
    @Value("${redemption.xtrm.bank-payment-method-id:XTR94500}")
    private String bankPaymentMethodId;

    /** Rapid Transfer / card rail (XTR94508) — dormant (CARD payout removed from the UI). */
    @Value("${redemption.xtrm.rapid-transfer-payment-method-id:XTR94508}")
    private String rapidTransferPaymentMethodId;

    /** Digital gift-card rail (XTR94505) — used for every non-bank-transfer (gift-card) redemption. */
    @Value("${redemption.xtrm.gift-card-payment-method-id:XTR94505}")
    private String giftCardPaymentMethodId;

    private final XtrmEnrollmentService enrollmentService;
    private final PartnerRedemptionRepository userRedemptionRepository;
    private final XtrmApiClient xtrmApiClient;
    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final UserRepository userRepository;
    private final XtrmRemitterResolver remitterResolver;

    public XtrmVendorService(XtrmEnrollmentService enrollmentService,
                             PartnerRedemptionRepository userRedemptionRepository,
                             XtrmApiClient xtrmApiClient,
                             RedemptionCatalogItemRepository catalogItemRepository,
                             UserRepository userRepository,
                             XtrmRemitterResolver remitterResolver) {
        this.enrollmentService = enrollmentService;
        this.userRedemptionRepository = userRedemptionRepository;
        this.xtrmApiClient = xtrmApiClient;
        this.catalogItemRepository = catalogItemRepository;
        this.userRepository = userRepository;
        this.remitterResolver = remitterResolver;
    }

    /**
     * Dispatch a single cash redemption (INSTANT / APPROVAL, and per-item BATCH). Sets
     * {@code vendorReferenceId} on the request on success; throws on any failure.
     */
    public void dispatch(RedemptionRequest request) {
        // A COMPANY-wallet payout is allowed for exactly one thing: a company distribution, where the
        // company wallet is only the internal budget and the payee is still an ordinary individual PAT
        // (resolved from request.getUserId(), which for a distribution row is the RECIPIENT).
        //
        // Kept as defence in depth rather than deleted. The original blocker's reasoning — "needs a company
        // beneficiary / SPN" — described a different feature: paying an XTRM *company* account, which is
        // still out of scope. And historical wallet_type=COMPANY, origin=SELF rows exist from the removed
        // company-redemption endpoint; those must never become payable, so a reconciliation sweep or a
        // future caller cannot accidentally pay one out.
        if (request.getWalletType() == WalletType.COMPANY
                && request.getOrigin() != RedemptionOrigin.COMPANY_DISTRIBUTION) {
            throw new BusinessRuleException("COMPANY_PAYOUT_NOT_SUPPORTED",
                    "Company-wallet cash payout isn't available yet.");
        }

        UUID userId = request.getUserId();
        // Enroll lazily if needed; returns the recipient's XTRM PAT, else throws XTRM_NOT_ENROLLED (422).
        String recipientUserId = enrollmentService.ensureEnrolledForPayout(userId);

        PartnerRedemption profile = userRedemptionRepository
                .findByUserIdAndClientId(userId, request.getClientId())
                .orElseThrow(() -> new BusinessRuleException("XTRM_NOT_ENROLLED",
                        "This account isn't set up for payouts yet."));

        // Two-rail routing by redemption TYPE (not the legacy profile payout-method): the reserved
        // bank-transfer card pays the user's linked bank (XTR94500); every other (gift-card) item pays
        // via the XTRM digital-gift-card rail (XTR94505) using the item SKU + the recipient's email.
        // CARD/ANYPAY are dormant (UI removed). A non-bank-transfer item with no SKU is un-routable.
        RedemptionCatalogItem catalogItem = catalogItemRepository.findById(request.getCatalogItemId())
                .orElseThrow(() -> new BusinessRuleException("REDEMPTION_UNROUTABLE",
                        "Catalog item not found for dispatch."));

        String paymentMethodId;
        String partnerLinkedBankId = null;
        String cardToken = null;
        String sku = null;
        String giftCardEmail = null;
        String destinationLabel;

        if (catalogItem.isBankTransfer()) {
            // Pay the bank chosen at submit (snapshotted on the redemption); fall back to the profile default
            // for redemptions created before multi-bank selection (payoutBeneficiaryId null).
            String chosenBeneficiary = isBlank(request.getPayoutBeneficiaryId())
                    ? profile.getPartnerLinkedBankId()
                    : request.getPayoutBeneficiaryId();
            if (isBlank(chosenBeneficiary)) {
                throw new BusinessRuleException("BANK_NOT_LINKED", "No bank account is linked.");
            }
            paymentMethodId = bankPaymentMethodId;
            partnerLinkedBankId = chosenBeneficiary;
            request.setPayoutMethod(RedemptionPayoutMethod.BANK);
            destinationLabel = !isBlank(request.getPayoutDestinationLabel())
                    ? request.getPayoutDestinationLabel()
                    : (isBlank(profile.getLinkedBankLabel()) ? "Bank transfer" : profile.getLinkedBankLabel());
        } else {
            if (isBlank(catalogItem.getProviderItemId())) {
                throw new BusinessRuleException("REDEMPTION_UNROUTABLE",
                        "This catalog item has no SKU and cannot be dispatched.");
            }
            String email = userRepository.findById(userId).map(User::getEmail).orElse(null);
            if (isBlank(email)) {
                throw new BusinessRuleException("REDEMPTION_UNROUTABLE",
                        "No email on file to deliver the digital gift card.");
            }
            paymentMethodId = giftCardPaymentMethodId;
            sku = catalogItem.getProviderItemId();
            giftCardEmail = email;
            destinationLabel = "Digital gift card: " + email;
        }

        String currency = mapCurrency(request.getCurrencyId());

        // Snapshot the destination onto the redemption — durable, independent of the mutable profile default.
        request.setPayoutDestinationLabel(destinationLabel);
        log.info("[step=xtrm_dispatch] redemptionId={}, rail={}, currency={}",
                request.getId(), catalogItem.isBankTransfer() ? "BANK" : "GIFT_CARD", currency);

        // Who pays. A distribution leg pays from its own company's wallet; a personal redemption keeps
        // paying from the platform. Reconciliation asks the same resolver, deliberately — if the two ever
        // disagreed about who paid, reconciliation would poll the wrong account and never settle the item.
        XtrmCredentials remitter = remitterResolver.forRedemption(request.getId());

        TransferFundResult result = xtrmApiClient.transferFund(new TransferFundCommand(
                request.getId().toString(), recipientUserId, paymentMethodId, partnerLinkedBankId, cardToken,
                sku, giftCardEmail,
                request.getAmount(), currency, "Reward redemption"), remitter);

        if (!result.success()) {
            String reason = String.join("; ", result.errors());
            if (isSendLimit(result.errors())) {
                // Definitive: XTRM rejected for the send limit — the transfer did not execute.
                log.warn("[step=xtrm_send_limit] redemptionId={}", request.getId());
                throw new BusinessRuleException("XTRM_SEND_LIMIT",
                        "This payout exceeds the recipient's current send limit. "
                                + "The recipient can raise it in their XTRM account.");
            }
            if (result.retryable()) {
                // Transient/ambiguous: XTRM was unreachable — it may or may not have executed the transfer.
                // The caller must HOLD (leave PROCESSING) and reconcile, never release/double-pay.
                log.warn("[step=xtrm_dispatch_unavailable] redemptionId={}", request.getId());
                throw new ExternalServiceException("XTRM_UNAVAILABLE",
                        "Payouts are temporarily unavailable. Please try again shortly.");
            }
            // Definitive rejection: XTRM returned a failure — the transfer did not execute. Safe to fail + release.
            // Raw vendor text is logged (sanitized) but never echoed in the exception message.
            log.error("[step=xtrm_dispatch_failed] redemptionId={}, reason={}",
                    request.getId(), truncate(reason, 200));
            throw new BusinessRuleException("XTRM_PAYOUT_REJECTED",
                    "The payout was rejected by the payment provider.");
        }

        request.setVendorReferenceId(result.transactionId());
        request.setBeneficiaryTransactionId(result.beneficiaryTransactionId());
        log.info("[step=xtrm_dispatch_success] redemptionId={}, vendorRef={}, beneficiaryTxn={}",
                request.getId(), request.getVendorReferenceId(), request.getBeneficiaryTransactionId());
    }

    // ---------------------------------------------------------------------
    // Batch (real XTRM BatchTransfer) — resolves each item's rail → SendMethodId + Destination. ANYPAY needs
    // the recipient's USD wallet id. Items that can't be batched (CARD rail, not enrolled, unresolved bank/wallet)
    // are returned as fallback ids for individual dispatch. Read-only + may call XTRM (GetBeneficiaryWallets).
    // ---------------------------------------------------------------------

    /** A batch line resolved and ready to send (status NOT yet transitioned to PROCESSING). Exactly one
     *  destination is set per rail: {@code bankBeneficiaryId} (BANK), {@code walletId} (ANYPAY), or
     *  {@code cardToken} (CARD). */
    public record PreparedBatchItem(
            UUID redemptionId, String customerTransactionId, String recipientUserId, BigDecimal amount,
            String sendMethodId, String bankBeneficiaryId, String walletId, String cardToken,
            RedemptionPayoutMethod payoutMethod, String payoutDestinationLabel) {
    }

    /** Split of a candidate set into batchable {@code prepared} + {@code fallbackIds} (dispatch individually). */
    public record BatchPreparation(List<PreparedBatchItem> prepared, List<UUID> fallbackIds) {
    }

    /** Resolve RESERVED batch requests into sendable items. Does NOT change status or dispatch. */
    public BatchPreparation prepareBatchItems(List<RedemptionRequest> requests) {
        List<PreparedBatchItem> prepared = new ArrayList<>();
        List<UUID> fallback = new ArrayList<>();
        Map<String, String> usdWalletByPat = new HashMap<>();
        for (RedemptionRequest r : requests) {
            if (r.getWalletType() == WalletType.COMPANY) {
                fallback.add(r.getId()); // company payout unsupported — let the individual path reject it
                continue;
            }
            PartnerRedemption profile = userRedemptionRepository
                    .findByUserIdAndClientId(r.getUserId(), r.getClientId()).orElse(null);
            String pat = profile == null ? null : profile.getRecipientUserId();
            if (isBlank(pat)) {
                fallback.add(r.getId()); // not enrolled — the individual path enrolls/handles it
                continue;
            }
            String customerTxnId = r.getId().toString().replace("-", "");
            RedemptionPayoutMethod rail = profile.getPayoutMethod();
            if (rail == RedemptionPayoutMethod.BANK) {
                if (isBlank(profile.getPartnerLinkedBankId())) {
                    fallback.add(r.getId());
                    continue;
                }
                prepared.add(new PreparedBatchItem(r.getId(), customerTxnId, pat, r.getAmount(),
                        bankPaymentMethodId, profile.getPartnerLinkedBankId(), null, null,
                        RedemptionPayoutMethod.BANK, destinationLabel(profile)));
            } else if (rail == RedemptionPayoutMethod.ANYPAY) {
                String walletId = usdWalletByPat.computeIfAbsent(pat, this::resolveUsdWalletId);
                if (isBlank(walletId)) {
                    fallback.add(r.getId()); // wallet lookup failed → individual AnyPay dispatch (uses PAT)
                    continue;
                }
                // Batch resolves the recipient's XTRM wallet id → wallet-specific label, e.g. "Wallet USD ••5566".
                String walletLabel = formatWalletLabel(mapCurrency(r.getCurrencyId()), walletId);
                prepared.add(new PreparedBatchItem(r.getId(), customerTxnId, pat, r.getAmount(),
                        anypayPaymentMethodId, null, walletId, null,
                        RedemptionPayoutMethod.ANYPAY, walletLabel));
            } else if (rail == RedemptionPayoutMethod.CARD) {
                // partner_redemption.partner_linked_card_id holds the XTRM CardToken directly (mirrors the bank rail).
                if (isBlank(profile.getPartnerLinkedCardId())) {
                    fallback.add(r.getId()); // no linked card → individual path throws CARD_NOT_LINKED (definitive → release)
                    continue;
                }
                prepared.add(new PreparedBatchItem(r.getId(), customerTxnId, pat, r.getAmount(),
                        rapidTransferPaymentMethodId, null, null, profile.getPartnerLinkedCardId(),
                        RedemptionPayoutMethod.CARD, destinationLabel(profile)));
            } else {
                // Unknown rail → individual dispatch.
                fallback.add(r.getId());
            }
        }
        return new BatchPreparation(prepared, fallback);
    }

    /** Submit a prepared batch to XTRM. Caller must have transitioned the items to PROCESSING first. */
    public BatchTransferResult dispatchPreparedBatch(String customerBatchId, List<PreparedBatchItem> prepared) {
        List<BatchItem> items = new ArrayList<>();
        for (PreparedBatchItem p : prepared) {
            items.add(new BatchItem(p.customerTransactionId(), p.recipientUserId(), p.amount(),
                    p.sendMethodId(), p.bankBeneficiaryId(), p.walletId(), p.cardToken(), "Reward redemption"));
        }
        return xtrmApiClient.batchTransfer(new BatchTransferCommand(customerBatchId, items));
    }

    private String resolveUsdWalletId(String pat) {
        try {
            GetWalletsResult res = xtrmApiClient.getBeneficiaryWallets(new GetWalletsCommand(pat));
            if (!res.success()) {
                return null;
            }
            return res.wallets().stream()
                    .filter(w -> "USD".equalsIgnoreCase(w.currency()))
                    .map(WalletInfo::id)
                    .findFirst().orElse(null);
        } catch (RuntimeException e) {
            log.warn("[step=batch_wallet_lookup_failed] {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean isSendLimit(List<String> errors) {
        return errors.stream().anyMatch(e -> e != null && e.toLowerCase().contains("limit"));
    }

    /**
     * Destination label for a single (TransferFund) dispatch. ANYPAY credits the recipient's default USD wallet;
     * we resolve its id for a wallet-specific label ({@code "Wallet USD ••5566"}), matching the batch path, and
     * fall back to a generic label only if the lookup fails (never blocks the payout — the transfer uses the PAT).
     * Card/bank use the profile's masked label.
     */
    private String singleDispatchLabel(PartnerRedemption profile, String recipientUserId, String currency) {
        if (profile.getPayoutMethod() == RedemptionPayoutMethod.ANYPAY) {
            String walletId = resolveUsdWalletId(recipientUserId);
            return isBlank(walletId) ? "Digital Wallet" : formatWalletLabel(currency, walletId);
        }
        return destinationLabel(profile);
    }

    /** Masked destination label for card/bank (from the profile's stored label). ANYPAY is a generic fallback. */
    private static String destinationLabel(PartnerRedemption profile) {
        return switch (profile.getPayoutMethod()) {
            case CARD -> isBlank(profile.getLinkedCardLabel()) ? "Card" : profile.getLinkedCardLabel();
            case BANK -> isBlank(profile.getLinkedBankLabel()) ? "Bank Account" : profile.getLinkedBankLabel();
            case ANYPAY -> "Digital Wallet";
        };
    }

    /** Wallet-specific label, e.g. {@code "Wallet USD ••5566"}. */
    private static String formatWalletLabel(String currency, String walletId) {
        return "Wallet " + currency + " ••" + last4(walletId);
    }

    /** Last 4 characters of an id, for masked destination labels (e.g. wallet {@code 445566} → {@code 5566}). */
    private static String last4(String s) {
        return s == null ? "" : (s.length() <= 4 ? s : s.substring(s.length() - 4));
    }

    private String mapCurrency(String currencyId) {
        return switch (currencyId.toLowerCase()) {
            case "cash" -> "USD";
            default -> currencyId.toUpperCase();
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
