package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.SubmitCompanyRedemptionRequest;
import com.tenxengage.app.dto.request.SubmitPersonalRedemptionRequest;
import com.tenxengage.app.dto.response.RedemptionRequestDetailResponse;
import com.tenxengage.app.dto.response.RedemptionRequestResponse;
import com.tenxengage.app.dto.response.RedemptionSubmissionConfirmationResponse;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.BatchCadence;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.event.RedemptionRequestedEvent;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.TenantRedemptionSettingsRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
            RedemptionOrchestrationService orchestrationService) {
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
    }

    @Transactional
    public RedemptionSubmissionConfirmationResponse submitPersonalRedemption(
            SubmitPersonalRedemptionRequest req, UUID userId) {
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

        long inFlight = redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                clientId, userId, IN_FLIGHT_STATUSES);
        if (inFlight >= maxInFlight) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Maximum in-flight redemptions reached");
        }

        var catalogItem = catalogItemRepository.findById(req.catalogItemId())
                .filter(item -> item.isActive())
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", req.catalogItemId()));

        var catalogConfig = catalogConfigRepository
                .findByClientIdAndRedemptionCatalogItemId(clientId, req.catalogItemId())
                .filter(c -> c.isEnabled())
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", req.catalogItemId()));

        if (!catalogItem.getCurrencyId().equals(req.currencyId())) {
            throw new BusinessRuleException("Catalog item currency does not match the requested currency");
        }

        BigDecimal effectiveMin = catalogConfig.getMinTransactionAmountOverride() != null
                ? catalogConfig.getMinTransactionAmountOverride()
                : catalogItem.getDefaultMinRedemptionAmount();

        if (req.amount().compareTo(effectiveMin) < 0) {
            throw new BusinessRuleException(
                    "Amount is below the minimum allowed: " + effectiveMin.stripTrailingZeros().toPlainString());
        }

        if (!wallet.getCurrencyId().equals(req.currencyId())) {
            throw new BusinessRuleException("Wallet currency does not match requested currency");
        }

        BigDecimal effectiveMinWalletBalance = catalogConfig.getMinWalletBalanceOverride() != null
                ? catalogConfig.getMinWalletBalanceOverride()
                : BigDecimal.ZERO;
        if (wallet.getAvailableBalance().compareTo(effectiveMinWalletBalance) < 0) {
            throw new BusinessRuleException(
                    "Available wallet balance is below the minimum required: " + effectiveMinWalletBalance);
        }

        if (wallet.getAvailableBalance().compareTo(req.amount()) < 0) {
            throw new BusinessRuleException("Insufficient available balance");
        }

        RedemptionProcessingMode mode = catalogConfig.getProcessingModeOverride() != null
                ? catalogConfig.getProcessingModeOverride()
                : catalogItem.getDefaultProcessingMode();

        RedemptionStatus status = switch (mode) {
            case APPROVAL_REQUIRED -> RedemptionStatus.PENDING_APPROVAL;
            // INSTANT dispatch deferred (US-05 BE-1) — start in RESERVED (funds locked,
            // awaiting dispatch). When initiateVendorSubmission is enabled post-commit,
            // it will transition RESERVED → PROCESSING. Remove this comment when live.
            case INSTANT -> RedemptionStatus.RESERVED;
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
                        .deleted(false)
                        .build());

        ledgerEntryRepository.save(LedgerEntry.builder()
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

        // INSTANT dispatch deferred — XTRM send limit at $0 until identity profile is completed.
        // Uncomment when XTRM account is verified and ready for live transfers.
        // if (mode == RedemptionProcessingMode.INSTANT) {
        //     orchestrationService.dispatch(savedRequest);
        // }

        eventPublisher.publishEvent(new RedemptionRequestedEvent(this, savedRequest));

        log.info("Redemption submitted: redemptionId={}, userId={}, walletId={}, amount={}, mode={}",
                savedRequest.getId(), userId, req.walletId(), req.amount(), mode);

        return RedemptionSubmissionConfirmationResponse.from(savedRequest);
    }

    @Transactional
    public RedemptionSubmissionConfirmationResponse submitCompanyRedemption(
            SubmitCompanyRedemptionRequest req, UUID userId) {
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
        UUID callerPartnerCompanyId = tenantValidator.getCurrentPartnerCompanyId();
        RewardWallet wallet = walletRepository.findByIdForUpdate(req.walletId())
                .filter(w -> w.getClientId().equals(clientId)
                        && w.getWalletType() == WalletType.COMPANY
                        && (callerPartnerCompanyId == null || callerPartnerCompanyId.equals(w.getPartnerCompanyId())))
                .orElseThrow(() -> new ResourceNotFoundException("RewardWallet", "id", req.walletId()));

        long inFlight = redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                clientId, userId, IN_FLIGHT_STATUSES);
        if (inFlight >= maxInFlight) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Maximum in-flight redemptions reached");
        }

        var catalogItem = catalogItemRepository.findById(req.catalogItemId())
                .filter(item -> item.isActive())
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", req.catalogItemId()));

        var catalogConfig = catalogConfigRepository
                .findByClientIdAndRedemptionCatalogItemId(clientId, req.catalogItemId())
                .filter(c -> c.isEnabled())
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", req.catalogItemId()));

        if (!catalogItem.getCurrencyId().equals(req.currencyId())) {
            throw new BusinessRuleException("Catalog item currency does not match the requested currency");
        }

        BigDecimal effectiveMin = catalogConfig.getMinTransactionAmountOverride() != null
                ? catalogConfig.getMinTransactionAmountOverride()
                : catalogItem.getDefaultMinRedemptionAmount();

        if (req.amount().compareTo(effectiveMin) < 0) {
            throw new BusinessRuleException(
                    "Amount is below the minimum allowed: " + effectiveMin.stripTrailingZeros().toPlainString());
        }

        if (!wallet.getCurrencyId().equals(req.currencyId())) {
            throw new BusinessRuleException("Wallet currency does not match requested currency");
        }

        BigDecimal effectiveMinWalletBalance = catalogConfig.getMinWalletBalanceOverride() != null
                ? catalogConfig.getMinWalletBalanceOverride()
                : BigDecimal.ZERO;
        if (wallet.getAvailableBalance().compareTo(effectiveMinWalletBalance) < 0) {
            throw new BusinessRuleException(
                    "Available wallet balance is below the minimum required: " + effectiveMinWalletBalance);
        }

        if (wallet.getAvailableBalance().compareTo(req.amount()) < 0) {
            throw new BusinessRuleException("Insufficient available balance");
        }

        RedemptionProcessingMode mode = catalogConfig.getProcessingModeOverride() != null
                ? catalogConfig.getProcessingModeOverride()
                : catalogItem.getDefaultProcessingMode();

        RedemptionStatus status = switch (mode) {
            case APPROVAL_REQUIRED -> RedemptionStatus.PENDING_APPROVAL;
            // INSTANT dispatch deferred (US-05 BE-1) — start in RESERVED (funds locked,
            // awaiting dispatch). When initiateVendorSubmission is enabled post-commit,
            // it will transition RESERVED → PROCESSING. Remove this comment when live.
            case INSTANT -> RedemptionStatus.RESERVED;
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
                        .walletType(WalletType.COMPANY)
                        .status(status)
                        .processingMode(mode)
                        .category(catalogItem.getCategory())
                        .scheduledBatchDate(scheduledBatchDate)
                        .submittedAt(Instant.now())
                        .clientIdempotencyKey(req.clientIdempotencyKey())
                        .deleted(false)
                        .build());

        ledgerEntryRepository.save(LedgerEntry.builder()
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

        // INSTANT dispatch deferred — XTRM send limit at $0 until identity profile is completed.
        // Uncomment when XTRM account is verified and ready for live transfers.
        // if (mode == RedemptionProcessingMode.INSTANT) {
        //     orchestrationService.dispatch(savedRequest);
        // }

        eventPublisher.publishEvent(new RedemptionRequestedEvent(this, savedRequest));

        log.info("Company redemption submitted: redemptionId={}, userId={}, walletId={}, amount={}, mode={}",
                savedRequest.getId(), userId, req.walletId(), req.amount(), mode);

        return RedemptionSubmissionConfirmationResponse.from(savedRequest);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRedemptionRequested(RedemptionRequestedEvent event) {
        redemptionEventProducer.publishRedemptionRequested(event.getRequest());
    }

    public Page<RedemptionRequestResponse> getPersonalRedemptions(UUID userId, Pageable pageable) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return redemptionRequestRepository
                .findByClientIdAndUserIdAndDeletedFalse(clientId, userId, pageable)
                .map(RedemptionRequestResponse::from);
    }

    public RedemptionRequestDetailResponse getRedemptionById(UUID id, UUID userId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        RedemptionRequest req = redemptionRequestRepository.findByIdAndClientIdAndUserId(id, clientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", id));
        var catalogItem = catalogItemRepository.findById(req.getCatalogItemId());
        String catalogItemName = catalogItem.map(item -> item.getName()).orElse("(unknown)");
        String imageUrl = catalogItem.map(item -> item.getImageUrl()).orElse(null);
        return RedemptionRequestDetailResponse.from(req, catalogItemName, imageUrl);
    }

    private LocalDate computeNextBatchDate(BatchCadence cadence) {
        LocalDate today = LocalDate.now();
        return switch (cadence) {
            case DAILY -> today.plusDays(1);
            case WEEKLY -> today.with(DayOfWeek.MONDAY).plusWeeks(1);
        };
    }
}
