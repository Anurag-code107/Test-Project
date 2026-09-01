package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.request.redemption.RejectReturnRequest;
import com.tenxengage.app.dto.request.redemption.SubmitReturnRequest;
import com.tenxengage.app.dto.response.redemption.ReturnDetailResponse;
import com.tenxengage.app.dto.response.redemption.ReturnQueueItemResponse;
import com.tenxengage.app.dto.response.redemption.ReturnSummaryResponse;
import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.ReturnResolution;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.exception.StateConflictException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RedemptionReturnRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.service.AuditLogService;
import com.tenxengage.app.service.ReturnEventProducer;
import com.tenxengage.app.service.WalletMutationDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReturnService {

    private static final Logger log = LoggerFactory.getLogger(ReturnService.class);

    /**
     * Only CANCELLED is non-terminal and allows resubmission.
     * RETURN_REJECTED and RETURN_CONFIRMED are terminal — no resubmission.
     * RETURN_TIMED_OUT must be resolved by admin before a new return can be submitted.
     * The existsBy check uses statusNotIn(RESUBMIT_ALLOWED_STATUSES) so any non-CANCELLED
     * return (including RETURN_REJECTED) blocks a new submission.
     */
    private static final List<ReturnStatus> RESUBMIT_ALLOWED_STATUSES =
            List.of(ReturnStatus.CANCELLED);
    private static final List<String> RESUBMIT_ALLOWED_STATUS_NAMES =
            RESUBMIT_ALLOWED_STATUSES.stream().map(Enum::name).toList();

    /**
     * Statuses from which no further automatic transitions are allowed.
     * Webhook idempotency guard: returns already in one of these states are no-ops.
     * Includes RETURN_TIMED_OUT per AC-5: a duplicate webhook for a timed-out return
     * must also be a no-op (admin uses /resolve for manual override).
     */
    private static final EnumSet<ReturnStatus> TERMINAL_STATUSES =
            EnumSet.of(
                    ReturnStatus.RETURN_CONFIRMED,
                    ReturnStatus.RETURN_REJECTED,
                    ReturnStatus.RETURN_TIMED_OUT);

    private final RedemptionReturnRepository returnRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final ClientCatalogItemConfigRepository clientCatalogItemConfigRepository;
    private final UserRepository userRepository;
    private final PartnerCompanyRepository partnerCompanyRepository;
    private final ReturnEventProducer returnEventProducer;
    private final ReturnVendorService returnVendorService;
    private final WalletMutationDelegate walletMutationDelegate;
    private final AuditLogService auditLogService;

    public ReturnService(RedemptionReturnRepository returnRepository,
                         RedemptionRequestRepository redemptionRequestRepository,
                         RedemptionCatalogItemRepository catalogItemRepository,
                         ClientCatalogItemConfigRepository clientCatalogItemConfigRepository,
                         UserRepository userRepository,
                         PartnerCompanyRepository partnerCompanyRepository,
                         ReturnEventProducer returnEventProducer,
                         ReturnVendorService returnVendorService,
                         WalletMutationDelegate walletMutationDelegate,
                         AuditLogService auditLogService) {
        this.returnRepository = returnRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.clientCatalogItemConfigRepository = clientCatalogItemConfigRepository;
        this.userRepository = userRepository;
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.returnEventProducer = returnEventProducer;
        this.returnVendorService = returnVendorService;
        this.walletMutationDelegate = walletMutationDelegate;
        this.auditLogService = auditLogService;
    }

    /**
     * Submit a return request for an eligible completed non-cash redemption.
     * Validates eligibility (422), duplicate check (409), copies amount from redemption.
     */
    @Transactional
    public ReturnDetailResponse submitReturn(SubmitReturnRequest request, UUID userId, UUID clientId) {
        // Acquire pessimistic lock on the originating redemption to prevent concurrent submit races
        RedemptionRequest redemption = redemptionRequestRepository.findByIdForUpdate(request.redemptionId())
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", request.redemptionId()));

        // IDOR guard: redemption must belong to calling user and same tenant
        if (!clientId.equals(redemption.getClientId())) {
            log.warn("submitReturn IDOR attempt redemptionId={} callerClientId={}", request.redemptionId(), clientId);
            throw new ResourceNotFoundException("RedemptionRequest", "id", request.redemptionId());
        }
        if (!userId.equals(redemption.getUserId())) {
            log.warn("submitReturn IDOR attempt redemptionId={} callerUserId={}", request.redemptionId(), userId);
            throw new ResourceNotFoundException("RedemptionRequest", "id", request.redemptionId());
        }

        // Eligibility: must be COMPLETED status
        if (!RedemptionStatus.COMPLETED.equals(redemption.getStatus())) {
            log.warn("submitReturn ineligible status redemptionId={} status={}", request.redemptionId(), redemption.getStatus());
            throw new BusinessRuleException("Return can only be submitted for a COMPLETED redemption");
        }

        // Eligibility: must be NON_CASH (Xoxoday) — no XTRM/cash returns
        if (!RedemptionCategory.NON_CASH.equals(redemption.getCategory())) {
            log.warn("submitReturn ineligible category redemptionId={} category={}", request.redemptionId(), redemption.getCategory());
            throw new BusinessRuleException("Cash redemptions cannot be returned");
        }

        // Eligibility: catalog item must be returnable and within return window
        RedemptionCatalogItem catalogItem = catalogItemRepository.findById(redemption.getCatalogItemId())
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", redemption.getCatalogItemId()));

        if (!catalogItem.isReturnable()) {
            log.warn("submitReturn non-returnable item redemptionId={} catalogItemId={}", request.redemptionId(), catalogItem.getId());
            throw new BusinessRuleException("This item is not eligible for return");
        }

        // Return window check: completedAt + effectiveWindowDays >= now
        // Tenant override from ClientCatalogItemConfig takes precedence over the platform default.
        if (redemption.getCompletedAt() == null) {
            log.warn("submitReturn completedAt null on COMPLETED redemption redemptionId={}", request.redemptionId());
            throw new BusinessRuleException("Return can only be submitted for a COMPLETED redemption");
        }
        int windowDays = resolveEffectiveWindowDays(catalogItem.getId(), clientId,
                catalogItem.getDefaultReturnWindowDays());
        Instant windowExpiry = redemption.getCompletedAt().plus(windowDays, ChronoUnit.DAYS);
        if (Instant.now().isAfter(windowExpiry)) {
            log.warn("submitReturn window expired redemptionId={} completedAt={} windowDays={}",
                    request.redemptionId(), redemption.getCompletedAt(), windowDays);
            throw new BusinessRuleException("Return window for this redemption has expired");
        }

        // Duplicate active return check (409).
        // Safe: PESSIMISTIC_WRITE on RedemptionRequest serialises concurrent submitReturn() for the same
        // redemptionId; no lock on RedemptionReturn is needed here.
        boolean hasActiveReturn = returnRepository.existsByRedemptionIdAndClientIdAndStatusNotIn(
                request.redemptionId(), clientId, RESUBMIT_ALLOWED_STATUS_NAMES);
        if (hasActiveReturn) {
            log.warn("submitReturn duplicate active return redemptionId={} clientId={}", request.redemptionId(), clientId);
            throw new StateConflictException("A return request is already active for this redemption");
        }

        // Build and persist the return — amount always copied from originating redemption
        RedemptionReturn ret = RedemptionReturn.builder()
                .clientId(clientId)
                .redemptionId(redemption.getId())
                .partnerUserId(userId)
                .status(ReturnStatus.PENDING_APPROVAL)
                .reason(request.reason())
                .amount(redemption.getAmount())
                .currencyId(redemption.getCurrencyId())
                .build();

        ret = returnRepository.save(ret);
        log.info("step=return_submitted returnId={} redemptionId={} userId={}", ret.getId(), ret.getRedemptionId(), userId);

        // Publish RETURN_REQUESTED event after persisting (persist-before-notify anti-pattern guard)
        returnEventProducer.publishReturnRequested(ret);

        String partnerDisplayName = resolveDisplayName(userId);
        return buildPartnerDetailResponse(ret, catalogItem.getName(), partnerDisplayName);
    }

    /**
     * List the calling partner's own return requests, paginated. Optional status filter.
     */
    @Transactional(readOnly = true)
    public Page<ReturnSummaryResponse> getPartnerReturns(
            UUID userId, UUID clientId, Pageable pageable) {
        return getPartnerReturns(userId, clientId, null, pageable);
    }

    /**
     * List the calling partner's own return requests, paginated, with optional status filter.
     */
    @Transactional(readOnly = true)
    public Page<ReturnSummaryResponse> getPartnerReturns(
            UUID userId, UUID clientId, ReturnStatus status, Pageable pageable) {
        Page<RedemptionReturn> page;
        if (status != null) {
            page = returnRepository.findByClientIdAndPartnerUserIdAndStatus(clientId, userId, status.name(), pageable);
        } else {
            page = returnRepository.findByClientIdAndPartnerUserId(clientId, userId, pageable);
        }

        // Batch-load catalog names to avoid N+1 per entry
        List<UUID> redemptionIds = page.getContent().stream()
                .map(RedemptionReturn::getRedemptionId)
                .distinct()
                .toList();
        Map<UUID, String> catalogNameByRedemptionId = loadCatalogNamesByRedemptionId(redemptionIds, clientId);

        return page.map(ret -> ReturnSummaryResponse.from(
                ret, catalogNameByRedemptionId.getOrDefault(ret.getRedemptionId(), "(unknown)")));
    }

    /**
     * Get a single return by ID.
     * Partner path: ownership-scoped (findByIdAndClientIdAndPartnerUserId).
     * Admin path: tenant-scoped only (findByIdAndClientId).
     * Admin-only fields (reviewNotes, vendorReturnReference) are included when isAdmin=true.
     */
    @Transactional(readOnly = true)
    public ReturnDetailResponse getReturnById(UUID id, UUID userId, UUID clientId, boolean isAdmin) {
        RedemptionReturn ret;
        if (isAdmin) {
            ret = returnRepository.findByIdAndClientId(id, clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("RedemptionReturn", "id", id));
        } else {
            ret = returnRepository.findByIdAndClientIdAndPartnerUserId(id, clientId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("RedemptionReturn", "id", id));
        }

        String catalogName = resolveCatalogName(ret.getRedemptionId(), clientId);
        String partnerDisplayName = resolveDisplayName(ret.getPartnerUserId());

        // For partner calls, mask admin-only fields by building a masked response directly
        if (!isAdmin) {
            return buildPartnerDetailResponse(ret, catalogName, partnerDisplayName);
        }

        return ReturnDetailResponse.from(ret, catalogName, partnerDisplayName);
    }

    /**
     * Cancel a PENDING_APPROVAL return (partner action).
     * Verifies ownership, validates state, transitions to CANCELLED.
     */
    @Transactional
    public void cancelReturn(UUID id, UUID userId, UUID clientId) {
        RedemptionReturn ret = returnRepository.findByIdAndClientIdAndPartnerUserId(id, clientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionReturn", "id", id));

        if (!ReturnStatus.PENDING_APPROVAL.equals(ret.getStatus())) {
            log.warn("cancelReturn wrong state returnId={} status={}", id, ret.getStatus());
            throw new StateConflictException("Only PENDING_APPROVAL returns can be cancelled");
        }

        ret.setStatus(ReturnStatus.CANCELLED);
        ret.setCancelledAt(Instant.now());
        returnRepository.save(ret);

        log.info("step=return_cancelled returnId={} userId={}", id, userId);
        returnEventProducer.publishReturnCancelled(ret);
    }

    // ── Admin actions ──────────────────────────────────────────────────────────

    /**
     * Approve a PENDING_APPROVAL return.
     * Transitions to APPROVED, records reviewedBy + approvedAt.
     * Fires async ReturnVendorService.notifyXoxodayReturn() after DB commit
     * so the transaction cannot be rolled back by a vendor-call failure.
     * Publishes RETURN_APPROVED event (also post-commit via ReturnEventProducer).
     */
    @Transactional
    public ReturnDetailResponse approveReturn(UUID id, UUID reviewerId, UUID clientId) {
        RedemptionReturn ret = returnRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionReturn", "id", id));

        if (!ReturnStatus.PENDING_APPROVAL.equals(ret.getStatus())) {
            log.warn("approveReturn wrong state returnId={} status={}", id, ret.getStatus());
            throw new StateConflictException("Only PENDING_APPROVAL returns can be approved");
        }

        ret.setStatus(ReturnStatus.APPROVED);
        ret.setReviewedBy(reviewerId);
        ret.setReviewedAt(Instant.now());
        ret.setApprovedAt(ret.getReviewedAt());
        ret = returnRepository.save(ret);

        log.info("step=return_approved returnId={} reviewerId={}", id, reviewerId);

        // Publish RETURN_APPROVED after commit (ReturnEventProducer uses afterCommit internally)
        returnEventProducer.publishReturnApproved(ret);

        // Fire async vendor notification after DB commit to prevent rollback-race.
        // Guard with isActualTransactionActive() — unit tests run without a transaction context.
        final RedemptionReturn savedRet = ret;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    returnVendorService.notifyXoxodayReturn(savedRet);
                }
            });
        } else {
            returnVendorService.notifyXoxodayReturn(savedRet);
        }

        String catalogName = resolveCatalogName(ret.getRedemptionId(), clientId);
        String partnerDisplayName = resolveDisplayName(ret.getPartnerUserId());
        return ReturnDetailResponse.from(ret, catalogName, partnerDisplayName);
    }

    /**
     * Reject a PENDING_APPROVAL return with a mandatory reason.
     * Transitions to RETURN_REJECTED; records rejectedAt, reviewedBy, reviewNotes.
     * Xoxoday is NOT contacted.
     * Publishes RETURN_REJECTED event (post-commit via ReturnEventProducer).
     */
    @Transactional
    public ReturnDetailResponse rejectReturn(UUID id, RejectReturnRequest request,
                                             UUID reviewerId, UUID clientId) {
        RedemptionReturn ret = returnRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionReturn", "id", id));

        if (!ReturnStatus.PENDING_APPROVAL.equals(ret.getStatus())) {
            log.warn("rejectReturn wrong state returnId={} status={}", id, ret.getStatus());
            throw new StateConflictException("Only PENDING_APPROVAL returns can be rejected");
        }

        Instant now = Instant.now();
        ret.setStatus(ReturnStatus.RETURN_REJECTED);
        ret.setReviewedBy(reviewerId);
        ret.setReviewedAt(now);
        ret.setRejectedAt(now);
        ret.setReviewNotes(request.rejectionReason());
        ret = returnRepository.save(ret);

        log.info("step=return_rejected_admin returnId={} reviewerId={}", id, reviewerId);

        returnEventProducer.publishReturnRejected(ret);

        String catalogName = resolveCatalogName(ret.getRedemptionId(), clientId);
        String partnerDisplayName = resolveDisplayName(ret.getPartnerUserId());
        return ReturnDetailResponse.from(ret, catalogName, partnerDisplayName);
    }

    /**
     * Process a Xoxoday return confirmation or rejection from an inbound webhook.
     *
     * Idempotency: if the return is already in a terminal state (RETURN_CONFIRMED,
     * RETURN_REJECTED) or RETURN_TIMED_OUT, logs at WARN and returns without
     * state change. Callers should still return HTTP 200 for idempotent duplicates.
     *
     * confirmed=true path:
     *   - Calls WalletMutationDelegate.doReturnCreditInTx() (REQUIRES_NEW — suspended outer tx)
     *   - Transitions to RETURN_CONFIRMED; sets confirmedAt
     *   - Publishes RETURN_CONFIRMED event (post-commit via ReturnEventProducer)
     *
     * confirmed=false path:
     *   - Transitions to RETURN_REJECTED; sets rejectedAt; sets reviewNotes=failureReason
     *   - Publishes RETURN_REJECTED event (post-commit via ReturnEventProducer)
     *
     * PROJECT-CONTEXT: Kafka publish is post-commit (ReturnEventProducer uses afterCommit).
     * WalletMutationDelegate.doReturnCreditInTx() is REQUIRES_NEW — idempotency guard
     * inside the delegate prevents double-credit if webhook and admin resolve race.
     */
    @Transactional
    public void processVendorConfirmation(String vendorReturnReference, boolean confirmed, String failureReason) {
        if (vendorReturnReference == null || vendorReturnReference.isBlank()) {
            log.warn("processVendorConfirmation called with blank vendorReturnReference — ignoring");
            return;
        }

        Optional<RedemptionReturn> retOpt = returnRepository.findByVendorReturnReference(vendorReturnReference);
        if (retOpt.isEmpty()) {
            log.warn("step=return_webhook_unknown_reference vendorReturnReference={}", vendorReturnReference);
            return;
        }
        RedemptionReturn ret = retOpt.get();

        // Idempotency guard: terminal + RETURN_TIMED_OUT states are final
        if (TERMINAL_STATUSES.contains(ret.getStatus())) {
            log.warn("step=return_webhook_duplicate returnId={} status={} vendorReturnReference={}",
                    ret.getId(), ret.getStatus(), vendorReturnReference);
            return;
        }

        Instant now = Instant.now();

        if (confirmed) {
            // Look up the original redemption to get the walletId for the credit
            RedemptionRequest originalRedemption = redemptionRequestRepository
                    .findByIdAndClientId(ret.getRedemptionId(), ret.getClientId())
                    .orElse(null);
            if (originalRedemption == null) {
                log.warn("processVendorConfirmation original redemption not found returnId={} redemptionId={}",
                        ret.getId(), ret.getRedemptionId());
                // Cannot credit without wallet reference — still transition to RETURN_CONFIRMED
                // so the return lifecycle is consistent; admin can investigate wallet manually
            } else {
                // REQUIRES_NEW — suspends current transaction; idempotency guard inside delegate
                // prevents double-credit on concurrent webhook + admin resolve race
                walletMutationDelegate.doReturnCreditInTx(
                        originalRedemption.getWalletId(),
                        ret.getAmount(),
                        "RETURN",
                        ret.getId());
            }

            ret.setStatus(ReturnStatus.RETURN_CONFIRMED);
            ret.setConfirmedAt(now);
            returnRepository.save(ret);

            log.info("step=return_confirmed returnId={} vendorReturnReference={}",
                    ret.getId(), vendorReturnReference);
            returnEventProducer.publishReturnConfirmed(ret);

        } else {
            // Rejected by vendor — no wallet credit
            ret.setStatus(ReturnStatus.RETURN_REJECTED);
            ret.setRejectedAt(now);
            ret.setReviewNotes(failureReason);
            returnRepository.save(ret);

            log.info("step=return_vendor_rejected returnId={} failureReason={}",
                    ret.getId(), failureReason);
            returnEventProducer.publishReturnRejected(ret);
        }
    }

    /**
     * Admin manual resolution of a RETURN_TIMED_OUT return.
     * CONFIRM path: credits wallet via WalletMutationDelegate, transitions to RETURN_CONFIRMED.
     * REJECT path: transitions to RETURN_REJECTED, no wallet credit.
     */
    @Transactional
    public ReturnDetailResponse resolveTimedOut(UUID id,
                                                ReturnResolution resolution,
                                                String notes,
                                                UUID reviewerId,
                                                UUID clientId) {
        RedemptionReturn ret = returnRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionReturn", "id", id));

        if (!ReturnStatus.RETURN_TIMED_OUT.equals(ret.getStatus())) {
            log.warn("resolveTimedOut wrong state returnId={} status={}", id, ret.getStatus());
            throw new StateConflictException("Only RETURN_TIMED_OUT returns can be resolved");
        }

        Instant now = Instant.now();
        ret.setReviewedBy(reviewerId);
        ret.setReviewedAt(now);
        ret.setReviewNotes(notes);

        if (ReturnResolution.CONFIRM.equals(resolution)) {
            RedemptionRequest originalRedemption = redemptionRequestRepository
                    .findByIdAndClientId(ret.getRedemptionId(), clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", ret.getRedemptionId()));

            // REQUIRES_NEW — idempotency guard inside delegate prevents double-credit
            // if a late Xoxoday webhook also fires
            walletMutationDelegate.doReturnCreditInTx(
                    originalRedemption.getWalletId(),
                    ret.getAmount(),
                    "RETURN",
                    ret.getId());

            ret.setStatus(ReturnStatus.RETURN_CONFIRMED);
            ret.setConfirmedAt(now);
            returnRepository.save(ret);

            log.info("step=return_timed_out_resolved_confirmed returnId={} reviewerId={}", id, reviewerId);
            returnEventProducer.publishReturnConfirmed(ret);

            // Audit: COMPLETED path — spec BE-4; must be per-path because a single @Audited
            // on the controller method cannot branch on resolution value.
            // Deferred to afterCommit so a rollback (e.g. optimistic-lock conflict) does not
            // produce a phantom audit record for a resolution that never durably persisted.
            final UUID confirmedRetId = ret.getId();
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        auditLogService.logAsync(AuditAction.COMPLETED, AuditResourceType.REDEMPTION_RETURN,
                                confirmedRetId, null, "Manually confirmed timed-out return", null);
                    }
                });
            } else {
                auditLogService.logAsync(AuditAction.COMPLETED, AuditResourceType.REDEMPTION_RETURN,
                        confirmedRetId, null, "Manually confirmed timed-out return", null);
            }
        } else if (ReturnResolution.REJECT.equals(resolution)) {
            ret.setStatus(ReturnStatus.RETURN_REJECTED);
            ret.setRejectedAt(now);
            returnRepository.save(ret);

            log.info("step=return_timed_out_resolved_rejected returnId={} reviewerId={}", id, reviewerId);
            returnEventProducer.publishReturnRejected(ret);

            // Audit: REJECTED path — spec BE-4; must be per-path because a single @Audited
            // on the controller method cannot branch on resolution value.
            // Deferred to afterCommit so a rollback does not produce a phantom audit record.
            final UUID rejectedRetId = ret.getId();
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        auditLogService.logAsync(AuditAction.REJECTED, AuditResourceType.REDEMPTION_RETURN,
                                rejectedRetId, null, "Manually rejected timed-out return", null);
                    }
                });
            } else {
                auditLogService.logAsync(AuditAction.REJECTED, AuditResourceType.REDEMPTION_RETURN,
                        rejectedRetId, null, "Manually rejected timed-out return", null);
            }
        } else {
            throw new IllegalArgumentException("Unsupported resolution value: " + resolution);
        }

        String catalogName = resolveCatalogName(ret.getRedemptionId(), clientId);
        String partnerDisplayName = resolveDisplayName(ret.getPartnerUserId());
        return ReturnDetailResponse.from(ret, catalogName, partnerDisplayName);
    }

    /**
     * Admin paginated list of all returns for the tenant.
     * Supports optional status, startDate (createdAt >=), endDate (createdAt <=) filters.
     * Batch-loads catalog names, partner display names, and company names to avoid N+1.
     *
     * @param clientId  tenant ID (JWT-derived, never from the request body)
     * @param status    optional status filter
     * @param startDate optional lower bound for createdAt (inclusive), interpreted as 00:00 UTC
     * @param endDate   optional upper bound for createdAt (inclusive), interpreted as 23:59:59.999 UTC
     * @param pageable  sort + pagination
     */
    @Transactional(readOnly = true)
    public Page<ReturnQueueItemResponse> getAdminReturns(
            UUID clientId,
            ReturnStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        Instant startInstant = startDate != null ? startDate.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        // Exclusive upper bound: endDate + 1 day at 00:00 UTC so the JPQL < :endDate predicate
        // covers any sub-millisecond timestamp on the calendar day without precision gaps.
        Instant endInstant = endDate != null ? endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        Page<RedemptionReturn> page = returnRepository.findByClientIdWithFilters(
                clientId, status != null ? status.name() : null, startInstant, endInstant, pageable);

        // Batch-load catalog names keyed by redemptionId
        List<UUID> redemptionIds = page.getContent().stream()
                .map(RedemptionReturn::getRedemptionId)
                .distinct()
                .toList();
        Map<UUID, String> catalogNameByRedemptionId = loadCatalogNamesByRedemptionId(redemptionIds, clientId);

        // Batch-load partner users
        List<UUID> partnerUserIds = page.getContent().stream()
                .map(RedemptionReturn::getPartnerUserId)
                .distinct()
                .toList();
        Map<UUID, User> usersById = userRepository.findAllById(partnerUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Batch-load partner companies keyed by partnerCompanyId
        List<UUID> companyIds = usersById.values().stream()
                .map(User::getPartnerCompanyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, String> companyNameById = companyIds.isEmpty()
                ? Collections.emptyMap()
                : partnerCompanyRepository.findAllById(companyIds).stream()
                        .collect(Collectors.toMap(PartnerCompany::getId, PartnerCompany::getName));

        return page.map(ret -> {
            User user = usersById.get(ret.getPartnerUserId());
            String displayName = user != null
                    ? String.join(" ",
                            Objects.toString(user.getFirstName(), ""),
                            Objects.toString(user.getLastName(), "")).strip()
                    : "(unknown)";
            String companyName = user != null && user.getPartnerCompanyId() != null
                    ? companyNameById.getOrDefault(user.getPartnerCompanyId(), "(unknown)")
                    : "(unknown)";
            String catalogName = catalogNameByRedemptionId.getOrDefault(ret.getRedemptionId(), "(unknown)");
            return ReturnQueueItemResponse.from(ret, catalogName, displayName, companyName);
        });
    }

    /**
     * Computes isReturnEligible for a single RedemptionRequest.
     * Hits the DB for the active-return check — use the batch overload in list contexts.
     */
    @Transactional(readOnly = true)
    public boolean isReturnEligible(RedemptionRequest req, RedemptionCatalogItem catalogItem, UUID clientId) {
        return isReturnEligibleInner(req, catalogItem, clientId,
                returnRepository.existsByRedemptionIdAndClientIdAndStatusNotIn(
                        req.getId(), clientId, RESUBMIT_ALLOWED_STATUS_NAMES));
    }

    /**
     * Batch-aware overload: pre-loaded set of redemptionIds that already have active returns.
     * Call {@link #getRedemptionIdsWithActiveReturns} once per page, then pass the result here
     * to avoid one DB round-trip per item.
     */
    public boolean isReturnEligible(RedemptionRequest req, RedemptionCatalogItem catalogItem,
                                    UUID clientId, Set<UUID> redemptionIdsWithActiveReturns) {
        return isReturnEligibleInner(req, catalogItem, clientId, redemptionIdsWithActiveReturns.contains(req.getId()));
    }

    private boolean isReturnEligibleInner(RedemptionRequest req, RedemptionCatalogItem catalogItem,
                                          UUID clientId, boolean hasActiveReturn) {
        if (!RedemptionStatus.COMPLETED.equals(req.getStatus())) return false;
        if (!RedemptionCategory.NON_CASH.equals(req.getCategory())) return false;
        if (!catalogItem.isReturnable()) return false;
        if (req.getCompletedAt() == null) return false;
        int windowDays = resolveEffectiveWindowDays(catalogItem.getId(), clientId,
                catalogItem.getDefaultReturnWindowDays());
        Instant windowExpiry = req.getCompletedAt().plus(windowDays, ChronoUnit.DAYS);
        if (Instant.now().isAfter(windowExpiry)) return false;
        return !hasActiveReturn;
    }

    /**
     * Returns the subset of the given redemptionIds that already have an active return.
     * Use this once per page in list endpoints, then pass the result to the batch
     * {@link #isReturnEligible(RedemptionRequest, RedemptionCatalogItem, Set)} overload.
     */
    @Transactional(readOnly = true)
    public Set<UUID> getRedemptionIdsWithActiveReturns(List<UUID> redemptionIds, UUID clientId) {
        if (redemptionIds.isEmpty()) return Set.of();
        return returnRepository.findRedemptionIdsWithActiveReturns(redemptionIds, clientId, RESUBMIT_ALLOWED_STATUS_NAMES);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Effective return window = tenant override if configured, otherwise catalog-item default.
     * Null override is treated as "not set" — falls back to defaultDays.
     */
    private int resolveEffectiveWindowDays(UUID catalogItemId, UUID clientId, int defaultDays) {
        return clientCatalogItemConfigRepository
                .findByClientIdAndRedemptionCatalogItemId(clientId, catalogItemId)
                .map(ClientCatalogItemConfig::getReturnWindowDaysOverride)
                .filter(v -> v != null)
                .orElse(defaultDays);
    }

    private String resolveDisplayName(UUID userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            log.warn("resolveDisplayName user not found userId={}", userId);
            return "(unknown)";
        }
        return String.join(" ",
                Objects.toString(user.get().getFirstName(), ""),
                Objects.toString(user.get().getLastName(), "")).strip();
    }

    private String resolveCatalogName(UUID redemptionId, UUID clientId) {
        return redemptionRequestRepository.findByIdAndClientId(redemptionId, clientId)
                .map(req -> catalogItemRepository.findById(req.getCatalogItemId())
                        .map(RedemptionCatalogItem::getName)
                        .orElse("(unknown)"))
                .orElse("(unknown)");
    }

    /**
     * Batch-load catalog names keyed by redemptionId to avoid N+1 in list endpoints.
     */
    private Map<UUID, String> loadCatalogNamesByRedemptionId(List<UUID> redemptionIds, UUID clientId) {
        if (redemptionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<RedemptionRequest> redemptions = redemptionRequestRepository.findByIdInAndClientId(redemptionIds, clientId);

        List<UUID> catalogItemIds = redemptions.stream()
                .map(RedemptionRequest::getCatalogItemId)
                .distinct()
                .toList();
        Map<UUID, String> namesByCatalogId = catalogItemRepository.findAllById(catalogItemIds).stream()
                .collect(Collectors.toMap(RedemptionCatalogItem::getId, RedemptionCatalogItem::getName));

        return redemptions.stream()
                .collect(Collectors.toMap(
                        RedemptionRequest::getId,
                        req -> namesByCatalogId.getOrDefault(req.getCatalogItemId(), "(unknown)")));
    }

    /**
     * Build a partner-facing ReturnDetailResponse that nulls out admin-only fields
     * (reviewNotes, vendorReturnReference) without mutating the entity.
     */
    private ReturnDetailResponse buildPartnerDetailResponse(
            RedemptionReturn ret, String catalogName, String partnerDisplayName) {
        return new ReturnDetailResponse(
                ret.getId(),
                ret.getRedemptionId(),
                catalogName,
                partnerDisplayName,
                ret.getAmount(),
                ret.getCurrencyId(),
                ret.getStatus(),
                ret.getReason(),
                ret.getReviewedAt(),
                null,   // reviewNotes — admin-only, excluded for partner
                null,   // vendorReturnReference — admin-only, excluded for partner
                ret.getApprovedAt(),
                ret.getTimedOutAt(),
                ret.getConfirmedAt(),
                ret.getRejectedAt(),
                ret.getCancelledAt(),
                ret.getCreatedAt(),
                ret.getUpdatedAt()
        );
    }
}
