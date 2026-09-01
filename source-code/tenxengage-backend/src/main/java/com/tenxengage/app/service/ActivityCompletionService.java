package com.tenxengage.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.ActivityDefinition;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.UserActivityProgress;
import com.tenxengage.app.entity.UserIncentiveCompletion;
import com.tenxengage.app.entity.enums.DocumentSubmissionStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.event.CompletionEvent;
import com.tenxengage.app.event.CompletionEventProducer;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.UserActivityDocumentSubmissionRepository;
import com.tenxengage.app.repository.UserActivityProgressRepository;
import com.tenxengage.app.repository.UserIncentiveCompletionRepository;
import com.tenxengage.app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles activity completion logic: when all required documents for an activity
 * are approved, marks the activity complete. When all activities for an incentive
 * are complete, creates the incentive completion, grants rewards, and publishes events.
 */
@Service
public class ActivityCompletionService {

    private static final Logger log = LoggerFactory.getLogger(ActivityCompletionService.class);

    private final UserActivityProgressRepository userActivityProgressRepository;
    private final UserActivityDocumentSubmissionRepository userActivityDocumentSubmissionRepository;
    private final UserIncentiveCompletionRepository userIncentiveCompletionRepository;
    private final IncentiveRepository incentiveRepository;
    private final RewardGrantService rewardGrantService;
    private final CompletionEventProducer completionEventProducer;
    private final NotificationEventProducer notificationEventProducer;
    private final UserRepository userRepository;
    private final PartnerCompanyRepository partnerCompanyRepository;
    private final ParticipantEligibilityChecker eligibilityChecker;
    private final ObjectMapper objectMapper;

    public ActivityCompletionService(
            UserActivityProgressRepository userActivityProgressRepository,
            UserActivityDocumentSubmissionRepository userActivityDocumentSubmissionRepository,
            UserIncentiveCompletionRepository userIncentiveCompletionRepository,
            IncentiveRepository incentiveRepository,
            RewardGrantService rewardGrantService,
            CompletionEventProducer completionEventProducer,
            NotificationEventProducer notificationEventProducer,
            UserRepository userRepository,
            PartnerCompanyRepository partnerCompanyRepository,
            ParticipantEligibilityChecker eligibilityChecker,
            ObjectMapper objectMapper) {
        this.userActivityProgressRepository = userActivityProgressRepository;
        this.userActivityDocumentSubmissionRepository = userActivityDocumentSubmissionRepository;
        this.userIncentiveCompletionRepository = userIncentiveCompletionRepository;
        this.incentiveRepository = incentiveRepository;
        this.rewardGrantService = rewardGrantService;
        this.completionEventProducer = completionEventProducer;
        this.notificationEventProducer = notificationEventProducer;
        this.userRepository = userRepository;
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.eligibilityChecker = eligibilityChecker;
        this.objectMapper = objectMapper;
    }

    /**
     * Called when a document submission is approved. Checks if all required documents
     * for the activity are now approved, and if so, marks the activity complete.
     * Then checks if all activities for the incentive are complete.
     *
     * @param clientId             the tenant client ID
     * @param userId               the user who submitted the documents
     * @param activityDefinitionId the activity whose document was approved
     * @param incentiveId          the parent incentive ID
     */
    @Transactional
    public void onDocumentApproved(UUID clientId, UUID userId,
                                   UUID activityDefinitionId, UUID incentiveId) {
        log.info("Document approved: checking activity completion for user={}, activity={}, incentive={}",
                userId, activityDefinitionId, incentiveId);

        // Load the incentive with activity definitions to get required document count
        Incentive incentive = incentiveRepository.findByIdAndClientIdAndDeletedFalse(incentiveId, clientId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Incentive not found: " + incentiveId));

        ActivityDefinition activityDef = incentive.getActivityDefinitions().stream()
                .filter(ad -> ad.getId().equals(activityDefinitionId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Activity definition not found: " + activityDefinitionId));

        // 1. Count APPROVED documents vs total required documents for this activity
        long approvedCount = userActivityDocumentSubmissionRepository
                .countByClientIdAndUserIdAndActivityDefinitionIdAndStatus(
                        clientId, userId, activityDefinitionId, DocumentSubmissionStatus.APPROVED);

        int totalRequired = activityDef.getRequiredDocuments().size();

        log.debug("Activity {} document progress: {}/{} approved",
                activityDefinitionId, approvedCount, totalRequired);

        if (approvedCount < totalRequired) {
            return; // Not all documents approved yet
        }

        // 2. Mark activity complete in user_activity_progress
        UserActivityProgress progress = userActivityProgressRepository
                .findByClientIdAndUserIdAndActivityDefinitionId(clientId, userId, activityDefinitionId)
                .orElseGet(() -> UserActivityProgress.builder()
                        .clientId(clientId)
                        .userId(userId)
                        .activityDefinitionId(activityDefinitionId)
                        .incentiveId(incentiveId)
                        .build());

        if (progress.isCompleted()) {
            log.info("Activity {} already completed for user {}", activityDefinitionId, userId);
            return;
        }

        progress.setCompleted(true);
        progress.setCompletedAt(Instant.now());
        userActivityProgressRepository.save(progress);

        log.info("Activity {} marked complete for user {}", activityDefinitionId, userId);

        // 3. Count completed activities for this user+incentive vs total activities
        long completedActivities = userActivityProgressRepository
                .countByClientIdAndUserIdAndIncentiveIdAndCompletedTrue(
                        clientId, userId, incentiveId);

        int totalActivities = incentive.getActivityDefinitions().size();

        log.debug("Incentive {} activity progress: {}/{} complete for user {}",
                incentiveId, completedActivities, totalActivities, userId);

        if (completedActivities < totalActivities) {
            return; // Not all activities complete yet
        }

        // 3b. Validate completion falls within incentive timeline
        Instant completionTime = Instant.now();
        if (incentive.getStartDate() != null && completionTime.isBefore(incentive.getStartDate())) {
            log.info("Activity completion for incentive {} is before start date, skipping rewards for user {}",
                    incentiveId, userId);
            return;
        }
        if (incentive.getEndDate() != null && completionTime.isAfter(incentive.getEndDate())) {
            log.info("Activity completion for incentive {} is after end date, skipping rewards for user {}",
                    incentiveId, userId);
            return;
        }

        // 3c. Participant eligibility check (region/role/partner type/custom fields)
        User user = userRepository.findByIdAndClientId(userId, clientId).orElse(null);
        if (user == null) {
            log.warn("User not found for eligibility check: client={}, user={}", clientId, userId);
            return;
        }

        PartnerCompany partnerCompany = null;
        Map<String, String> partnerMetadata = Map.of();
        if (user.getPartnerCompanyId() != null) {
            partnerCompany = partnerCompanyRepository
                .findByIdAndClientId(user.getPartnerCompanyId(), clientId)
                .orElse(null);
            if (partnerCompany != null) {
                partnerMetadata = parseMetadata(partnerCompany.getMetadata());
            }
        }

        Map<UUID, Set<UUID>> userLocationsByLevel = partnerCompany != null
            ? ParticipantEligibilityChecker.buildLocationMap(partnerCompany.getLocationAssignments()) : Map.of();
        String userPartnerType = partnerMetadata.get("Partner Type");
        UUID userRoleId = user.getClientRole() != null ? user.getClientRole().getId() : null;
        String userRoleName = user.getClientRole() != null ? user.getClientRole().getName() : null;
        String externalPartnerId = partnerCompany != null ? partnerCompany.getExternalPartnerId() : null;

        if (!eligibilityChecker.matchesUserEligibility(
                incentive, userLocationsByLevel, userRoleId, userRoleName, userPartnerType,
                externalPartnerId, partnerMetadata)) {
            log.info("User {} not eligible for activity incentive {}, skipping rewards",
                    userId, incentiveId);
            return;
        }

        // 4. All activities complete -- create incentive completion
        if (userIncentiveCompletionRepository.existsByClientIdAndIncentiveIdAndUserId(
                clientId, incentiveId, userId)) {
            log.info("Incentive {} already completed for user {}", incentiveId, userId);
            return;
        }

        Instant completedAt = Instant.now();
        UserIncentiveCompletion completion = UserIncentiveCompletion.builder()
                .clientId(clientId)
                .incentiveId(incentiveId)
                .userId(userId)
                .completedAt(completedAt)
                .createdAt(completedAt)
                .build();
        userIncentiveCompletionRepository.save(completion);

        log.info("Incentive {} completed for user {}, completionId={}",
                incentiveId, userId, completion.getId());

        // 5. Grant rewards
        grantActivityRewards(clientId, userId, incentive, completion.getId());

        // 6. Publish CompletionEvent for downstream consumers (e.g., JourneyCompletionService)
        completionEventProducer.publish(new CompletionEvent(
                clientId, userId, incentiveId, completion.getId(),
                IncentiveType.ACTIVITY, completedAt));

        // 7. Send notification
        notificationEventProducer.publish(new NotificationEvent(
                "INCENTIVE_COMPLETED", clientId,
                "Activity Incentive Completed",
                "You have completed all activities for incentive '" + incentive.getName() + "'.",
                "INCENTIVE", incentiveId, null,
                List.of(userId), null));
    }

    /**
     * Grants rewards for a completed activity incentive by iterating over
     * the configured reward amounts (currency -> amount map).
     */
    private void grantActivityRewards(UUID clientId, UUID userId,
                                      Incentive incentive, UUID completionId) {
        Map<String, BigDecimal> rewardAmounts = rewardGrantService
                .parseAmountMap(incentive.getRewardAmounts());

        if (rewardAmounts.isEmpty()) {
            log.debug("No reward amounts configured for incentive {}", incentive.getId());
            return;
        }

        // Resolve user location and partner company for cap checks
        User user = userRepository.findByIdAndClientId(userId, clientId).orElse(null);
        UUID locationValueId = resolveLocationValueForBudget(user, incentive);
        UUID partnerCompanyId = null;
        if (user != null) {
            partnerCompanyId = user.getPartnerCompanyId();
        }

        for (Map.Entry<String, BigDecimal> entry : rewardAmounts.entrySet()) {
            String currencyId = entry.getKey();
            BigDecimal amount = entry.getValue();

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            RewardGrantService.RewardGrantRequest request =
                    new RewardGrantService.RewardGrantRequest(
                            clientId, userId, incentive.getId(),
                            null, // no claimActionId for activity completions
                            completionId,
                            currencyId, amount,
                            locationValueId, partnerCompanyId);

            rewardGrantService.grantReward(request, incentive);
        }
    }

    private UUID resolveLocationValueForBudget(User user, Incentive incentive) {
        if (user == null || user.getPartnerCompany() == null || incentive.getBudgets() == null) {
            return null;
        }
        IncentiveBudget budget = incentive.getBudgets().stream().findFirst().orElse(null);
        return ParticipantEligibilityChecker.resolveLocationValueForBudget(
            user.getPartnerCompany().getLocationAssignments(), budget);
    }

    private Map<String, String> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) return Map.of();
        try {
            Map<String, Object> raw = objectMapper.readValue(metadataJson,
                new TypeReference<Map<String, Object>>() {});
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
}
