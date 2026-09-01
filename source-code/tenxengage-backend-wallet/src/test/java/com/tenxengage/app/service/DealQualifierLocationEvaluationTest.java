package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.CriterionResult;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.LocationLevel;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.entity.PartnerCompanyLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-063 — {@link DealQualifierService#evaluateRegion} must consult the LOCATION
 * rule's value set, not just the presence of LOCATION rules. The pre-fix code emitted
 * {@code "Location 'X' is eligible"} for any non-null partner region whenever the
 * incentive carried any LOCATION rule, collapsing into a meaningless approval. These
 * tests pin the membership-test semantics, including hierarchy descent inherited from
 * {@link ParticipantEligibilityChecker#buildLocationMap} (BUG-061) and OR-across-levels
 * matching (BUG-062), so a future refactor cannot regress to the silent-pass behavior.
 */
class DealQualifierLocationEvaluationTest {

    private LocationLevel regionLevel;
    private LocationLevel countryLevel;
    private LocationLevel stateLevel;

    private LocationValue americas;
    private LocationValue emea;
    private LocationValue usa;
    private LocationValue germany;
    private LocationValue california;

    @BeforeEach
    void setUp() {
        regionLevel = level("Region", 0);
        countryLevel = level("Country", 1);
        stateLevel = level("State", 2);

        americas = locationValue("AMERICAS", regionLevel, null);
        emea = locationValue("EMEA", regionLevel, null);
        usa = locationValue("USA", countryLevel, americas);
        germany = locationValue("Germany", countryLevel, emea);
        california = locationValue("California", stateLevel, usa);
    }

    @Test
    void emitsOpenToAllLocations_whenIncentiveHasNoLocationRules() {
        Incentive incentive = incentiveWithRules();
        Map<UUID, Set<UUID>> partnerLocations = ParticipantEligibilityChecker
                .buildLocationMap(List.of(assignment(usa)));

        List<CriterionResult> met = new ArrayList<>();
        List<CriterionResult> unmet = new ArrayList<>();

        DealQualifierService.evaluateRegion("AMERICAS", partnerLocations, incentive,
                Map.of(), met, unmet);

        assertThat(unmet).isEmpty();
        assertThat(met).extracting(CriterionResult::description)
            .containsExactly("Open to all locations");
    }

    @Test
    void emitsOpenToAllLocations_whenLocationRulesLackLevelMetadata() {
        // Legacy data path: rules with no locationLevel cannot be membership-tested.
        // Mirrors the rulesByLevel.isEmpty() short-circuit in
        // ParticipantEligibilityChecker.matchesLocationRules so a deal qualifier
        // and the participant matcher agree on legacy rows.
        IncentiveAudienceRule legacy = IncentiveAudienceRule.builder()
                .ruleType("LOCATION")
                .ruleValue(americas.getId().toString())
                .locationLevel(null)
                .build();
        Incentive incentive = incentiveWithRules(legacy);

        List<CriterionResult> met = new ArrayList<>();
        List<CriterionResult> unmet = new ArrayList<>();

        DealQualifierService.evaluateRegion("AMERICAS", Map.of(), incentive,
                Map.of(), met, unmet);

        assertThat(unmet).isEmpty();
        assertThat(met).extracting(CriterionResult::description)
            .containsExactly("Open to all locations");
    }

    @Test
    void emitsMet_whenPartnerLocationMatchesRuleAtSameLevel() {
        Incentive incentive = incentiveWithRules(audienceRule(regionLevel, americas.getId()));
        Map<UUID, Set<UUID>> partnerLocations = ParticipantEligibilityChecker
                .buildLocationMap(List.of(assignment(americas)));

        List<CriterionResult> met = new ArrayList<>();
        List<CriterionResult> unmet = new ArrayList<>();

        DealQualifierService.evaluateRegion("AMERICAS", partnerLocations, incentive,
                Map.of(americas.getId(), "AMERICAS"), met, unmet);

        assertThat(unmet).isEmpty();
        assertThat(met).extracting(CriterionResult::description)
            .containsExactly("Region 'AMERICAS' is in the eligible audience");
    }

    @Test
    void emitsMet_whenPartnerMatchesViaAncestorDescent_BUG063() {
        // Heart of BUG-063 + BUG-061: a partner tagged only at Country:USA must
        // qualify under a Region:AMERICAS rule because USA descends from AMERICAS.
        // Pre-fix emitted "Location 'AMERICAS' is eligible" for *any* non-null
        // region; the membership test now only emits met when the value set
        // actually contains the partner's expanded location.
        Incentive incentive = incentiveWithRules(audienceRule(regionLevel, americas.getId()));
        Map<UUID, Set<UUID>> partnerLocations = ParticipantEligibilityChecker
                .buildLocationMap(List.of(assignment(usa)));

        List<CriterionResult> met = new ArrayList<>();
        List<CriterionResult> unmet = new ArrayList<>();

        DealQualifierService.evaluateRegion("AMERICAS", partnerLocations, incentive,
                Map.of(americas.getId(), "AMERICAS"), met, unmet);

        assertThat(unmet).isEmpty();
        assertThat(met)
            .as("BUG-063 with hierarchy descent: a Country:USA partner satisfies a "
                + "Region:AMERICAS rule via the ancestor walk in buildLocationMap.")
            .extracting(CriterionResult::description)
            .containsExactly("Region 'AMERICAS' is in the eligible audience");
    }

    @Test
    void emitsUnmet_whenPartnerLocationIsOutsideEligibleSet_BUG063Repro() {
        // Exact repro from the bug report: rule names AMERICAS only, partner sits
        // in EMEA. Pre-fix path emitted a met "Location 'EMEA' is eligible" because
        // a non-null region was treated as automatic approval. After fix the criterion
        // must be unmet AND the hint must name the eligible set so the seller knows
        // why their deal didn't qualify.
        Incentive incentive = incentiveWithRules(audienceRule(regionLevel, americas.getId()));
        Map<UUID, Set<UUID>> partnerLocations = ParticipantEligibilityChecker
                .buildLocationMap(List.of(assignment(germany)));

        List<CriterionResult> met = new ArrayList<>();
        List<CriterionResult> unmet = new ArrayList<>();

        DealQualifierService.evaluateRegion("EMEA", partnerLocations, incentive,
                Map.of(americas.getId(), "AMERICAS"), met, unmet);

        assertThat(met).isEmpty();
        assertThat(unmet).hasSize(1);
        CriterionResult criterion = unmet.get(0);
        assertThat(criterion.ruleType()).isEqualTo("LOCATION");
        assertThat(criterion.description())
            .as("BUG-063: pre-fix said 'Location X is eligible'; post-fix must say it is NOT eligible.")
            .contains("'EMEA'")
            .contains("not in the eligible audience");
        assertThat(criterion.hint())
            .as("Unmet hint must name the rule's eligible set so the seller can see what would qualify.")
            .contains("Region: AMERICAS");
    }

    @Test
    void emitsUnmet_whenPartnerHasNoLocationAssignments() {
        Incentive incentive = incentiveWithRules(audienceRule(regionLevel, americas.getId()));

        List<CriterionResult> met = new ArrayList<>();
        List<CriterionResult> unmet = new ArrayList<>();

        DealQualifierService.evaluateRegion(null, Map.of(), incentive,
                Map.of(americas.getId(), "AMERICAS"), met, unmet);

        assertThat(met).isEmpty();
        assertThat(unmet).hasSize(1);
        assertThat(unmet.get(0).description())
            .isEqualTo("Partner location could not be determined");
    }

    @Test
    void emitsMet_whenMultiLevelRuleMatchesAtAnyOneLevel_BUG062Semantics() {
        // OR-across-levels: a Country:Germany-tagged partner (whose region is EMEA)
        // satisfies an audience that names both Region:AMERICAS and Country:Germany,
        // because matching at any populated level is sufficient. Pre-BUG-062 AND
        // semantics would have rejected this; the deal qualifier must agree with
        // ParticipantEligibilityChecker so seller and partner-matcher views never
        // disagree on whether the same partner is in-audience.
        Incentive incentive = incentiveWithRules(
                audienceRule(regionLevel, americas.getId()),
                audienceRule(countryLevel, germany.getId()));
        Map<UUID, Set<UUID>> partnerLocations = ParticipantEligibilityChecker
                .buildLocationMap(List.of(assignment(germany)));

        List<CriterionResult> met = new ArrayList<>();
        List<CriterionResult> unmet = new ArrayList<>();

        DealQualifierService.evaluateRegion("EMEA", partnerLocations, incentive,
                Map.of(americas.getId(), "AMERICAS", germany.getId(), "Germany"), met, unmet);

        assertThat(unmet).isEmpty();
        assertThat(met).hasSize(1);
        assertThat(met.get(0).description())
            .as("OR-across-levels admits the partner via the Country:Germany match even though "
                + "their region (EMEA) is not in the audience.")
            .contains("'Germany'");
    }

    @Test
    void emitsUnmet_whenRuleValuesAreNonUuidAndNoLevelHasMatchableUuids() {
        // Defensive: a malformed (non-UUID) rule_value cannot be membership-tested.
        // After filtering it out, the level loses its only required value and the
        // requiredByLevel map collapses to empty, falling through to "open to all".
        // This is the same tolerance pattern as ParticipantEligibilityChecker.
        IncentiveAudienceRule malformed = IncentiveAudienceRule.builder()
                .ruleType("LOCATION")
                .ruleValue("not-a-uuid")
                .locationLevel(regionLevel)
                .build();
        Incentive incentive = incentiveWithRules(malformed);
        Map<UUID, Set<UUID>> partnerLocations = ParticipantEligibilityChecker
                .buildLocationMap(List.of(assignment(germany)));

        List<CriterionResult> met = new ArrayList<>();
        List<CriterionResult> unmet = new ArrayList<>();

        DealQualifierService.evaluateRegion("EMEA", partnerLocations, incentive,
                Map.of(), met, unmet);

        assertThat(unmet).isEmpty();
        assertThat(met).extracting(CriterionResult::description)
            .containsExactly("Open to all locations");
    }

    @Test
    void emitsMet_whenStateLevelRuleMatchesPartnerStateAssignment() {
        // Same-level deeper match — proves the membership test is not Region-only.
        Incentive incentive = incentiveWithRules(audienceRule(stateLevel, california.getId()));
        Map<UUID, Set<UUID>> partnerLocations = ParticipantEligibilityChecker
                .buildLocationMap(List.of(assignment(california)));

        List<CriterionResult> met = new ArrayList<>();
        List<CriterionResult> unmet = new ArrayList<>();

        DealQualifierService.evaluateRegion("AMERICAS", partnerLocations, incentive,
                Map.of(california.getId(), "California"), met, unmet);

        assertThat(unmet).isEmpty();
        assertThat(met).extracting(CriterionResult::description)
            .containsExactly("State 'California' is in the eligible audience");
    }

    private static LocationLevel level(String name, int depth) {
        LocationLevel level = LocationLevel.builder()
                .name(name)
                .depth(depth)
                .build();
        level.setId(UUID.randomUUID());
        return level;
    }

    private static LocationValue locationValue(String name, LocationLevel level, LocationValue parent) {
        LocationValue value = LocationValue.builder()
                .name(name)
                .level(level)
                .parent(parent)
                .build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private static PartnerCompanyLocation assignment(LocationValue value) {
        PartnerCompanyLocation pcl = PartnerCompanyLocation.builder()
                .locationValue(value)
                .build();
        pcl.setId(UUID.randomUUID());
        return pcl;
    }

    private static IncentiveAudienceRule audienceRule(LocationLevel level, UUID valueId) {
        return IncentiveAudienceRule.builder()
                .ruleType("LOCATION")
                .ruleValue(valueId.toString())
                .locationLevel(level)
                .build();
    }

    private static Incentive incentiveWithRules(IncentiveAudienceRule... rules) {
        Incentive incentive = new Incentive();
        incentive.setAudienceRules(new ArrayList<>(List.of(rules)));
        return incentive;
    }
}
