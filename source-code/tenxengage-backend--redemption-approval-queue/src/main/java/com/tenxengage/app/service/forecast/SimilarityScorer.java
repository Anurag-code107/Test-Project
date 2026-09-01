package com.tenxengage.app.service.forecast;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashSet;
import java.util.Set;

/**
 * Computes weighted similarity between two incentive fingerprints across 7 dimensions.
 */
@Component
public class SimilarityScorer {

    private static final double W_TYPE = 0.25;
    private static final double W_REGION = 0.20;
    private static final double W_PRODUCT = 0.20;
    private static final double W_BUDGET = 0.10;
    private static final double W_PAYOUT = 0.10;
    private static final double W_DURATION = 0.08;
    private static final double W_PARTNER = 0.07;

    public double score(IncentiveFingerprint candidate, IncentiveFingerprint historical) {
        double typeScore = typeScore(candidate.incentiveType(), historical.incentiveType());
        double regionScore = jaccard(candidate.regions(), historical.regions());
        double productScore = jaccard(candidate.productCategories(), historical.productCategories());
        double budgetScore = budgetScore(candidate.totalBudget(), historical.totalBudget());
        double payoutScore = payoutScore(candidate, historical);
        double durationScore = ratioScore(candidate.durationDays(), historical.durationDays());
        double partnerScore = jaccard(candidate.partnerTypes(), historical.partnerTypes());

        return W_TYPE * typeScore
             + W_REGION * regionScore
             + W_PRODUCT * productScore
             + W_BUDGET * budgetScore
             + W_PAYOUT * payoutScore
             + W_DURATION * durationScore
             + W_PARTNER * partnerScore;
    }

    private double typeScore(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;
        if (isCompatible(a, b)) return 0.3;
        return 0.0;
    }

    private boolean isCompatible(String a, String b) {
        Set<String> compatible = Set.of("SALES", "JOURNEY");
        return compatible.contains(a) && compatible.contains(b);
    }

    private <T> double jaccard(Set<T> a, Set<T> b) {
        if (a == null || b == null || (a.isEmpty() && b.isEmpty())) return 0.0;
        Set<T> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) return 0.0;
        Set<T> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (double) intersection.size() / union.size();
    }

    private double budgetScore(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || a.compareTo(BigDecimal.ZERO) <= 0 || b.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        double logA = Math.log(a.doubleValue());
        double logB = Math.log(b.doubleValue());
        double maxLog = Math.max(Math.abs(logA), Math.abs(logB));
        if (maxLog == 0) return 1.0;
        return Math.max(0, 1.0 - Math.abs(logA - logB) / maxLog);
    }

    private double payoutScore(IncentiveFingerprint a, IncentiveFingerprint b) {
        if (a.payoutType() == null || b.payoutType() == null) return 0.0;
        boolean sameType = a.payoutType().equals(b.payoutType());
        boolean similarBands = Math.abs(a.bandCount() - b.bandCount()) <= 1;
        if (sameType && similarBands) return 1.0;
        if (sameType) return 0.5;
        return 0.0;
    }

    private double ratioScore(int a, int b) {
        if (a <= 0 || b <= 0) return 0.0;
        int max = Math.max(a, b);
        return 1.0 - (double) Math.abs(a - b) / max;
    }
}
