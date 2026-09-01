package com.tenxengage.app.batch.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.service.ParticipantEligibilityChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the contract between {@code incentive_audience_rules.rule_value} for ROLE rules
 * and the user-identity fields that {@link ParticipantEligibilityChecker} reads.
 *
 * <p>BUG-020: rule_value now holds {@code ClientRole.id} as a UUID string (matching LOCATION),
 * with a transitional fallback that still matches on {@code ClientRole.name} if the value
 * doesn't parse as a UUID. Both paths are covered here so a future refactor that breaks
 * either contract fails loudly.
 */
class SeedAudienceRoleIntegrityTest {

    private ParticipantEligibilityChecker eligibilityChecker;

    @BeforeEach
    void setUp() {
        eligibilityChecker = new ParticipantEligibilityChecker(new ObjectMapper());
    }

    @Test
    void userRoleId_matchesIncentiveWithMatchingUuidRuleValue() {
        UUID partnerAdminRoleId = UUID.randomUUID();
        Incentive incentive = incentiveWithRoleAudience(partnerAdminRoleId.toString());

        boolean eligible = eligibilityChecker.matchesUserEligibility(
            incentive, Map.of(), partnerAdminRoleId, "Partner Admin",
            null, null, Map.of());

        assertThat(eligible)
            .as("A user whose ClientRole.id equals the UUID stored in rule_value must be eligible — "
                + "this is the canonical BUG-020 contract.")
            .isTrue();
    }

    @Test
    void userRoleId_doesNotMatchIncentiveWithDifferentUuid() {
        UUID partnerAdminRoleId = UUID.randomUUID();
        UUID partnerSellerRoleId = UUID.randomUUID();
        Incentive incentive = incentiveWithRoleAudience(partnerAdminRoleId.toString());

        boolean eligible = eligibilityChecker.matchesUserEligibility(
            incentive, Map.of(), partnerSellerRoleId, "Partner Seller",
            null, null, Map.of());

        assertThat(eligible)
            .as("A user whose ClientRole.id differs from the rule_value UUID must not be eligible.")
            .isFalse();
    }

    @Test
    void transitionalFallback_matchesDisplayNameWhenRuleValueIsNotAUuid() {
        // Simulates a row where rule_value holds a display name instead of a UUID
        // (external writer or regressed seed path). The fallback compares case-insensitively
        // against ClientRole.name and logs a WARN so operators can spot and clean up the
        // offending writer.
        Incentive incentive = incentiveWithRoleAudience("Partner Seller");

        boolean eligible = eligibilityChecker.matchesUserEligibility(
            incentive, Map.of(), UUID.randomUUID(), "Partner Seller",
            null, null, Map.of());

        assertThat(eligible)
            .as("Transitional fallback must match a legacy display-name rule_value against "
                + "ClientRole.name, so the app stays functional even if an external writer regresses.")
            .isTrue();
    }

    @Test
    void transitionalFallback_isCaseInsensitive() {
        Incentive incentive = incentiveWithRoleAudience("partner seller");

        boolean eligible = eligibilityChecker.matchesUserEligibility(
            incentive, Map.of(), UUID.randomUUID(), "Partner Seller",
            null, null, Map.of());

        assertThat(eligible)
            .as("Legacy display-name rule_value comparison must be case-insensitive.")
            .isTrue();
    }

    private Incentive incentiveWithRoleAudience(String ruleValue) {
        IncentiveAudienceRule rule = IncentiveAudienceRule.builder()
            .ruleType("ROLE")
            .ruleValue(ruleValue)
            .build();
        Incentive incentive = new Incentive();
        incentive.setAudienceRules(List.of(rule));
        return incentive;
    }
}
