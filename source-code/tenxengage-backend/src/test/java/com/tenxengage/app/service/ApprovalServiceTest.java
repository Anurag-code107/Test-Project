package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.IncentiveDetailResponse;
import com.tenxengage.app.entity.ApprovalDecisionEntity;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveApprover;
import com.tenxengage.app.entity.enums.ApprovalDecision;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.ApprovalDecisionRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalTokenService tokenService;
    @Mock
    private ApprovalDecisionRepository decisionRepository;
    @Mock
    private IncentiveRepository incentiveRepository;
    @Mock
    private IncentiveService incentiveService;
    @Mock
    private NotificationEventProducer notificationEventProducer;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ApprovalService approvalService;

    private UUID incentiveId;
    private UUID clientId;
    private UUID tokenId;
    private String approverEmail;
    private Incentive pendingIncentive;
    private ApprovalTokenService.ApprovalClaims validClaims;

    @BeforeEach
    void setUp() {
        incentiveId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        tokenId = UUID.randomUUID();
        approverEmail = "approver@test.com";

        validClaims = new ApprovalTokenService.ApprovalClaims(
                incentiveId, approverEmail, tokenId, 1);

        pendingIncentive = Incentive.builder()
                .name("Test Incentive")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.PENDING_APPROVAL)
                .clientId(clientId)
                .createdBy(UUID.randomUUID())
                .requiresApproval(true)
                .requiredApprovals(1)
                .approvalRound(1)
                .approvers(new ArrayList<>(List.of(
                        IncentiveApprover.builder()
                                .email(approverEmail)
                                .category("FINANCE")
                                .build())))
                .build();
        pendingIncentive.setId(incentiveId);
    }

    // -------------------------------------------------------------------------
    // getIncentiveForApproval()
    // -------------------------------------------------------------------------

    @Test
    void getIncentiveForApproval_returnsDetailForValidToken() {
        IncentiveDetailResponse mockDetail = mock(IncentiveDetailResponse.class);

        when(tokenService.parseApprovalToken("valid-token")).thenReturn(validClaims);
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.empty());
        when(incentiveService.toDetailResponseForApproval(pendingIncentive)).thenReturn(mockDetail);

        ApprovalService.ApprovalReviewResult result = approvalService.getIncentiveForApproval("valid-token");

        assertThat(result.valid()).isTrue();
        assertThat(result.approverEmail()).isEqualTo(approverEmail);
        assertThat(result.approverCategory()).isEqualTo("FINANCE");
        assertThat(result.incentiveDetail()).isEqualTo(mockDetail);
    }

    @Test
    void getIncentiveForApproval_rejectsExpiredToken() {
        when(tokenService.parseApprovalToken("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        ApprovalService.ApprovalReviewResult result = approvalService.getIncentiveForApproval("expired");

        assertThat(result.valid()).isFalse();
        assertThat(result.rejectReason()).isEqualTo("expired");
    }

    @Test
    void getIncentiveForApproval_rejectsInvalidToken() {
        when(tokenService.parseApprovalToken("invalid")).thenThrow(new JwtException("bad"));

        ApprovalService.ApprovalReviewResult result = approvalService.getIncentiveForApproval("invalid");

        assertThat(result.valid()).isFalse();
        assertThat(result.rejectReason()).isEqualTo("invalid");
    }

    @Test
    void getIncentiveForApproval_rejectsOldApprovalRound() {
        pendingIncentive.setApprovalRound(2); // incentive resubmitted at round 2

        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims); // claims have round 1
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));

        ApprovalService.ApprovalReviewResult result = approvalService.getIncentiveForApproval("token");

        assertThat(result.valid()).isFalse();
        assertThat(result.rejectReason()).isEqualTo("expired");
    }

    @Test
    void getIncentiveForApproval_rejectsAlreadyDecided() {
        ApprovalDecisionEntity prior = ApprovalDecisionEntity.builder()
                .decision(ApprovalDecision.APPROVED).build();

        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.of(prior));

        ApprovalService.ApprovalReviewResult result = approvalService.getIncentiveForApproval("token");

        assertThat(result.valid()).isFalse();
        assertThat(result.rejectReason()).isEqualTo("already_decided");
        assertThat(result.priorDecision()).isEqualTo("approved");
    }

    // -------------------------------------------------------------------------
    // processApproval()
    // -------------------------------------------------------------------------

    @Test
    void processApproval_recordsApprovedDecision() {
        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(decisionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.empty());
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));
        when(decisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.APPROVED)).thenReturn(1L);

        ApprovalService.ApprovalResult result = approvalService.processApproval("token", "approved", null);

        assertThat(result.success()).isTrue();
        assertThat(result.action()).isEqualTo("approved");

        ArgumentCaptor<ApprovalDecisionEntity> captor = ArgumentCaptor.forClass(ApprovalDecisionEntity.class);
        verify(decisionRepository).save(captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(captor.getValue().getApproverEmail()).isEqualTo(approverEmail);
    }

    @Test
    void processApproval_recordsRejectedDecision() {
        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(decisionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.empty());
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));
        when(decisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.REJECTED)).thenReturn(1L);
        when(decisionRepository.countByIncentiveId(incentiveId)).thenReturn(1L);
        when(decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.APPROVED)).thenReturn(0L);

        ApprovalService.ApprovalResult result = approvalService.processApproval("token", "rejected", "Budget too high");

        assertThat(result.success()).isTrue();
        assertThat(result.action()).isEqualTo("rejected");

        ArgumentCaptor<ApprovalDecisionEntity> captor = ArgumentCaptor.forClass(ApprovalDecisionEntity.class);
        verify(decisionRepository).save(captor.capture());
        assertThat(captor.getValue().getComment()).isEqualTo("Budget too high");
    }

    @Test
    void processApproval_requiresCommentOnRejection() {
        ApprovalService.ApprovalResult result = approvalService.processApproval("token", "rejected", null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("comment is required");
    }

    @Test
    void processApproval_requiresCommentOnRejection_blank() {
        ApprovalService.ApprovalResult result = approvalService.processApproval("token", "rejected", "   ");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("comment is required");
    }

    @Test
    void processApproval_rejectsInvalidAction() {
        ApprovalService.ApprovalResult result = approvalService.processApproval("token", "maybe", null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Invalid action");
    }

    @Test
    void processApproval_rejectsExpiredToken() {
        when(tokenService.parseApprovalToken("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        ApprovalService.ApprovalResult result = approvalService.processApproval("expired", "approved", null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("expired");
    }

    @Test
    void processApproval_rejectsReusedToken() {
        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(decisionRepository.findByTokenId(tokenId))
                .thenReturn(Optional.of(ApprovalDecisionEntity.builder().build()));

        ApprovalService.ApprovalResult result = approvalService.processApproval("token", "approved", null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("already been used");
    }

    @Test
    void processApproval_rejectsDuplicateDecision() {
        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(decisionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.of(ApprovalDecisionEntity.builder().build()));

        ApprovalService.ApprovalResult result = approvalService.processApproval("token", "approved", null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("already submitted");
    }

    @Test
    void processApproval_rejectsNonPendingIncentive() {
        pendingIncentive.setStatus(IncentiveStatus.ACTIVE);

        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(decisionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.empty());
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));

        ApprovalService.ApprovalResult result = approvalService.processApproval("token", "approved", null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("no longer pending");
    }

    @Test
    void processApproval_rejectsOldRoundToken() {
        pendingIncentive.setApprovalRound(2);

        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(decisionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.empty());
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));

        ApprovalService.ApprovalResult result = approvalService.processApproval("token", "approved", null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("resubmitted");
    }

    @Test
    void processApproval_autoActivatesWhenThresholdMet() {
        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(decisionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.empty());
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));
        when(decisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.APPROVED)).thenReturn(1L);

        approvalService.processApproval("token", "approved", null);

        assertThat(pendingIncentive.getStatus()).isEqualTo(IncentiveStatus.ACTIVE);
        verify(incentiveRepository).save(pendingIncentive);
    }

    @Test
    void processApproval_autoDeniesWhenInsufficientRemaining() {
        pendingIncentive.setRequiredApprovals(2);
        IncentiveApprover approver2 = IncentiveApprover.builder().email("other@test.com").category("LEGAL").build();
        pendingIncentive.getApprovers().add(approver2);

        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(decisionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.empty());
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));
        when(decisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.REJECTED)).thenReturn(1L);
        when(decisionRepository.countByIncentiveId(incentiveId)).thenReturn(2L);
        when(decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.APPROVED)).thenReturn(0L);

        approvalService.processApproval("token", "rejected", "Not aligned with strategy");

        assertThat(pendingIncentive.getStatus()).isEqualTo(IncentiveStatus.DENIED);
        verify(incentiveRepository).save(pendingIncentive);
    }

    @Test
    void processApproval_publishesNotificationEvent() {
        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(decisionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.empty());
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));
        when(decisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.APPROVED)).thenReturn(1L);

        approvalService.processApproval("token", "approved", null);

        // Called twice: once for the decision event, once for auto-activation event
        verify(notificationEventProducer, org.mockito.Mockito.atLeastOnce()).publish(any());
    }

    @Test
    void processApproval_auditsDecision() {
        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(decisionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.empty());
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));
        when(decisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.APPROVED)).thenReturn(1L);

        approvalService.processApproval("token", "approved", null);

        verify(auditLogService).logWithActor(any(), any(), eq(incentiveId), any(), any(), eq(clientId), any(), any(), any());
    }

    @Test
    void processApproval_doesNotAutoActivate_whenBelowThreshold() {
        pendingIncentive.setRequiredApprovals(3);

        when(tokenService.parseApprovalToken("token")).thenReturn(validClaims);
        when(decisionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());
        when(decisionRepository.findByIncentiveIdAndApproverEmail(incentiveId, approverEmail))
                .thenReturn(Optional.empty());
        when(incentiveRepository.findById(incentiveId)).thenReturn(Optional.of(pendingIncentive));
        when(decisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(decisionRepository.countByIncentiveIdAndDecision(incentiveId, ApprovalDecision.APPROVED)).thenReturn(1L);

        approvalService.processApproval("token", "approved", null);

        assertThat(pendingIncentive.getStatus()).isEqualTo(IncentiveStatus.PENDING_APPROVAL);
        verify(incentiveRepository, never()).save(any());
    }
}
