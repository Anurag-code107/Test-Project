package com.tenxengage.app.service;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.response.CriterionResult;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.LocationLevel;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyLocation;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.LocationLevelRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.repository.PartnerCompanyLocationRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.PartnerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-063 — JPA-backed proof that the rewritten {@link DealQualifierService#evaluateRegion}
 * works against entities hydrated by Hibernate, not just hand-built fixtures. Goes beyond
 * the unit test in {@link com.tenxengage.app.service.DealQualifierLocationEvaluationTest}
 * by exercising the real-world graph the service sees: persisted {@link Incentive} with
 * lazy {@link IncentiveAudienceRule#getLocationLevel()} hydration, persisted
 * {@link PartnerCompanyLocation} chains walked by
 * {@link ParticipantEligibilityChecker#buildLocationMap}, and the bulk
 * {@link LocationValueRepository#findByIdIn} round-trip used to render unmet hints.
 *
 * <p>Calling {@link DealQualifierService#evaluateRegion} directly (rather than going through
 * the full {@code evaluateDeal} controller path) keeps this test scoped to the bug's surface
 * and avoids an unrelated Postgres type-inference issue in {@link IncentiveRepository#searchByClientId}'s
 * {@code :search IS NULL} JPQL clause when invoked outside the controller layer.
 */
@Transactional
@Tag("integration")
class DealQualifierLocationIntegrationTest extends AbstractLocalIntegrationTest {

    @Autowired private ClientRepository clientRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;
    @Autowired private PartnerCompanyLocationRepository partnerLocationRepository;
    @Autowired private LocationLevelRepository levelRepository;
    @Autowired private LocationValueRepository valueRepository;
    @Autowired private IncentiveRepository incentiveRepository;
    @Autowired private UserRepository userRepository;

    private Client client;
    private PartnerCompany partner;
    private User seller;
    private LocationLevel regionLevel;
    private LocationValue americas;
    private LocationValue emea;
    private LocationValue usa;

    @BeforeEach
    void setUp() {
        client = clientRepository.save(ClientFixtures.activeEnterprise().build());

        regionLevel = levelRepository.save(LocationLevel.builder()
                .clientId(client.getId())
                .name("Region")
                .depth(0)
                .build());
        LocationLevel countryLevel = levelRepository.save(LocationLevel.builder()
                .clientId(client.getId())
                .name("Country")
                .depth(1)
                .build());

        americas = valueRepository.save(LocationValue.builder()
                .clientId(client.getId())
                .level(regionLevel)
                .name("AMERICAS")
                .build());
        emea = valueRepository.save(LocationValue.builder()
                .clientId(client.getId())
                .level(regionLevel)
                .name("EMEA")
                .build());
        usa = valueRepository.save(LocationValue.builder()
                .clientId(client.getId())
                .level(countryLevel)
                .parent(americas)
                .name("United States")
                .build());

        partner = partnerCompanyRepository.save(
                PartnerFixtures.activeReseller(client.getId()).build());

        seller = userRepository.save(User.builder()
                .email("dq-seller-" + System.nanoTime() + "@test.com")
                .firstName("Test").lastName("Seller")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(client.getId())
                .partnerCompanyId(partner.getId())
                .build());
    }

    @Test
    void evaluateRegion_emitsUnmet_whenPersistedPartnerInAmericasFacesEmeaOnlyIncentive_BUG063Repro() {
        // BUG-063 anchor through the JPA layer: the audience rule's locationLevel must
        // hydrate correctly and the level/value lookup machinery has to see it. Pre-fix
        // the persisted EMEA-only rule produced a met "Location 'AMERICAS' is eligible"
        // because the rule's value set was never consulted; post-fix it must produce an
        // unmet criterion naming both the partner's region and the eligible set.
        assignPartnerTo(americas);
        Incentive emeaScoped = persistIncentiveWithLocationRule("EMEA-only Incentive", emea);

        Incentive reloaded = incentiveRepository.findById(emeaScoped.getId()).orElseThrow();
        Map<UUID, Set<UUID>> userLocationsByLevel = ParticipantEligibilityChecker
                .buildLocationMap(reloadAssignments());
        Map<UUID, String> valueNames = loadValueNames(reloaded);

        List<CriterionResult> met = new ArrayList<>();
        List<CriterionResult> unmet = new ArrayList<>();

        DealQualifierService.evaluateRegion("AMERICAS", userLocationsByLevel, reloaded,
                valueNames, met, unmet);

        assertThat(met).extracting(CriterionResult::ruleType)
                .as("BUG-063: pre-fix path emitted a met LOCATION criterion. Post-fix the "
                    + "partner's AMERICAS assignment must NOT satisfy the EMEA-only audience.")
                .doesNotContain("LOCATION");
        assertThat(unmet).hasSize(1);
        CriterionResult location = unmet.get(0);
        assertThat(location.ruleType()).isEqualTo("LOCATION");
        assertThat(location.description())
                .contains("'AMERICAS'")
                .contains("not in the eligible audience");
        assertThat(location.hint())
                .as("Unmet hint must resolve EMEA's UUID back to its name via the bulk "
                    + "LocationValueRepository.findByIdIn call so the seller sees what would qualify.")
                .contains("Region: EMEA");
    }

    @Test
    void evaluateRegion_emitsMet_whenPersistedPartnerSatisfiesRuleViaAncestorDescent_BUG063AndBUG061() {
        // BUG-063 + BUG-061 intersection through real JPA: partner is tagged ONLY at
        // Country:USA, the persisted incentive's audience names Region:AMERICAS. The
        // expanded location map built by ParticipantEligibilityChecker.buildLocationMap
        // walks USA's parent chain (which Hibernate must lazy-load) up to AMERICAS, so
        // the deal qualifier must accept the ancestor match. Locks the descent path
        // against accidental regression to strict-level matching.
        assignPartnerTo(usa);
        Incentive americasScoped = persistIncentiveWithLocationRule(
                "AMERICAS-only Incentive (descent test)", americas);

        Incentive reloaded = incentiveRepository.findById(americasScoped.getId()).orElseThrow();
        Map<UUID, Set<UUID>> userLocationsByLevel = ParticipantEligibilityChecker
                .buildLocationMap(reloadAssignments());
        Map<UUID, String> valueNames = loadValueNames(reloaded);

        List<CriterionResult> met = new ArrayList<>();
        List<CriterionResult> unmet = new ArrayList<>();

        DealQualifierService.evaluateRegion("AMERICAS", userLocationsByLevel, reloaded,
                valueNames, met, unmet);

        assertThat(unmet).extracting(CriterionResult::ruleType)
                .as("Country:USA-tagged partner must qualify for Region:AMERICAS via ancestor walk.")
                .doesNotContain("LOCATION");
        assertThat(met).hasSize(1);
        CriterionResult location = met.get(0);
        assertThat(location.ruleType()).isEqualTo("LOCATION");
        assertThat(location.description())
                .contains("Region")
                .contains("'AMERICAS'");
    }

    private void assignPartnerTo(LocationValue value) {
        partnerLocationRepository.save(PartnerCompanyLocation.builder()
                .clientId(client.getId())
                .partnerCompany(partner)
                .locationValue(value)
                .build());
    }

    private List<PartnerCompanyLocation> reloadAssignments() {
        return partnerLocationRepository.findByPartnerCompanyId(partner.getId());
    }

    private Incentive persistIncentiveWithLocationRule(String name, LocationValue value) {
        Incentive incentive = Incentive.builder()
                .name(name)
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.ACTIVE)
                .clientId(client.getId())
                .createdBy(seller.getId())
                .startDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
        IncentiveAudienceRule rule = IncentiveAudienceRule.builder()
                .incentive(incentive)
                .ruleType("LOCATION")
                .ruleValue(value.getId().toString())
                .locationLevel(value.getLevel())
                .build();
        incentive.getAudienceRules().add(rule);
        return incentiveRepository.save(incentive);
    }

    private Map<UUID, String> loadValueNames(Incentive incentive) {
        List<UUID> ruleValueIds = incentive.getAudienceRules().stream()
                .filter(r -> "LOCATION".equalsIgnoreCase(r.getRuleType()))
                .map(IncentiveAudienceRule::getRuleValue)
                .map(UUID::fromString)
                .distinct()
                .toList();
        return valueRepository.findByIdIn(ruleValueIds).stream()
                .collect(Collectors.toMap(LocationValue::getId, LocationValue::getName));
    }
}
