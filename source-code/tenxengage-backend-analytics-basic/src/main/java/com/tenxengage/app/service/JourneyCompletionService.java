package com.tenxengage.app.service;

import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.JourneyStage;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.UserIncentiveCompletion;
import com.tenxengage.app.entity.UserJourneyStageProgress;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.ClaimActionRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.UserIncentiveCompletionRepository;
import com.tenxengage.app.repository.UserJourneyStageProgressRepository;
import com.tenxengage.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles journey incentive completion logic. A journey incentive consists of
 * multiple stages, each linked to another incentive (SALES, TRAINING, or ACTIVITY).
 * When a linked incentive is completed (or a sales deal is claimed), this service
 * checks if the corresponding journey stage can be marked complete and whether
 * the entire journey is now finished.
 *
 * Supports both sequential and parallel journey modes:
 * - Sequential: stages must be completed in sort_order; prior stages must be done first.
 * - Parallel: stages can be completed in any order.
 */
@Service
public class JourneyCompletionService {

    private static final Logger log = LoggerFactory.getLogger(JourneyCompletionService.class);

    private final IncentiveRepository incentiveRepository;
    private final UserJourneyStageProgressRepository stageProgressRepository;
    private final UserIncentiveCompletionRepository completionRepository;
    private final RewardGrantService rewardGrantService;
    private final NotificationEventProducer notificationEventProducer;
    private final UserRepository userRepository;
    private final ClaimActionRepository claimActionRepository;

    public JourneyCompletionService(IncentiveRepository incentiveRepository,
                                    UserJourneyStageProgressRepository stageProgressRepository,
                                    UserIncentiveCompletionRepository completionRepository,
                                    RewardGrantService rewardGrantService,
                                    NotificationEventProducer notificationEventProducer,
                                    UserRepository userRepository,
                                    ClaimActionRepository claimActionRepository) {
        this.incentiveRepository = incentiveRepository;
        this.stageProgressRepository = stageProgressRepository;
        this.completionRepository = completionRepository;
        this.rewardGrantService = rewardGrantService;
        this.notificationEventProducer = notificationEventProducer;
        this.userRepository = userRepository;
        this.claimActionRepository = claimActionRepository;
    }

    /**
     * Called when a non-sales incentive is completed (TRAINING, ACTIVITY).
     * Finds all journey incentives that reference this completed incentive as a stage,
     * and processes each one.
     *
     * @param clientId             the tenant client ID
     * @param userId               the user who completed the incentive
     * @param completedIncentiveId the incentive that was just completed
     */
    @Transactional
    public void onIncentiveCompleted(UUID clientId, UUID userId, UUID completedIncentiveId) {
        log.info("Incentive completed: checking journey stages for user={}, incentive={}",
                userId, completedIncentiveId);

        List<Incentive> journeyIncentives = incentiveRepository
                .findJourneyIncentivesContainingStage(completedIncentiveId);

        if (journeyIncentives.isEmpty()) {
            log.debug("No journey incentives reference completed incentive {}", completedIncentiveId);
            return;
        }

        for (Incentive journey : journeyIncentives) {
            if (!journey.getClientId().equals(clientId)) {
                continue; // Skip journeys from other tenants
            }

            List<JourneyStage> stages = journey.getJourneyStages().stream()
                    .sorted(Comparator.comparingInt(JourneyStage::getSortOrder))
                    .toList();

            boolean isSequential = Boolean.TRUE.equals(journey.getJourneySequential());

            for (JourneyStage stage : stages) {
                if (!stage.getLinkedIncentiveId().equals(completedIncentiveId)) {
                    continue;
                }

                if (isSequential) {
                    processJourneyStage(clientId, userId, journey, stage, stages);
                } else {
                    processParallelJourney(clientId, userId, journey, stage);
                }
            }
        }
    }

    /**
     * Called when a sales deal is claimed. Checks if the claimed sales incentive
     * is a stage in any journey, and processes accordingly.
     *
     * @param clientId          the tenant client ID
     * @param userId            the user who claimed the deal
     * @param salesIncentiveId  the sales incentive that was claimed
     */
    @Transactional
    public void onSalesClaimed(UUID clientId, UUID userId, UUID salesIncentiveId) {
        log.info("Sales claimed: checking journey stages for user={}, salesIncentive={}",
                userId, salesIncentiveId);

        List<Incentive> journeyIncentives = incentiveRepository
                .findJourneyIncentivesContainingStage(salesIncentiveId);

        if (journeyIncentives.isEmpty()) {
            log.debug("No journey incentives reference sales incentive {}", salesIncentiveId);
            return;
        }

        for (Incentive journey : journeyIncentives) {
            if (!journey.getClientId().equals(clientId)) {
                continue;
            }

            List<JourneyStage> stages = journey.getJourneyStages().stream()
                    .sorted(Comparator.comparingInt(JourneyStage::getSortOrder))
                    .toList();

            boolean isSequential = Boolean.TRUE.equals(journey.getJourneySequential());

            for (JourneyStage stage : stages) {
                if (!stage.getLinkedIncentiveId().equals(salesIncentiveId)) {
                    continue;
                }

                if (isSequential) {
                    processJourneyStage(clientId, userId, journey, stage, stages);
                } else {
                    processParallelJourney(clientId, userId, journey, stage);
                }
            }
        }
    }

    // -- Private methods ---------------------------------------------------------------------------------

    /**
     * Processes a stage in sequential mode. Verifies that all prior stages (by sort order)
     * are complete before marking this stage. After marking, cascades forward to check
     * if subsequent stages can also be completed.
     */
    private void processJourneyStage(UUID clientId, UUID userId, Incentive journey,
                                     JourneyStage stage, List<JourneyStage> allStages) {
        UUID journeyId = journey.getId();

        // Check if this stage is already complete
        if (isStageComplete(clientId, userId, journeyId, stage.getId())) {
            log.debug("Stage {} already complete for user {} in journey {}",
                    stage.getId(), userId, journeyId);
            return;
        }

        // Verify all prior stages are complete (sequential requirement)
        for (JourneyStage priorStage : allStages) {
            if (priorStage.getSortOrder() >= stage.getSortOrder()) {
                break; // Only check stages with lower sort order
            }
            if (!isStageComplete(clientId, userId, journeyId, priorStage.getId())) {
                log.debug("Prior stage {} not complete; cannot complete stage {} in journey {}",
                        priorStage.getId(), stage.getId(), journeyId);
                return;
            }
        }

        // Verify the linked incentive is actually complete
        if (!isLinkedIncentiveComplete(clientId, userId, journeyId, stage)) {
            log.debug("Linked incentive {} not complete for stage {} in journey {}",
                    stage.getLinkedIncentiveId(), stage.getId(), journeyId);
            return;
        }

        // Mark this stage complete
        markStageComplete(clientId, userId, journeyId, stage);

        // Check overall journey completion
        checkJourneyCompletion(clientId, userId, journey, allStages);

        // Cascade forward: check if any subsequent stages can now be completed
        // (their linked incentive may have already been completed earlier)
        for (JourneyStage nextStage : allStages) {
            if (nextStage.getSortOrder() <= stage.getSortOrder()) {
                continue; // Only check stages after the current one
            }
            if (isStageComplete(clientId, userId, journeyId, nextStage.getId())) {
                continue; // Already complete
            }
            if (isLinkedIncentiveComplete(clientId, userId, journeyId, nextStage)) {
                // All prior stages should now be complete (including the one we just marked),
                // so check the full prior-stage requirement
                boolean allPriorComplete = true;
                for (JourneyStage prior : allStages) {
                    if (prior.getSortOrder() >= nextStage.getSortOrder()) {
                        break;
                    }
                    if (!isStageComplete(clientId, userId, journeyId, prior.getId())) {
                        allPriorComplete = false;
                        break;
                    }
                }
                if (allPriorComplete) {
                    markStageComplete(clientId, userId, journeyId, nextStage);
                    checkJourneyCompletion(clientId, userId, journey, allStages);
                }
            }
        }
    }

    /**
     * Processes a stage in parallel mode. Marks the stage complete immediately
     * (no ordering requirement) and checks if the journey is now fully done.
     */
    private void processParallelJourney(UUID clientId, UUID userId,
                                        Incentive journey, JourneyStage stage) {
        UUID journeyId = journey.getId();

        if (isStageComplete(clientId, userId, journeyId, stage.getId())) {
            log.debug("Stage {} already complete for user {} in parallel journey {}",
                    stage.getId(), userId, journeyId);
            return;
        }

        if (!isLinkedIncentiveComplete(clientId, userId, journeyId, stage)) {
            log.debug("Linked incentive {} not complete for stage {} in parallel journey {}",
                    stage.getLinkedIncentiveId(), stage.getId(), journeyId);
            return;
        }

        markStageComplete(clientId, userId, journeyId, stage);

        List<JourneyStage> allStages = journey.getJourneyStages().stream()
                .sorted(Comparator.comparingInt(JourneyStage::getSortOrder))
                .toList();
        checkJourneyCompletion(clientId, userId, journey, allStages);
    }

    /**
     * Checks if a specific stage is already complete for a user.
     */
    private boolean isStageComplete(UUID clientId, UUID userId, UUID journeyId, UUID stageId) {
        return stageProgressRepository
                .findByClientIdAndUserIdAndJourneyIdAndStageId(clientId, userId, journeyId, stageId)
                .map(UserJourneyStageProgress::isCompleted)
                .orElse(false);
    }

    /**
     * Checks if the incentive linked to a journey stage is complete for the user.
     * For SALES-type incentives, checks if the user has any claim for an eligible PO.
     * For other types (TRAINING, ACTIVITY), checks UserIncentiveCompletion.
     */
    private boolean isLinkedIncentiveComplete(UUID clientId, UUID userId,
                                              UUID journeyId, JourneyStage stage) {
        UUID linkedIncentiveId = stage.getLinkedIncentiveId();

        // Determine the type of the linked incentive
        Incentive linkedIncentive = incentiveRepository.findById(linkedIncentiveId).orElse(null);
        if (linkedIncentive == null) {
            log.warn("Linked incentive {} not found for stage {} in journey {}",
                    linkedIncentiveId, stage.getId(), journeyId);
            return false;
        }

        if (linkedIncentive.getIncentiveType() == IncentiveType.SALES) {
            // For SALES type, check if user has any claim for an eligible PO
            return claimActionRepository.existsClaimForEligiblePo(
                    clientId, userId, linkedIncentiveId);
        }

        // For TRAINING, ACTIVITY, etc., check UserIncentiveCompletion
        return completionRepository.existsByClientIdAndIncentiveIdAndUserId(
                clientId, linkedIncentiveId, userId);
    }

    /**
     * Creates or updates the UserJourneyStageProgress record to mark a stage as complete.
     */
    private void markStageComplete(UUID clientId, UUID userId, UUID journeyId,
                                   JourneyStage stage) {
        UserJourneyStageProgress progress = stageProgressRepository
                .findByClientIdAndUserIdAndJourneyIdAndStageId(
                        clientId, userId, journeyId, stage.getId())
                .orElseGet(() -> UserJourneyStageProgress.builder()
                        .clientId(clientId)
                        .userId(userId)
                        .journeyId(journeyId)
                        .stageId(stage.getId())
                        .linkedIncentiveId(stage.getLinkedIncentiveId())
                        .build());

        progress.setCompleted(true);
        progress.setCompletedAt(Instant.now());
        stageProgressRepository.save(progress);

        log.info("Journey stage marked complete: journey={}, stage={}, user={}",
                journeyId, stage.getId(), userId);
    }

    /**
     * Checks if all stages in the journey are complete. If so, creates the
     * UserIncentiveCompletion, grants optional rewards, and publishes a notification.
     */
    private void checkJourneyCompletion(UUID clientId, UUID userId,
                                        Incentive journey, List<JourneyStage> allStages) {
        UUID journeyId = journey.getId();

        // Already completed?
        if (completionRepository.existsByClientIdAndIncentiveIdAndUserId(clientId, journeyId, userId)) {
            return;
        }

        // Count completed stages
        long completedCount = stageProgressRepository
                .countByClientIdAndUserIdAndJourneyIdAndCompletedTrue(clientId, userId, journeyId);

        int totalStages = allStages.size();

        log.debug("Journey {} stage progress: {}/{} complete for user {}",
                journeyId, completedCount, totalStages, userId);

        if (completedCount < totalStages) {
            return; // Not all stages complete yet
        }

        // Validate journey completion falls within journey timeline
        Instant completionTime = Instant.now();
        if (journey.getStartDate() != null && completionTime.isBefore(journey.getStartDate())) {
            log.info("Journey {} completion is before start date, skipping for user {}", journeyId, userId);
            return;
        }
        if (journey.getEndDate() != null && completionTime.isAfter(journey.getEndDate())) {
            log.info("Journey {} completion is after end date, skipping for user {}", journeyId, userId);
            return;
        }

        // All stages complete -- create journey completion
        Instant completedAt = Instant.now();
        UserIncentiveCompletion completion = UserIncentiveCompletion.builder()
                .clientId(clientId)
                .incentiveId(journeyId)
                .userId(userId)
                .completedAt(completedAt)
                .createdAt(completedAt)
                .build();
        completionRepository.save(completion);

        log.info("Journey {} completed for user {}, completionId={}",
                journeyId, userId, completion.getId());

        // Grant optional rewards (only if rewardAmounts is configured on the journey)
        grantJourneyRewards(clientId, userId, journey, completion.getId());

        // Send notification
        notificationEventProducer.publish(new NotificationEvent(
                "JOURNEY_COMPLETED", clientId,
                "Journey Completed",
                "You have completed all stages of journey '" + journey.getName() + "'.",
                "INCENTIVE", journeyId, null,
                List.of(userId), null));
    }

    /**
     * Grants rewards for a completed journey incentive. Only grants if the journey
     * has reward amounts configured (journeys may not always have direct rewards).
     */
    private void grantJourneyRewards(UUID clientId, UUID userId,
                                     Incentive journey, UUID completionId) {
        Map<String, BigDecimal> rewardAmounts = rewardGrantService
                .parseAmountMap(journey.getRewardAmounts());

        if (rewardAmounts.isEmpty()) {
            log.debug("No reward amounts configured for journey {}", journey.getId());
            return;
        }

        // Resolve user location and partner company for cap checks
        User user = userRepository.findByIdAndClientId(userId, clientId).orElse(null);
        UUID locationValueId = resolveLocationValueForBudget(user, journey);
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
                            clientId, userId, journey.getId(),
                            null, // no claimActionId for journey completions
                            completionId,
                            currencyId, amount,
                            locationValueId, partnerCompanyId);

            rewardGrantService.grantReward(request, journey);
        }
    }

    private UUID resolveLocationValueForBudget(User user, Incentive journey) {
        if (user == null || user.getPartnerCompany() == null || journey.getBudgets() == null) {
            return null;
        }
        IncentiveBudget budget = journey.getBudgets().stream().findFirst().orElse(null);
        return ParticipantEligibilityChecker.resolveLocationValueForBudget(
            user.getPartnerCompany().getLocationAssignments(), budget);
    }
}
