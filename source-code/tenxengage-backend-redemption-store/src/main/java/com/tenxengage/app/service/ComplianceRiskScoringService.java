package com.tenxengage.app.service;

import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.enums.ComplianceRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ComplianceRiskScoringService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceRiskScoringService.class);

    private static final BigDecimal BUDGET_LOW_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal BUDGET_MEDIUM_THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal BUDGET_HIGH_THRESHOLD = new BigDecimal("150000");

    private static final BigDecimal MAX_USER_LOW_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal MAX_USER_MEDIUM_THRESHOLD = new BigDecimal("2000");
    private static final BigDecimal MAX_USER_HIGH_THRESHOLD = new BigDecimal("5000");

    public ComplianceRiskLevel calculateRiskLevel(Incentive incentive) {
        List<ComplianceRiskLevel> factors = new ArrayList<>();

        factors.add(assessBudgetRisk(incentive));
        factors.add(assessRewardTypeRisk(incentive));
        factors.add(assessMaxPerUserRisk(incentive));

        ComplianceRiskLevel overall = factors.stream()
                .reduce(ComplianceRiskLevel.LOW, this::higherOf);

        log.info("Compliance risk calculated: incentiveId={}, level={}, factors={}",
                incentive.getId(), overall, factors);

        return overall;
    }

    public boolean requiresComplianceApproval(ComplianceRiskLevel level) {
        return level == ComplianceRiskLevel.HIGH || level == ComplianceRiskLevel.CRITICAL;
    }

    private ComplianceRiskLevel assessBudgetRisk(Incentive incentive) {
        BigDecimal totalBudget = BigDecimal.ZERO;

        if (incentive.getBudgets() != null) {
            for (IncentiveBudget budget : incentive.getBudgets()) {
                if (budget.getTotalBudget() != null) {
                    totalBudget = totalBudget.add(budget.getTotalBudget());
                }
            }
        }

        if (totalBudget.compareTo(BUDGET_HIGH_THRESHOLD) > 0) {
            return ComplianceRiskLevel.CRITICAL;
        } else if (totalBudget.compareTo(BUDGET_MEDIUM_THRESHOLD) > 0) {
            return ComplianceRiskLevel.HIGH;
        } else if (totalBudget.compareTo(BUDGET_LOW_THRESHOLD) > 0) {
            return ComplianceRiskLevel.MEDIUM;
        }
        return ComplianceRiskLevel.LOW;
    }

    private ComplianceRiskLevel assessRewardTypeRisk(Incentive incentive) {
        String rewardCurrencies = incentive.getRewardCurrencies();
        if (rewardCurrencies == null || rewardCurrencies.isBlank()) {
            return ComplianceRiskLevel.LOW;
        }

        String normalized = rewardCurrencies.toUpperCase();

        // Cash is highest risk for anti-bribery purposes
        if (normalized.contains("CASH") || normalized.contains("USD")
                || normalized.contains("EUR") || normalized.contains("GBP")) {
            return ComplianceRiskLevel.HIGH;
        }

        // Points carry medium risk (convertible to monetary value at 200:1)
        if (normalized.contains("POINTS")) {
            return ComplianceRiskLevel.MEDIUM;
        }

        // Credits, tickets, non-monetary are low risk
        return ComplianceRiskLevel.LOW;
    }

    private ComplianceRiskLevel assessMaxPerUserRisk(Incentive incentive) {
        BigDecimal maxPerUser = incentive.getMaxPerUser();
        if (maxPerUser == null) {
            return ComplianceRiskLevel.LOW;
        }

        if (maxPerUser.compareTo(MAX_USER_HIGH_THRESHOLD) > 0) {
            return ComplianceRiskLevel.CRITICAL;
        } else if (maxPerUser.compareTo(MAX_USER_MEDIUM_THRESHOLD) > 0) {
            return ComplianceRiskLevel.HIGH;
        } else if (maxPerUser.compareTo(MAX_USER_LOW_THRESHOLD) > 0) {
            return ComplianceRiskLevel.MEDIUM;
        }
        return ComplianceRiskLevel.LOW;
    }

    private ComplianceRiskLevel higherOf(ComplianceRiskLevel a, ComplianceRiskLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
