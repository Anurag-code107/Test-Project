package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.BankTransferRedemptionRequest;
import com.tenxengage.app.dto.request.SubmitPersonalRedemptionRequest;
import com.tenxengage.app.dto.response.RedemptionRequestDetailResponse;
import com.tenxengage.app.dto.response.RedemptionRequestResponse;
import com.tenxengage.app.dto.response.RedemptionSubmissionConfirmationResponse;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.xtrm.PartnerLinkedBank;
import com.tenxengage.app.entity.enums.BatchCadence;
import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionOrigin;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.event.RedemptionFailedEvent;
import com.tenxengage.app.event.RedemptionRequestedEvent;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.TenantRedemptionSettingsRepository;
import com.tenxengage.app.repository.xtrm.PartnerLinkedBankRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RedemptionSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionSubmissionService.class);
    private static final List<RedemptionStatus> IN_FLIGHT_STATUSES =
            List.of(RedemptionStatus.PENDING_APPROVAL, RedemptionStatus.RESERVED, RedemptionStatus.PROCESSING);

    private final TenantValidator tenantValidator;
    private final TenantRedemptionSettingsRepository settingsRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final ClientCatalogItemConfigRepository catalogConfigRepository;
    private final RewardWalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final RedemptionEventProducer redemptionEventProducer;
    private final ApplicationEventPublisher eventPublisher;
    private final RedemptionOrchestrationService orchestrationService;
    private final WalletService walletService;
    private final BankTransferCardService bankTransferCardService;
    private final PartnerRedemptionRepository partnerRedemptionRepository;
    private final PartnerLinkedBankRepository linkedBankRepository;

    /** Self-reference for @Transactional(failInstantDispatch) invoked from the non-transactional AFTER_COMMIT listener. */
    private RedemptionSubmissionService self;

    public RedemptionSubmissionService(
            TenantValidator tenantValidator,
            TenantRedemptionSettingsRepository settingsRepository,
            RedemptionRequestRepository redemptionRequestRepository,
            RedemptionCatalogItemRepository catalogItemRepository,
            ClientCatalogItemConfigRepository catalogConfigRepository,
            RewardWalletRepository walletRepository,
            LedgerEntryRepository ledgerEntryRepository,
            RedemptionEventProducer redemptionEventProducer,
            ApplicationEventPublisher eventPublisher,
            RedemptionOrchestrationService orchestrationService,
            WalletService walletService,
            BankTransferCardService bankTransferCardService,
            PartnerRedemptionRepository partnerRedemptionRepository,
            PartnerLinkedBankRepository linkedBankRepository) {
        this.tenantValidator = tenantValidator;
        this.settingsRepository = settingsRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.catalogConfigRepository = catalogConfigRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.redemptionEventProducer = redemptionEventProducer;
        this.eventPublisher = eventPublisher;
        this.orchestrationService = orchestrationService;
        this.walletService = walletService;
        this.bankTransferCardService = bankTransferCardService;
        this.partnerRedemptionRepository = partnerRedemptionRepository;
        this.linkedBankRepository = linkedBankRepository;
    }

    @Autowired
    public void setSelf(@Lazy RedemptionSubmissionService self) {
        this.self = self;
    }

    /**
     * Submit a BANK-TRANSFER redemption — pay {@code amount} into the user's linked bank via the
     * reserved per-client bank-transfer card. Personal-only. Rejects up-front (409) when no default
     * bank is linked, so funds are never reserved-then-failed. Delegates to the full personal
     * submission core with {@code allowBankTransfer=true} (so the reserved card — blocked on the
     * public store path — is redeemable here); the two-rail dispatcher then pays the bank rail.
     */
    @Transactional
    public RedemptionSubmissionConfirmationResponse submitBankTransfer(
            BankTransferRedemptionRequest req, UUID userId) {
        UUID clientId = tenantValidator.getCurrentClientId();

        // Resolve the payout destination: either the bank the user explicitly chose (multi-bank) or their
        // default. The chosen beneficiary + label are persisted on the redemption so the after-commit
        // dispatch honors the choice (it otherwise reads only the mutable profile default).
        String beneficiaryId;
        String destinationLabel;
        if (req.bankId() != null) {
            PartnerLinkedBank bank = linkedBankRepository
                    .findByIdAndUserIdAndClientId(req.bankId(), userId, clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("PartnerLinkedBank", "id", req.bankId()));
            beneficiaryId = bank.getXtrmBeneficiaryId();
            destinationLabel = bank.getMaskedLabel();
        } else {
            var profile = partnerRedemptionRepository.findByUserIdAndClientId(userId, clientId).orElse(null);
            if (profile == null || profile.getPartnerLinkedBankId() == null
                    || profile.getPartnerLinkedBankId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No bank account is linked.");
            }
            beneficiaryId = profile.getPartnerLinkedBankId();
            destinationLabel = profile.getLinkedBankLabel();
        }

        // Idempotent get-or-create (runs in its own REQUIRES_NEW tx via the injected bean proxy).
        var card = bankTransferCardService.ensureBankTransferCard(clientId);

        SubmitPersonalRedemptionRequest delegate = new SubmitPersonalRedemptionRequest(
                card.getId(), req.walletId(), req.amount(), card.getCurrencyId(), req.clientIdempotencyKey());
        return submitPersonalRedemption(delegate, userId, true, beneficiaryId, destinationLabel);
    }

    @Transactional
    public RedemptionSubmissionConfirmationResponse submitPersonalRedemption(
            SubmitPersonalRedemptionRequest req, UUID userId) {
        // Public store path: the reserved bank-transfer card is NEVER redeemable here — only through
        // the dedicated bank-transfer endpoint (which calls the allowBankTransfer=true overload). This
        // guarantees the "no linked bank" precondition can't be bypassed by posting the card's id here.
        return submitPersonalRedemption(req, userId, false);
    }

    /**
     * Core personal submission. {@code allowBankTransfer} is true ONLY for the dedicated bank-transfer
     * endpoint (redeeming the reserved is-bank-transfer card); false for the public store path. The
     * full submission core (min/balance checks, in-flight limit, idempotency, reservation, ledger,
     * dispatch) runs either way, so the bank-transfer path inherits every guardrail.
     */
    @Transactional
    public RedemptionSubmissionConfirmationResponse submitPersonalRedemption(
            SubmitPersonalRedemptionRequest req, UUID userId, boolean allowBankTransfer) {
        return submitPersonalRedemption(req, userId, allowBankTransfer, null, null);
    }

    /**
     * Core personal submission with an optional resolved bank-transfer destination. {@code payoutBeneficiaryId}
     * + {@code payoutDestinationLabel} are set only by the bank-transfer path, so the chosen bank is persisted
     * on the redemption and honored by the after-commit dispatch (null on every other path).
     */
    @Transactional
    public RedemptionSubmissionConfirmationResponse submitPersonalRedemption(
            SubmitPersonalRedemptionRequest req, UUID userId, boolean allowBankTransfer,
            String payoutBeneficiaryId, String payoutDestinationLabel) {
        UUID clientId = tenantValidator.getCurrentClientId();

        var settings = settingsRepository.findByClientId(clientId).orElse(null);
        int maxInFlight = settings != null ? settings.getMaxInFlightRedemptions() : 10;

        // Idempotency check: return existing response if the client already submitted with this key.
        if (req.clientIdempotencyKey() != null) {
            Optional<RedemptionRequest> existing = redemptionRequestRepository
                    .findByClientIdAndUserIdAndClientIdempotencyKey(clientId, userId, req.clientIdempotencyKey());
            if (existing.isPresent()) {
                return RedemptionSubmissionConfirmationResponse.from(existing.get());
            }
        }

        // Acquire wallet lock BEFORE the in-flight count to prevent concurrent submissions
        // from both passing the count check in the same window (TOCTOU).
        RewardWallet wallet = walletRepository.findByIdForUpdate(req.walletId())
                .filter(w -> w.getClientId().equals(clientId) && w.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("RewardWallet", "id", req.walletId()));

        // SELF only: a distribution row carries user_id = recipient, so counting company awards here
        // would let them consume the recipient's own submission allowance.
        long inFlight = redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(
                clientId, userId, RedemptionOrigin.SELF, IN_FLIGHT_STATUSES);
        if (inFlight >= maxInFlight) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Maximum in-flight redemptions reached");
        }

        // Client-owned catalog (Model 2): the item must be active AND owned by the buyer's client.
        // The single gate is isActive (config.enabled is no longer used); the owner check blocks
        // redeeming another client's item by id. The reserved bank-transfer card is allowed ONLY on
        // the dedicated endpoint's allowBankTransfer=true path.
        var catalogItem = catalogItemRepository.findById(req.catalogItemId())
                .filter(item -> item.isActive())
                .filter(item -> !item.isDeleted())
                .filter(item -> allowBankTransfer || !item.isBankTransfer())
                .filter(item -> clientId.equals(item.getOwnerClientId()))
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", req.catalogItemId()));

        // config = optional per-item overrides (not a gate). Absent → item defaults apply.
        var catalogConfig = catalogConfigRepository
                .findByClientIdAndRedemptionCatalogItemId(clientId, req.catalogItemId())
                .orElse(null);

        if (!catalogItem.getCurrencyId().equals(req.currencyId())) {
            throw new BusinessRuleException("Catalog item currency does not match the requested currency");
        }

        BigDecimal effectiveMin = (catalogConfig != null && catalogConfig.getMinTransactionAmountOverride() != null)
                ? catalogConfig.getMinTransactionAmountOverride()
                : catalogItem.getDefaultMinRedemptionAmount();

        if (req.amount().compareTo(effectiveMin) < 0) {
            throw new BusinessRuleException(
                    "Amount is below the minimum allowed: " + effectiveMin.stripTrailingZeros().toPlainString());
        }

        // Upper bound: VARIABLE gift cards cap at maxValue; FIXED cards have min == max == faceValue, so this
        // (with the min check above) pins the amount to the denomination. A client override narrows the
        // ceiling, symmetric with the min above. Null on both sides = legacy/no ceiling.
        BigDecimal effectiveMax = (catalogConfig != null && catalogConfig.getMaxTransactionAmountOverride() != null)
                ? catalogConfig.getMaxTransactionAmountOverride()
                : catalogItem.getDefaultMaxRedemptionAmount();
        if (effectiveMax != null && req.amount().compareTo(effectiveMax) > 0) {
            throw new BusinessRuleException(
                    "Amount is above the maximum allowed: " + effectiveMax.stripTrailingZeros().toPlainString());
        }

        if (!wallet.getCurrencyId().equals(req.currencyId())) {
            throw new BusinessRuleException("Wallet currency does not match requested currency");
        }

        BigDecimal effectiveMinWalletBalance = (catalogConfig != null && catalogConfig.getMinWalletBalanceOverride() != null)
                ? catalogConfig.getMinWalletBalanceOverride()
                : BigDecimal.ZERO;
        if (wallet.getAvailableBalance().compareTo(effectiveMinWalletBalance) < 0) {
            throw new BusinessRuleException(
                    "Available wallet balance is below the minimum required: " + effectiveMinWalletBalance);
        }

        if (wallet.getAvailableBalance().compareTo(req.amount()) < 0) {
            throw new BusinessRuleException("Insufficient available balance");
        }

        RedemptionProcessingMode mode = (catalogConfig != null && catalogConfig.getProcessingModeOverride() != null)
                ? catalogConfig.getProcessingModeOverride()
                : catalogItem.getDefaultProcessingMode();

        RedemptionStatus status = switch (mode) {
            case APPROVAL_REQUIRED -> RedemptionStatus.PENDING_APPROVAL;
            // CASH INSTANT is dispatched to XTRM AFTER commit and finalized by the webhook, so it starts
            // PROCESSING. NON_CASH INSTANT still completes synchronously below (Xoxoday stub, no webhook yet).
            case INSTANT -> catalogItem.getCategory() == RedemptionCategory.CASH
                    ? RedemptionStatus.PROCESSING
                    : RedemptionStatus.RESERVED;
            case BATCH -> RedemptionStatus.RESERVED;
        };

        LocalDate scheduledBatchDate = null;
        if (mode == RedemptionProcessingMode.BATCH) {
            BatchCadence cadence = settings != null ? settings.getBatchCadence() : BatchCadence.DAILY;
            scheduledBatchDate = computeNextBatchDate(cadence);
        }

        BigDecimal availBefore = wallet.getAvailableBalance();
        BigDecimal resvBefore = wallet.getReservedBalance();
        wallet.setAvailableBalance(availBefore.subtract(req.amount()));
        wallet.setReservedBalance(resvBefore.add(req.amount()));
        walletRepository.save(wallet);

        RedemptionRequest savedRequest = redemptionRequestRepository.save(
                RedemptionRequest.builder()
                        .clientId(clientId)
                        .userId(userId)
                        .walletId(req.walletId())
                        .catalogItemId(req.catalogItemId())
                        .amount(req.amount())
                        .currencyId(req.currencyId())
                        .walletType(WalletType.INDIVIDUAL)
                        .status(status)
                        .processingMode(mode)
                        .category(catalogItem.getCategory())
                        .scheduledBatchDate(scheduledBatchDate)
                        .submittedAt(Instant.now())
                        .clientIdempotencyKey(req.clientIdempotencyKey())
                        // Bank-transfer only: the chosen bank's XTRM beneficiary + masked label, snapshotted so
                        // the after-commit dispatch pays THIS bank (not the mutable profile default). Null elsewhere.
                        .payoutBeneficiaryId(payoutBeneficiaryId)
                        .payoutDestinationLabel(payoutDestinationLabel)
                        .deleted(false)
                        .build());

        LedgerEntry reserveEntry = ledgerEntryRepository.save(LedgerEntry.builder()
                .clientId(clientId)
                .rewardWalletId(req.walletId())
                .entryType(LedgerEntryType.RESERVE)
                .amount(req.amount())
                .currencyId(req.currencyId())
                .referenceType("REDEMPTION_REQUEST")
                .referenceId(savedRequest.getId())
                .availableBalanceBefore(availBefore)
                .availableBalanceAfter(wallet.getAvailableBalance())
                .reservedBalanceBefore(resvBefore)
                .reservedBalanceAfter(wallet.getReservedBalance())
                .build());
        savedRequest.setReserveLedgerEntryId(reserveEntry.getId());

        if (mode == RedemptionProcessingMode.INSTANT
                && catalogItem.getCategory() != RedemptionCategory.CASH) {
            // NON_CASH INSTANT completes synchronously via the vendor stub. CASH INSTANT is dispatched to
            // XTRM after commit (see onRedemptionRequested) and finalized by the webhook — never in-transaction.
            orchestrationService.dispatch(savedRequest);
            BigDecimal resvAfterDebit = wallet.getReservedBalance().subtract(req.amount());
            LedgerEntry debitEntry = ledgerEntryRepository.save(LedgerEntry.builder()
                    .clientId(clientId)
                    .rewardWalletId(req.walletId())
                    .entryType(LedgerEntryType.DEBIT)
                    .amount(req.amount())
                    .currencyId(req.currencyId())
                    .referenceType("REDEMPTION_REQUEST")
                    .referenceId(savedRequest.getId())
                    .availableBalanceBefore(wallet.getAvailableBalance())
                    .availableBalanceAfter(wallet.getAvailableBalance())
                    .reservedBalanceBefore(wallet.getReservedBalance())
                    .reservedBalanceAfter(resvAfterDebit)
                    .build());
            wallet.setReservedBalance(resvAfterDebit);
            walletRepository.save(wallet);
            savedRequest.setDebitLedgerEntryId(debitEntry.getId());
            savedRequest.setStatus(RedemptionStatus.COMPLETED);
            savedRequest.setCompletedAt(Instant.now());
        }

        eventPublisher.publishEvent(new RedemptionRequestedEvent(this, savedRequest));

        log.info("Redemption submitted: redemptionId={}, userId={}, walletId={}, amount={}, mode={}",
                savedRequest.getId(), userId, req.walletId(), req.amount(), mode);

        return RedemptionSubmissionConfirmationResponse.from(savedRequest);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRedemptionRequested(RedemptionRequestedEvent event) {
        RedemptionRequest request = event.getRequest();
        redemptionEventProducer.publishRedemptionRequested(request);

        // CASH INSTANT payouts are dispatched to XTRM AFTER commit so the external call + enrollment
        // writes run OUTSIDE the submission transaction; the XTRM webhook finalizes PROCESSING -> COMPLETED.
        // (NON_CASH INSTANT already completed synchronously within the submission transaction.)
        if (request.getProcessingMode() == RedemptionProcessingMode.INSTANT
                && request.getCategory() == RedemptionCategory.CASH
                && request.getStatus() == RedemptionStatus.PROCESSING) {
            dispatchInstantCashAfterCommit(request.getId());
        }
    }

    /**
     * Dispatches a CASH INSTANT redemption to the vendor after the submission transaction has committed
     * (mirrors {@code BatchRedemptionProcessor.dispatchAfterCommit}). Stamps {@code dispatchAttemptedAt}
     * before the call so recovery can distinguish "never attempted" from "attempted".
     *
     * <p>Failure handling splits by kind:
     * <ul>
     *   <li><b>Definitive</b> ({@link BusinessRuleException} — not-enrolled, send-limit, bank-not-linked,
     *       payout-rejected): the vendor did <b>not</b> execute the transfer, so we release the reservation
     *       and mark the redemption FAILED (FR-06 / FR-09).</li>
     *   <li><b>Ambiguous / transient</b> (transport failure, e.g. {@code ExternalServiceException}): the
     *       vendor may have accepted the transfer, so we leave it PROCESSING (funds reserved) for
     *       reconciliation — never released, to avoid a double-payment.</li>
     * </ul>
     * Repository writes here run in their own transactions.
     */
    void dispatchInstantCashAfterCommit(UUID requestId) {
        RedemptionRequest request = redemptionRequestRepository.findById(requestId).orElse(null);
        if (request == null) {
            return;
        }
        // Durably record the attempt in its OWN committed transaction BEFORE the external call. If this write
        // were lost (bare save in the AFTER_COMMIT context) while the transfer succeeded, the batch recovery
        // sweep (dispatchAttemptedAt IS NULL) would re-dispatch it — a double-pay. REQUIRES_NEW guarantees it commits.
        self.stampDispatchAttempt(requestId);
        try {
            orchestrationService.dispatch(request);
            if (request.getVendorReferenceId() != null) {
                // Persist the dispatch-output fields in their own committed transaction (reliable in AFTER_COMMIT).
                self.persistVendorRef(requestId, request.getVendorReferenceId(), request.getBeneficiaryTransactionId(),
                        request.getPayoutMethod(), request.getPayoutDestinationLabel());
            }
            log.info("[step=instant_cash_dispatch_sent] redemptionId={}", requestId);
        } catch (BusinessRuleException definitive) {
            // Vendor did not execute the transfer — release the reservation and fail the redemption.
            log.warn("[step=instant_cash_dispatch_rejected] redemptionId={}, code={} — releasing reservation",
                    requestId, definitive.getErrorCode());
            self.failInstantDispatch(requestId, definitive.getMessage());
        } catch (Exception ambiguous) {
            // Transport/ambiguous failure — the vendor may have accepted; leave PROCESSING for reconciliation.
            log.error("[step=instant_cash_dispatch_ambiguous] redemptionId={} — leaving PROCESSING for reconciliation",
                    requestId, ambiguous);
        }
    }

    /**
     * Stamps {@code dispatchAttemptedAt} in a new committed transaction, invoked via the {@code self} proxy
     * from the non-transactional AFTER_COMMIT path BEFORE the vendor call. Crash-recovery marker:
     * {@code BatchRedemptionProcessor} re-dispatches only items with {@code dispatchAttemptedAt IS NULL}, so this
     * write must be durable before any money moves — otherwise a lost write means a re-dispatch (double-pay).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stampDispatchAttempt(UUID requestId) {
        redemptionRequestRepository.findById(requestId).ifPresent(request -> {
            request.setDispatchAttemptedAt(Instant.now());
            redemptionRequestRepository.save(request);
        });
    }

    /**
     * Persists the vendor reference id in a new committed transaction (reliable in the AFTER_COMMIT context,
     * unlike a bare save). Invoked via the {@code self} proxy after a successful dispatch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistVendorRef(UUID requestId, String vendorReferenceId, String beneficiaryTransactionId,
                                 RedemptionPayoutMethod payoutMethod, String payoutDestinationLabel) {
        redemptionRequestRepository.findById(requestId).ifPresent(request -> {
            request.setVendorReferenceId(vendorReferenceId);
            request.setBeneficiaryTransactionId(beneficiaryTransactionId);
            request.setPayoutMethod(payoutMethod);
            request.setPayoutDestinationLabel(payoutDestinationLabel);
            redemptionRequestRepository.save(request);
        });
    }

    /**
     * Atomically releases a CASH INSTANT redemption's reservation and marks it FAILED after a definitive
     * dispatch rejection. Invoked via the {@code self} proxy from the non-transactional AFTER_COMMIT path so
     * the wallet release ({@code releaseReservedBalance}, MANDATORY) + status flip + failure event commit
     * together. Idempotent: a no-op if the request is no longer PROCESSING.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failInstantDispatch(UUID requestId, String reason) {
        RedemptionRequest request = redemptionRequestRepository.findByIdForUpdate(requestId).orElse(null);
        if (request == null) {
            return;
        }
        if (request.getStatus() != RedemptionStatus.PROCESSING) {
            log.info("[step=instant_cash_fail_skip] redemptionId={}, status={}", requestId, request.getStatus());
            return;
        }
        walletService.releaseReservedBalance(request);
        request.setStatus(RedemptionStatus.FAILED);
        request.setFailureReason(reason);
        request.setCompletedAt(Instant.now());
        redemptionRequestRepository.save(request);
        eventPublisher.publishEvent(new RedemptionFailedEvent(this, request));
        log.info("[step=instant_cash_dispatch_failed] redemptionId={} — reservation released, marked FAILED", requestId);
    }

    public Page<RedemptionRequestResponse> getPersonalRedemptions(UUID userId, Pageable pageable) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return redemptionRequestRepository
                .findByClientIdAndUserIdAndDeletedFalse(clientId, userId, pageable)
                .map(req -> RedemptionRequestResponse.from(req, null));
    }

    public RedemptionRequestDetailResponse getRedemptionById(UUID id, UUID userId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        RedemptionRequest req = redemptionRequestRepository.findByIdAndClientIdAndUserIdAndOrigin(
                        id, clientId, userId, RedemptionOrigin.SELF)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", id));
        var catalogItem = catalogItemRepository.findById(req.getCatalogItemId());
        String catalogItemName = catalogItem.map(item -> item.getName()).orElse("(unknown)");
        // Uploaded images are served through the API proxy, never as the raw storage key.
        String imageUrl = catalogItem
                .map(item -> item.getImageUrl() != null
                        ? "/api/v1/admin/redemption-catalog/" + item.getId() + "/image" : null)
                .orElse(null);
        String providerImageUrl = catalogItem.map(item -> item.getProviderImageUrl()).orElse(null);
        return RedemptionRequestDetailResponse.from(req, catalogItemName, imageUrl, providerImageUrl, null, null);
    }

    private LocalDate computeNextBatchDate(BatchCadence cadence) {
        LocalDate today = LocalDate.now();
        return switch (cadence) {
            case DAILY -> today.plusDays(1);
            case WEEKLY -> today.with(DayOfWeek.MONDAY).plusWeeks(1);
        };
    }
}
