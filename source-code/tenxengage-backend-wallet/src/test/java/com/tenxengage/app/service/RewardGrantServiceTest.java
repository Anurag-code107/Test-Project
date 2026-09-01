package com.tenxengage.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.BudgetUtilization;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.RewardTransaction;
import com.tenxengage.app.entity.enums.AllocationMethod;
import com.tenxengage.app.entity.enums.BudgetMode;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.BudgetUtilizationRepository;
import com.tenxengage.app.repository.RewardTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardGrantServiceTest {

    @Mock
    private RewardTransactionRepository rewardTransactionRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private BudgetUtilizationRepository budgetUtilizationRepository;

    @Mock
    private NotificationEventProducer notificationEventProducer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RewardGrantService rewardGrantService;

    private UUID clientId;
    private UUID userId;
    private UUID incentiveId;
    private Incentive incentive;

    @BeforeEach
    void setUp() {
        rewardGrantService = new RewardGrantService(
            rewardTransactionRepository,
            walletService,
            budgetUtilizationRepository,
            notificationEventProducer,
            objectMapper
        );

        clientId = UUID.randomUUID();
        userId = UUID.randomUUID();
        incentiveId = UUID.randomUUID();

        incentive = Incentive.builder()
            .name("Test Incentive")
            .incentiveType(IncentiveType.TRAINING)
            .status(IncentiveStatus.ACTIVE)
            .clientId(clientId)
            .createdBy(UUID.randomUUID())
            .rewardCurrencies("cash,points")
            .budgets(new ArrayList<>())
            .build();
        incentive.setId(incentiveId);
    }

    @Test
    void grantReward_happyPath_creditsBalanceAndCreatesTransaction() {
        RewardGrantService.RewardGrantRequest request = new RewardGrantService.RewardGrantRequest(
            clientId, userId, incentiveId, null, UUID.randomUUID(),
            "cash", new BigDecimal("100"), null, null
        );

        when(rewardTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardGrantService.RewardGrantResult result = rewardGrantService.grantReward(request, incentive);

        assertThat(result.amountAwarded()).isEqualByComparingTo("100");
        assertThat(result.amountPotential()).isEqualByComparingTo("100");
        assertThat(result.budgetCapped()).isFalse();

        verify(walletService).creditInCurrentTx(
            eq(clientId), eq(userId), eq("cash"), eq(new BigDecimal("100")),
            eq("INCENTIVE"), isNull(), isNull());

        ArgumentCaptor<RewardTransaction> txCaptor = ArgumentCaptor.forClass(RewardTransaction.class);
        verify(rewardTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmountAwarded()).isEqualByComparingTo("100");
    }

    @Test
    void grantReward_budgetExhausted_awardsZero() {
        IncentiveBudget budget = IncentiveBudget.builder()
            .incentive(incentive)
            .currencyId("cash")
            .totalBudget(new BigDecimal("5000"))
            .allocationMethod(AllocationMethod.EQUAL)
            .budgetMode(BudgetMode.GLOBAL)
            .build();
        incentive.setBudgets(new ArrayList<>(List.of(budget)));

        BudgetUtilization utilization = BudgetUtilization.builder()
            .incentiveId(incentiveId)
            .currencyId("cash")
            .utilized(new BigDecimal("5000"))
            .build();

        when(budgetUtilizationRepository
            .findByIncentiveIdAndCurrencyIdAndLocationValueIdIsNullForUpdate(incentiveId, "cash"))
            .thenReturn(Optional.of(utilization));
        when(budgetUtilizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rewardTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardGrantService.RewardGrantRequest request = new RewardGrantService.RewardGrantRequest(
            clientId, userId, incentiveId, null, UUID.randomUUID(),
            "cash", new BigDecimal("100"), null, null
        );

        RewardGrantService.RewardGrantResult result = rewardGrantService.grantReward(request, incentive);

        assertThat(result.amountAwarded()).isEqualByComparingTo("0");
        assertThat(result.budgetCapped()).isTrue();
    }

    @Test
    void grantReward_perCurrencyUserCap_capsCorrectly() {
        incentive.setMaxPerUserByCurrency("{\"cash\":\"50\"}");

        when(rewardTransactionRepository
            .sumAwardedByUserAndIncentiveAndCurrency(clientId, userId, incentiveId, "cash"))
            .thenReturn(new BigDecimal("30"));
        when(rewardTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardGrantService.RewardGrantRequest request = new RewardGrantService.RewardGrantRequest(
            clientId, userId, incentiveId, null, UUID.randomUUID(),
            "cash", new BigDecimal("100"), null, null
        );

        RewardGrantService.RewardGrantResult result = rewardGrantService.grantReward(request, incentive);

        assertThat(result.amountAwarded()).isEqualByComparingTo("20");
        assertThat(result.budgetCapped()).isTrue();
    }

    @Test
    void grantReward_perCurrencyPartnerCap_capsCorrectly() {
        UUID partnerCompanyId = UUID.randomUUID();
        incentive.setMaxPerPartnerByCurrency("{\"cash\":\"50\"}");

        when(rewardTransactionRepository
            .sumAwardedByClientIdAndIncentiveIdAndPartnerCompanyId(clientId, incentiveId, partnerCompanyId))
            .thenReturn(new BigDecimal("30"));
        when(rewardTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardGrantService.RewardGrantRequest request = new RewardGrantService.RewardGrantRequest(
            clientId, userId, incentiveId, null, UUID.randomUUID(),
            "cash", new BigDecimal("100"), null, partnerCompanyId
        );

        RewardGrantService.RewardGrantResult result = rewardGrantService.grantReward(request, incentive);

        assertThat(result.amountAwarded()).isEqualByComparingTo("20");
        assertThat(result.budgetCapped()).isTrue();
    }

    @Test
    void grantReward_noCapsConfigured_awardsFullAmount() {
        when(rewardTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardGrantService.RewardGrantRequest request = new RewardGrantService.RewardGrantRequest(
            clientId, userId, incentiveId, null, UUID.randomUUID(),
            "points", new BigDecimal("5000"), null, null
        );

        RewardGrantService.RewardGrantResult result = rewardGrantService.grantReward(request, incentive);

        assertThat(result.amountAwarded()).isEqualByComparingTo("5000");
        assertThat(result.amountPotential()).isEqualByComparingTo("5000");
        assertThat(result.budgetCapped()).isFalse();
    }

    @Test
    void grantReward_budgetCappedFlag_setWhenReduced() {
        IncentiveBudget budget = IncentiveBudget.builder()
            .incentive(incentive)
            .currencyId("cash")
            .totalBudget(new BigDecimal("100"))
            .allocationMethod(AllocationMethod.EQUAL)
            .budgetMode(BudgetMode.GLOBAL)
            .build();
        incentive.setBudgets(new ArrayList<>(List.of(budget)));

        BudgetUtilization utilization = BudgetUtilization.builder()
            .incentiveId(incentiveId)
            .currencyId("cash")
            .utilized(new BigDecimal("80"))
            .build();

        when(budgetUtilizationRepository
            .findByIncentiveIdAndCurrencyIdAndLocationValueIdIsNullForUpdate(incentiveId, "cash"))
            .thenReturn(Optional.of(utilization));
        when(budgetUtilizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rewardTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardGrantService.RewardGrantRequest request = new RewardGrantService.RewardGrantRequest(
            clientId, userId, incentiveId, null, UUID.randomUUID(),
            "cash", new BigDecimal("50"), null, null
        );

        RewardGrantService.RewardGrantResult result = rewardGrantService.grantReward(request, incentive);

        assertThat(result.amountAwarded()).isEqualByComparingTo("20");
        assertThat(result.budgetCapped()).isTrue();

        ArgumentCaptor<RewardTransaction> txCaptor = ArgumentCaptor.forClass(RewardTransaction.class);
        verify(rewardTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().isBudgetCapped()).isTrue();
    }

    @Test
    void grantReward_budgetCappedFlag_falseWhenNotReduced() {
        IncentiveBudget budget = IncentiveBudget.builder()
            .incentive(incentive)
            .currencyId("cash")
            .totalBudget(new BigDecimal("10000"))
            .allocationMethod(AllocationMethod.EQUAL)
            .budgetMode(BudgetMode.GLOBAL)
            .build();
        incentive.setBudgets(new ArrayList<>(List.of(budget)));

        BudgetUtilization utilization = BudgetUtilization.builder()
            .incentiveId(incentiveId)
            .currencyId("cash")
            .utilized(BigDecimal.ZERO)
            .build();

        when(budgetUtilizationRepository
            .findByIncentiveIdAndCurrencyIdAndLocationValueIdIsNullForUpdate(incentiveId, "cash"))
            .thenReturn(Optional.of(utilization));
        when(budgetUtilizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rewardTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardGrantService.RewardGrantRequest request = new RewardGrantService.RewardGrantRequest(
            clientId, userId, incentiveId, null, UUID.randomUUID(),
            "cash", new BigDecimal("100"), null, null
        );

        RewardGrantService.RewardGrantResult result = rewardGrantService.grantReward(request, incentive);

        assertThat(result.amountAwarded()).isEqualByComparingTo("100");
        assertThat(result.budgetCapped()).isFalse();

        ArgumentCaptor<RewardTransaction> txCaptor = ArgumentCaptor.forClass(RewardTransaction.class);
        verify(rewardTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().isBudgetCapped()).isFalse();
    }

    @Test
    void grantReward_sendsNotification_whenAwarded() {
        when(rewardTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardGrantService.RewardGrantRequest request = new RewardGrantService.RewardGrantRequest(
            clientId, userId, incentiveId, null, UUID.randomUUID(),
            "cash", new BigDecimal("100"), null, null
        );

        rewardGrantService.grantReward(request, incentive);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventProducer).publish(eventCaptor.capture());

        NotificationEvent event = eventCaptor.getValue();
        assertThat(event.notificationTypeKey()).isEqualTo("REWARD_EARNED");
        assertThat(event.clientId()).isEqualTo(clientId);
        assertThat(event.targetUserIds()).containsExactly(userId);
    }

    @Test
    void grantReward_credit_routesThroughWalletService() {
        when(rewardTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RewardGrantService.RewardGrantRequest request = new RewardGrantService.RewardGrantRequest(
            clientId, userId, incentiveId, null, UUID.randomUUID(),
            "cash", new BigDecimal("75"), null, null
        );

        rewardGrantService.grantReward(request, incentive);

        verify(walletService).creditInCurrentTx(
            eq(clientId), eq(userId), eq("cash"), eq(new BigDecimal("75")),
            eq("INCENTIVE"), any(), isNull());
    }
}
