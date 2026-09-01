package com.tenxengage.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.TrainingCourseAssignment;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.UserIncentiveCompletion;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.event.CompletionEvent;
import com.tenxengage.app.event.CompletionEventProducer;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.UserCourseCompletionRepository;
import com.tenxengage.app.repository.UserIncentiveCompletionRepository;
import com.tenxengage.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Evaluates whether a user has completed the required training courses for
 * each active TRAINING incentive, and grants rewards when completion criteria are met.
 */
@Service
public class TrainingCompletionService {

    private static final Logger log = LoggerFactory.getLogger(TrainingCompletionService.class);

    private final IncentiveRepository incentiveRepository;
    private final UserIncentiveCompletionRepository userIncentiveCompletionRepository;
    private final UserCourseCompletionRepository userCourseCompletionRepository;
    private final RewardGrantService rewardGrantService;
    private final UserRepository userRepository;
    private final PartnerCompanyRepository partnerCompanyRepository;
    private final CompletionEventProducer completionEventProducer;
    private final NotificationEventProducer notificationEventProducer;
    private final ParticipantEligibilityChecker eligibilityChecker;
    private final ObjectMapper objectMapper;

    public TrainingCompletionService(IncentiveRepository incentiveRepository,
                                     UserIncentiveCompletionRepository userIncentiveCompletionRepository,
                                     UserCourseCompletionRepository userCourseCompletionRepository,
                                     RewardGrantService rewardGrantService,
                                     UserRepository userRepository,
                                     PartnerCompanyRepository partnerCompanyRepository,
                                     CompletionEventProducer completionEventProducer,
                                     NotificationEventProducer notificationEventProducer,
                                     ParticipantEligibilityChecker eligibilityChecker,
                                     ObjectMapper objectMapper) {
        this.incentiveRepository = incentiveRepository;
        this.userIncentiveCompletionRepository = userIncentiveCompletionRepository;
        this.userCourseCompletionRepository = userCourseCompletionRepository;
        this.rewardGrantService = rewardGrantService;
        this.userRepository = userRepository;
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.completionEventProducer = completionEventProducer;
        this.notificationEventProducer = notificationEventProducer;
        this.eligibilityChecker = eligibilityChecker;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates all active TRAINING incentives for the given client and user.
     * For each incentive not yet completed, checks eligibility and course completion,
     * then grants rewards if the completion threshold is met.
     *
     * @param clientId the tenant client ID
     * @param userId   the user to evaluate
     */
    @Transactional
    public void evaluateTrainingCompletions(UUID clientId, UUID userId) {
        log.info("Evaluating training completions: client={}, user={}", clientId, userId);

        // 1. Load all ACTIVE TRAINING incentives with eager associations
        List<Incentive> activeTrainingIncentives = incentiveRepository
            .findActiveTrainingByClientIdWithAssociations(clientId);

        if (activeTrainingIncentives.isEmpty()) {
            log.debug("No active training incentives found for client={}", clientId);
            return;
        }

        // 2. Pre-load user's existing completions to avoid repeated queries
        Set<UUID> alreadyCompletedIncentiveIds = userIncentiveCompletionRepository
            .findByClientIdAndUserId(clientId, userId)
            .stream()
            .map(UserIncentiveCompletion::getIncentiveId)
            .collect(Collectors.toSet());

        // 3. Load the user entity and partner company for eligibility checks
        User user = userRepository.findByIdAndClientId(userId, clientId).orElse(null);
        if (user == null) {
            log.warn("User not found: client={}, user={}", clientId, userId);
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

        // 4. Evaluate each incentive
        int evaluated = 0;
        int completed = 0;
        for (Incentive incentive : activeTrainingIncentives) {
            if (alreadyCompletedIncentiveIds.contains(incentive.getId())) {
                continue;
            }

            evaluated++;

            // Count how many required courses the user has completed
            List<TrainingCourseAssignment> requiredCourses = incentive.getTrainingCourses().stream()
                .filter(tc -> Boolean.TRUE.equals(tc.getRequired()))
                .toList();

            long completedCount = countCompletedCourses(clientId, userId, requiredCourses);

            // Resolve the latest completion date among required courses for date range check
            Set<UUID> requiredCourseIds = requiredCourses.stream()
                .map(tc -> {
                    try { return UUID.fromString(tc.getCourseId()); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(id -> id != null)
                .collect(Collectors.toSet());
            Optional<Instant> latestCompletion = requiredCourseIds.isEmpty()
                ? Optional.empty()
                : userCourseCompletionRepository.findLatestCompletionDate(clientId, userId, requiredCourseIds);

            if (!checkParticipantEligibility(user, incentive, latestCompletion.orElse(null),
                    partnerCompany, partnerMetadata)) {
                log.debug("User {} not eligible for incentive {}", userId, incentive.getId());
                continue;
            }

            // Determine the threshold: explicit trainingRequiredCount, or all required courses
            int threshold;
            if (incentive.getTrainingRequiredCount() != null) {
                threshold = incentive.getTrainingRequiredCount();
            } else {
                threshold = requiredCourses.size();
            }

            if (completedCount >= threshold && threshold > 0) {
                completeIncentive(clientId, userId, incentive);
                completed++;
            }
        }

        log.info("Training evaluation complete: client={}, user={}, evaluated={}, completed={}",
            clientId, userId, evaluated, completed);
    }

    // -- Private helpers ---------------------------------------------------------------------------------

    /**
     * Checks whether the user is eligible to participate in the given incentive.
     * Validates date range (using completion date) and delegates region/role/partner type/
     * custom field checks to the shared ParticipantEligibilityChecker.
     */
    private boolean checkParticipantEligibility(User user, Incentive incentive, Instant completionDate,
            PartnerCompany partnerCompany, Map<String, String> partnerMetadata) {
        // Date range check: use the actual course completion date if available, otherwise fall back to now
        Instant checkDate = completionDate != null ? completionDate : Instant.now();

        if (incentive.getStartDate() != null && checkDate.isBefore(incentive.getStartDate())) {
            return false;
        }
        if (incentive.getEndDate() != null && checkDate.isAfter(incentive.getEndDate())) {
            return false;
        }

        // Delegate location/role/partner type/custom field checks to shared checker
        Map<UUID, Set<UUID>> userLocationsByLevel = partnerCompany != null
            ? ParticipantEligibilityChecker.buildLocationMap(partnerCompany.getLocationAssignments()) : Map.of();
        String userPartnerType = partnerMetadata.get("Partner Type");
        UUID userRoleId = user.getClientRole() != null ? user.getClientRole().getId() : null;
        String userRoleName = user.getClientRole() != null ? user.getClientRole().getName() : null;
        String externalPartnerId = partnerCompany != null ? partnerCompany.getExternalPartnerId() : null;

        return eligibilityChecker.matchesUserEligibility(
            incentive, userLocationsByLevel, userRoleId, userRoleName, userPartnerType,
            externalPartnerId, partnerMetadata);
    }

    /**
     * Counts how many of the required courses the user has completed.
     * <p>
     * TODO: Wire actual UserCourseCompletion or data object query here.
     * This placeholder always returns 0 until the course completion data source is available.
     */
    private long countCompletedCourses(UUID clientId, UUID userId,
                                       List<TrainingCourseAssignment> requiredCourses) {
        if (requiredCourses.isEmpty()) {
            return 0;
        }

        Set<UUID> requiredCourseIds = requiredCourses.stream()
            .map(tc -> {
                try {
                    return UUID.fromString(tc.getCourseId());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid course ID format: {}", tc.getCourseId());
                    return null;
                }
            })
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (requiredCourseIds.isEmpty()) {
            return 0;
        }

        return userCourseCompletionRepository
            .countByClientIdAndUserIdAndCourseIdIn(clientId, userId, requiredCourseIds);
    }

    /**
     * Completes the incentive for the user: creates completion record, grants rewards,
     * publishes completion event, and sends notification.
     */
    @Transactional
    private void completeIncentive(UUID clientId, UUID userId, Incentive incentive) {
        // Guard against duplicate completion (race condition safety)
        if (userIncentiveCompletionRepository.existsByClientIdAndIncentiveIdAndUserId(
                clientId, incentive.getId(), userId)) {
            log.debug("Incentive already completed, skipping: incentive={}, user={}",
                incentive.getId(), userId);
            return;
        }

        Instant now = Instant.now();

        // Create completion record
        UserIncentiveCompletion completion = UserIncentiveCompletion.builder()
            .clientId(clientId)
            .incentiveId(incentive.getId())
            .userId(userId)
            .completedAt(now)
            .createdAt(now)
            .build();
        userIncentiveCompletionRepository.save(completion);

        log.info("Training incentive completed: incentive={}, user={}, completion={}",
            incentive.getId(), userId, completion.getId());

        // Parse reward amounts and grant each currency
        Map<String, BigDecimal> rewardAmounts = rewardGrantService.parseAmountMap(
            incentive.getRewardAmounts());

        // Resolve user location for budget cap calculations
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

            RewardGrantService.RewardGrantRequest grantRequest = new RewardGrantService.RewardGrantRequest(
                clientId, userId, incentive.getId(),
                null,                   // claimActionId - not applicable for training
                completion.getId(),     // completionId
                currencyId, amount, locationValueId, partnerCompanyId
            );

            RewardGrantService.RewardGrantResult result = rewardGrantService.grantReward(
                grantRequest, incentive);

            log.debug("Reward granted for training completion: incentive={}, user={}, "
                + "currency={}, potential={}, awarded={}, capped={}",
                incentive.getId(), userId, currencyId,
                result.amountPotential(), result.amountAwarded(), result.budgetCapped());
        }

        // Publish completion event
        completionEventProducer.publish(new CompletionEvent(
            clientId, userId, incentive.getId(), completion.getId(),
            IncentiveType.TRAINING, now));

        // Publish notification
        notificationEventProducer.publish(new NotificationEvent(
            "TRAINING_COMPLETED", clientId,
            "Training Incentive Completed",
            "Congratulations! You have completed all required training for '"
                + incentive.getName() + "'.",
            "INCENTIVE", incentive.getId(), null,
            List.of(userId), null));
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
