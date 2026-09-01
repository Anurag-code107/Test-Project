package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.AudienceRuleRequest;
import com.tenxengage.app.dto.request.CreateIncentiveRequest;
import com.tenxengage.app.dto.request.UpdateIncentiveRequest;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.enums.AllocationMethod;
import com.tenxengage.app.entity.enums.BudgetMode;
import com.tenxengage.app.dto.response.IncentiveDetailResponse;
import com.tenxengage.app.dto.response.IncentiveResponse;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveApprover;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.IncentiveDocument;
import com.tenxengage.app.entity.LocationLevel;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.entity.RewardTransaction;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.event.ApprovalEventProducer;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ApprovalDecisionRepository;
import com.tenxengage.app.repository.BudgetUtilizationRepository;
import com.tenxengage.app.repository.DataObjectFieldRepository;
import com.tenxengage.app.repository.IncentiveDocumentRepository;
import com.tenxengage.app.repository.IncentiveForecastRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.LocationLevelRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RewardTransactionRepository;
import com.tenxengage.app.repository.UserIncentiveCompletionRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.forecast.ForecastEngine;
import com.tenxengage.app.dto.request.UpdateIncentiveStatusRequest;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockedStatic;

@ExtendWith(MockitoExtension.class)
class IncentiveServiceTest {

    @Mock
    private IncentiveRepository incentiveRepository;
    @Mock
    private IncentiveDocumentRepository documentRepository;
    @Mock
    private IncentiveForecastRepository forecastRepository;
    @Mock
    private ApprovalDecisionRepository approvalDecisionRepository;
    @Mock
    private DataObjectFieldRepository fieldRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantValidator tenantValidator;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ApprovalEventProducer approvalEventProducer;
    @Mock
    private NotificationEventProducer notificationEventProducer;
    @Mock
    private RewardTransactionRepository rewardTransactionRepository;
    @Mock
    private UserIncentiveCompletionRepository userIncentiveCompletionRepository;
    @Mock
    private BudgetUtilizationRepository budgetUtilizationRepository;
    @Mock
    private ForecastEngine forecastEngine;
    @Mock
    private ParticipantEligibilityChecker eligibilityChecker;
    @Mock
    private PartnerCompanyRepository partnerCompanyRepository;
    @Mock
    private LocationValueRepository locationValueRepository;
    @Mock
    private LocationLevelRepository locationLevelRepository;

    @InjectMocks
    private IncentiveService incentiveService;

    private UUID clientId;
    private UUID userId;
    private UUID incentiveId;
    private Incentive draftIncentive;
    private Incentive activeIncentive;
    private Incentive pendingIncentive;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        userId = UUID.randomUUID();
        incentiveId = UUID.randomUUID();

        draftIncentive = Incentive.builder()
                .name("Test Incentive")
                .description("Test description")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.DRAFT)
                .clientId(clientId)
                .createdBy(userId)
                .startDate(Instant.now())
                .endDate(Instant.now().plus(90, ChronoUnit.DAYS))
                .requiresApproval(true)
                .requiredApprovals(1)
                .approvers(new ArrayList<>(List.of(
                        IncentiveApprover.builder()
                                .email("approver@test.com")
                                .category("FINANCE")
                                .build())))
                .build();
        draftIncentive.setId(incentiveId);

        activeIncentive = Incentive.builder()
                .name("Active Incentive")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.ACTIVE)
                .clientId(clientId)
                .createdBy(userId)
                .startDate(Instant.now().minus(30, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(60, ChronoUnit.DAYS))
                .build();
        activeIncentive.setId(UUID.randomUUID());

        pendingIncentive = Incentive.builder()
                .name("Pending Incentive")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.PENDING_APPROVAL)
                .clientId(clientId)
                .createdBy(userId)
                .requiresApproval(true)
                .requiredApprovals(1)
                .approvers(new ArrayList<>(List.of(
                        IncentiveApprover.builder()
                                .email("approver@test.com")
                                .category("FINANCE")
                                .build())))
                .build();
        pendingIncentive.setId(UUID.randomUUID());
    }

    // -------------------------------------------------------------------------
    // getIncentiveById()
    // -------------------------------------------------------------------------

    @Test
    void getIncentiveById_throwsWhenNotInTenant() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> incentiveService.getIncentiveById(incentiveId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // updateStatus() - State Machine
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_transitionsFromDraftToActive() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));
        when(incentiveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IncentiveResponse response = incentiveService.updateStatus(
                incentiveId, new UpdateIncentiveStatusRequest(IncentiveStatus.ACTIVE));

        assertThat(draftIncentive.getStatus()).isEqualTo(IncentiveStatus.ACTIVE);
        assertThat(draftIncentive.getStatusChangedAt()).isNotNull();
    }

    @Test
    void updateStatus_transitionsFromActiveToInactive() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(activeIncentive.getId(), clientId)).thenReturn(Optional.of(activeIncentive));
        when(incentiveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        incentiveService.updateStatus(
                activeIncentive.getId(), new UpdateIncentiveStatusRequest(IncentiveStatus.INACTIVE));

        assertThat(activeIncentive.getStatus()).isEqualTo(IncentiveStatus.INACTIVE);
    }

    @Test
    void updateStatus_throwsOnInvalidTransition_draftToDenied() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));

        assertThatThrownBy(() -> incentiveService.updateStatus(
                incentiveId, new UpdateIncentiveStatusRequest(IncentiveStatus.DENIED)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void updateStatus_throwsOnReactivatingExpiredIncentive() {
        Incentive expiredInactive = Incentive.builder()
                .name("Expired")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.INACTIVE)
                .clientId(clientId)
                .createdBy(userId)
                .endDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        expiredInactive.setId(UUID.randomUUID());

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(expiredInactive.getId(), clientId)).thenReturn(Optional.of(expiredInactive));

        assertThatThrownBy(() -> incentiveService.updateStatus(
                expiredInactive.getId(), new UpdateIncentiveStatusRequest(IncentiveStatus.ACTIVE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("end date has passed");
    }

    // -------------------------------------------------------------------------
    // submitForApproval()
    // -------------------------------------------------------------------------

    @Test
    void submitForApproval_changesStatusToPendingApproval() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));
        when(incentiveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        incentiveService.submitForApproval(incentiveId);

        assertThat(draftIncentive.getStatus()).isEqualTo(IncentiveStatus.PENDING_APPROVAL);
        verify(approvalEventProducer).publish(any());
    }

    @Test
    void submitForApproval_throwsWhenNotDraft() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(activeIncentive.getId(), clientId)).thenReturn(Optional.of(activeIncentive));

        assertThatThrownBy(() -> incentiveService.submitForApproval(activeIncentive.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only DRAFT");
    }

    @Test
    void submitForApproval_throwsWhenApprovalNotRequired() {
        draftIncentive.setRequiresApproval(false);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));

        assertThatThrownBy(() -> incentiveService.submitForApproval(incentiveId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not require approval");
    }

    @Test
    void submitForApproval_throwsWhenNoApprovers() {
        draftIncentive.setApprovers(new ArrayList<>());

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));

        assertThatThrownBy(() -> incentiveService.submitForApproval(incentiveId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("At least one approver");
    }

    // -------------------------------------------------------------------------
    // resubmitForApproval()
    // -------------------------------------------------------------------------

    @Test
    void resubmitForApproval_incrementsApprovalRound() {
        Incentive deniedIncentive = Incentive.builder()
                .name("Denied")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.DENIED)
                .clientId(clientId)
                .createdBy(userId)
                .requiresApproval(true)
                .approvalRound(1)
                .approvers(new ArrayList<>(List.of(
                        IncentiveApprover.builder().email("a@test.com").category("FINANCE").build())))
                .build();
        deniedIncentive.setId(UUID.randomUUID());

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(deniedIncentive.getId(), clientId)).thenReturn(Optional.of(deniedIncentive));
        when(incentiveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        incentiveService.resubmitForApproval(deniedIncentive.getId());

        assertThat(deniedIncentive.getApprovalRound()).isEqualTo(2);
        assertThat(deniedIncentive.getStatus()).isEqualTo(IncentiveStatus.PENDING_APPROVAL);
        verify(approvalDecisionRepository).deleteAll(any());
    }

    @Test
    void resubmitForApproval_throwsWhenNotDenied() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));

        assertThatThrownBy(() -> incentiveService.resubmitForApproval(incentiveId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only DENIED");
    }

    // -------------------------------------------------------------------------
    // deleteIncentive()
    // -------------------------------------------------------------------------

    @Test
    void deleteIncentive_softDeletesDraftIncentive() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));
        when(incentiveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        incentiveService.deleteIncentive(incentiveId);

        assertThat(draftIncentive.getDeleted()).isTrue();
        verify(incentiveRepository).save(draftIncentive);
    }

    @Test
    void deleteIncentive_throwsWhenInactiveWithAwardedRewards() {
        Incentive inactiveWithRewards = Incentive.builder()
                .name("Has Rewards")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.INACTIVE)
                .clientId(clientId)
                .createdBy(userId)
                .build();
        inactiveWithRewards.setId(UUID.randomUUID());

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(inactiveWithRewards.getId(), clientId))
                .thenReturn(Optional.of(inactiveWithRewards));
        RewardTransaction tx = RewardTransaction.builder()
                .amountAwarded(new BigDecimal("100.00"))
                .build();
        when(rewardTransactionRepository.findByClientIdAndIncentiveId(clientId, inactiveWithRewards.getId()))
                .thenReturn(List.of(tx));

        assertThatThrownBy(() -> incentiveService.deleteIncentive(inactiveWithRewards.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("audit");
    }

    // -------------------------------------------------------------------------
    // uploadDocuments() - File Validation
    // -------------------------------------------------------------------------

    @Test
    void uploadDocuments_throwsWhenFileListEmpty() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));

        assertThatThrownBy(() -> incentiveService.uploadDocuments(incentiveId, List.of(), List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("At least one file");
    }

    @Test
    void uploadDocuments_throwsWhenTooManyFiles() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));

        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            files.add(mock(MultipartFile.class));
        }

        assertThatThrownBy(() -> incentiveService.uploadDocuments(incentiveId, files, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Maximum 10");
    }

    @Test
    void uploadDocuments_throwsWhenFileEmpty() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));

        MultipartFile emptyFile = mock(MultipartFile.class);
        when(emptyFile.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> incentiveService.uploadDocuments(
                incentiveId, List.of(emptyFile), List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void uploadDocuments_throwsWhenFileExceedsSizeLimit() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));

        MultipartFile largeFile = mock(MultipartFile.class);
        when(largeFile.isEmpty()).thenReturn(false);
        when(largeFile.getSize()).thenReturn(11L * 1024 * 1024); // 11MB
        when(largeFile.getOriginalFilename()).thenReturn("large.pdf");

        assertThatThrownBy(() -> incentiveService.uploadDocuments(
                incentiveId, List.of(largeFile), List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    void uploadDocuments_throwsWhenUnsupportedExtension() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));

        MultipartFile badFile = mock(MultipartFile.class);
        when(badFile.isEmpty()).thenReturn(false);
        when(badFile.getSize()).thenReturn(1024L);
        when(badFile.getOriginalFilename()).thenReturn("script.exe");

        assertThatThrownBy(() -> incentiveService.uploadDocuments(
                incentiveId, List.of(badFile), List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unsupported type");
    }

    // -------------------------------------------------------------------------
    // resendApprovalEmails()
    // -------------------------------------------------------------------------

    @Test
    void resendApprovalEmails_throwsWhenNotPendingApproval() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)).thenReturn(Optional.of(draftIncentive));

        assertThatThrownBy(() -> incentiveService.resendApprovalEmails(incentiveId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING_APPROVAL");
    }

    @Test
    void resendApprovalEmails_throwsWhenNoApprovers() {
        pendingIncentive.setApprovers(new ArrayList<>());

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(pendingIncentive.getId(), clientId)).thenReturn(Optional.of(pendingIncentive));

        assertThatThrownBy(() -> incentiveService.resendApprovalEmails(pendingIncentive.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No approvers");
    }

    // -------------------------------------------------------------------------
    // BUG-034: createIncentive() audience-rule resolver
    // -------------------------------------------------------------------------

    private CreateIncentiveRequest minimalCreateRequest(List<AudienceRuleRequest> audienceRules) {
        return new CreateIncentiveRequest(
            "Audience Rule Test", null, IncentiveType.SALES,
            null, null, null, null, null, null, null,
            audienceRules,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    void createIncentive_locationRule_acceptsLocationValueUuidDirectly() {
        // BUG-079: ruleValue carries the LocationValue UUID directly. The previous
        // (level, name) → UUID lookup in the service is gone; resolution happens
        // at the frontend boundary now.
        UUID levelId = UUID.randomUUID();
        UUID valueId = UUID.randomUUID();
        LocationLevel level = LocationLevel.builder()
            .clientId(clientId).name("Region").depth(0).build();
        level.setId(levelId);
        LocationValue value = LocationValue.builder()
            .clientId(clientId).level(level).name("AMERICAS").build();
        value.setId(valueId);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(locationLevelRepository.findById(levelId)).thenReturn(Optional.of(level));
        when(locationValueRepository.findById(valueId)).thenReturn(Optional.of(value));
        when(incentiveRepository.save(any(Incentive.class))).thenAnswer(inv -> inv.getArgument(0));

        AudienceRuleRequest rule = new AudienceRuleRequest("LOCATION", valueId.toString(), levelId);
        incentiveService.createIncentive(minimalCreateRequest(List.of(rule)));

        ArgumentCaptor<Incentive> captor = ArgumentCaptor.forClass(Incentive.class);
        verify(incentiveRepository).save(captor.capture());
        List<IncentiveAudienceRule> saved = captor.getValue().getAudienceRules();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getRuleType()).isEqualTo("LOCATION");
        assertThat(saved.get(0).getRuleValue()).isEqualTo(valueId.toString());
        assertThat(saved.get(0).getLocationLevel()).isSameAs(level);
    }

    @Test
    void createIncentive_regionRule_isRejected() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);

        AudienceRuleRequest rule = new AudienceRuleRequest("REGION", "AMERICAS");
        assertThatThrownBy(() -> incentiveService.createIncentive(minimalCreateRequest(List.of(rule))))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("REGION")
            .hasMessageContaining("LOCATION");
        verify(incentiveRepository, never()).save(any());
    }

    @Test
    void createIncentive_locationRuleMissingLevelId_isRejected() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);

        AudienceRuleRequest rule = new AudienceRuleRequest("LOCATION", UUID.randomUUID().toString(), null);
        assertThatThrownBy(() -> incentiveService.createIncentive(minimalCreateRequest(List.of(rule))))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("locationLevelId");
        verify(incentiveRepository, never()).save(any());
    }

    @Test
    void createIncentive_locationRuleUnparseableUuid_isRejected() {
        // BUG-079: ruleValue must be a UUID for LOCATION rules. A name in this slot
        // (legacy frontend bundle, hand-rolled call) fails loud at the boundary
        // before the level lookup, so no level/value stubs are needed here.
        UUID levelId = UUID.randomUUID();

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);

        AudienceRuleRequest rule = new AudienceRuleRequest("LOCATION", "AMERICAS", levelId);
        assertThatThrownBy(() -> incentiveService.createIncentive(minimalCreateRequest(List.of(rule))))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("LocationValue UUID")
            .hasMessageContaining("AMERICAS");
        verify(incentiveRepository, never()).save(any());
    }

    @Test
    void createIncentive_locationRuleUnknownValueId_isRejected() {
        UUID levelId = UUID.randomUUID();
        UUID unknownValueId = UUID.randomUUID();
        LocationLevel level = LocationLevel.builder()
            .clientId(clientId).name("Region").depth(0).build();
        level.setId(levelId);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(locationLevelRepository.findById(levelId)).thenReturn(Optional.of(level));
        when(locationValueRepository.findById(unknownValueId)).thenReturn(Optional.empty());

        AudienceRuleRequest rule = new AudienceRuleRequest("LOCATION", unknownValueId.toString(), levelId);
        assertThatThrownBy(() -> incentiveService.createIncentive(minimalCreateRequest(List.of(rule))))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Unknown LocationValue UUID")
            .hasMessageContaining(unknownValueId.toString());
        verify(incentiveRepository, never()).save(any());
    }

    @Test
    void createIncentive_locationRuleValueAtWrongLevel_isRejected() {
        // BUG-079: cross-level safety. A UUID that exists but belongs to a different
        // level (e.g. the user passed a Country UUID but said the level is Region) is
        // rejected so the FK metadata stays internally consistent.
        UUID requestedLevelId = UUID.randomUUID();
        UUID actualLevelId = UUID.randomUUID();
        UUID valueId = UUID.randomUUID();
        LocationLevel requestedLevel = LocationLevel.builder()
            .clientId(clientId).name("Region").depth(0).build();
        requestedLevel.setId(requestedLevelId);
        LocationLevel actualLevel = LocationLevel.builder()
            .clientId(clientId).name("Country").depth(1).build();
        actualLevel.setId(actualLevelId);
        LocationValue valueAtCountry = LocationValue.builder()
            .clientId(clientId).level(actualLevel).name("United States").build();
        valueAtCountry.setId(valueId);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(locationLevelRepository.findById(requestedLevelId)).thenReturn(Optional.of(requestedLevel));
        when(locationValueRepository.findById(valueId)).thenReturn(Optional.of(valueAtCountry));

        AudienceRuleRequest rule = new AudienceRuleRequest("LOCATION", valueId.toString(), requestedLevelId);
        assertThatThrownBy(() -> incentiveService.createIncentive(minimalCreateRequest(List.of(rule))))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Unknown LocationValue UUID");
        verify(incentiveRepository, never()).save(any());
    }

    @Test
    void createIncentive_locationRuleLevelNotInTenant_isRejected() {
        UUID foreignClientId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        LocationLevel foreignLevel = LocationLevel.builder()
            .clientId(foreignClientId).name("Region").depth(0).build();
        foreignLevel.setId(levelId);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(locationLevelRepository.findById(levelId)).thenReturn(Optional.of(foreignLevel));

        AudienceRuleRequest rule = new AudienceRuleRequest("LOCATION", UUID.randomUUID().toString(), levelId);
        assertThatThrownBy(() -> incentiveService.createIncentive(minimalCreateRequest(List.of(rule))))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Unknown locationLevelId");
        verify(incentiveRepository, never()).save(any());
    }

    @Test
    void createIncentive_roleAndPartnerTypeRules_passthroughUnchanged() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(incentiveRepository.save(any(Incentive.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID roleId = UUID.randomUUID();
        AudienceRuleRequest roleRule = new AudienceRuleRequest("ROLE", roleId.toString());
        AudienceRuleRequest partnerRule = new AudienceRuleRequest("PARTNER_TYPE", "Reseller");

        incentiveService.createIncentive(minimalCreateRequest(List.of(roleRule, partnerRule)));

        ArgumentCaptor<Incentive> captor = ArgumentCaptor.forClass(Incentive.class);
        verify(incentiveRepository).save(captor.capture());
        List<IncentiveAudienceRule> saved = captor.getValue().getAudienceRules();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getRuleType()).isEqualTo("ROLE");
        assertThat(saved.get(0).getRuleValue()).isEqualTo(roleId.toString());
        assertThat(saved.get(0).getLocationLevel()).isNull();
        assertThat(saved.get(1).getRuleType()).isEqualTo("PARTNER_TYPE");
        assertThat(saved.get(1).getRuleValue()).isEqualTo("Reseller");
    }

    // -------------------------------------------------------------------------
    // Per-level budget allocation
    // -------------------------------------------------------------------------

    private CreateIncentiveRequest createRequestWithBudgets(List<com.tenxengage.app.dto.request.BudgetRequest> budgets) {
        return new CreateIncentiveRequest(
            "Per-Level Budget Test", null, IncentiveType.SALES,
            null, null, null,
            budgets,
            null, null, null,
            List.of(),
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    void createIncentive_perLocationBudget_persistsAllocationsAtAnyDepth() {
        // Three allocations at different depths: Region, Country, State.
        // Should produce three LocationBudgetAllocation rows on the budget.
        UUID regionLevelId = UUID.randomUUID();
        UUID countryId = UUID.randomUUID();
        UUID stateId = UUID.randomUUID();
        UUID regionValueId = UUID.randomUUID();

        LocationLevel regionLevel = LocationLevel.builder()
            .clientId(clientId).name("Region").depth(0).build();
        regionLevel.setId(regionLevelId);

        LocationValue regionValue = LocationValue.builder()
            .clientId(clientId).level(regionLevel).name("AMERICAS").build();
        regionValue.setId(regionValueId);
        LocationValue countryValue = LocationValue.builder()
            .clientId(clientId).level(regionLevel).name("United States").build();
        countryValue.setId(countryId);
        LocationValue stateValue = LocationValue.builder()
            .clientId(clientId).level(regionLevel).name("California").build();
        stateValue.setId(stateId);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(locationLevelRepository.findById(regionLevelId)).thenReturn(Optional.of(regionLevel));
        when(locationValueRepository.findById(regionValueId)).thenReturn(Optional.of(regionValue));
        when(locationValueRepository.findById(countryId)).thenReturn(Optional.of(countryValue));
        when(locationValueRepository.findById(stateId)).thenReturn(Optional.of(stateValue));
        when(incentiveRepository.save(any(Incentive.class))).thenAnswer(inv -> inv.getArgument(0));

        com.tenxengage.app.dto.request.BudgetRequest budget = new com.tenxengage.app.dto.request.BudgetRequest(
            "100000",
            "cash",
            "EQUAL",
            "PER_LOCATION",
            regionLevelId,
            List.of(
                new com.tenxengage.app.dto.request.LocationAllocationRequest(regionValueId, "100000"),
                new com.tenxengage.app.dto.request.LocationAllocationRequest(countryId, "60000"),
                new com.tenxengage.app.dto.request.LocationAllocationRequest(stateId, "40000")
            )
        );

        incentiveService.createIncentive(createRequestWithBudgets(List.of(budget)));

        ArgumentCaptor<Incentive> captor = ArgumentCaptor.forClass(Incentive.class);
        verify(incentiveRepository).save(captor.capture());
        List<com.tenxengage.app.entity.IncentiveBudget> savedBudgets = captor.getValue().getBudgets();
        assertThat(savedBudgets).hasSize(1);
        com.tenxengage.app.entity.IncentiveBudget saved = savedBudgets.get(0);
        assertThat(saved.getBudgetMode())
            .isEqualTo(com.tenxengage.app.entity.enums.BudgetMode.PER_LOCATION);
        assertThat(saved.getBudgetLocationLevel()).isSameAs(regionLevel);
        assertThat(saved.getLocationAllocations()).hasSize(3);
        assertThat(saved.getLocationAllocations())
            .extracting(a -> a.getLocationValue().getId())
            .containsExactlyInAnyOrder(regionValueId, countryId, stateId);
        assertThat(saved.getLocationAllocations())
            .extracting(a -> a.getAmount().toPlainString())
            .containsExactlyInAnyOrder("100000", "60000", "40000");
    }

    @Test
    void createIncentive_globalBudget_persistsNoAllocations() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(incentiveRepository.save(any(Incentive.class))).thenAnswer(inv -> inv.getArgument(0));

        com.tenxengage.app.dto.request.BudgetRequest budget = new com.tenxengage.app.dto.request.BudgetRequest(
            "50000", "cash", "EQUAL", "GLOBAL", null, null
        );
        incentiveService.createIncentive(createRequestWithBudgets(List.of(budget)));

        ArgumentCaptor<Incentive> captor = ArgumentCaptor.forClass(Incentive.class);
        verify(incentiveRepository).save(captor.capture());
        com.tenxengage.app.entity.IncentiveBudget saved = captor.getValue().getBudgets().get(0);
        assertThat(saved.getBudgetMode())
            .isEqualTo(com.tenxengage.app.entity.enums.BudgetMode.GLOBAL);
        assertThat(saved.getBudgetLocationLevel()).isNull();
        assertThat(saved.getLocationAllocations()).isEmpty();
    }

    @Test
    void createIncentive_perLocationBudget_emptyAllocations_isRejected() {
        // Defensive: API consumers can't silently persist a PER_LOCATION budget
        // with no per-location splits. The UI guarantees this via auto-fill;
        // the backend hard-rejects to keep the data model consistent.
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);

        com.tenxengage.app.dto.request.BudgetRequest budget = new com.tenxengage.app.dto.request.BudgetRequest(
            "100000", "cash", "EQUAL", "PER_LOCATION", null, null
        );

        assertThatThrownBy(() -> incentiveService.createIncentive(createRequestWithBudgets(List.of(budget))))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("PER_LOCATION")
            .hasMessageContaining("locationAllocations");
        verify(incentiveRepository, never()).save(any());
    }

    @Test
    void createIncentive_perLocationBudget_unknownLocationValueId_isRejected() {
        UUID regionLevelId = UUID.randomUUID();
        UUID missingValueId = UUID.randomUUID();
        LocationLevel regionLevel = LocationLevel.builder()
            .clientId(clientId).name("Region").depth(0).build();
        regionLevel.setId(regionLevelId);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(locationLevelRepository.findById(regionLevelId)).thenReturn(Optional.of(regionLevel));
        when(locationValueRepository.findById(missingValueId)).thenReturn(Optional.empty());

        com.tenxengage.app.dto.request.BudgetRequest budget = new com.tenxengage.app.dto.request.BudgetRequest(
            "100000", "cash", "EQUAL", "PER_LOCATION", regionLevelId,
            List.of(new com.tenxengage.app.dto.request.LocationAllocationRequest(missingValueId, "100000"))
        );

        assertThatThrownBy(() -> incentiveService.createIncentive(createRequestWithBudgets(List.of(budget))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(missingValueId.toString());
        verify(incentiveRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // BUG-067: updateIncentive() must flush orphan-removal DELETEs before
    // re-adding budgets, otherwise Hibernate's default action ordering INSERTs
    // first and trips uq_budget_incentive_currency.
    // -------------------------------------------------------------------------

    private UpdateIncentiveRequest minimalUpdateRequestWithBudgets(
            List<com.tenxengage.app.dto.request.BudgetRequest> budgets) {
        return new UpdateIncentiveRequest(
            null, null, null, null, null,
            budgets,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null
        );
    }

    @Test
    void updateIncentive_replacingBudgetsWithSameCurrencies_flushesBetweenClearAndAdd() {
        // Pre-existing budgets on the incentive — same currencies the update payload re-sends.
        IncentiveBudget existingCash = IncentiveBudget.builder()
            .incentive(draftIncentive)
            .totalBudget(new BigDecimal("500000"))
            .currencyId("cash")
            .allocationMethod(AllocationMethod.EQUAL)
            .budgetMode(BudgetMode.GLOBAL)
            .build();
        IncentiveBudget existingPoints = IncentiveBudget.builder()
            .incentive(draftIncentive)
            .totalBudget(new BigDecimal("100000"))
            .currencyId("points")
            .allocationMethod(AllocationMethod.EQUAL)
            .budgetMode(BudgetMode.GLOBAL)
            .build();
        draftIncentive.setBudgets(new ArrayList<>(List.of(existingCash, existingPoints)));

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId))
                .thenReturn(Optional.of(draftIncentive));
        when(incentiveRepository.save(any(Incentive.class))).thenAnswer(inv -> inv.getArgument(0));
        when(forecastRepository.findTopByIncentiveIdOrderByGeneratedAtDesc(incentiveId))
                .thenReturn(Optional.empty());

        com.tenxengage.app.dto.request.BudgetRequest cash = new com.tenxengage.app.dto.request.BudgetRequest(
            "600000", "cash", "EQUAL", "GLOBAL", null, null
        );
        com.tenxengage.app.dto.request.BudgetRequest points = new com.tenxengage.app.dto.request.BudgetRequest(
            "150000", "points", "EQUAL", "GLOBAL", null, null
        );

        incentiveService.updateIncentive(incentiveId, minimalUpdateRequestWithBudgets(List.of(cash, points)));

        // The fix: flush() must precede save() so orphan-removal DELETEs commit
        // ahead of the new INSERTs and the unique constraint isn't violated.
        var inOrder = inOrder(incentiveRepository);
        inOrder.verify(incentiveRepository).flush();
        inOrder.verify(incentiveRepository).save(draftIncentive);

        // The replacement actually happened — same two currencies, new totals.
        assertThat(draftIncentive.getBudgets())
            .extracting(IncentiveBudget::getCurrencyId, IncentiveBudget::getTotalBudget)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("cash", new BigDecimal("600000")),
                org.assertj.core.groups.Tuple.tuple("points", new BigDecimal("150000")));
    }

    // -------------------------------------------------------------------------
    // generateForecastStreaming() — BUG-070
    // -------------------------------------------------------------------------

    @Test
    void generateForecastStreaming_initializesLazyLocationLevelProxiesBeforeVirtualThread() {
        // BUG-070: forecast generation runs on a virtual thread that has no Hibernate
        // Session, so any lazy LocationLevel @ManyToOne on audience rules / budgets
        // must be hydrated inside the @Transactional method before the thread starts —
        // otherwise the assembler hits LazyInitializationException.
        LocationLevel ruleLevel = new LocationLevel();
        ruleLevel.setId(UUID.randomUUID());

        LocationLevel budgetLevel = new LocationLevel();
        budgetLevel.setId(UUID.randomUUID());

        IncentiveAudienceRule locationRule = IncentiveAudienceRule.builder()
                .ruleType("LOCATION")
                .ruleValue(UUID.randomUUID().toString())
                .locationLevel(ruleLevel)
                .build();
        IncentiveAudienceRule roleRule = IncentiveAudienceRule.builder()
                .ruleType("ROLE")
                .ruleValue("PARTNER_SELLER")
                .build();
        draftIncentive.setAudienceRules(new ArrayList<>(List.of(locationRule, roleRule)));

        IncentiveBudget perLocationBudget = IncentiveBudget.builder()
                .totalBudget(new BigDecimal("100"))
                .currencyId("cash")
                .allocationMethod(AllocationMethod.EQUAL)
                .budgetMode(BudgetMode.PER_LOCATION)
                .budgetLocationLevel(budgetLevel)
                .build();
        draftIncentive.setBudgets(new ArrayList<>(List.of(perLocationBudget)));

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId))
                .thenReturn(Optional.of(draftIncentive));
        when(forecastRepository.findTopByIncentiveIdOrderByGeneratedAtDesc(incentiveId))
                .thenReturn(Optional.empty());

        try (MockedStatic<org.hibernate.Hibernate> hib = mockStatic(org.hibernate.Hibernate.class)) {
            incentiveService.generateForecastStreaming(incentiveId);

            // Both lazy proxies on the entity must be force-loaded before the
            // virtual thread is dispatched. The role rule has no LocationLevel,
            // so it must NOT trigger an initialize call (would NPE in production).
            hib.verify(() -> org.hibernate.Hibernate.initialize(ruleLevel));
            hib.verify(() -> org.hibernate.Hibernate.initialize(budgetLevel));
            hib.verifyNoMoreInteractions();
        }
    }
}
