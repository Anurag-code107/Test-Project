package com.tenxengage.app.service.forecast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarityScorerTest {

    private SimilarityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new SimilarityScorer();
    }

    @Test
    void identicalFingerprints_scoreIsOne() {
        IncentiveFingerprint fp = new IncentiveFingerprint(
                "SALES", Set.of("AMERICAS", "EMEAR"), Set.of("Cloud Services"),
                new BigDecimal("100000"), 90, "PERCENTAGE", 3, Set.of("Reseller"));

        double score = scorer.score(fp, fp);
        assertThat(score).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void sameTypeAndRegion_ranksHigherThanTypeOnly() {
        IncentiveFingerprint candidate = new IncentiveFingerprint(
                "SALES", Set.of("AMERICAS"), Set.of("Cloud Services"),
                new BigDecimal("100000"), 90, "PERCENTAGE", 2, Set.of("Reseller"));

        IncentiveFingerprint sameTypeAndRegion = new IncentiveFingerprint(
                "SALES", Set.of("AMERICAS"), Set.of("Storage"),
                new BigDecimal("120000"), 60, "FLAT", 1, Set.of("Distributor"));

        IncentiveFingerprint sameTypeOnly = new IncentiveFingerprint(
                "SALES", Set.of("APJ"), Set.of("Storage"),
                new BigDecimal("120000"), 60, "FLAT", 1, Set.of("Distributor"));

        double scoreWithRegion = scorer.score(candidate, sameTypeAndRegion);
        double scoreWithoutRegion = scorer.score(candidate, sameTypeOnly);

        assertThat(scoreWithRegion).isGreaterThan(scoreWithoutRegion);
    }

    @Test
    void differentIncentiveType_lowScore() {
        IncentiveFingerprint sales = new IncentiveFingerprint(
                "SALES", Set.of("AMERICAS"), Set.of("Cloud Services"),
                new BigDecimal("100000"), 90, "PERCENTAGE", 2, Set.of("Reseller"));

        IncentiveFingerprint training = new IncentiveFingerprint(
                "TRAINING", Set.of("AMERICAS"), Set.of("Cloud Services"),
                new BigDecimal("100000"), 90, "PERCENTAGE", 2, Set.of("Reseller"));

        double score = scorer.score(sales, training);
        // Only type differs (0.25 weight), everything else matches
        assertThat(score).isLessThan(0.8);
    }

    @Test
    void compatibleTypes_salesAndJourney_partialScore() {
        IncentiveFingerprint sales = new IncentiveFingerprint(
                "SALES", Set.of("AMERICAS"), Set.of(),
                new BigDecimal("100000"), 90, null, 0, Set.of());

        IncentiveFingerprint journey = new IncentiveFingerprint(
                "JOURNEY", Set.of("AMERICAS"), Set.of(),
                new BigDecimal("100000"), 90, null, 0, Set.of());

        double score = scorer.score(sales, journey);
        assertThat(score).isGreaterThan(0.0);
    }

    @Test
    void budgetWithin20Percent_highBudgetScore() {
        IncentiveFingerprint a = new IncentiveFingerprint(
                "SALES", Set.of(), Set.of(), new BigDecimal("100000"),
                90, null, 0, Set.of());

        IncentiveFingerprint b = new IncentiveFingerprint(
                "SALES", Set.of(), Set.of(), new BigDecimal("90000"),
                90, null, 0, Set.of());

        double score = scorer.score(a, b);
        assertThat(score).isGreaterThan(0.2);
    }

    @Test
    void budgetDoubled_lowerScore() {
        IncentiveFingerprint small = new IncentiveFingerprint(
                "SALES", Set.of("AMERICAS"), Set.of("Cloud Services"),
                new BigDecimal("100000"), 90, "PERCENTAGE", 2, Set.of());

        IncentiveFingerprint large = new IncentiveFingerprint(
                "SALES", Set.of("AMERICAS"), Set.of("Cloud Services"),
                new BigDecimal("500000"), 90, "PERCENTAGE", 2, Set.of());

        IncentiveFingerprint similar = new IncentiveFingerprint(
                "SALES", Set.of("AMERICAS"), Set.of("Cloud Services"),
                new BigDecimal("110000"), 90, "PERCENTAGE", 2, Set.of());

        double scoreLarge = scorer.score(small, large);
        double scoreSimilar = scorer.score(small, similar);
        assertThat(scoreSimilar).isGreaterThan(scoreLarge);
    }

    @Test
    void durationSimilarity_30DayVs90Day() {
        IncentiveFingerprint short30 = new IncentiveFingerprint(
                "SALES", Set.of(), Set.of(), BigDecimal.ZERO,
                30, null, 0, Set.of());

        IncentiveFingerprint short35 = new IncentiveFingerprint(
                "SALES", Set.of(), Set.of(), BigDecimal.ZERO,
                35, null, 0, Set.of());

        IncentiveFingerprint long90 = new IncentiveFingerprint(
                "SALES", Set.of(), Set.of(), BigDecimal.ZERO,
                90, null, 0, Set.of());

        double scoreSimilarDuration = scorer.score(short30, short35);
        double scoreDifferentDuration = scorer.score(short30, long90);
        assertThat(scoreSimilarDuration).isGreaterThan(scoreDifferentDuration);
    }

    @Test
    void nullFields_doesNotThrow() {
        IncentiveFingerprint withNulls = new IncentiveFingerprint(
                null, null, null, null, 0, null, 0, null);

        IncentiveFingerprint normal = new IncentiveFingerprint(
                "SALES", Set.of("AMERICAS"), Set.of("Cloud"),
                new BigDecimal("100000"), 90, "PERCENTAGE", 2, Set.of("Reseller"));

        double score = scorer.score(withNulls, normal);
        assertThat(score).isGreaterThanOrEqualTo(0.0);
        assertThat(score).isLessThanOrEqualTo(1.0);
    }

    @Test
    void emptySetOverlap_zeroJaccardScore() {
        IncentiveFingerprint a = new IncentiveFingerprint(
                "SALES", Set.of("AMERICAS"), Set.of("Cloud"),
                BigDecimal.ZERO, 90, null, 0, Set.of());

        IncentiveFingerprint b = new IncentiveFingerprint(
                "SALES", Set.of("APJ"), Set.of("Security"),
                BigDecimal.ZERO, 90, null, 0, Set.of());

        double score = scorer.score(a, b);
        // Type matches (0.25), regions and products don't overlap
        assertThat(score).isLessThan(0.5);
    }

    @Test
    void scoreAlwaysBetweenZeroAndOne() {
        IncentiveFingerprint a = new IncentiveFingerprint(
                "SALES", Set.of("AMERICAS", "EMEAR", "APJ", "LATAM"),
                Set.of("Cloud", "Security", "Routers"),
                new BigDecimal("250000"), 180, "PERCENTAGE", 5, Set.of("Reseller", "OEM"));

        IncentiveFingerprint b = new IncentiveFingerprint(
                "TRAINING", Set.of(), Set.of(),
                new BigDecimal("1000"), 7, "FLAT", 0, Set.of());

        double score = scorer.score(a, b);
        assertThat(score).isBetween(0.0, 1.0);
    }
}
