package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.IncentiveDetailResponse;
import com.tenxengage.app.entity.ApprovalDecisionEntity;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveApprover;
import com.tenxengage.app.entity.enums.ApprovalDecision;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.ApprovalDecisionRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalTokenService tokenService;
    private final ApprovalDecisionRepository decisionRepository;
    private final IncentiveRepository incentiveRepository;
    private final IncentiveService incentiveService;
    private final NotificationEventProducer notificationEventProducer;
    private final AuditLogService auditLogService;

    public ApprovalService(ApprovalTokenService tokenService,
                           ApprovalDecisionRepository decisionRepository,
                           IncentiveRepository incentiveRepository,
                           IncentiveService incentiveService,
                           NotificationEventProducer notificationEventProducer,
                           AuditLogService auditLogService) {
        this.tokenService = tokenService;
        this.decisionRepository = decisionRepository;
        this.incentiveRepository = incentiveRepository;
        this.incentiveService = incentiveService;
        this.notificationEventProducer = notificationEventProducer;
        this.auditLogService = auditLogService;
    }

    public record ApprovalResult(boolean success, String message, String action) {}

    /** Result of validating a token for the review page. */
    public record ApprovalReviewResult(
        boolean valid,
        String rejectReason,
        String priorDecision,
        IncentiveDetailResponse incentiveDetail,
        String approverEmail,
        String approverCategory
    ) {
        public static ApprovalReviewResult rejected(String reason, String decision) {
            return new ApprovalReviewResult(false, reason, decision, null, null, null);
        }
        public static ApprovalReviewResult ok(IncentiveDetailResponse detail, String email, String category) {
            return new ApprovalReviewResult(true, null, null, detail, email, category);
        }
    }

    @Transactional(readOnly = true)
    public ApprovalReviewResult getIncentiveForApproval(String token) {
        ApprovalTokenService.ApprovalClaims claims;
        try {
            claims = tokenService.parseApprovalToken(token);
        } catch (ExpiredJwtException e) {
            return ApprovalReviewResult.rejected("expired", null);
        } catch (JwtException e) {
            return ApprovalReviewResult.rejected("invalid", null);
        }

        Incentive incentive = incentiveRepository.findById(claims.incentiveId()).orElse(null);
        if (incentive == null) {
            return ApprovalReviewResult.rejected("invalid", null);
        }

        // Validate approval round
        if (claims.approvalRound() != incentive.getApprovalRound()) {
            return ApprovalReviewResult.rejected("expired", "expired");
        }

        // Check if already decided
        Optional<ApprovalDecisionEntity> existing = decisionRepository
            .findByIncentiveIdAndApproverEmail(claims.incentiveId(), claims.approverEmail());
        if (existing.isPresent()) {
            String decision = existing.get().getDecision().name().toLowerCase();
            return ApprovalReviewResult.rejected("already_decided", decision);
        }

        // Build detail from the already-fetched (token-validated) incentive
        IncentiveDetailResponse detail = incentiveService.toDetailResponseForApproval(incentive);

        // Find approver category
        String approverCategory = "";
        if (incentive.getApprovers() != null) {
            approverCategory = incentive.getApprovers().stream()
                .filter(a -> a.getEmail().equalsIgnoreCase(claims.approverEmail()))
                .map(IncentiveApprover::getCategory)
                .findFirst()
                .orElse("");
        }

        return ApprovalReviewResult.ok(detail, claims.approverEmail(), approverCategory);
    }

    @Transactional
    public ApprovalResult processApproval(String token, String action, String comment) {
        // Parse and validate action
        ApprovalDecision decision;
        try {
            decision = ApprovalDecision.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException e) {
            return new ApprovalResult(false, "Invalid action. Must be APPROVED or REJECTED.", null);
        }

        // Validate comment is required on rejection
        if (decision == ApprovalDecision.REJECTED && (comment == null || comment.isBlank())) {
            return new ApprovalResult(false, "A comment is required when rejecting.", null);
        }

        // Parse and validate token
        ApprovalTokenService.ApprovalClaims claims;
        try {
            claims = tokenService.parseApprovalToken(token);
        } catch (ExpiredJwtException e) {
            return new ApprovalResult(false, "This approval link has expired.", null);
        } catch (JwtException e) {
            return new ApprovalResult(false, "Invalid approval link.", null);
        }

        UUID incentiveId = claims.incentiveId();
        String approverEmail = claims.approverEmail();
        UUID tokenId = claims.tokenId();

        // Check token reuse
        if (decisionRepository.findByTokenId(tokenId).isPresent()) {
            return new ApprovalResult(false, "This approval link has already been used.", null);
        }

        // Check duplicate decision by same approver
        if (decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail).isPresent()) {
            return new ApprovalResult(false, "You have already submitted your decision for this incentive.", null);
        }

        // Validate incentive is in PENDING_APPROVAL
        Incentive incentive = incentiveRepository.findById(incentiveId).orElse(null);
        if (incentive == null) {
            return new ApprovalResult(false, "Incentive not found.", null);
        }
        if (incentive.getStatus() != IncentiveStatus.PENDING_APPROVAL) {
            return new ApprovalResult(false, "This incentive is no longer pending approval.", null);
        }

        // Validate approval round — reject tokens from previous rounds
        if (claims.approvalRound() != incentive.getApprovalRound()) {
            return new ApprovalResult(false, "This approval link is no longer valid. The incentive has been resubmitted.", null);
        }

        // Record the decision
        ApprovalDecisionEntity decisionEntity = ApprovalDecisionEntity.builder()
            .incentive(incentive)
            .approverEmail(approverEmail)
            .decision(decision)
            .decidedAt(Instant.now())
            .tokenId(tokenId)
            .comment(comment)
            .build();
        decisionRepository.save(decisionEntity);
        log.info("Approval decision recorded: incentive={}, approver={}, decision={}", incentiveId, approverEmail, decision);

        // Audit the approval decision
        String decisionLabel = decision == ApprovalDecision.APPROVED ? "approved" : "rejected";
        try {
            AuditAction auditAction = decision == ApprovalDecision.APPROVED ? AuditAction.APPROVED : AuditAction.REJECTED;
            auditLogService.logWithActor(auditAction, AuditResourceType.INCENTIVE, incentiveId,
                    incentive.getName(), approverEmail + " " + decisionLabel + " incentive '" + incentive.getName() + "'",
                    incentive.getClientId(), approverEmail, approverEmail, null);
        } catch (Exception e) {
            log.warn("Failed to write approval audit log: {}", e.getMessage());
        }

        // Notify about approval decision
        notificationEventProducer.publish(new NotificationEvent(
            "INCENTIVE_APPROVAL_DECISION", incentive.getClientId(),
            "Approval Decision: " + incentive.getName(),
            approverEmail + " has " + decision.name().toLowerCase() + " incentive '" + incentive.getName() + "'.",
            "INCENTIVE", incentiveId, null, null, null));

        // Check threshold for auto-activation or auto-denial
        int requiredApprovals = incentive.getRequiredApprovals() != null ? incentive.getRequiredApprovals() : 1;
        int totalApprovers = incentive.getApprovers() != null ? incentive.getApprovers().size() : 1;

        if (decision == ApprovalDecision.APPROVED) {
            long approvalCount = decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.APPROVED);
            if (approvalCount >= requiredApprovals) {
                incentive.setStatus(IncentiveStatus.ACTIVE);
                incentive.setStatusChangedAt(Instant.now());
                incentiveRepository.save(incentive);
                log.info("Incentive {} auto-activated: {} approvals met threshold of {}", incentiveId, approvalCount, requiredApprovals);

                notificationEventProducer.publish(new NotificationEvent(
                    "INCENTIVE_ACTIVATED", incentive.getClientId(),
                    "Incentive Now Active: " + incentive.getName(),
                    "Incentive '" + incentive.getName() + "' has been approved and is now active.",
                    "INCENTIVE", incentiveId, null, null,
                    Map.of("incentiveId", incentiveId.toString())));
            }
        } else if (decision == ApprovalDecision.REJECTED) {
            long rejectionCount = decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.REJECTED);
            // Deny if it's no longer possible to reach required approvals
            int remainingApprovers = totalApprovers - (int) decisionRepository.countByIncentiveId(incentiveId);
            long currentApprovals = decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.APPROVED);
            if (currentApprovals + remainingApprovers < requiredApprovals) {
                incentive.setStatus(IncentiveStatus.DENIED);
                incentive.setStatusChangedAt(Instant.now());
                incentiveRepository.save(incentive);
                log.info("Incentive {} auto-denied: {} rejections, only {} possible approvals remaining (need {})",
                    incentiveId, rejectionCount, currentApprovals + remainingApprovers, requiredApprovals);

                notificationEventProducer.publish(new NotificationEvent(
                    "INCENTIVE_DENIED", incentive.getClientId(),
                    "Incentive Denied: " + incentive.getName(),
                    "Incentive '" + incentive.getName() + "' has been denied — insufficient approvals.",
                    "INCENTIVE", incentiveId, null, null, null));
            }
        }

        return new ApprovalResult(true, "You have " + decisionLabel + " this incentive.", decisionLabel);
    }
}
