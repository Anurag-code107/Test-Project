package com.tenxengage.app.service;

import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.enums.ComplianceRiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ComplianceRiskScoringService.
 * This service is pure logic with no injected dependencies, so no mocks are needed.
 */
class ComplianceRiskScoringServiceTest {

    private ComplianceRiskScoringService complianceRiskScoringService;

    @BeforeEach
    void setUp() {
        complianceRiskScoringService = new ComplianceRiskScoringService();
    }

    // -------------------------------------------------------------------------
    // calculateRiskLevel
    // -------------------------------------------------------------------------

    @Test
    void calculateRiskLevel_returnsLowForSmallBudgetNonMonetary() {
        // Budget $5K (under $10K LOW threshold), credits (LOW risk currency)
        Incentive incentive = buildIncentive(new BigDecimal("5000"), "CREDITS", null);

        ComplianceRiskLevel result = complianceRiskScoringService.calculateRiskLevel(incentive);

        assertThat(result).isEqualTo(ComplianceRiskLevel.LOW);
    }

    @Test
    void calculateRiskLevel_returnsMediumForModerateBudget() {
        // Budget $30K (between $10K and $50K = MEDIUM budget), points (MEDIUM currency)
        Incentive incentive = buildIncentive(new BigDecimal("30000"), "POINTS", null);

        ComplianceRiskLevel result = complianceRiskScoringService.calculateRiskLevel(incentive);

        assertThat(result).isEqualTo(ComplianceRiskLevel.MEDIUM);
    }

    @Test
    void calculateRiskLevel_returnsHighForLargeCashIncentive() {
        // Budget $100K (between $50K and $150K = HIGH budget), cash (HIGH currency)
        Incentive incentive = buildIncentive(new BigDecimal("100000"), "CASH", null);

        ComplianceRiskLevel result = complianceRiskScoringService.calculateRiskLevel(incentive);

        assertThat(result).isEqualTo(ComplianceRiskLevel.HIGH);
    }

    @Test
    void calculateRiskLevel_returnsCriticalForVeryLargeBudget() {
        // Budget $200K (over $150K = CRITICAL budget)
        Incentive incentive = buildIncentive(new BigDecimal("200000"), "POINTS", null);

        ComplianceRiskLevel result = complianceRiskScoringService.calculateRiskLevel(incentive);

        assertThat(result).isEqualTo(ComplianceRiskLevel.CRITICAL);
    }

    @Test
    void calculateRiskLevel_usesHighestFactor() {
        // Budget $5K (LOW), but CASH currency (HIGH) => overall HIGH
        Incentive incentive = buildIncentive(new BigDecimal("5000"), "CASH", null);

        ComplianceRiskLevel result = complianceRiskScoringService.calculateRiskLevel(incentive);

        assertThat(result).isEqualTo(ComplianceRiskLevel.HIGH);
    }

    // -------------------------------------------------------------------------
    // requiresComplianceApproval
    // -------------------------------------------------------------------------

    @Test
    void requiresComplianceApproval_trueForHigh() {
        boolean result = complianceRiskScoringService.requiresComplianceApproval(ComplianceRiskLevel.HIGH);

        assertThat(result).isTrue();
    }

    @Test
    void requiresComplianceApproval_falseForLow() {
        boolean result = complianceRiskScoringService.requiresComplianceApproval(ComplianceRiskLevel.LOW);

        assertThat(result).isFalse();
    }

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal Incentive with the specified budget, reward currency, and maxPerUser.
     */
    private Incentive buildIncentive(BigDecimal budgetAmount, String rewardCurrencies,
                                     BigDecimal maxPerUser) {
        IncentiveBudget budget = IncentiveBudget.builder()
                .totalBudget(budgetAmount)
                .currencyId(rewardCurrencies != null ? rewardCurrencies.toLowerCase() : "credits")
                .build();
        budget.setId(UUID.randomUUID());

        Incentive incentive = Incentive.builder()
                .name("Test Incentive")
                .rewardCurrencies(rewardCurrencies)
                .maxPerUser(maxPerUser)
                .budgets(List.of(budget))
                .clientId(UUID.randomUUID())
                .createdBy(UUID.randomUUID())
                .build();
        incentive.setId(UUID.randomUUID());

        return incentive;
    }
}
