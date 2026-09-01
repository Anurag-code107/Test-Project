package com.tenxengage.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.LocationLevel;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.entity.PartnerCompanyLocation;
import com.tenxengage.app.entity.enums.AllocationMethod;
import com.tenxengage.app.entity.enums.BudgetMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-061 — eligibility and budget lookups must walk up the location hierarchy. A partner
 * assigned only at {@code Country:USA} must implicitly satisfy a {@code Region:AMERICAS}
 * rule, and a {@code Region}-level budget must roll its country-only assignment up to its
 * region. The pre-fix exact-level lookup silently denied descendants — these tests pin the
 * descent behavior so a future refactor can't regress it.
 *
 * <p>BUG-062 — multi-level audience rules use OR across levels: a partner matching at any
 * single populated level is admitted. The pre-fix AND-across-levels semantics silently
 * rejected partners whenever the admin selected a deeper level than the population was
 * tagged at (e.g. State/City rules against a Country-tagged partner). The OR tests below
 * lock in the corrected semantics.
 */
class ParticipantEligibilityLocationHierarchyTest {

    private ParticipantEligibilityChecker eligibilityChecker;

    private LocationLevel regionLevel;
    private LocationLevel countryLevel;
    private LocationLevel stateLevel;
    private LocationLevel cityLevel;

    private LocationValue americas;
    private LocationValue emea;
    private LocationValue usa;
    private LocationValue germany;
    private LocationValue california;
    private LocationValue losAngeles;

    @BeforeEach
    void setUp() {
        eligibilityChecker = new ParticipantEligibilityChecker(new ObjectMapper());

        regionLevel = level("Region", 0);
        countryLevel = level("Country", 1);
        stateLevel = level("State", 2);
        cityLevel = level("City", 3);

        americas = locationValue("AMERICAS", regionLevel, null);
        emea = locationValue("EMEA", regionLevel, null);
        usa = locationValue("USA", countryLevel, americas);
        germany = locationValue("Germany", countryLevel, emea);
        california = locationValue("California", stateLevel, usa);
        losAngeles = locationValue("Los Angeles", cityLevel, california);
    }

    @Test
    void buildLocationMap_includesAncestorLevelsForCountryOnlyPartner() {
        PartnerCompanyLocation onlyUsa = assignment(usa);

        Map<UUID, Set<UUID>> byLevel =
            ParticipantEligibilityChecker.buildLocationMap(List.of(onlyUsa));

        assertThat(byLevel.get(countryLevel.getId()))
            .as("Exact-level assignment must still be present.")
            .containsExactly(usa.getId());
        assertThat(byLevel.get(regionLevel.getId()))
            .as("BUG-061: USA's region (AMERICAS) must be inferred from the parent chain "
                + "so a Region-scoped rule can match a Country-only partner.")
            .containsExactly(americas.getId());
    }

    @Test
    void countryOnlyPartner_isEligibleForRegionScopedIncentive() {
        Incentive incentive = incentiveWithLocationRule(regionLevel, americas.getId());
        Map<UUID, Set<UUID>> byLevel =
            ParticipantEligibilityChecker.buildLocationMap(List.of(assignment(usa)));

        boolean eligible = eligibilityChecker.matchesUserEligibility(
            incentive, byLevel, UUID.randomUUID(), "Partner Seller", null, null, Map.of());

        assertThat(eligible)
            .as("BUG-061 root case: a Country:USA-only partner must qualify for a Region:AMERICAS "
                + "incentive because USA is a descendant of AMERICAS in the hierarchy.")
            .isTrue();
    }

    @Test
    void countryOnlyPartner_isNotEligibleForUnrelatedRegion() {
        Incentive incentive = incentiveWithLocationRule(regionLevel, emea.getId());
        Map<UUID, Set<UUID>> byLevel =
            ParticipantEligibilityChecker.buildLocationMap(List.of(assignment(usa)));

        boolean eligible = eligibilityChecker.matchesUserEligibility(
            incentive, byLevel, UUID.randomUUID(), "Partner Seller", null, null, Map.of());

        assertThat(eligible)
            .as("Hierarchy walk must not over-match: USA rolls up to AMERICAS, NOT EMEA.")
            .isFalse();
    }

    @Test
    void resolveLocationValueForBudget_walksUpToBudgetLevel() {
        IncentiveBudget regionBudget = budgetAtLevel(regionLevel);

        UUID resolved = ParticipantEligibilityChecker.resolveLocationValueForBudget(
            List.of(assignment(usa)), regionBudget);

        assertThat(resolved)
            .as("BUG-061: a Country-only partner must resolve to its parent Region for a "
                + "Region-scoped budget, not return null.")
            .isEqualTo(americas.getId());
    }

    @Test
    void resolveLocationValueForBudget_prefersExactLevelMatchOverAncestor() {
        // Partner is assigned at BOTH Country:USA and Region:EMEA (intentionally mismatched
        // to prove the resolver picked the exact-level row, not the ancestor of USA).
        IncentiveBudget regionBudget = budgetAtLevel(regionLevel);

        UUID resolved = ParticipantEligibilityChecker.resolveLocationValueForBudget(
            List.of(assignment(usa), assignment(emea)), regionBudget);

        assertThat(resolved)
            .as("Exact-level assignment must win over ancestor descent so admins can override "
                + "the implicit roll-up by adding a direct Region row.")
            .isEqualTo(emea.getId());
    }

    @Test
    void multiLevelRule_countryTaggedPartnerIsEligible_BUG062Repro() {
        // Exact repro from BUG-062: admin builds an audience picking values at all four
        // levels (Region+Country+State+City), but the partner population is only tagged at
        // Country. Pre-fix, AND-across-levels demanded a State and City match the partner
        // had no way to provide, silently rejecting them.
        Incentive incentive = incentiveWithLocationRules(
            audienceRule(regionLevel, americas.getId()),
            audienceRule(countryLevel, usa.getId()),
            audienceRule(stateLevel, california.getId()),
            audienceRule(cityLevel, losAngeles.getId()));

        Map<UUID, Set<UUID>> byLevel =
            ParticipantEligibilityChecker.buildLocationMap(List.of(assignment(usa)));

        boolean eligible = eligibilityChecker.matchesUserEligibility(
            incentive, byLevel, UUID.randomUUID(), "Partner Seller", null, null, Map.of());

        assertThat(eligible)
            .as("BUG-062: a Country:USA-only partner must be eligible for an incentive whose "
                + "audience also names State and City values, because the admin's multi-level "
                + "selection reads as inclusive targeting (OR across levels), not as a stack "
                + "of independent constraints.")
            .isTrue();
    }

    @Test
    void deepOnlyRule_countryTaggedPartnerIsNotEligible() {
        // Counterpart to the repro: when the admin only picks at deeper levels (State+City)
        // and the partner has no tag at any of those levels, OR-across-levels still excludes
        // them — proves the fix doesn't over-match.
        Incentive incentive = incentiveWithLocationRules(
            audienceRule(stateLevel, california.getId()),
            audienceRule(cityLevel, losAngeles.getId()));

        Map<UUID, Set<UUID>> byLevel =
            ParticipantEligibilityChecker.buildLocationMap(List.of(assignment(usa)));

        boolean eligible = eligibilityChecker.matchesUserEligibility(
            incentive, byLevel, UUID.randomUUID(), "Partner Seller", null, null, Map.of());

        assertThat(eligible)
            .as("OR-across-levels must still reject a partner with no value matching any "
                + "populated level — Country-only tagging does not satisfy a State+City-only rule.")
            .isFalse();
    }

    @Test
    void multiLevelRule_orSemanticAdmitsPartnerMatchingOnlyAtOneLevel() {
        // Lock-in test for OR-not-AND: the rule names AMERICAS at Region and Germany at
        // Country. The partner is tagged Country:Germany (whose ancestor is EMEA, not
        // AMERICAS). Under the pre-fix AND semantic this partner failed the Region check
        // and was rejected; under OR the Country match is sufficient.
        Incentive incentive = incentiveWithLocationRules(
            audienceRule(regionLevel, americas.getId()),
            audienceRule(countryLevel, germany.getId()));

        Map<UUID, Set<UUID>> byLevel =
            ParticipantEligibilityChecker.buildLocationMap(List.of(assignment(germany)));

        boolean eligible = eligibilityChecker.matchesUserEligibility(
            incentive, byLevel, UUID.randomUUID(), "Partner Seller", null, null, Map.of());

        assertThat(eligible)
            .as("BUG-062: matching at any single populated level must admit the partner. "
                + "Country:Germany satisfies the Country rule even though the partner's region "
                + "(EMEA) does not match the rule's Region:AMERICAS — pre-fix AND would have "
                + "rejected this case.")
            .isTrue();
    }

    @Test
    void multiLevelRule_partnerMatchingNoLevelIsRejected() {
        // Negative case bracketing the OR semantic: rule wants AMERICAS+USA, partner is
        // Germany (EMEA). No level matches, so the partner is excluded.
        Incentive incentive = incentiveWithLocationRules(
            audienceRule(regionLevel, americas.getId()),
            audienceRule(countryLevel, usa.getId()));

        Map<UUID, Set<UUID>> byLevel =
            ParticipantEligibilityChecker.buildLocationMap(List.of(assignment(germany)));

        boolean eligible = eligibilityChecker.matchesUserEligibility(
            incentive, byLevel, UUID.randomUUID(), "Partner Seller", null, null, Map.of());

        assertThat(eligible)
            .as("OR-across-levels must reject when no populated level produces a match — "
                + "guards against the fix collapsing into 'always allow'.")
            .isFalse();
    }

    @Test
    void hierarchyWalk_terminatesOnCycle() {
        // Construct a parent loop: A <-> B. Pre-fix this never mattered because there
        // was no walk; the walk now needs a depth cap to stay safe even if seed data
        // ever introduces a cycle.
        LocationValue a = locationValue("A", countryLevel, null);
        LocationValue b = locationValue("B", countryLevel, a);
        a.setParent(b);

        Map<UUID, Set<UUID>> byLevel =
            ParticipantEligibilityChecker.buildLocationMap(List.of(assignment(a)));

        assertThat(byLevel.get(countryLevel.getId()))
            .as("Walk must complete without hanging even when parent_id forms a cycle.")
            .contains(a.getId(), b.getId());

        UUID resolved = ParticipantEligibilityChecker.resolveLocationValueForBudget(
            List.of(assignment(a)), budgetAtLevel(regionLevel));

        assertThat(resolved)
            .as("Cycle that never reaches the target level must return null, not loop forever.")
            .isNull();
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

    private static Incentive incentiveWithLocationRule(LocationLevel level, UUID valueId) {
        return incentiveWithLocationRules(audienceRule(level, valueId));
    }

    private static Incentive incentiveWithLocationRules(IncentiveAudienceRule... rules) {
        Incentive incentive = new Incentive();
        incentive.setAudienceRules(List.of(rules));
        return incentive;
    }

    private static IncentiveAudienceRule audienceRule(LocationLevel level, UUID valueId) {
        return IncentiveAudienceRule.builder()
            .ruleType("LOCATION")
            .ruleValue(valueId.toString())
            .locationLevel(level)
            .build();
    }

    private static IncentiveBudget budgetAtLevel(LocationLevel level) {
        return IncentiveBudget.builder()
            .totalBudget(BigDecimal.ZERO)
            .currencyId("USD")
            .allocationMethod(AllocationMethod.EQUAL)
            .budgetMode(BudgetMode.PER_LOCATION)
            .budgetLocationLevel(level)
            .build();
    }
}
