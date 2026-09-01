package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.response.RedemptionRequestDetailResponse;
import com.tenxengage.app.dto.response.redemption.ApprovalQueueItemResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionRequestType;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.event.RedemptionApprovedEvent;
import com.tenxengage.app.event.RedemptionRejectedEvent;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.exception.StateConflictException;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.RedemptionOrchestrationService;
import com.tenxengage.app.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RedemptionApprovalService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionApprovalService.class);

    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final TenantValidator tenantValidator;
    private final RedemptionOrchestrationService redemptionOrchestrationService;
    private final WalletService walletService;
    private final NotificationEventProducer notificationEventProducer;
    private final ApplicationEventPublisher eventPublisher;

    public RedemptionApprovalService(RedemptionRequestRepository redemptionRequestRepository,
                                     RedemptionCatalogItemRepository catalogItemRepository,
                                     TenantValidator tenantValidator,
                                     RedemptionOrchestrationService redemptionOrchestrationService,
                                     WalletService walletService,
                                     NotificationEventProducer notificationEventProducer,
                                     ApplicationEventPublisher eventPublisher) {
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.tenantValidator = tenantValidator;
        this.redemptionOrchestrationService = redemptionOrchestrationService;
        this.walletService = walletService;
        this.notificationEventProducer = notificationEventProducer;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<ApprovalQueueItemResponse> getApprovalQueue(
            String currencyId,
            UUID catalogItemId,
            LocalDate startDate,
            LocalDate endDate,
            RedemptionRequestType requestType,
            Pageable pageable) {

        if (requestType == RedemptionRequestType.RETURN) {
            return Page.empty(pageable);
        }

        if (tenantValidator.isTenxAdmin()) {
            throw new AccessDeniedException("Platform admins cannot access tenant approval queues");
        }

        UUID clientId = tenantValidator.getCurrentClientId();
        Instant startInstant = startDate != null ? startDate.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant endInstant = endDate != null ? endDate.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC) : null;

        return redemptionRequestRepository
                .findApprovalQueue(clientId, currencyId, catalogItemId, startInstant, endInstant, pageable)
                .map(ApprovalQueueItemResponse::from);
    }

    @Transactional
    public RedemptionRequestDetailResponse approveRedemption(UUID redemptionId, UUID approverId) {
        if (tenantValidator.isTenxAdmin()) {
            throw new AccessDeniedException("Platform admins cannot approve tenant redemptions");
        }

        UUID clientId = tenantValidator.getCurrentClientId();

        RedemptionRequest request = redemptionRequestRepository
                .findByIdAndClientIdForUpdate(redemptionId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", redemptionId));

        if (request.getStatus() != RedemptionStatus.PENDING_APPROVAL) {
            throw new StateConflictException("Redemption is not in PENDING_APPROVAL state");
        }

        // NON_CASH vendor (Xoxoday) not yet integrated — blocked on credentials (US-06 BE-1).
        // Blocking here keeps the item in PENDING_APPROVAL so the admin can re-action it
        // once US-06 ships. Remove this guard when Xoxoday dispatch is implemented.
        if (request.getCategory() == RedemptionCategory.NON_CASH) {
            throw new StateConflictException(
                    "NON_CASH redemption approval is not yet supported — Xoxoday vendor integration pending (US-06)");
        }

        request.setStatus(RedemptionStatus.RESERVED);
        request.setReviewedBy(approverId);
        request.setReviewedAt(Instant.now());

        RedemptionRequest saved = redemptionRequestRepository.save(request);

        // Dispatch is deferred to AFTER_COMMIT via onRedemptionApproved — ensures XTRM is
        // only called after the approval row is durably committed, preventing phantom transfers
        // on DB rollback. Recovery for post-commit dispatch failures is tracked in US-06.
        eventPublisher.publishEvent(new RedemptionApprovedEvent(this, saved, approverId));

        var catalogItemApprove = catalogItemRepository.findById(saved.getCatalogItemId());
        String catalogItemName = catalogItemApprove.map(RedemptionCatalogItem::getName).orElse("(unknown)");
        String imageUrl = catalogItemApprove.map(RedemptionCatalogItem::getImageUrl).orElse(null);

        return RedemptionRequestDetailResponse.from(saved, catalogItemName, imageUrl);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRedemptionApproved(RedemptionApprovedEvent event) {
        RedemptionRequest request = event.getRequest();

        // CASH dispatch fires post-commit — safe to call XTRM now that the approval row is durable.
        // NON_CASH (Xoxoday) is blocked at the service layer until US-06 BE-1 is implemented.
        if (request.getCategory() == RedemptionCategory.CASH) {
            // Mark attempt BEFORE calling vendor so recovery can distinguish
            // "never attempted" (dispatchAttemptedAt IS NULL) from "attempted but ambiguous".
            redemptionRequestRepository.findById(request.getId()).ifPresent(fresh -> {
                fresh.setDispatchAttemptedAt(java.time.Instant.now());
                redemptionRequestRepository.save(fresh);
            });
            try {
                redemptionOrchestrationService.dispatch(request);
                // Persist vendorReferenceId set on the detached entity by the vendor service.
                // Required so the webhook can validate the callback belongs to this transfer.
                if (request.getVendorReferenceId() != null) {
                    final String vendorRef = request.getVendorReferenceId();
                    redemptionRequestRepository.findById(request.getId()).ifPresent(fresh -> {
                        fresh.setVendorReferenceId(vendorRef);
                        redemptionRequestRepository.save(fresh);
                    });
                }
            } catch (Exception e) {
                // Do NOT release wallet or mark FAILED on any dispatch exception.
                // A timeout or dropped-response means the vendor MAY have accepted the transfer —
                // releasing funds now could create a double-payment if a success webhook arrives later.
                // Leave the request in RESERVED and log for manual reconciliation.
                log.error("[APPROVAL_DISPATCH_AMBIGUOUS] redemptionId={} — outcome unknown, leaving in RESERVED for manual reconciliation",
                        request.getId(), e);
            }
        }

        NotificationEvent notification = new NotificationEvent(
                "redemption.approved",
                request.getClientId(),
                "Redemption approved",
                "Your redemption request has been approved.",
                "REDEMPTION_REQUEST",
                request.getId(),
                event.getApproverId(),
                List.of(request.getUserId()),
                Map.of()
        );
        try {
            notificationEventProducer.publish(notification);
        } catch (Exception e) {
            log.warn("Failed to publish redemption.approved notification for redemptionId={}", request.getId(), e);
        }
    }

    @Transactional
    public RedemptionRequestDetailResponse rejectRedemption(UUID redemptionId,
                                                             String rejectionReason,
                                                             UUID approverId) {
        if (tenantValidator.isTenxAdmin()) {
            throw new AccessDeniedException("Platform admins cannot reject tenant redemptions");
        }

        UUID clientId = tenantValidator.getCurrentClientId();

        RedemptionRequest request = redemptionRequestRepository
                .findByIdAndClientIdForUpdate(redemptionId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", redemptionId));

        if (request.getStatus() != RedemptionStatus.PENDING_APPROVAL) {
            throw new StateConflictException("Redemption is not in PENDING_APPROVAL state");
        }

        request.setStatus(RedemptionStatus.CANCELLED);
        request.setReviewedBy(approverId);
        request.setReviewedAt(Instant.now());
        request.setRejectionReason(rejectionReason);

        walletService.releaseReservedBalance(request);

        RedemptionRequest saved = redemptionRequestRepository.save(request);

        eventPublisher.publishEvent(new RedemptionRejectedEvent(this, saved, approverId));

        var catalogItemReject = catalogItemRepository.findById(saved.getCatalogItemId());
        String catalogItemNameReject = catalogItemReject.map(RedemptionCatalogItem::getName).orElse("(unknown)");
        String imageUrlReject = catalogItemReject.map(RedemptionCatalogItem::getImageUrl).orElse(null);

        return RedemptionRequestDetailResponse.from(saved, catalogItemNameReject, imageUrlReject);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRedemptionRejected(RedemptionRejectedEvent event) {
        RedemptionRequest request = event.getRequest();
        NotificationEvent notification = new NotificationEvent(
                "redemption.rejected",
                request.getClientId(),
                "Redemption rejected",
                "Your redemption request has been rejected.",
                "REDEMPTION_REQUEST",
                request.getId(),
                event.getApproverId(),
                List.of(request.getUserId()),
                Map.of()
        );
        try {
            notificationEventProducer.publish(notification);
        } catch (Exception e) {
            log.warn("Failed to publish redemption.rejected notification for redemptionId={}", request.getId(), e);
        }
    }
}
