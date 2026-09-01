package com.tenxengage.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.request.ActivityDefinitionRequest;
import com.tenxengage.app.dto.request.AudienceRuleRequest;
import com.tenxengage.app.dto.request.BudgetRequest;
import com.tenxengage.app.dto.request.ApproverRequest;
import com.tenxengage.app.dto.request.CreateIncentiveRequest;
import com.tenxengage.app.dto.request.DocumentRequest;
import com.tenxengage.app.dto.request.EligibilityRuleGroupRequest;
import com.tenxengage.app.dto.request.EligibilityRuleRequest;
import com.tenxengage.app.dto.request.JourneyStageRequest;
import com.tenxengage.app.dto.request.PayoutBandRequest;
import com.tenxengage.app.dto.request.PayoutConfigRequest;
import com.tenxengage.app.dto.request.SalesRequirementRequest;
import com.tenxengage.app.dto.request.TrainingCourseRequest;
import com.tenxengage.app.dto.request.UpdateIncentiveRequest;
import com.tenxengage.app.dto.request.UpdateIncentiveStatusRequest;
import com.tenxengage.app.dto.response.ApprovalStatusResponse;
import com.tenxengage.app.dto.response.ApproverStatusResponse;
import com.tenxengage.app.dto.response.DocumentSummaryResponse;
import com.tenxengage.app.dto.response.ForecastResponse;
import com.tenxengage.app.dto.response.IncentiveDetailResponse;
import com.tenxengage.app.dto.response.IncentiveResponse;
import com.tenxengage.app.dto.response.JourneyStageSummaryResponse;
import com.tenxengage.app.entity.ApprovalDecisionEntity;
import com.tenxengage.app.entity.ActivityDefinition;
import com.tenxengage.app.entity.ActivityDocumentRequirement;
import com.tenxengage.app.entity.EligibilityRule;
import com.tenxengage.app.entity.EligibilityRuleGroup;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveApprover;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.IncentiveDocument;
import com.tenxengage.app.entity.IncentiveForecast;
import com.tenxengage.app.entity.JourneyStage;
import com.tenxengage.app.entity.RewardTransaction;
import com.tenxengage.app.entity.PayoutBand;
import com.tenxengage.app.entity.PayoutConfig;
import com.tenxengage.app.entity.SalesRequirement;
import com.tenxengage.app.entity.TrainingCourseAssignment;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.ApprovalDecision;
import com.tenxengage.app.entity.enums.AllocationMethod;
import com.tenxengage.app.entity.enums.BudgetMode;
import com.tenxengage.app.entity.enums.EligibilityRuleType;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.entity.enums.PayoutType;
import com.tenxengage.app.entity.enums.RuleOperator;
import com.tenxengage.app.event.ApprovalEventProducer;
import com.tenxengage.app.event.ApprovalRequestEvent;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.entity.DataObjectField;
import com.tenxengage.app.entity.BudgetUtilization;
import com.tenxengage.app.repository.ApprovalDecisionRepository;
import com.tenxengage.app.repository.BudgetUtilizationRepository;
import com.tenxengage.app.repository.DataObjectFieldRepository;
import com.tenxengage.app.repository.IncentiveDocumentRepository;
import com.tenxengage.app.repository.IncentiveForecastRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.RewardTransactionRepository;
import com.tenxengage.app.repository.UserIncentiveCompletionRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.entity.UserIncentiveCompletion;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.repository.PartnerCompanyRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IncentiveService {

    private static final Logger log = LoggerFactory.getLogger(IncentiveService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "xlsx", "xls", "docx", "doc");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword"
    );
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final int MAX_FILES_PER_UPLOAD = 10;
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
        "terms-conditions", "eligible-products", "program-rules", "faq"
    );

    private final IncentiveRepository incentiveRepository;
    private final IncentiveDocumentRepository documentRepository;
    private final IncentiveForecastRepository forecastRepository;
    private final ApprovalDecisionRepository approvalDecisionRepository;
    private final DataObjectFieldRepository fieldRepository;
    private final UserRepository userRepository;
    private final TenantValidator tenantValidator;
    private final FileStorageService fileStorageService;
    private final ApprovalEventProducer approvalEventProducer;
    private final NotificationEventProducer notificationEventProducer;
    private final RewardTransactionRepository rewardTransactionRepository;
    private final UserIncentiveCompletionRepository userIncentiveCompletionRepository;
    private final BudgetUtilizationRepository budgetUtilizationRepository;
    private final com.tenxengage.app.service.forecast.ForecastEngine forecastEngine;
    private final ParticipantEligibilityChecker eligibilityChecker;
    private final PartnerCompanyRepository partnerCompanyRepository;
    private final com.tenxengage.app.repository.LocationValueRepository locationValueRepository;
    private final com.tenxengage.app.repository.LocationLevelRepository locationLevelRepository;

    public IncentiveService(IncentiveRepository incentiveRepository,
                            IncentiveDocumentRepository documentRepository,
                            IncentiveForecastRepository forecastRepository,
                            ApprovalDecisionRepository approvalDecisionRepository,
                            DataObjectFieldRepository fieldRepository,
                            UserRepository userRepository,
                            TenantValidator tenantValidator,
                            FileStorageService fileStorageService,
                            ApprovalEventProducer approvalEventProducer,
                            NotificationEventProducer notificationEventProducer,
                            RewardTransactionRepository rewardTransactionRepository,
                            UserIncentiveCompletionRepository userIncentiveCompletionRepository,
                            BudgetUtilizationRepository budgetUtilizationRepository,
                            com.tenxengage.app.service.forecast.ForecastEngine forecastEngine,
                            ParticipantEligibilityChecker eligibilityChecker,
                            PartnerCompanyRepository partnerCompanyRepository,
                            com.tenxengage.app.repository.LocationValueRepository locationValueRepository,
                            com.tenxengage.app.repository.LocationLevelRepository locationLevelRepository) {
        this.incentiveRepository = incentiveRepository;
        this.documentRepository = documentRepository;
        this.forecastRepository = forecastRepository;
        this.approvalDecisionRepository = approvalDecisionRepository;
        this.fieldRepository = fieldRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.tenantValidator = tenantValidator;
        this.approvalEventProducer = approvalEventProducer;
        this.notificationEventProducer = notificationEventProducer;
        this.forecastEngine = forecastEngine;
        this.rewardTransactionRepository = rewardTransactionRepository;
        this.userIncentiveCompletionRepository = userIncentiveCompletionRepository;
        this.budgetUtilizationRepository = budgetUtilizationRepository;
        this.eligibilityChecker = eligibilityChecker;
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.locationValueRepository = locationValueRepository;
        this.locationLevelRepository = locationLevelRepository;
    }

    // ── List ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<IncentiveResponse> getIncentives(IncentiveType type,
                                                  IncentiveStatus status,
                                                  String search,
                                                  Pageable pageable) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        // Fetch user completions once for the whole page
        Map<UUID, Instant> completionMap = buildCompletionMap(clientId, userId);

        // Fetch budget utilization percentages for all incentives in one query
        Map<UUID, Integer> utilizationMap = buildUtilizationMap(clientId);

        return incentiveRepository.searchByClientId(clientId, type, status, search, pageable)
            .map(incentive -> toListResponse(incentive, completionMap, utilizationMap));
    }

    @Transactional(readOnly = true)
    public Page<IncentiveResponse> getIncentivesForPartner(IncentiveType type,
                                                            String search,
                                                            Pageable pageable) {
        var userDetails = tenantValidator.getCurrentUserDetails();
        UUID clientId = userDetails.getClientId();
        UUID userId = userDetails.getUserId();

        // Load user and partner company for eligibility data
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Map<UUID, Set<UUID>> userLocationsByLevel = Map.of();
        UUID userRoleId = null;
        String userRoleName = null;
        String userPartnerType = null;
        String externalPartnerId = null;
        Map<String, String> partnerMetadata = Map.of();

        if (user.getPartnerCompanyId() != null) {
            PartnerCompany pc = partnerCompanyRepository
                .findByIdAndClientId(user.getPartnerCompanyId(), clientId)
                .orElse(null);
            if (pc != null) {
                userLocationsByLevel = ParticipantEligibilityChecker.buildLocationMap(pc.getLocationAssignments());
                externalPartnerId = pc.getExternalPartnerId();
                partnerMetadata = parseMetadata(pc.getMetadata());
                userPartnerType = partnerMetadata.get("Partner Type");
            }
        }
        if (user.getClientRoleId() != null && user.getClientRole() != null) {
            userRoleId = user.getClientRole().getId();
            userRoleName = user.getClientRole().getName();
        }

        // Fetch ACTIVE incentives only for partner view
        Page<Incentive> page = incentiveRepository.searchByClientId(
            clientId, type, IncentiveStatus.ACTIVE, search, pageable);

        // Filter by participant eligibility
        Map<UUID, Set<UUID>> finalLocations = userLocationsByLevel;
        UUID finalRoleId = userRoleId;
        String finalRoleName = userRoleName;
        String finalPartnerType = userPartnerType;
        String finalExternalId = externalPartnerId;
        Map<String, String> finalMetadata = partnerMetadata;

        List<Incentive> filtered = page.getContent().stream()
            .filter(inc -> eligibilityChecker.matchesUserEligibility(
                inc, finalLocations, finalRoleId, finalRoleName, finalPartnerType,
                finalExternalId, finalMetadata))
            .toList();

        Map<UUID, Instant> completionMap = buildCompletionMap(clientId, userId);
        Map<UUID, Integer> utilizationMap = buildUtilizationMap(clientId);

        List<IncentiveResponse> responses = filtered.stream()
            .map(incentive -> toListResponse(incentive, completionMap, utilizationMap))
            .toList();

        return new org.springframework.data.domain.PageImpl<>(
            responses, pageable, page.getTotalElements());
    }

    private Map<String, String> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) return Map.of();
        try {
            Map<String, Object> raw = MAPPER.readValue(metadataJson,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse partner metadata: {}", e.getMessage());
            return Map.of();
        }
    }

    // ── Detail ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public IncentiveDetailResponse getIncentiveById(UUID id) {
        Incentive incentive = findByIdAndClient(id);
        ForecastResponse forecast = forecastRepository
            .findTopByIncentiveIdOrderByGeneratedAtDesc(id)
            .map(ForecastResponse::from)
            .orElse(null);
        return toDetailResponse(incentive, forecast);
    }

    // ── Detail (for approval flow — accepts pre-fetched entity) ────────

    @Transactional(readOnly = true)
    public IncentiveDetailResponse toDetailResponseForApproval(Incentive incentive) {
        ForecastResponse forecast = forecastRepository
            .findTopByIncentiveIdOrderByGeneratedAtDesc(incentive.getId())
            .map(ForecastResponse::from)
            .orElse(null);
        return toDetailResponse(incentive, forecast);
    }

    // ── Create ──────────────────────────────────────────────────────────

    @Transactional
    public IncentiveDetailResponse createIncentive(CreateIncentiveRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        Incentive incentive = Incentive.builder()
            .name(request.name())
            .description(request.description())
            .incentiveType(request.incentiveType())
            .status(IncentiveStatus.DRAFT)
            .clientId(clientId)
            .createdBy(userId)
            .startDate(parseDate(request.startDate()))
            .endDate(parseDate(request.endDate()))
            .timezone(request.timezone())
            .journeySequential(request.journeySequential() != null ? request.journeySequential() : true)
            .rewardCurrencies(toJson(request.rewardCurrencies()))
            .rewardMessage(request.rewardMessage())
            .rewardAmounts(toJsonMap(request.rewardAmounts()))
            .fiscalYears(toJson(request.fiscalYears()))
            .fiscalQuarters(toJson(request.fiscalQuarters()))
            .trainingRequiredCount(request.trainingRequiredCount())
            .countriesText(request.countriesText())
            .specificPartners(request.specificPartners())
            .customFieldValues(request.customFieldValues())
            .requiresApproval(request.requiresApproval() != null ? request.requiresApproval() : false)
            .requiredApprovals(request.requiredApprovals() != null ? request.requiredApprovals() : 0)
            .build();

        // Budget
        if (request.budgets() != null) {
            for (BudgetRequest br : request.budgets()) {
                incentive.getBudgets().add(buildBudget(br, incentive));
            }
        }
        if (request.maxPerPartner() != null) {
            incentive.setMaxPerPartner(parseBigDecimal(request.maxPerPartner()));
        }
        if (request.maxPerUser() != null) {
            incentive.setMaxPerUser(parseBigDecimal(request.maxPerUser()));
        }
        if (request.maxClaimersPerDeal() != null) {
            incentive.setMaxClaimersPerDeal(request.maxClaimersPerDeal());
        }

        // Audience rules
        if (request.audienceRules() != null) {
            for (AudienceRuleRequest r : request.audienceRules()) {
                incentive.getAudienceRules().add(buildAudienceRule(r, incentive));
            }
        }

        // Sales requirements
        if (request.salesRequirements() != null) {
            int sortIdx = 0;
            for (SalesRequirementRequest r : request.salesRequirements()) {
                incentive.getSalesRequirements().add(buildSalesRequirement(r, incentive, sortIdx++));
            }
        }

        // Training courses
        if (request.trainingCourses() != null) {
            int sortIdx = 0;
            for (TrainingCourseRequest r : request.trainingCourses()) {
                incentive.getTrainingCourses().add(buildTrainingCourse(r, incentive, sortIdx++));
            }
        }

        // Activity definitions
        if (request.activityDefinitions() != null) {
            for (ActivityDefinitionRequest r : request.activityDefinitions()) {
                incentive.getActivityDefinitions().add(buildActivityDefinition(r, incentive));
            }
        }

        // Journey stages
        if (request.journeyStages() != null) {
            for (JourneyStageRequest r : request.journeyStages()) {
                incentive.getJourneyStages().add(buildJourneyStage(r, incentive));
            }
        }

        // Documents
        if (request.documents() != null) {
            for (DocumentRequest d : request.documents()) {
                incentive.getDocuments().add(buildDocument(d, incentive));
            }
        }

        // Approvers
        if (request.approvers() != null) {
            int sortIdx = 0;
            for (ApproverRequest a : request.approvers()) {
                incentive.getApprovers().add(IncentiveApprover.builder()
                    .incentive(incentive)
                    .email(a.email())
                    .category(a.category())
                    .sortOrder(sortIdx++)
                    .build());
            }
        }

        incentive = incentiveRepository.save(incentive);
        log.info("Created incentive {} of type {} for client {}", incentive.getId(), request.incentiveType(), clientId);

        notificationEventProducer.publish(new NotificationEvent(
            "INCENTIVE_CREATED", clientId,
            "New Incentive Created: " + incentive.getName(),
            "A new " + request.incentiveType() + " incentive '" + incentive.getName() + "' has been created.",
            "INCENTIVE", incentive.getId(), userId, null, null));

        return toDetailResponse(incentive, null);
    }

    // ── Update ──────────────────────────────────────────────────────────

    @Transactional
    public IncentiveDetailResponse updateIncentive(UUID id, UpdateIncentiveRequest request) {
        Incentive incentive = findByIdAndClient(id);

        // Basic fields
        if (request.name() != null) incentive.setName(request.name());
        if (request.description() != null) incentive.setDescription(request.description());
        if (request.startDate() != null) incentive.setStartDate(parseDate(request.startDate()));
        if (request.endDate() != null) incentive.setEndDate(parseDate(request.endDate()));
        if (request.timezone() != null) incentive.setTimezone(request.timezone());
        if (request.journeySequential() != null) incentive.setJourneySequential(request.journeySequential());

        // Reward fields
        if (request.rewardCurrencies() != null) {
            incentive.setRewardCurrencies(toJson(request.rewardCurrencies()));
        }
        if (request.rewardMessage() != null) {
            incentive.setRewardMessage(request.rewardMessage());
        }
        if (request.rewardAmounts() != null) {
            incentive.setRewardAmounts(toJsonMap(request.rewardAmounts()));
        }

        // New builder fields
        if (request.fiscalYears() != null) {
            incentive.setFiscalYears(toJson(request.fiscalYears()));
        }
        if (request.fiscalQuarters() != null) {
            incentive.setFiscalQuarters(toJson(request.fiscalQuarters()));
        }
        if (request.trainingRequiredCount() != null) {
            incentive.setTrainingRequiredCount(request.trainingRequiredCount());
        }
        if (request.countriesText() != null) {
            incentive.setCountriesText(request.countriesText());
        }
        if (request.specificPartners() != null) {
            incentive.setSpecificPartners(request.specificPartners());
        }
        if (request.customFieldValues() != null) {
            incentive.setCustomFieldValues(request.customFieldValues());
        }

        // Budget — replace
        if (request.budgets() != null) {
            incentive.getBudgets().clear();
            // Flush orphan-removal DELETEs before re-adding INSERTs so the
            // uq_budget_incentive_currency (incentive_id, currency_id) unique
            // constraint isn't tripped by Hibernate's default action ordering
            // when a payload re-uses an existing currency on the same incentive.
            incentiveRepository.flush();
            for (BudgetRequest br : request.budgets()) {
                incentive.getBudgets().add(buildBudget(br, incentive));
            }
        }
        if (request.maxPerPartner() != null) {
            incentive.setMaxPerPartner(parseBigDecimal(request.maxPerPartner()));
        }
        if (request.maxPerUser() != null) {
            incentive.setMaxPerUser(parseBigDecimal(request.maxPerUser()));
        }
        if (request.maxClaimersPerDeal() != null) {
            incentive.setMaxClaimersPerDeal(request.maxClaimersPerDeal());
        }

        // Audience rules — replace all
        if (request.audienceRules() != null) {
            incentive.getAudienceRules().clear();
            for (AudienceRuleRequest r : request.audienceRules()) {
                incentive.getAudienceRules().add(buildAudienceRule(r, incentive));
            }
        }

        // Sales requirements — replace all
        if (request.salesRequirements() != null) {
            incentive.getSalesRequirements().clear();
            int sortIdx = 0;
            for (SalesRequirementRequest r : request.salesRequirements()) {
                incentive.getSalesRequirements().add(buildSalesRequirement(r, incentive, sortIdx++));
            }
        }

        // Training courses — replace all
        if (request.trainingCourses() != null) {
            incentive.getTrainingCourses().clear();
            int sortIdx = 0;
            for (TrainingCourseRequest r : request.trainingCourses()) {
                incentive.getTrainingCourses().add(buildTrainingCourse(r, incentive, sortIdx++));
            }
        }

        // Activity definitions — replace all
        if (request.activityDefinitions() != null) {
            incentive.getActivityDefinitions().clear();
            for (ActivityDefinitionRequest r : request.activityDefinitions()) {
                incentive.getActivityDefinitions().add(buildActivityDefinition(r, incentive));
            }
        }

        // Journey stages — replace all
        if (request.journeyStages() != null) {
            incentive.getJourneyStages().clear();
            for (JourneyStageRequest r : request.journeyStages()) {
                incentive.getJourneyStages().add(buildJourneyStage(r, incentive));
            }
        }

        // Documents — replace all
        if (request.documents() != null) {
            incentive.getDocuments().clear();
            for (DocumentRequest d : request.documents()) {
                incentive.getDocuments().add(buildDocument(d, incentive));
            }
        }

        // Approval fields
        if (request.requiresApproval() != null) {
            incentive.setRequiresApproval(request.requiresApproval());
        }
        if (request.requiredApprovals() != null) {
            incentive.setRequiredApprovals(request.requiredApprovals());
        }

        // Approvers — replace all
        if (request.approvers() != null) {
            incentive.getApprovers().clear();
            int sortIdx = 0;
            for (ApproverRequest a : request.approvers()) {
                incentive.getApprovers().add(IncentiveApprover.builder()
                    .incentive(incentive)
                    .email(a.email())
                    .category(a.category())
                    .sortOrder(sortIdx++)
                    .build());
            }
        }

        incentive = incentiveRepository.save(incentive);
        ForecastResponse forecast = forecastRepository
            .findTopByIncentiveIdOrderByGeneratedAtDesc(id)
            .map(ForecastResponse::from)
            .orElse(null);
        return toDetailResponse(incentive, forecast);
    }

    // ── Status transition ───────────────────────────────────────────────

    @Transactional
    public IncentiveResponse updateStatus(UUID id, UpdateIncentiveStatusRequest request) {
        Incentive incentive = findByIdAndClient(id);
        validateStatusTransition(incentive.getStatus(), request.status());

        // Block reactivation of expired incentives
        if (request.status() == IncentiveStatus.ACTIVE
                && incentive.getEndDate() != null
                && incentive.getEndDate().isBefore(Instant.now())) {
            throw new BusinessRuleException(
                "Cannot reactivate an incentive whose end date has passed");
        }

        incentive.setStatus(request.status());
        incentive.setStatusChangedAt(Instant.now());
        incentive = incentiveRepository.save(incentive);
        log.info("Incentive {} status changed to {}", id, request.status());

        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        // Publish notification for status change
        String notifKey = request.status() == IncentiveStatus.ACTIVE
            ? "INCENTIVE_ACTIVATED"
            : request.status() == IncentiveStatus.INACTIVE
                ? "INCENTIVE_DEACTIVATED"
                : null;
        if (notifKey != null) {
            notificationEventProducer.publish(new NotificationEvent(
                notifKey, clientId,
                incentive.getName() + " — " + request.status(),
                "Incentive '" + incentive.getName() + "' is now " + request.status() + ".",
                "INCENTIVE", incentive.getId(), userId, null,
                Map.of("incentiveId", incentive.getId().toString())));
        }

        Map<UUID, Instant> completionMap = buildCompletionMap(clientId, userId);
        Map<UUID, Integer> utilizationMap = buildUtilizationMap(clientId);
        return toListResponse(incentive, completionMap, utilizationMap);
    }

    // ── Submit for approval ─────────────────────────────────────────────

    @Transactional
    public IncentiveResponse submitForApproval(UUID id) {
        Incentive incentive = findByIdAndClient(id);

        if (incentive.getStatus() != IncentiveStatus.DRAFT) {
            throw new BusinessRuleException("Only DRAFT incentives can be submitted for approval");
        }
        if (!Boolean.TRUE.equals(incentive.getRequiresApproval())) {
            throw new BusinessRuleException("This incentive does not require approval");
        }
        if (incentive.getApprovers() == null || incentive.getApprovers().isEmpty()) {
            throw new BusinessRuleException("At least one approver must be configured");
        }

        incentive.setStatus(IncentiveStatus.PENDING_APPROVAL);
        incentive.setStatusChangedAt(Instant.now());
        incentive = incentiveRepository.save(incentive);
        log.info("Incentive {} submitted for approval", id);

        notificationEventProducer.publish(new NotificationEvent(
            "INCENTIVE_SUBMITTED_FOR_APPROVAL", incentive.getClientId(),
            "Incentive Submitted for Approval: " + incentive.getName(),
            "Incentive '" + incentive.getName() + "' has been submitted for approval.",
            "INCENTIVE", incentive.getId(), tenantValidator.getCurrentUserId(), null, null));

        // Publish Kafka event for async email processing
        List<ApprovalRequestEvent.ApproverInfo> approverInfos = incentive.getApprovers().stream()
            .map(a -> new ApprovalRequestEvent.ApproverInfo(a.getEmail(), a.getCategory()))
            .toList();
        approvalEventProducer.publish(new ApprovalRequestEvent(
            incentive.getId(), incentive.getName(), incentive.getApprovalRound(), approverInfos));

        return toListResponseWithCompletions(incentive);
    }

    // ── Resubmit for approval (from DENIED) ───────────────────────────

    @Transactional
    public IncentiveResponse resubmitForApproval(UUID id) {
        Incentive incentive = findByIdAndClient(id);

        if (incentive.getStatus() != IncentiveStatus.DENIED) {
            throw new BusinessRuleException("Only DENIED incentives can be resubmitted for approval");
        }
        if (incentive.getApprovers() == null || incentive.getApprovers().isEmpty()) {
            throw new BusinessRuleException("At least one approver must be configured");
        }

        // Clear all previous decisions
        List<ApprovalDecisionEntity> oldDecisions =
            approvalDecisionRepository.findAllByIncentiveId(id);
        approvalDecisionRepository.deleteAll(oldDecisions);

        // Increment approval round so old tokens are invalidated
        incentive.setApprovalRound(incentive.getApprovalRound() + 1);

        // Set status back to PENDING_APPROVAL
        incentive.setStatus(IncentiveStatus.PENDING_APPROVAL);
        incentive.setStatusChangedAt(Instant.now());
        incentive = incentiveRepository.save(incentive);
        log.info("Incentive {} resubmitted for approval (round {})", id, incentive.getApprovalRound());

        // Resend emails to all approvers
        List<ApprovalRequestEvent.ApproverInfo> approverInfos = incentive.getApprovers().stream()
            .map(a -> new ApprovalRequestEvent.ApproverInfo(a.getEmail(), a.getCategory()))
            .toList();
        approvalEventProducer.publish(new ApprovalRequestEvent(
            incentive.getId(), incentive.getName(), incentive.getApprovalRound(), approverInfos));

        return toListResponseWithCompletions(incentive);
    }

    // ── Approval Status ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApprovalStatusResponse getApprovalStatus(UUID id) {
        Incentive incentive = findByIdAndClient(id);

        List<IncentiveApprover> approvers = incentive.getApprovers() != null
            ? incentive.getApprovers() : List.of();
        List<ApprovalDecisionEntity> decisions =
            approvalDecisionRepository.findAllByIncentiveId(id);

        Map<String, ApprovalDecisionEntity> decisionsByEmail = decisions.stream()
            .collect(Collectors.toMap(ApprovalDecisionEntity::getApproverEmail, Function.identity(),
                (a, b) -> a));

        int approvedCount = 0;
        int rejectedCount = 0;
        int pendingCount = 0;
        List<ApproverStatusResponse> approverStatuses = new ArrayList<>();

        for (IncentiveApprover approver : approvers) {
            ApprovalDecisionEntity decision = decisionsByEmail.get(approver.getEmail());
            String decisionStr = null;
            Instant decidedAt = null;
            String comment = null;

            if (decision != null) {
                decisionStr = decision.getDecision().name();
                decidedAt = decision.getDecidedAt();
                comment = decision.getComment();
                if (decision.getDecision() == ApprovalDecision.APPROVED) {
                    approvedCount++;
                } else {
                    rejectedCount++;
                }
            } else {
                pendingCount++;
            }

            approverStatuses.add(new ApproverStatusResponse(
                approver.getId(), approver.getEmail(), approver.getCategory(),
                approver.getSortOrder(), decisionStr, decidedAt, comment));
        }

        return new ApprovalStatusResponse(
            incentive.getRequiredApprovals(), approvedCount, rejectedCount,
            pendingCount, approverStatuses);
    }

    // ── Resend approval emails ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public void resendApprovalEmails(UUID id) {
        Incentive incentive = findByIdAndClient(id);

        if (incentive.getStatus() != IncentiveStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException("Only PENDING_APPROVAL incentives can resend approval emails");
        }
        if (incentive.getApprovers() == null || incentive.getApprovers().isEmpty()) {
            throw new BusinessRuleException("No approvers configured for this incentive");
        }

        List<ApprovalDecisionEntity> decisions =
            approvalDecisionRepository.findAllByIncentiveId(id);
        Set<String> decidedEmails = decisions.stream()
            .map(ApprovalDecisionEntity::getApproverEmail)
            .collect(Collectors.toSet());

        List<ApprovalRequestEvent.ApproverInfo> approverInfos = incentive.getApprovers().stream()
            .filter(a -> !decidedEmails.contains(a.getEmail()))
            .map(a -> new ApprovalRequestEvent.ApproverInfo(a.getEmail(), a.getCategory()))
            .toList();

        if (approverInfos.isEmpty()) {
            throw new BusinessRuleException("All approvers have already responded");
        }

        approvalEventProducer.publish(new ApprovalRequestEvent(
            incentive.getId(), incentive.getName(), incentive.getApprovalRound(), approverInfos));
        log.info("Resent approval emails to {} pending approver(s) for incentive {}",
            approverInfos.size(), id);
    }

    @Transactional(readOnly = true)
    public void resendApprovalToApprover(UUID id, String email) {
        Incentive incentive = findByIdAndClient(id);

        if (incentive.getStatus() != IncentiveStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException("Only PENDING_APPROVAL incentives can resend approval emails");
        }

        IncentiveApprover approver = incentive.getApprovers().stream()
            .filter(a -> a.getEmail().equalsIgnoreCase(email))
            .findFirst()
            .orElseThrow(() -> new BusinessRuleException("Approver not found: " + email));

        approvalDecisionRepository.findByIncentiveIdAndApproverEmail(id, approver.getEmail())
            .ifPresent(d -> {
                throw new BusinessRuleException("Approver has already responded");
            });

        List<ApprovalRequestEvent.ApproverInfo> approverInfos = List.of(
            new ApprovalRequestEvent.ApproverInfo(approver.getEmail(), approver.getCategory()));
        approvalEventProducer.publish(new ApprovalRequestEvent(
            incentive.getId(), incentive.getName(), incentive.getApprovalRound(), approverInfos));
        log.info("Resent approval email to {} for incentive {}", email, id);
    }

    // ── Delete ──────────────────────────────────────────────────────────

    @Transactional
    public void deleteIncentive(UUID id) {
        Incentive incentive = findByIdAndClient(id);

        // Prevent deletion of INACTIVE incentives that have reward transactions (audit trail)
        if (incentive.getStatus() == IncentiveStatus.INACTIVE) {
            UUID clientId = tenantValidator.getCurrentClientId();
            List<RewardTransaction> transactions =
                rewardTransactionRepository.findByClientIdAndIncentiveId(clientId, incentive.getId());
            boolean hasAwardedRewards = transactions.stream()
                .anyMatch(rt -> rt.getAmountAwarded() != null
                    && rt.getAmountAwarded().compareTo(java.math.BigDecimal.ZERO) > 0);
            if (hasAwardedRewards) {
                throw new BusinessRuleException(
                    "Cannot delete an inactive incentive that has awarded rewards — required for audit");
            }
        }

        incentive.setDeleted(true);
        incentiveRepository.save(incentive);
        log.info("Soft deleted incentive {}", id);
    }

    // ── Clone ───────────────────────────────────────────────────────────

    @Transactional
    public IncentiveDetailResponse cloneIncentive(UUID id, String newName, String newDescription) {
        Incentive source = findByIdAndClient(id);
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        Incentive clone = Incentive.builder()
            .name(newName)
            .description(newDescription != null ? newDescription : source.getDescription())
            .incentiveType(source.getIncentiveType())
            .status(IncentiveStatus.DRAFT)
            .clientId(clientId)
            .createdBy(userId)
            .startDate(source.getStartDate())
            .endDate(source.getEndDate())
            .timezone(source.getTimezone())
            .journeySequential(source.getJourneySequential())
            .rewardCurrencies(source.getRewardCurrencies())
            .rewardMessage(source.getRewardMessage())
            .rewardAmounts(source.getRewardAmounts())
            .fiscalYears(source.getFiscalYears())
            .fiscalQuarters(source.getFiscalQuarters())
            .trainingRequiredCount(source.getTrainingRequiredCount())
            .countriesText(source.getCountriesText())
            .specificPartners(source.getSpecificPartners())
            .customFieldValues(source.getCustomFieldValues())
            .requiresApproval(source.getRequiresApproval())
            .requiredApprovals(source.getRequiredApprovals())
            .build();

        // Clone budgets — including per-location allocations so cloned PER_LOCATION
        // incentives keep their hierarchy splits.
        for (IncentiveBudget srcBudget : source.getBudgets()) {
            IncentiveBudget cloneBudget = IncentiveBudget.builder()
                .incentive(clone)
                .totalBudget(srcBudget.getTotalBudget())
                .currencyId(srcBudget.getCurrencyId())
                .allocationMethod(srcBudget.getAllocationMethod())
                .budgetMode(srcBudget.getBudgetMode())
                .budgetLocationLevel(srcBudget.getBudgetLocationLevel())
                .build();
            if (srcBudget.getLocationAllocations() != null) {
                for (com.tenxengage.app.entity.LocationBudgetAllocation srcAlloc : srcBudget.getLocationAllocations()) {
                    cloneBudget.getLocationAllocations().add(
                        com.tenxengage.app.entity.LocationBudgetAllocation.builder()
                            .budget(cloneBudget)
                            .locationValue(srcAlloc.getLocationValue())
                            .amount(srcAlloc.getAmount())
                            .build()
                    );
                }
            }
            clone.getBudgets().add(cloneBudget);
        }

        // Clone audience rules
        for (IncentiveAudienceRule rule : source.getAudienceRules()) {
            clone.getAudienceRules().add(IncentiveAudienceRule.builder()
                .incentive(clone)
                .ruleType(rule.getRuleType())
                .ruleValue(rule.getRuleValue())
                .build());
        }

        // Clone sales requirements (deep)
        for (SalesRequirement srcReq : source.getSalesRequirements()) {
            SalesRequirement cloneReq = SalesRequirement.builder()
                .incentive(clone)
                .name(srcReq.getName())
                .sortOrder(srcReq.getSortOrder())
                .build();

            for (EligibilityRuleGroup srcGroup : srcReq.getEligibilityGroups()) {
                EligibilityRuleGroup cloneGroup = EligibilityRuleGroup.builder()
                    .requirement(cloneReq)
                    .sortOrder(srcGroup.getSortOrder())
                    .build();
                for (EligibilityRule srcRule : srcGroup.getRules()) {
                    cloneGroup.getRules().add(EligibilityRule.builder()
                        .ruleGroup(cloneGroup)
                        .ruleType(srcRule.getRuleType())
                        .fieldId(srcRule.getFieldId())
                        .operator(srcRule.getOperator())
                        .value(srcRule.getValue())
                        .valueMax(srcRule.getValueMax())
                        .selectedProducts(srcRule.getSelectedProducts())
                        .sortOrder(srcRule.getSortOrder())
                        .build());
                }
                cloneReq.getEligibilityGroups().add(cloneGroup);
            }

            for (PayoutConfig srcPayout : srcReq.getPayouts()) {
                PayoutConfig clonePayout = PayoutConfig.builder()
                    .requirement(cloneReq)
                    .currencyId(srcPayout.getCurrencyId())
                    .payoutType(srcPayout.getPayoutType())
                    .against(srcPayout.getAgainst())
                    .maxPerDeal(srcPayout.getMaxPerDeal())
                    .sortOrder(srcPayout.getSortOrder())
                    .build();
                for (PayoutBand srcBand : srcPayout.getBands()) {
                    clonePayout.getBands().add(PayoutBand.builder()
                        .payoutConfig(clonePayout)
                        .minAmount(srcBand.getMinAmount())
                        .maxAmount(srcBand.getMaxAmount())
                        .payoutValue(srcBand.getPayoutValue())
                        .sortOrder(srcBand.getSortOrder())
                        .build());
                }
                cloneReq.getPayouts().add(clonePayout);
            }

            clone.getSalesRequirements().add(cloneReq);
        }

        // Clone training courses
        for (TrainingCourseAssignment course : source.getTrainingCourses()) {
            clone.getTrainingCourses().add(TrainingCourseAssignment.builder()
                .incentive(clone)
                .courseId(course.getCourseId())
                .courseName(course.getCourseName())
                .courseCategory(course.getCourseCategory())
                .courseProvider(course.getCourseProvider())
                .courseDuration(course.getCourseDuration())
                .courseLevel(course.getCourseLevel())
                .required(course.getRequired())
                .sortOrder(course.getSortOrder())
                .build());
        }

        // Clone activity definitions (deep)
        for (ActivityDefinition srcDef : source.getActivityDefinitions()) {
            ActivityDefinition cloneDef = ActivityDefinition.builder()
                .incentive(clone)
                .name(srcDef.getName())
                .description(srcDef.getDescription())
                .categoryId(srcDef.getCategoryId())
                .sortOrder(srcDef.getSortOrder())
                .build();
            for (ActivityDocumentRequirement srcDoc : srcDef.getRequiredDocuments()) {
                cloneDef.getRequiredDocuments().add(ActivityDocumentRequirement.builder()
                    .activityDefinition(cloneDef)
                    .name(srcDoc.getName())
                    .description(srcDoc.getDescription())
                    .required(srcDoc.getRequired())
                    .sortOrder(srcDoc.getSortOrder())
                    .build());
            }
            clone.getActivityDefinitions().add(cloneDef);
        }

        // Clone journey stages
        for (JourneyStage stage : source.getJourneyStages()) {
            clone.getJourneyStages().add(JourneyStage.builder()
                .incentive(clone)
                .linkedIncentiveId(stage.getLinkedIncentiveId())
                .sortOrder(stage.getSortOrder())
                .build());
        }

        // Clone approvers
        for (IncentiveApprover approver : source.getApprovers()) {
            clone.getApprovers().add(IncentiveApprover.builder()
                .incentive(clone)
                .email(approver.getEmail())
                .category(approver.getCategory())
                .sortOrder(approver.getSortOrder())
                .build());
        }

        clone = incentiveRepository.save(clone);
        log.info("Cloned incentive {} to {}", id, clone.getId());
        return toDetailResponse(clone, null);
    }

    // ── Forecast ────────────────────────────────────────────────────────

    /**
     * Generate a forecast via SSE streaming. Returns an SseEmitter that streams
     * progress events and the final forecast result.
     */
    @Transactional(readOnly = true)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter generateForecastStreaming(UUID id) {
        Incentive incentive = findByIdAndClient(id);

        // Eagerly initialize lazy collections and proxies before leaving the transaction scope —
        // forecast generation runs on a virtual thread with no Hibernate Session bound.
        if (incentive.getAudienceRules() != null) {
            incentive.getAudienceRules().size();
            for (IncentiveAudienceRule rule : incentive.getAudienceRules()) {
                if (rule.getLocationLevel() != null) {
                    org.hibernate.Hibernate.initialize(rule.getLocationLevel());
                }
            }
        }
        if (incentive.getBudgets() != null) {
            incentive.getBudgets().size();
            for (IncentiveBudget b : incentive.getBudgets()) {
                if (b.getBudgetLocationLevel() != null) {
                    org.hibernate.Hibernate.initialize(b.getBudgetLocationLevel());
                }
                if (b.getLocationAllocations() != null) {
                    b.getLocationAllocations().size();
                }
            }
        }
        if (incentive.getSalesRequirements() != null) {
            incentive.getSalesRequirements().forEach(sr -> {
                if (sr.getPayouts() != null) sr.getPayouts().forEach(pc -> {
                    if (pc.getBands() != null) pc.getBands().size();
                });
                if (sr.getEligibilityGroups() != null) sr.getEligibilityGroups().forEach(eg -> {
                    if (eg.getRules() != null) eg.getRules().size();
                });
            });
        }

        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(120_000L);

        org.springframework.security.core.context.SecurityContext securityContext =
                org.springframework.security.core.context.SecurityContextHolder.getContext();

        Thread.startVirtualThread(() -> {
            org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
            try {
                com.tenxengage.app.service.forecast.ForecastResult result =
                        forecastEngine.generateWithStreaming(incentive, emitter);

                IncentiveForecast savedForecast = forecastRepository
                        .findTopByIncentiveIdOrderByGeneratedAtDesc(id).orElse(null);
                ForecastResponse response = ForecastResponse.from(savedForecast);

                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("forecast")
                        .data(response));
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("done")
                        .data(java.util.Map.of("status", "complete")));
                emitter.complete();
            } catch (Exception e) {
                log.error("Forecast SSE stream failed for incentive {}: {}", id, e.getMessage(), e);
                emitter.completeWithError(e);
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    /**
     * Generate a preview forecast from builder state (no saved incentive required).
     * Used for all creation flows (from scratch, from existing, from template).
     */
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter generateForecastPreview(
            com.tenxengage.app.dto.request.ForecastPreviewRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        log.info("Forecast preview request: type={}, regions={}, budget={}, products={}, payoutType={}, bands={}",
                request.incentiveType(), request.regions(), request.totalBudget(),
                request.productSkus(), request.payoutType(),
                request.payoutBands() != null ? request.payoutBands().size() : 0);

        // Build a transient Incentive from the request (not saved to DB)
        Incentive transient_ = new Incentive();
        transient_.setClientId(clientId);
        transient_.setName("Preview");
        if (request.incentiveType() != null) {
            transient_.setIncentiveType(IncentiveType.valueOf(request.incentiveType()));
        }
        if (request.startDate() != null && !request.startDate().isBlank()) {
            transient_.setStartDate(parseFlexibleDate(request.startDate()));
        }
        if (request.endDate() != null && !request.endDate().isBlank()) {
            transient_.setEndDate(parseFlexibleDate(request.endDate()));
        }

        // Build audience rules. Two shapes are accepted from the wire:
        //
        //  1. `locationSelections` (preferred) — a level-keyed map mirroring
        //     the builder's `audience.locationSelections`. Each entry becomes
        //     a transient LOCATION rule with `locationLevel` set to a stub
        //     LocationLevel carrying the level UUID. The forecast resolver
        //     uses the (level, name) pair to look up the LocationValue and
        //     walks parent_id up to the depth-0 ancestor, so the forecast
        //     scope honors the full hierarchy the user selected.
        //
        //  2. `regions` (legacy fallback) — a flat depth-0 name list. Used
        //     only when `locationSelections` is null/empty. Emits REGION-typed
        //     rules; the resolver's REGION branch treats these as depth-0
        //     names directly.
        //
        // Preview rules are transient in-memory stubs; they never hit the DB.
        List<IncentiveAudienceRule> audienceRules = new ArrayList<>();
        boolean usedLocationSelections = false;
        if (request.locationSelections() != null && !request.locationSelections().isEmpty()) {
            for (var entry : request.locationSelections().entrySet()) {
                String levelIdStr = entry.getKey();
                List<String> names = entry.getValue();
                if (names == null || names.isEmpty() || levelIdStr == null) continue;
                UUID levelId;
                try {
                    levelId = UUID.fromString(levelIdStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping locationSelections entry with non-UUID levelId: {}", levelIdStr);
                    continue;
                }
                com.tenxengage.app.entity.LocationLevel levelStub =
                        new com.tenxengage.app.entity.LocationLevel();
                levelStub.setId(levelId);
                for (String name : names) {
                    if (name == null || name.isBlank()) continue;
                    IncentiveAudienceRule rule = new IncentiveAudienceRule();
                    rule.setRuleType("LOCATION");
                    rule.setRuleValue(name);
                    rule.setLocationLevel(levelStub);
                    audienceRules.add(rule);
                    usedLocationSelections = true;
                }
            }
        }
        if (!usedLocationSelections && request.regions() != null) {
            for (String region : request.regions()) {
                IncentiveAudienceRule rule = new IncentiveAudienceRule();
                rule.setRuleType("REGION");
                rule.setRuleValue(region);
                audienceRules.add(rule);
            }
        }
        if (request.partnerTypes() != null) {
            for (String pt : request.partnerTypes()) {
                IncentiveAudienceRule rule = new IncentiveAudienceRule();
                rule.setRuleType("PARTNER_TYPE");
                rule.setRuleValue(pt);
                audienceRules.add(rule);
            }
        }
        transient_.setAudienceRules(audienceRules);

        // Build budget
        if (request.totalBudget() != null) {
            // Build budget entries — one per currency if regionBudgets has per-currency data
            List<IncentiveBudget> budgetList = new ArrayList<>();
            BudgetMode mode = ("PER_REGION".equals(request.budgetMode()) || "PER_LOCATION".equals(request.budgetMode()))
                ? BudgetMode.PER_LOCATION : BudgetMode.GLOBAL;

            // Check if regionBudgets actually has data (not just empty currency maps)
            boolean hasRegionBudgetData = false;
            if (request.regionBudgets() != null) {
                for (var entry : request.regionBudgets().values()) {
                    if (entry != null && !entry.isEmpty()) {
                        hasRegionBudgetData = true;
                        break;
                    }
                }
            }

            if (hasRegionBudgetData) {
                // Build one IncentiveBudget per currency from regionBudgets
                for (var entry : request.regionBudgets().entrySet()) {
                    IncentiveBudget b = new IncentiveBudget();
                    b.setCurrencyId(entry.getKey());
                    b.setBudgetMode(mode);
                    // Sum region amounts to get total for this currency
                    BigDecimal total = entry.getValue().values().stream()
                            .map(v -> { try { return new BigDecimal(v); } catch (Exception e) { return BigDecimal.ZERO; } })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    b.setTotalBudget(total);
                    // Location budget allocations are handled separately via the location_budget_allocations table
                    budgetList.add(b);
                }
            } else {
                IncentiveBudget b = new IncentiveBudget();
                b.setTotalBudget(new BigDecimal(request.totalBudget()));
                b.setCurrencyId(request.budgetCurrency() != null ? request.budgetCurrency() : "cash");
                b.setBudgetMode(mode);
                budgetList.add(b);
            }
            transient_.setBudgets(budgetList);
        } else {
            transient_.setBudgets(List.of());
        }

        // Reward currencies
        if (request.selectedCurrencies() != null) {
            transient_.setRewardCurrencies(String.join(",", request.selectedCurrencies()));
        }
        if (request.maxPerPartner() != null) {
            transient_.setMaxPerPartner(new BigDecimal(request.maxPerPartner()));
        }
        if (request.maxPerUser() != null) {
            transient_.setMaxPerUser(new BigDecimal(request.maxPerUser()));
        }

        // Build sales requirements from payout info if provided
        if (request.payoutType() != null || (request.productSkus() != null && !request.productSkus().isEmpty())) {
            SalesRequirement req = new SalesRequirement();
            req.setName("Preview Requirement");
            req.setSortOrder(0);

            // Build eligibility rules from product SKUs
            if (request.productSkus() != null && !request.productSkus().isEmpty()) {
                EligibilityRuleGroup group = new EligibilityRuleGroup();
                group.setSortOrder(0);
                EligibilityRule rule = new EligibilityRule();
                rule.setSelectedProducts(String.join(",", request.productSkus()));
                group.setRules(List.of(rule));
                req.setEligibilityGroups(List.of(group));
            } else {
                req.setEligibilityGroups(List.of());
            }

            // Build payout config
            if (request.payoutType() != null) {
                PayoutConfig pc = new PayoutConfig();
                pc.setPayoutType(PayoutType.valueOf(request.payoutType()));
                pc.setAgainst(request.payoutAgainst());
                if (request.maxPerDeal() != null) {
                    pc.setMaxPerDeal(new BigDecimal(request.maxPerDeal()));
                }

                // Build payout bands
                if (request.payoutBands() != null) {
                    List<PayoutBand> bands = new ArrayList<>();
                    for (var bandReq : request.payoutBands()) {
                        PayoutBand band = new PayoutBand();
                        if (bandReq.minAmount() != null) band.setMinAmount(new BigDecimal(bandReq.minAmount()));
                        if (bandReq.maxAmount() != null) band.setMaxAmount(new BigDecimal(bandReq.maxAmount()));
                        if (bandReq.payoutValue() != null) band.setPayoutValue(new BigDecimal(bandReq.payoutValue()));
                        bands.add(band);
                    }
                    pc.setBands(bands);
                } else {
                    pc.setBands(List.of());
                }
                req.setPayouts(List.of(pc));
            } else {
                req.setPayouts(List.of());
            }

            transient_.setSalesRequirements(List.of(req));
        } else {
            transient_.setSalesRequirements(List.of());
        }

        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(120_000L);

        org.springframework.security.core.context.SecurityContext securityContext =
                org.springframework.security.core.context.SecurityContextHolder.getContext();

        Thread.startVirtualThread(() -> {
            org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
            try {
                com.tenxengage.app.service.forecast.ForecastResult result =
                        forecastEngine.generatePreviewWithStreaming(transient_, emitter);

                // Build response directly from result (no persisted forecast)
                ForecastResponse response = buildPreviewResponse(result);

                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("forecast")
                        .data(response));
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("done")
                        .data(java.util.Map.of("status", "complete")));
                emitter.complete();
            } catch (Exception e) {
                log.error("Preview forecast SSE failed: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    private Instant parseFlexibleDate(String dateStr) {
        try {
            return Instant.parse(dateStr);
        } catch (Exception e1) {
            try {
                return LocalDate.parse(dateStr.substring(0, 10)).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (Exception e2) {
                return Instant.now();
            }
        }
    }

    private ForecastResponse buildPreviewResponse(com.tenxengage.app.service.forecast.ForecastResult result) {
        List<ForecastResponse.LocationBreakdown> locations = result.locationBreakdown().stream()
                .map(lb -> new ForecastResponse.LocationBreakdown(
                        lb.locationValueId(),
                        lb.name(),
                        lb.parentId(),
                        lb.budgetUtilizedPct() != null ? lb.budgetUtilizedPct().toPlainString() : "0",
                        lb.netNewDeals(),
                        lb.netNewBookings() != null ? lb.netNewBookings().toPlainString() : "0",
                        lb.roi() != null ? lb.roi().toPlainString() : "0",
                        lb.participationRate() != null ? lb.participationRate().toPlainString() : "0",
                        lb.budgetAllocated() != null ? lb.budgetAllocated().toPlainString() : "0",
                        lb.budgetPredictedSpend() != null ? lb.budgetPredictedSpend().toPlainString() : "0"))
                .toList();

        List<ForecastResponse.AiInsight> insights = result.insights().stream()
                .map(i -> new ForecastResponse.AiInsight(i.type(), i.title(), i.detail(), i.confidence()))
                .toList();

        java.util.Map<String, List<ForecastResponse.AiInsight>> topLevelInsights = new java.util.LinkedHashMap<>();
        if (result.topLevelInsights() != null) {
            for (var entry : result.topLevelInsights().entrySet()) {
                topLevelInsights.put(entry.getKey(), entry.getValue().stream()
                        .map(i -> new ForecastResponse.AiInsight(i.type(), i.title(), i.detail(), i.confidence()))
                        .toList());
            }
        }

        return new ForecastResponse(
                null, // no persisted ID
                null, // no incentive ID
                result.roi() != null ? result.roi().toPlainString() : "0",
                result.estimatedParticipation(),
                result.participationRate() != null ? result.participationRate().toPlainString() : "0",
                result.estimatedTotalCost() != null ? result.estimatedTotalCost().toPlainString() : "0",
                result.netNewBookings() != null ? result.netNewBookings().toPlainString() : "0",
                result.netNewDeals(),
                result.netNewBookings() != null ? result.netNewBookings().toPlainString() : "0",
                result.confidenceScore() != null ? result.confidenceScore().toPlainString() : "0",
                result.dataQualityScore() != null ? result.dataQualityScore().toPlainString() : "0",
                result.modelVersion(),
                locations,
                List.of(), // monthly projections
                result.similarIncentiveIds() != null ? result.similarIncentiveIds() : List.of(),
                insights,
                topLevelInsights,
                result.reasoning(),
                java.time.Instant.now().toString()
        );
    }

    /**
     * Get the most recent forecast for an incentive (synchronous, for page reload).
     */
    @Transactional(readOnly = true)
    public ForecastResponse getLatestForecast(UUID id) {
        findByIdAndClient(id); // validate access
        return forecastRepository.findTopByIncentiveIdOrderByGeneratedAtDesc(id)
                .map(ForecastResponse::from)
                .orElse(null);
    }

    // ── Documents ──────────────────────────────────────────────────────

    @Transactional
    public List<DocumentSummaryResponse> uploadDocuments(UUID incentiveId,
                                                          List<MultipartFile> files,
                                                          List<String> categories) {
        Incentive incentive = findByIdAndClient(incentiveId);

        if (files == null || files.isEmpty()) {
            throw new BusinessRuleException("At least one file is required");
        }
        if (files.size() > MAX_FILES_PER_UPLOAD) {
            throw new BusinessRuleException("Maximum " + MAX_FILES_PER_UPLOAD + " files per upload");
        }

        List<IncentiveDocument> savedDocs = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);

            // Validate file is not empty
            if (file.isEmpty()) {
                throw new BusinessRuleException("File at index " + i + " is empty");
            }

            // Validate file size
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessRuleException(
                    "File \"" + file.getOriginalFilename() + "\" exceeds the 10 MB size limit");
            }

            // Validate file extension
            String originalFilename = sanitizeFilename(file.getOriginalFilename());
            String extension = getFileExtension(originalFilename);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new BusinessRuleException(
                    "File \"" + originalFilename + "\" has unsupported type. Allowed: PDF, XLSX, XLS, DOCX, DOC");
            }

            // Validate content type
            String contentType = file.getContentType();
            if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType)) {
                throw new BusinessRuleException(
                    "File \"" + originalFilename + "\" has invalid content type: " + contentType);
            }

            // Resolve category
            String category = "program-rules"; // default
            if (categories != null && i < categories.size() && categories.get(i) != null) {
                String cat = categories.get(i).trim().toLowerCase();
                if (!ALLOWED_CATEGORIES.contains(cat)) {
                    throw new BusinessRuleException("Invalid document category: " + categories.get(i));
                }
                category = cat;
            }

            IncentiveDocument doc = IncentiveDocument.builder()
                .incentive(incentive)
                .name(originalFilename)
                .documentType(category)
                .fileType(extension)
                .size(formatFileSize(file.getSize()))
                .build();

            doc = documentRepository.save(doc);

            // Store file binary in MinIO
            try {
                String objectKey = incentiveId + "/" + doc.getId() + "/" + originalFilename;
                fileStorageService.upload(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
                doc.setStoragePath(objectKey);
                documentRepository.save(doc);
            } catch (Exception e) {
                log.error("Failed to upload file to storage: {}", e.getMessage());
                throw new BusinessRuleException("Failed to store file: " + originalFilename);
            }

            savedDocs.add(doc);
        }

        log.info("Uploaded {} documents for incentive {}", savedDocs.size(), incentiveId);
        return savedDocs.stream().map(DocumentSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DownloadResult downloadDocument(UUID incentiveId, UUID documentId) {
        findByIdAndClient(incentiveId);
        IncentiveDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        if (!doc.getIncentive().getId().equals(incentiveId)) {
            throw new BusinessRuleException("Document does not belong to this incentive");
        }
        if (doc.getStoragePath() == null) {
            throw new BusinessRuleException("No file stored for this document");
        }

        java.io.InputStream stream = fileStorageService.download(doc.getStoragePath());
        return new DownloadResult(stream, doc.getName(), doc.getFileType());
    }

    public record DownloadResult(java.io.InputStream inputStream, String filename, String fileType) {}

    @Transactional
    public void deleteDocument(UUID incentiveId, UUID documentId) {
        findByIdAndClient(incentiveId); // verify access
        IncentiveDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        if (!doc.getIncentive().getId().equals(incentiveId)) {
            throw new BusinessRuleException("Document does not belong to this incentive");
        }

        if (doc.getStoragePath() != null) {
            fileStorageService.delete(doc.getStoragePath());
        }

        documentRepository.delete(doc);
        log.info("Deleted document {} from incentive {}", documentId, incentiveId);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════════════

    private Incentive findByIdAndClient(UUID id) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return incentiveRepository.findByIdAndClientIdAndDeletedFalse(id, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Incentive", "id", id));
    }

    private void validateStatusTransition(IncentiveStatus current, IncentiveStatus target) {
        boolean valid = switch (current) {
            case DRAFT -> target == IncentiveStatus.PENDING_APPROVAL || target == IncentiveStatus.ACTIVE;
            case PENDING_APPROVAL -> target == IncentiveStatus.ACTIVE || target == IncentiveStatus.DENIED;
            case DENIED -> target == IncentiveStatus.PENDING_APPROVAL;
            case ACTIVE -> target == IncentiveStatus.INACTIVE;
            case INACTIVE -> target == IncentiveStatus.ACTIVE;
        };
        if (!valid) {
            throw new BusinessRuleException(
                String.format("Invalid status transition from %s to %s", current, target));
        }
    }

    // ── Response mappers ────────────────────────────────────────────────

    /** Convenience wrapper that builds the completion map for a single response */
    private IncentiveResponse toListResponseWithCompletions(Incentive incentive) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        Map<UUID, Instant> completionMap = buildCompletionMap(clientId, userId);
        Map<UUID, Integer> utilizationMap = buildUtilizationMap(clientId);
        return toListResponse(incentive, completionMap, utilizationMap);
    }

    private Map<UUID, Instant> buildCompletionMap(UUID clientId, UUID userId) {
        return userIncentiveCompletionRepository.findByClientIdAndUserId(clientId, userId).stream()
            .collect(Collectors.toMap(UserIncentiveCompletion::getIncentiveId,
                UserIncentiveCompletion::getCompletedAt, (a, b) -> a));
    }

    private Map<UUID, Integer> buildUtilizationMap(UUID clientId) {
        List<BudgetUtilization> allUtils = budgetUtilizationRepository.findAll();
        // Group by incentive, sum utilized
        Map<UUID, BigDecimal> utilizedByIncentive = new HashMap<>();
        for (BudgetUtilization bu : allUtils) {
            utilizedByIncentive.merge(bu.getIncentiveId(), bu.getUtilized(), BigDecimal::add);
        }

        // For each incentive with utilization, compute % against its max budget
        Map<UUID, Integer> result = new HashMap<>();
        for (var entry : utilizedByIncentive.entrySet()) {
            UUID incentiveId = entry.getKey();
            BigDecimal totalUtilized = entry.getValue();
            // Find max budget for this incentive
            incentiveRepository.findById(incentiveId).ifPresent(incentive -> {
                if (incentive.getClientId().equals(clientId) && incentive.getBudgets() != null) {
                    BigDecimal maxBudget = incentive.getBudgets().stream()
                            .map(b -> b.getTotalBudget())
                            .filter(b -> b != null)
                            .max(BigDecimal::compareTo)
                            .orElse(BigDecimal.ZERO);
                    if (maxBudget.compareTo(BigDecimal.ZERO) > 0) {
                        int pct = totalUtilized.multiply(BigDecimal.valueOf(100))
                                .divide(maxBudget, 0, java.math.RoundingMode.HALF_UP)
                                .intValue();
                        result.put(incentiveId, Math.min(pct, 100));
                    }
                }
            });
        }
        return result;
    }

    private IncentiveResponse toListResponse(Incentive incentive, Map<UUID, Instant> completionMap,
                                              Map<UUID, Integer> utilizationMap) {
        String createdByName = resolveCreatedByName(incentive);

        // Documents
        List<DocumentSummaryResponse> documents = incentive.getDocuments() != null
            ? incentive.getDocuments().stream().map(DocumentSummaryResponse::from).toList()
            : Collections.emptyList();

        // Counts + progress
        Integer trainingCourseCount = null;
        Integer activityDefinitionCount = null;
        Integer partnerProgressCompleted = null;
        String partnerProgressLabel = null;

        boolean isUserCompleted = completionMap.containsKey(incentive.getId());

        IncentiveType type = incentive.getIncentiveType();
        if (type == IncentiveType.TRAINING && incentive.getTrainingCourses() != null) {
            int total = incentive.getTrainingCourses().size();
            trainingCourseCount = total;
            partnerProgressLabel = "courses";
            if (isUserCompleted) {
                partnerProgressCompleted = total;
            } else if (total > 0) {
                partnerProgressCompleted = Math.min(total - 1, 1);
            }
        } else if (type == IncentiveType.ACTIVITY && incentive.getActivityDefinitions() != null) {
            int total = incentive.getActivityDefinitions().size();
            activityDefinitionCount = total;
            partnerProgressLabel = "activities";
            if (isUserCompleted) {
                partnerProgressCompleted = total;
            } else if (total > 0) {
                partnerProgressCompleted = Math.min(total - 1, 1);
            }
        }

        // User completion fields (null for SALES)
        Boolean userCompleted = (type != IncentiveType.SALES) ? isUserCompleted : null;
        Instant userCompletedAt = (type != IncentiveType.SALES) ? completionMap.get(incentive.getId()) : null;

        // Journey stages
        List<JourneyStageSummaryResponse> journeyStages = Collections.emptyList();
        if (type == IncentiveType.JOURNEY && incentive.getJourneyStages() != null
                && !incentive.getJourneyStages().isEmpty()) {
            journeyStages = incentive.getJourneyStages().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(stage -> {
                    Incentive linked = stage.getLinkedIncentive();
                    if (linked != null) {
                        Boolean stageCompleted = completionMap.containsKey(linked.getId());
                        return new JourneyStageSummaryResponse(
                            stage.getSortOrder(),
                            linked.getIncentiveType(),
                            linked.getName(),
                            linked.getDescription(),
                            linked.getStatus(),
                            stageCompleted
                        );
                    }
                    return new JourneyStageSummaryResponse(
                        stage.getSortOrder(),
                        null,
                        "Unknown Stage",
                        null,
                        null,
                        null
                    );
                })
                .toList();
        }

        Integer utilizationPct = utilizationMap.getOrDefault(incentive.getId(), null);

        return IncentiveResponse.from(
            incentive,
            createdByName,
            documents,
            trainingCourseCount,
            activityDefinitionCount,
            partnerProgressCompleted,
            partnerProgressLabel,
            journeyStages,
            incentive.getRequiresApproval(),
            userCompleted,
            userCompletedAt,
            utilizationPct
        );
    }

    private IncentiveDetailResponse toDetailResponse(Incentive incentive, ForecastResponse forecast) {
        ApprovalStatusResponse approvalStatus = null;
        if (Boolean.TRUE.equals(incentive.getRequiresApproval())
                && incentive.getApprovers() != null
                && !incentive.getApprovers().isEmpty()) {
            approvalStatus = buildApprovalStatus(incentive);
        }
        // Resolve location value UUIDs to display names for LOCATION audience rules
        Map<String, String> locationValueNames = resolveLocationValueNames(incentive);
        return IncentiveDetailResponse.from(incentive, resolveCreatedByName(incentive), forecast, approvalStatus, locationValueNames);
    }

    private ApprovalStatusResponse buildApprovalStatus(Incentive incentive) {
        List<IncentiveApprover> approvers = incentive.getApprovers();
        List<ApprovalDecisionEntity> decisions =
            approvalDecisionRepository.findAllByIncentiveId(incentive.getId());

        Map<String, ApprovalDecisionEntity> decisionsByEmail = decisions.stream()
            .collect(Collectors.toMap(ApprovalDecisionEntity::getApproverEmail,
                java.util.function.Function.identity(), (a, b) -> a));

        int approvedCount = 0;
        int rejectedCount = 0;
        int pendingCount = 0;
        List<ApproverStatusResponse> statuses = new ArrayList<>();

        for (IncentiveApprover approver : approvers) {
            ApprovalDecisionEntity d = decisionsByEmail.get(approver.getEmail());
            String decision = null;
            java.time.Instant decidedAt = null;
            String comment = null;

            if (d != null) {
                decision = d.getDecision().name();
                decidedAt = d.getDecidedAt();
                comment = d.getComment();
                if (d.getDecision() == com.tenxengage.app.entity.enums.ApprovalDecision.APPROVED) {
                    approvedCount++;
                } else {
                    rejectedCount++;
                }
            } else {
                pendingCount++;
            }

            statuses.add(new ApproverStatusResponse(
                approver.getId(), approver.getEmail(), approver.getCategory(),
                approver.getSortOrder(), decision, decidedAt, comment));
        }

        return new ApprovalStatusResponse(
            incentive.getRequiredApprovals(), approvedCount, rejectedCount,
            pendingCount, statuses);
    }

    private String resolveCreatedByName(Incentive incentive) {
        if (incentive.getCreatedByUser() != null) {
            User user = incentive.getCreatedByUser();
            return user.getFirstName() + " " + user.getLastName();
        }
        return userRepository.findById(incentive.getCreatedBy())
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse("Unknown");
    }

    private Map<String, String> resolveLocationValueNames(Incentive incentive) {
        if (incentive.getAudienceRules() == null) return Map.of();
        List<UUID> valueIds = incentive.getAudienceRules().stream()
            .filter(r -> "LOCATION".equals(r.getRuleType()))
            .map(r -> { try { return UUID.fromString(r.getRuleValue()); } catch (Exception e) { return null; } })
            .filter(id -> id != null)
            .distinct()
            .toList();
        if (valueIds.isEmpty()) return Map.of();
        return locationValueRepository.findByIdIn(valueIds).stream()
            .collect(java.util.stream.Collectors.toMap(
                lv -> lv.getId().toString(),
                com.tenxengage.app.entity.LocationValue::getName,
                (a, b) -> a));
    }

    // ── Entity builders from request DTOs ───────────────────────────────

    private IncentiveBudget buildBudget(BudgetRequest req, Incentive incentive) {
        BudgetMode mode = req.budgetMode() != null ? BudgetMode.valueOf(req.budgetMode()) : BudgetMode.GLOBAL;

        // PER_LOCATION must carry allocations. The UI guarantees this via auto-fill,
        // but reject empty payloads here so non-UI API consumers can't silently
        // persist a "PER_LOCATION" budget with no per-location splits — that state
        // is semantically meaningless and surfaces as broken data downstream.
        boolean hasAllocations = req.locationAllocations() != null
            && !req.locationAllocations().isEmpty();
        if (mode == BudgetMode.PER_LOCATION && !hasAllocations) {
            throw new BusinessRuleException(
                "PER_LOCATION budgets require at least one entry in locationAllocations");
        }

        com.tenxengage.app.entity.LocationLevel level = null;
        if (req.budgetLocationLevelId() != null) {
            level = locationLevelRepository.findById(req.budgetLocationLevelId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Unknown budgetLocationLevelId: " + req.budgetLocationLevelId()));
        }

        IncentiveBudget budget = IncentiveBudget.builder()
            .incentive(incentive)
            .totalBudget(new BigDecimal(req.totalBudget()))
            .currencyId(req.currencyId())
            .allocationMethod(AllocationMethod.valueOf(req.allocationMethod()))
            .budgetMode(mode)
            .budgetLocationLevel(level)
            .build();

        // Per-location allocations: one row per (budget, locationValue). The schema
        // accepts any-depth locationValueIds; the client is responsible for the
        // children-sum-to-parent invariant.
        if (hasAllocations) {
            for (com.tenxengage.app.dto.request.LocationAllocationRequest alloc : req.locationAllocations()) {
                if (alloc == null || alloc.locationValueId() == null) continue;
                com.tenxengage.app.entity.LocationValue lv = locationValueRepository.findById(alloc.locationValueId())
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown locationValueId in budget allocation: " + alloc.locationValueId()));
                budget.getLocationAllocations().add(
                    com.tenxengage.app.entity.LocationBudgetAllocation.builder()
                        .budget(budget)
                        .locationValue(lv)
                        .amount(new BigDecimal(alloc.amount()))
                        .build()
                );
            }
        }

        return budget;
    }

    /**
     * Build an EligibilityRule from a request. The ruleType can be either:
     * - A legacy enum name: PRODUCTS, BOOKING_AMOUNT, CUSTOMER_TYPE
     * - A data object field UUID: resolved to the correct enum via the field's ruleWidget/dataType
     */
    private EligibilityRule buildEligibilityRule(EligibilityRuleRequest rReq, EligibilityRuleGroup group, int sortOrder) {
        EligibilityRuleType ruleType;
        UUID fieldId = null;

        try {
            // Try as legacy enum first
            ruleType = EligibilityRuleType.valueOf(rReq.ruleType());
        } catch (IllegalArgumentException e) {
            // Must be a field UUID — resolve to rule type
            fieldId = UUID.fromString(rReq.ruleType());
            DataObjectField field = fieldRepository.findById(fieldId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown rule field: " + rReq.ruleType()));
            ruleType = resolveRuleType(field);
        }

        // Collect values into selectedProducts column (used by tagging engine)
        String selectedProducts = null;
        if (rReq.selectedProducts() != null && !rReq.selectedProducts().isEmpty()) {
            selectedProducts = String.join(",", rReq.selectedProducts());
        } else if (rReq.listValues() != null && !rReq.listValues().isEmpty()) {
            selectedProducts = String.join(",", rReq.listValues());
        } else if (rReq.customerTypes() != null && !rReq.customerTypes().isEmpty()) {
            selectedProducts = String.join(",", rReq.customerTypes());
        }

        return EligibilityRule.builder()
                .ruleGroup(group)
                .ruleType(ruleType)
                .fieldId(fieldId)
                .operator(rReq.operator() != null ? RuleOperator.valueOf(rReq.operator()) : null)
                .value(rReq.value())
                .valueMax(rReq.valueMax())
                .selectedProducts(selectedProducts)
                .sortOrder(sortOrder)
                .build();
    }

    private EligibilityRuleType resolveRuleType(DataObjectField field) {
        // Check ruleWidget first
        if ("PRODUCT_PICKER".equals(field.getRuleWidget())) {
            return EligibilityRuleType.PRODUCTS;
        }
        // Fall back to dataType-based mapping
        return switch (field.getDataType()) {
            case CURRENCY, NUMBER -> EligibilityRuleType.BOOKING_AMOUNT;
            case LIST -> EligibilityRuleType.CUSTOMER_TYPE;
            default -> EligibilityRuleType.CUSTOMER_TYPE; // TEXT, BOOLEAN, DATE — stored as value-based
        };
    }

    private IncentiveAudienceRule buildAudienceRule(AudienceRuleRequest req, Incentive incentive) {
        String ruleType = req.ruleType();

        if ("REGION".equals(ruleType) || "COUNTRY".equals(ruleType)) {
            // BUG-034: REGION / COUNTRY are no longer accepted on input. Callers must emit LOCATION
            // with locationLevelId + a LocationValue UUID in ruleValue (BUG-079). A 400 here is the
            // cutover guardrail — stale frontend bundles fail loud instead of silently re-entering
            // the broken state.
            throw new BusinessRuleException(
                "Audience rule type '" + ruleType + "' is no longer accepted. "
                    + "Use ruleType=LOCATION with locationLevelId and ruleValue=<LocationValue UUID>.");
        }

        if ("LOCATION".equals(ruleType)) {
            // BUG-079: ruleValue is the LocationValue UUID directly — names live in frontend state
            // and Excel files only, resolved at the boundary (builderRequestMapper / excelTemplateParser).
            // The wire is now identity-stable across renames; the in-service resolver previously here
            // (name → UUID via findByClientIdAndLevelIdAndName) is gone.
            UUID clientId = tenantValidator.getCurrentClientId();
            UUID levelId = req.locationLevelId();
            String valueIdStr = req.ruleValue();

            if (levelId == null) {
                throw new BusinessRuleException(
                    "LOCATION audience rules require locationLevelId.");
            }

            UUID valueId;
            try {
                valueId = UUID.fromString(valueIdStr);
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException(
                    "LOCATION audience rule ruleValue must be a LocationValue UUID, got: '" + valueIdStr + "'");
            }

            com.tenxengage.app.entity.LocationLevel level = locationLevelRepository.findById(levelId)
                .filter(l -> clientId.equals(l.getClientId()))
                .orElseThrow(() -> new BusinessRuleException(
                    "Unknown locationLevelId for tenant: " + levelId));

            com.tenxengage.app.entity.LocationValue value = locationValueRepository.findById(valueId)
                .filter(v -> clientId.equals(v.getClientId()))
                .filter(v -> v.getLevel() != null && levelId.equals(v.getLevel().getId()))
                .orElseThrow(() -> new BusinessRuleException(
                    "Unknown LocationValue UUID '" + valueId
                        + "' at level '" + level.getName() + "' for tenant."));

            return IncentiveAudienceRule.builder()
                .incentive(incentive)
                .ruleType("LOCATION")
                .ruleValue(value.getId().toString())
                .locationLevel(level)
                .build();
        }

        // ROLE / PARTNER_TYPE / future scalar rule types: passthrough.
        return IncentiveAudienceRule.builder()
            .incentive(incentive)
            .ruleType(ruleType)
            .ruleValue(req.ruleValue())
            .build();
    }

    private SalesRequirement buildSalesRequirement(SalesRequirementRequest req, Incentive incentive, int sortOrder) {
        SalesRequirement sr = SalesRequirement.builder()
            .incentive(incentive)
            .name(req.name())
            .sortOrder(sortOrder)
            .build();

        if (req.eligibilityGroups() != null) {
            int groupIdx = 0;
            for (EligibilityRuleGroupRequest gReq : req.eligibilityGroups()) {
                EligibilityRuleGroup group = EligibilityRuleGroup.builder()
                    .requirement(sr)
                    .sortOrder(groupIdx++)
                    .build();

                if (gReq.rules() != null) {
                    int ruleIdx = 0;
                    for (EligibilityRuleRequest rReq : gReq.rules()) {
                        group.getRules().add(buildEligibilityRule(rReq, group, ruleIdx++));
                    }
                }
                sr.getEligibilityGroups().add(group);
            }
        }

        if (req.payouts() != null) {
            int payoutIdx = 0;
            for (PayoutConfigRequest pReq : req.payouts()) {
                // Skip unconfigured payout shells (empty payoutType)
                if (pReq.payoutType() == null || pReq.payoutType().isBlank()) {
                    continue;
                }
                PayoutConfig payout = PayoutConfig.builder()
                    .requirement(sr)
                    .currencyId(pReq.currencyId())
                    .payoutType(PayoutType.valueOf(pReq.payoutType()))
                    .against(pReq.against())
                    .maxPerDeal(parseBigDecimal(pReq.maxPerDeal()))
                    .sortOrder(payoutIdx++)
                    .build();

                if (pReq.bands() != null) {
                    int bandIdx = 0;
                    for (PayoutBandRequest bReq : pReq.bands()) {
                        payout.getBands().add(PayoutBand.builder()
                            .payoutConfig(payout)
                            .minAmount(parseBigDecimal(bReq.minAmount()))
                            .maxAmount(parseBigDecimal(bReq.maxAmount()))
                            .payoutValue(parseBigDecimal(bReq.payoutValue()))
                            .sortOrder(bandIdx++)
                            .build());
                    }
                }
                sr.getPayouts().add(payout);
            }
        }

        return sr;
    }

    private TrainingCourseAssignment buildTrainingCourse(TrainingCourseRequest req, Incentive incentive, int sortOrder) {
        return TrainingCourseAssignment.builder()
            .incentive(incentive)
            .courseId(req.courseId())
            .courseName(req.courseName())
            .courseCategory(req.courseCategory())
            .courseProvider(req.courseProvider())
            .courseDuration(req.courseDuration())
            .courseLevel(req.courseLevel())
            .required(req.required())
            .sortOrder(sortOrder)
            .build();
    }

    private ActivityDefinition buildActivityDefinition(ActivityDefinitionRequest req, Incentive incentive) {
        ActivityDefinition def = ActivityDefinition.builder()
            .incentive(incentive)
            .name(req.name())
            .description(req.description())
            .categoryId(req.categoryId())
            .sortOrder(req.sortOrder())
            .build();

        if (req.requiredDocuments() != null) {
            int docIdx = 0;
            for (var docReq : req.requiredDocuments()) {
                def.getRequiredDocuments().add(ActivityDocumentRequirement.builder()
                    .activityDefinition(def)
                    .name(docReq.name())
                    .description(docReq.description())
                    .required(docReq.required())
                    .sortOrder(docIdx++)
                    .build());
            }
        }
        return def;
    }

    private JourneyStage buildJourneyStage(JourneyStageRequest req, Incentive incentive) {
        return JourneyStage.builder()
            .incentive(incentive)
            .linkedIncentiveId(UUID.fromString(req.linkedIncentiveId()))
            .sortOrder(req.sortOrder())
            .build();
    }

    private IncentiveDocument buildDocument(DocumentRequest req, Incentive incentive) {
        String safeName = sanitizeFilename(req.name());
        String extension = req.fileType().toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException("Unsupported file type: " + extension + ". Allowed: PDF, XLSX, XLS, DOCX, DOC");
        }
        String category = req.documentType().trim().toLowerCase();
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new BusinessRuleException("Invalid document category: " + req.documentType());
        }
        return IncentiveDocument.builder()
            .incentive(incentive)
            .name(safeName)
            .documentType(category)
            .fileType(extension)
            .size(req.size())
            .build();
    }

    // ── Utility ─────────────────────────────────────────────────────────

    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "unnamed";
        // Remove path separators, null bytes, and directory traversal
        return filename
            .replace("\\", "")
            .replace("/", "")
            .replace("\0", "")
            .replace("..", "")
            .trim();
    }

    private static String getFileExtension(String filename) {
        if (filename == null) return "";
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == filename.length() - 1) return "";
        return filename.substring(dotIdx + 1).toLowerCase();
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        if (dateStr.contains("T")) return Instant.parse(dateStr);
        return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        return new BigDecimal(value);
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String toJsonMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String toNestedJsonMap(Map<String, Map<String, String>> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
