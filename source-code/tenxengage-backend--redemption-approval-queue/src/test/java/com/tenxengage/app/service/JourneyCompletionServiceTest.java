package com.tenxengage.app.service;

import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.JourneyStage;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.UserJourneyStageProgress;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.ClaimActionRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.UserIncentiveCompletionRepository;
import com.tenxengage.app.repository.UserJourneyStageProgressRepository;
import com.tenxengage.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JourneyCompletionServiceTest {

    @Mock
    private IncentiveRepository incentiveRepository;

    @Mock
    private UserJourneyStageProgressRepository stageProgressRepository;

    @Mock
    private UserIncentiveCompletionRepository completionRepository;

    @Mock
    private RewardGrantService rewardGrantService;

    @Mock
    private NotificationEventProducer notificationEventProducer;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClaimActionRepository claimActionRepository;

    private JourneyCompletionService journeyCompletionService;

    private UUID clientId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        journeyCompletionService = new JourneyCompletionService(
            incentiveRepository,
            stageProgressRepository,
            completionRepository,
            rewardGrantService,
            notificationEventProducer,
            userRepository,
            claimActionRepository
        );

        clientId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void onIncentiveCompleted_allStagesComplete_triggersJourneyCompletion() {
        UUID journeyId = UUID.randomUUID();
        UUID completedIncentiveId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();

        // Build a parallel journey with one stage
        Incentive journey = buildJourneyIncentive(journeyId, false);
        JourneyStage stage = buildStage(stageId, journey, completedIncentiveId, 1);
        journey.setJourneyStages(new ArrayList<>(List.of(stage)));

        // Build the linked TRAINING incentive
        Incentive linkedIncentive = Incentive.builder()
            .name("Linked Training")
            .incentiveType(IncentiveType.TRAINING)
            .status(IncentiveStatus.ACTIVE)
            .clientId(clientId)
            .createdBy(UUID.randomUUID())
            .build();
        linkedIncentive.setId(completedIncentiveId);

        when(incentiveRepository.findJourneyIncentivesContainingStage(completedIncentiveId))
            .thenReturn(List.of(journey));

        // Stage is not yet complete
        when(stageProgressRepository
            .findByClientIdAndUserIdAndJourneyIdAndStageId(clientId, userId, journeyId, stageId))
            .thenReturn(Optional.empty());

        // Linked incentive is complete
        when(incentiveRepository.findById(completedIncentiveId)).thenReturn(Optional.of(linkedIncentive));
        when(completionRepository.existsByClientIdAndIncentiveIdAndUserId(clientId, completedIncentiveId, userId))
            .thenReturn(true);

        // Stage save
        when(stageProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Journey not yet completed
        when(completionRepository.existsByClientIdAndIncentiveIdAndUserId(clientId, journeyId, userId))
            .thenReturn(false);

        // All 1 stage is now complete
        when(stageProgressRepository.countByClientIdAndUserIdAndJourneyIdAndCompletedTrue(
            clientId, userId, journeyId)).thenReturn(1L);

        // Completion save
        when(completionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        journeyCompletionService.onIncentiveCompleted(clientId, userId, completedIncentiveId);

        verify(stageProgressRepository).save(any());
        verify(completionRepository).save(any());
        verify(notificationEventProducer).publish(any());
    }

    @Test
    void onIncentiveCompleted_sequentialMode_outOfOrder_noCompletion() {
        UUID journeyId = UUID.randomUUID();
        UUID stage1LinkedId = UUID.randomUUID();
        UUID stage2LinkedId = UUID.randomUUID();
        UUID stage1Id = UUID.randomUUID();
        UUID stage2Id = UUID.randomUUID();

        Incentive journey = buildJourneyIncentive(journeyId, true); // sequential
        JourneyStage stage1 = buildStage(stage1Id, journey, stage1LinkedId, 1);
        JourneyStage stage2 = buildStage(stage2Id, journey, stage2LinkedId, 2);
        journey.setJourneyStages(new ArrayList<>(List.of(stage1, stage2)));

        when(incentiveRepository.findJourneyIncentivesContainingStage(stage2LinkedId))
            .thenReturn(List.of(journey));

        // Stage 2 is not complete yet
        when(stageProgressRepository
            .findByClientIdAndUserIdAndJourneyIdAndStageId(clientId, userId, journeyId, stage2Id))
            .thenReturn(Optional.empty());

        // Stage 1 (prior) is NOT complete -- this blocks sequential processing
        when(stageProgressRepository
            .findByClientIdAndUserIdAndJourneyIdAndStageId(clientId, userId, journeyId, stage1Id))
            .thenReturn(Optional.empty());

        journeyCompletionService.onIncentiveCompleted(clientId, userId, stage2LinkedId);

        // Stage should not be marked because prior stage is not done
        verify(stageProgressRepository, never()).save(any());
        verify(completionRepository, never()).save(any());
    }

    @Test
    void onIncentiveCompleted_nonSequential_anyOrder_works() {
        UUID journeyId = UUID.randomUUID();
        UUID stage1LinkedId = UUID.randomUUID();
        UUID stage2LinkedId = UUID.randomUUID();
        UUID stage1Id = UUID.randomUUID();
        UUID stage2Id = UUID.randomUUID();

        Incentive journey = buildJourneyIncentive(journeyId, false); // parallel
        JourneyStage stage1 = buildStage(stage1Id, journey, stage1LinkedId, 1);
        JourneyStage stage2 = buildStage(stage2Id, journey, stage2LinkedId, 2);
        journey.setJourneyStages(new ArrayList<>(List.of(stage1, stage2)));

        // Build the linked TRAINING incentive for stage 2
        Incentive linkedIncentive = Incentive.builder()
            .name("Stage 2 Training")
            .incentiveType(IncentiveType.TRAINING)
            .status(IncentiveStatus.ACTIVE)
            .clientId(clientId)
            .createdBy(UUID.randomUUID())
            .build();
        linkedIncentive.setId(stage2LinkedId);

        when(incentiveRepository.findJourneyIncentivesContainingStage(stage2LinkedId))
            .thenReturn(List.of(journey));

        // Stage 2 not yet complete
        when(stageProgressRepository
            .findByClientIdAndUserIdAndJourneyIdAndStageId(clientId, userId, journeyId, stage2Id))
            .thenReturn(Optional.empty());

        // Linked incentive is complete
        when(incentiveRepository.findById(stage2LinkedId)).thenReturn(Optional.of(linkedIncentive));
        when(completionRepository.existsByClientIdAndIncentiveIdAndUserId(clientId, stage2LinkedId, userId))
            .thenReturn(true);

        when(stageProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Journey not yet completed (only 1 of 2 stages done)
        when(completionRepository.existsByClientIdAndIncentiveIdAndUserId(clientId, journeyId, userId))
            .thenReturn(false);
        when(stageProgressRepository.countByClientIdAndUserIdAndJourneyIdAndCompletedTrue(
            clientId, userId, journeyId)).thenReturn(1L);

        journeyCompletionService.onIncentiveCompleted(clientId, userId, stage2LinkedId);

        // Stage 2 is saved regardless of stage 1 status (parallel mode)
        verify(stageProgressRepository).save(any());
        // Journey not completed yet because only 1 of 2 stages done
        verify(completionRepository, never()).save(any());
    }

    @Test
    void onIncentiveCompleted_journeyHasOwnRewards_grantsReward() {
        UUID journeyId = UUID.randomUUID();
        UUID completedIncentiveId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();

        Incentive journey = buildJourneyIncentive(journeyId, false);
        journey.setRewardAmounts("{\"cash\":\"500\"}");
        JourneyStage stage = buildStage(stageId, journey, completedIncentiveId, 1);
        journey.setJourneyStages(new ArrayList<>(List.of(stage)));

        Incentive linkedIncentive = Incentive.builder()
            .name("Linked Training")
            .incentiveType(IncentiveType.TRAINING)
            .status(IncentiveStatus.ACTIVE)
            .clientId(clientId)
            .createdBy(UUID.randomUUID())
            .build();
        linkedIncentive.setId(completedIncentiveId);

        when(incentiveRepository.findJourneyIncentivesContainingStage(completedIncentiveId))
            .thenReturn(List.of(journey));
        when(stageProgressRepository
            .findByClientIdAndUserIdAndJourneyIdAndStageId(clientId, userId, journeyId, stageId))
            .thenReturn(Optional.empty());
        when(incentiveRepository.findById(completedIncentiveId)).thenReturn(Optional.of(linkedIncentive));
        when(completionRepository.existsByClientIdAndIncentiveIdAndUserId(clientId, completedIncentiveId, userId))
            .thenReturn(true);
        when(stageProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(completionRepository.existsByClientIdAndIncentiveIdAndUserId(clientId, journeyId, userId))
            .thenReturn(false);
        when(stageProgressRepository.countByClientIdAndUserIdAndJourneyIdAndCompletedTrue(
            clientId, userId, journeyId)).thenReturn(1L);
        when(completionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Mock rewardGrantService.parseAmountMap to return the parsed map
        when(rewardGrantService.parseAmountMap("{\"cash\":\"500\"}"))
            .thenReturn(java.util.Map.of("cash", new java.math.BigDecimal("500")));

        // Mock user lookup for reward granting
        User user = buildUser();
        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.of(user));

        when(rewardGrantService.grantReward(any(), eq(journey)))
            .thenReturn(new RewardGrantService.RewardGrantResult(
                new java.math.BigDecimal("500"), new java.math.BigDecimal("500"), false));

        journeyCompletionService.onIncentiveCompleted(clientId, userId, completedIncentiveId);

        verify(rewardGrantService).grantReward(any(), eq(journey));
    }

    @Test
    void onIncentiveCompleted_journeyNoRewards_noAdditionalReward() {
        UUID journeyId = UUID.randomUUID();
        UUID completedIncentiveId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();

        Incentive journey = buildJourneyIncentive(journeyId, false);
        journey.setRewardAmounts(null); // no rewards on journey itself
        JourneyStage stage = buildStage(stageId, journey, completedIncentiveId, 1);
        journey.setJourneyStages(new ArrayList<>(List.of(stage)));

        Incentive linkedIncentive = Incentive.builder()
            .name("Linked Training")
            .incentiveType(IncentiveType.TRAINING)
            .status(IncentiveStatus.ACTIVE)
            .clientId(clientId)
            .createdBy(UUID.randomUUID())
            .build();
        linkedIncentive.setId(completedIncentiveId);

        when(incentiveRepository.findJourneyIncentivesContainingStage(completedIncentiveId))
            .thenReturn(List.of(journey));
        when(stageProgressRepository
            .findByClientIdAndUserIdAndJourneyIdAndStageId(clientId, userId, journeyId, stageId))
            .thenReturn(Optional.empty());
        when(incentiveRepository.findById(completedIncentiveId)).thenReturn(Optional.of(linkedIncentive));
        when(completionRepository.existsByClientIdAndIncentiveIdAndUserId(clientId, completedIncentiveId, userId))
            .thenReturn(true);
        when(stageProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(completionRepository.existsByClientIdAndIncentiveIdAndUserId(clientId, journeyId, userId))
            .thenReturn(false);
        when(stageProgressRepository.countByClientIdAndUserIdAndJourneyIdAndCompletedTrue(
            clientId, userId, journeyId)).thenReturn(1L);
        when(completionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(rewardGrantService.parseAmountMap(null)).thenReturn(java.util.Collections.emptyMap());

        journeyCompletionService.onIncentiveCompleted(clientId, userId, completedIncentiveId);

        verify(rewardGrantService, never()).grantReward(any(), any());
    }

    @Test
    void onIncentiveCompleted_alreadyCompleted_skips() {
        UUID completedIncentiveId = UUID.randomUUID();

        when(incentiveRepository.findJourneyIncentivesContainingStage(completedIncentiveId))
            .thenReturn(List.of());

        journeyCompletionService.onIncentiveCompleted(clientId, userId, completedIncentiveId);

        verify(stageProgressRepository, never()).save(any());
        verify(completionRepository, never()).save(any());
        verify(rewardGrantService, never()).grantReward(any(), any());
    }

    // -- Helper methods ---------------------------------------------------------------------------

    private Incentive buildJourneyIncentive(UUID id, boolean sequential) {
        Incentive journey = Incentive.builder()
            .name("Test Journey")
            .incentiveType(IncentiveType.JOURNEY)
            .status(IncentiveStatus.ACTIVE)
            .clientId(clientId)
            .createdBy(UUID.randomUUID())
            .journeySequential(sequential)
            .journeyStages(new ArrayList<>())
            .budgets(new ArrayList<>())
            .build();
        journey.setId(id);
        return journey;
    }

    private JourneyStage buildStage(UUID stageId, Incentive journey,
                                    UUID linkedIncentiveId, int sortOrder) {
        JourneyStage stage = JourneyStage.builder()
            .incentive(journey)
            .linkedIncentiveId(linkedIncentiveId)
            .sortOrder(sortOrder)
            .build();
        stage.setId(stageId);
        return stage;
    }

    private User buildUser() {
        User user = User.builder()
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .passwordHash("hashed")
            .clientId(clientId)
            .build();
        user.setId(userId);
        return user;
    }
}
